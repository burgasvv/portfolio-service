package routing

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate
import org.burgas.database.Authority
import org.burgas.database.DatabaseFactory
import org.burgas.database.IdentityEntity
import org.burgas.database.IdentityTable
import org.burgas.dto.IdentityRequest
import org.burgas.module
import org.burgas.security.CsrfToken
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import kotlin.test.Test
import kotlin.test.assertEquals

class IdentityRouterTest {

    @Test
    fun `test identity endpoints`() = testApplication {
        this.application {
            module()
        }
        val httpClient = this.createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val csrfToken = httpClient.get("/api/v1/security/csrf-token") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }.body<CsrfToken>()

        httpClient.post("/api/v1/identities/create") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.token)
            val identityRequest = IdentityRequest(
                authority = Authority.ADMIN,
                email = "admin@gmail.com",
                password = "admin",
                phone = "+79453215689",
                enabled = true,
                firstname = "Admin",
                lastname = "Admin",
                patronymic = "Admin",
                birthdate = LocalDate(1990, 2, 23),
                about = "Описание Admin аккаунта"
            )
            setBody(identityRequest)
        }
            .apply {
                assertEquals(HttpStatusCode.Found, this.status)
            }

        val identityEntity = newSuspendedTransaction(
            readOnly = true, db = DatabaseFactory.POSTGRES_REPLICA, context = Dispatchers.Default
        ) {
            IdentityEntity.find { IdentityTable.email eq "admin@gmail.com" }.single()
        }
        httpClient.post("/api/v1/identities/update") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
            val identityRequest = IdentityRequest(
                id = identityEntity.id.value,
                telegram = "@Admin",
                whatsUp = "@Admin",
                max = "@Admin"
            )
            setBody(identityRequest)
        }
            .apply {
                assertEquals(HttpStatusCode.Found, this.status)
            }

        httpClient.get("/api/v1/identities/by-id") {
            parameter("identityId", identityEntity.id.value)
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }

        httpClient.delete("/api/v1/identities/delete") {
            parameter("identityId", identityEntity.id.value)
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }
    }
}