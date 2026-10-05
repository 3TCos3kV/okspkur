package com.acute.infrastructure.seed

import at.favre.lib.crypto.bcrypt.BCrypt
import java.sql.Connection
import java.sql.DriverManager

enum class Volume(
    val users: Int,
    val specialists: Int,
    val specialties: Int,
    val slotsPerSpecialist: Int,
    val bookings: Int,
) {
    SMALL(users = 50, specialists = 10, specialties = 3, slotsPerSpecialist = 50, bookings = 300),
    WORKING(users = 3_000, specialists = 200, specialties = 10, slotsPerSpecialist = 1_000, bookings = 80_000),
}

const val DEMO_PASSWORD = "demo"

private val SPECIALTIES = listOf(
    "Терапевт", "Кардиолог", "Невролог", "Офтальмолог", "Стоматолог",
    "Дерматолог", "Эндокринолог", "Хирург", "Оториноларинголог", "Психотерапевт",
)
private val SERVICE_KINDS = listOf("Консультация", "Приём", "Расширенный приём")
private val SURNAMES = listOf(
    "Иванов", "Смирнов", "Кузнецов", "Попов", "Васильев",
    "Петров", "Соколов", "Михайлов", "Новиков", "Фёдоров",
)
private val INITIALS = listOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "К")

private const val SCHEDULE_START = "2026-09-01 09:00+03"
private const val SCHEDULE_DAYS = 180
private const val SLOT_STEP_MIN = 90
private const val SLOTS_PER_DAY = 6
private const val BOOKING_PERMUTATION = 7919

fun main(args: Array<String>) {
    val volume = args.singleOrNull()?.let { Volume.valueOf(it.uppercase()) }
        ?: error("Укажите объём наполнения: small или working")
    val env = System.getenv()
    DriverManager.getConnection(
        env["DATABASE_URL"] ?: "jdbc:postgresql://localhost:26432/ivan_tikhonov",
        env["DB_USER"] ?: "ivan_tikhonov",
        env["DB_PASSWORD"] ?: "changeme",
    ).use { Seeder(it, env["DB_SCHEMA"] ?: "ivan_tikhonov").run(volume) }
}

class Seeder(private val connection: Connection, private val schema: String) {

    init {
        require(schema.matches(Regex("[a-z_][a-z0-9_]*"))) { "Недопустимое имя схемы: $schema" }
    }

    fun run(volume: Volume) {
        connection.autoCommit = false
        execute("CREATE EXTENSION IF NOT EXISTS pg_stat_statements WITH SCHEMA public")
        execute("DROP SCHEMA IF EXISTS $schema CASCADE")
        execute("CREATE SCHEMA $schema")
        execute("SET search_path TO $schema")
        execute(javaClass.getResource("/db/schema.sql")!!.readText())
        insertUsers(volume)
        insertSpecialists(volume)
        insertServices(volume)
        insertSlots(volume)
        insertBookings(volume)
        execute("SELECT setval('bookings_id_seq', (SELECT max(id) FROM bookings))")
        execute("ANALYZE")
        connection.commit()
        printCounts()
    }

    private fun insertUsers(volume: Volume) {
        // Соль фиксирована, чтобы повторное наполнение давало ту же базу байт в байт.
        val hash = String(BCrypt.withDefaults().hash(10, ByteArray(16), DEMO_PASSWORD.toByteArray()))
        execute(
            """
            INSERT INTO users (id, login, password_hash, full_name)
            SELECT 1, 'admin', '$hash', 'Администратор'
            UNION ALL
            SELECT i, 'client' || lpad((i - 1)::text, 5, '0'), '$hash', ${personName("i")}
            FROM generate_series(2, ${volume.users}) AS i
            """,
        )
    }

    private fun insertSpecialists(volume: Volume) = execute(
        """
        INSERT INTO specialists (id, full_name, specialty)
        SELECT i, ${personName("i + 7")}, ${array(SPECIALTIES)}[1 + (i - 1) % ${volume.specialties}]
        FROM generate_series(1, ${volume.specialists}) AS i
        """,
    )

