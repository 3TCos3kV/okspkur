package com.acute.presentation

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ApiTest {
    private fun apiTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment { config = MapApplicationConfig() }
        application { configure(fakeComponents()) }
        block()
    }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun ApplicationTestBuilder.token(): String {
        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"admin","password":"demo"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return checkNotNull(response.json()["token"]).jsonPrimitive.content
    }

    private suspend fun ApplicationTestBuilder.get(path: String): HttpResponse {
        val token = token()
        return client.get(path) { bearerAuth(token) }
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String? = null): HttpResponse {
        val token = token()
        return client.post(path) {
            bearerAuth(token)
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    private suspend fun HttpResponse.error(status: HttpStatusCode, message: String) {
        assertEquals(status, this.status)
        assertEquals(setOf("error"), json().keys)
        assertEquals(message, json().getValue("error").jsonPrimitive.content)
    }

    private fun metrics(response: HttpResponse) {
        assertTrue(checkNotNull(response.headers["X-Response-Time-Ms"]).matches(Regex("[0-9]+\\.[0-9]{2}")))
        assertTrue(checkNotNull(response.headers["X-Db-Time-Ms"]).matches(Regex("[0-9]+\\.[0-9]{2}")))
        assertNotNull(response.headers["X-Db-Queries"]?.toIntOrNull())
    }

    @Test fun `список записей без токена отвечает 401`() = apiTest {
        client.get("/api/bookings").error(HttpStatusCode.Unauthorized, "Требуется авторизация")
    }
    @Test fun `вход с неверным паролем отвечает 401`() = apiTest {
        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"admin","password":"wrong"}""")
        }
        response.error(HttpStatusCode.Unauthorized, "Неверные логин или пароль")
    }
    @Test fun `вход с верным паролем возвращает токен`() = apiTest {
        assertTrue(token().split('.').size == 3)
    }
    @Test fun `список записей возвращает items и total`() = apiTest {
        val response = get("/api/bookings")
        assertEquals(HttpStatusCode.OK, response.status)
        val result = response.json()
        assertEquals(setOf("items", "total"), result.keys)
        assertEquals(2, result.getValue("items").jsonArray.size)
        assertEquals(2L, result.getValue("total").jsonPrimitive.long)
        val row = result.getValue("items").jsonArray.first().jsonObject
        assertEquals(setOf("id", "startsAt", "endsAt", "status", "specialist", "service", "clientName"), row.keys)
        assertEquals(setOf("id", "name"), row.getValue("service").jsonObject.keys)
        assertEquals(setOf("id", "fullName", "specialty"), row.getValue("specialist").jsonObject.keys)
    }
    @Test fun `список записей соблюдает размер страницы`() = apiTest {
        val response = get("/api/bookings?size=1&page=2")
        assertEquals(HttpStatusCode.OK, response.status)
        val result = response.json()
        assertEquals(1, result.getValue("items").jsonArray.size)
        assertEquals(1L, result.getValue("items").jsonArray.first().jsonObject.getValue("id").jsonPrimitive.long)
        assertEquals(2L, result.getValue("total").jsonPrimitive.long)
    }
    @Test fun `размер страницы вне диапазона отвечает 422`() = apiTest {
        get("/api/bookings?size=0").error(HttpStatusCode.UnprocessableEntity, "Параметр size должен быть от 1 до 100")
    }
    @Test fun `неверная дата в фильтре отвечает 422`() = apiTest {
        get("/api/bookings?from=2026-02-30").error(
            HttpStatusCode.UnprocessableEntity, "Параметр from должен быть датой в формате YYYY-MM-DD",
        )
    }
    @Test fun `карточка несуществующей записи отвечает 404`() = apiTest {
        get("/api/bookings/999").error(HttpStatusCode.NotFound, "Запись не найдена")
    }
    @Test fun `карточка содержит слот специалиста услугу и клиента`() = apiTest {
        val response = get("/api/bookings/1")
        assertEquals(HttpStatusCode.OK, response.status)
        val card = response.json()
        assertEquals(
            setOf("id", "status", "createdAt", "cancelledAt", "slot", "specialist", "service", "client"), card.keys,
        )
        assertEquals(JsonNull, card.getValue("cancelledAt"))
        assertEquals("2026-09-01T03:00:00+03:00", card.getValue("createdAt").jsonPrimitive.content)
        val slot = card.getValue("slot").jsonObject
        assertEquals(setOf("id", "startsAt", "endsAt"), slot.keys)
        assertEquals("2026-10-01T10:00:00+03:00", slot.getValue("startsAt").jsonPrimitive.content)
        assertEquals("2026-10-01T10:30:00+03:00", slot.getValue("endsAt").jsonPrimitive.content)
        assertEquals(setOf("id", "fullName", "specialty"), card.getValue("specialist").jsonObject.keys)
        assertEquals(setOf("id", "name", "specialty", "durationMin"), card.getValue("service").jsonObject.keys)
        assertEquals(setOf("id", "fullName", "login"), card.getValue("client").jsonObject.keys)
    }
    @Test fun `бронирование свободного слота отвечает 201`() = apiTest {
        val response = post("/api/bookings", """{"slotId":3,"serviceId":1}""")
        assertEquals(HttpStatusCode.Created, response.status)
        val card = response.json()
        assertEquals("active", card.getValue("status").jsonPrimitive.content)
        assertEquals(1L, card.getValue("client").jsonObject.getValue("id").jsonPrimitive.long)
        assertEquals("2026-10-04T03:00:00+03:00", card.getValue("createdAt").jsonPrimitive.content)
    }
    @Test fun `бронирование занятого слота отвечает 409`() = apiTest {
        post("/api/bookings", """{"slotId":1,"serviceId":1}""").error(HttpStatusCode.Conflict, "Слот уже занят")
    }
    @Test fun `бронирование услуги другой специальности отвечает 422`() = apiTest {
        post("/api/bookings", """{"slotId":3,"serviceId":3}""").error(
            HttpStatusCode.UnprocessableEntity, "Услуга не относится к специальности специалиста",
        )
    }
    @Test fun `битое тело запроса отвечает 400`() = apiTest {
        post("/api/bookings", "{").error(HttpStatusCode.BadRequest, "Некорректное тело запроса")
    }
    @Test fun `тело без обязательного поля отвечает 400`() = apiTest {
        post("/api/bookings", """{"slotId":3}""").error(HttpStatusCode.BadRequest, "Некорректное тело запроса")
    }
    @Test fun `повторная отмена отвечает 409`() = apiTest {
        post("/api/bookings/2/cancel").error(HttpStatusCode.Conflict, "Запись уже отменена")
    }
    @Test fun `свободные слоты без обязательной даты отвечают 422`() = apiTest {
        get("/api/services/1/free-slots?from=2026-10-01").error(
            HttpStatusCode.UnprocessableEntity, "Не задан параметр to",
        )
    }
    @Test fun `свободные слоты несуществующей услуги отвечают 404`() = apiTest {
        get("/api/services/999/free-slots?from=2026-10-01&to=2026-10-31").error(
            HttpStatusCode.NotFound, "Услуга не найдена",
        )
    }
    @Test fun `сводка с to раньше from отвечает 422`() = apiTest {
        get("/api/summary?from=2026-10-31&to=2026-10-01").error(
            HttpStatusCode.UnprocessableEntity, "Параметр to раньше from",
        )
    }
    @Test fun `сводка возвращает загрузку и долю отмен`() = apiTest {
        val response = get("/api/summary?from=2026-10-01&to=2026-10-31")
        assertEquals(HttpStatusCode.OK, response.status)
        val summary = response.json()
        assertEquals(setOf("from", "to", "totalBookings", "cancelledShare", "specialists"), summary.keys)
        assertEquals(2L, summary.getValue("totalBookings").jsonPrimitive.long)
        assertEquals(0.5, summary.getValue("cancelledShare").jsonPrimitive.double)
        val specialist = summary.getValue("specialists").jsonArray.first().jsonObject
        assertEquals(setOf("id", "fullName", "specialty", "slotMinutes", "bookedMinutes", "load"), specialist.keys)
        assertEquals(120L, specialist.getValue("slotMinutes").jsonPrimitive.long)
        assertEquals(30L, specialist.getValue("bookedMinutes").jsonPrimitive.long)
        assertEquals(0.25, specialist.getValue("load").jsonPrimitive.double)
    }
    @Test fun `ответ содержит заголовки времени и числа запросов`() = apiTest {
        metrics(get("/api/bookings"))
    }
    @Test fun `ошибки и статика содержат заголовки метрик`() = apiTest {
        metrics(client.get("/api/bookings"))
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        metrics(response)
        assertEquals("0", response.headers["X-Db-Queries"])
    }
    @Test fun `отмена освобождает слот для нового бронирования`() = apiTest {
        val cancelled = post("/api/bookings/1/cancel")
        assertEquals(HttpStatusCode.OK, cancelled.status)
        assertEquals("2026-10-04T03:00:00+03:00", cancelled.json().getValue("cancelledAt").jsonPrimitive.content)
        assertEquals(HttpStatusCode.Created, post("/api/bookings", """{"slotId":1,"serviceId":1}""").status)
    }
    @Test fun `свободные слоты подходят услуге и включают отменённые`() = apiTest {
        val response = get("/api/services/2/free-slots?from=2026-10-01&to=2026-10-31")
        assertEquals(HttpStatusCode.OK, response.status)
        val result = response.json()
        assertEquals(1L, result.getValue("total").jsonPrimitive.long)
        val slot = result.getValue("items").jsonArray.single().jsonObject
        assertEquals(setOf("id", "startsAt", "endsAt", "specialist"), slot.keys)
        assertEquals(2L, slot.getValue("id").jsonPrimitive.long)
    }
    @Test fun `услуги возвращаются по контракту`() = apiTest {
        val response = get("/api/services")
        assertEquals(HttpStatusCode.OK, response.status)
        val services = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertEquals(3, services.size)
        assertEquals(setOf("id", "name", "specialty", "durationMin"), services.first().jsonObject.keys)
    }
    @Test fun `фильтры списка применяются вместе`() = apiTest {
        val response = get("/api/bookings?status=cancelled&specialistId=1&from=2026-10-02&to=2026-10-02")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1L, response.json().getValue("total").jsonPrimitive.long)
        val row = response.json().getValue("items").jsonArray.single().jsonObject
        assertEquals(2L, row.getValue("id").jsonPrimitive.long)
    }
    @Test fun `сводка включает специалистов без слотов`() = apiTest {
        val response = get("/api/summary?from=2026-12-01&to=2026-12-31")
        assertEquals(HttpStatusCode.OK, response.status)
        val result = response.json()
        assertEquals(0.0, result.getValue("cancelledShare").jsonPrimitive.double)
        assertEquals(2, result.getValue("specialists").jsonArray.size)
        assertTrue(result.getValue("specialists").jsonArray.all {
            it.jsonObject.getValue("load").jsonPrimitive.double == 0.0
        })
    }
    @Test fun `услуга длиннее слота отвечает 422`() = apiTest {
        post("/api/bookings", """{"slotId":3,"serviceId":2}""").error(
            HttpStatusCode.UnprocessableEntity, "Услуга длиннее слота",
        )
    }
    @Test fun `неправильный идентификатор карточки отвечает 404`() = apiTest {
        get("/api/bookings/abc").error(HttpStatusCode.NotFound, "Запись не найдена")
    }
    @Test fun `неверный токен отвечает 401`() = apiTest {
        client.get("/api/bookings") { bearerAuth("invalid") }.error(
            HttpStatusCode.Unauthorized, "Требуется авторизация",
        )
    }
}
