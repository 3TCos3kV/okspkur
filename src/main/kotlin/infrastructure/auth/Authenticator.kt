package com.acute.infrastructure.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.acute.infrastructure.db.Users
import com.acute.infrastructure.db.dbTransaction
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import java.time.Clock
import java.time.Duration

data class JwtConfig(val secret: String, val issuer: String, val audience: String, val ttl: Duration)
data class UserCredentials(val id: Long, val passwordHash: String)

class Authenticator(
    private val findUser: (String) -> UserCredentials?,
    private val jwt: JwtConfig,
    private val clock: Clock,
) {
    fun login(login: String, password: String): String? {
        val user = findUser(login) ?: return null
        if (!BCrypt.verifyer().verify(password.toCharArray(), user.passwordHash).verified) return null
        return JWT.create()
            .withIssuer(jwt.issuer)
            .withAudience(jwt.audience)
            .withSubject(user.id.toString())
            .withExpiresAt(clock.instant().plus(jwt.ttl))
            .sign(Algorithm.HMAC256(jwt.secret))
    }
}

fun findUserByLogin(database: Database): (String) -> UserCredentials? = { login ->
    dbTransaction(database) {
        val row = Users.select(Users.id, Users.passwordHash).where { Users.login eq login }
            .orderBy(Users.id to SortOrder.ASC).limit(1).singleOrNull()
        if (row == null) null else UserCredentials(row[Users.id], row[Users.passwordHash])
    }
}