    private fun insertServices(volume: Volume) = execute(
        """
        INSERT INTO services (id, name, specialty, duration_min)
        SELECT j * 3 + k + 1,
               ${array(SPECIALTIES)}[j + 1] || ': ' || lower(${array(SERVICE_KINDS)}[k + 1]),
               ${array(SPECIALTIES)}[j + 1],
               30 * (k + 1)
        FROM generate_series(0, ${volume.specialties - 1}) AS j,
             generate_series(0, ${SERVICE_KINDS.size - 1}) AS k
        """,
    )

    // Слоты не пересекаются, пока slotsPerSpecialist <= SLOTS_PER_DAY * SCHEDULE_DAYS: тогда в один день
    // попадают не больше SLOTS_PER_DAY соседних k, у них разные k % SLOTS_PER_DAY, а длина слота не больше шага.
    private fun insertSlots(volume: Volume) = execute(
        """
        INSERT INTO slots (id, specialist_id, starts_at, ends_at)
        SELECT (s - 1) * ${volume.slotsPerSpecialist} + k + 1, s, t, t + make_interval(mins => 30 * (1 + (k + s) % 3))
        FROM generate_series(1, ${volume.specialists}) AS s,
             generate_series(0, ${volume.slotsPerSpecialist - 1}) AS k,
             LATERAL (
                 SELECT timestamptz '$SCHEDULE_START'
                        + make_interval(days => k * $SCHEDULE_DAYS / ${volume.slotsPerSpecialist})
                        + make_interval(mins => $SLOT_STEP_MIN * (k % $SLOTS_PER_DAY))
             ) AS start(t)
        """,
    )

    // Умножение на простое число по модулю числа слотов — перестановка: занятыми оказываются ровно
    // volume.bookings слотов, разбросанных по всему расписанию, причём без random() и зависимости от сида.
    private fun insertBookings(volume: Volume) {
        val slots = volume.specialists * volume.slotsPerSpecialist
        execute(
            """
            INSERT INTO bookings (id, slot_id, service_id, client_id, status, created_at, cancelled_at)
            SELECT row_number() OVER (ORDER BY sl.id),
                   sl.id,
                   sv.id,
                   2 + sl.id % ${volume.users - 1},
                   CASE WHEN sl.id % 7 = 0 THEN 'cancelled' ELSE 'active' END,
                   sl.starts_at - make_interval(days => 1 + (sl.id % 20)::int),
                   CASE WHEN sl.id % 7 = 0 THEN sl.starts_at - make_interval(days => (sl.id % 20)::int, hours => 12) END
            FROM slots sl
            JOIN specialists sp ON sp.id = sl.specialist_id
            JOIN services sv ON sv.specialty = sp.specialty
                            AND sv.duration_min = 30 * (1 + sl.id % (extract(epoch FROM sl.ends_at - sl.starts_at)::int / 1800))
            WHERE sl.id * $BOOKING_PERMUTATION % $slots < ${volume.bookings}
            """,
        )
    }

    private fun printCounts() {
        listOf("users", "specialists", "services", "slots", "bookings").forEach { table ->
            connection.createStatement().use { st ->
                st.executeQuery("SELECT count(*) FROM $table").use { rs ->
                    rs.next()
                    println("%-12s %,d".format(table, rs.getLong(1)))
                }
            }
        }
    }

    private fun personName(seed: String) =
        "${array(SURNAMES)}[1 + ($seed) % 10] || ' ' || ${array(INITIALS)}[1 + ($seed) / 10 % 10] || '. ' || " +
            "${array(INITIALS)}[1 + ($seed) / 100 % 10] || '.'"

    private fun array(values: List<String>) = values.joinToString(prefix = "(ARRAY[", postfix = "])") { "'$it'" }

    private fun execute(sql: String) {
        connection.createStatement().use { it.execute(sql.trimIndent()) }
    }
}
