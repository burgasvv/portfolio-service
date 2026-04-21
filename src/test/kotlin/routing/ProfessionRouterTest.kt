package routing

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate
import org.burgas.database.*
import org.burgas.dto.ProfessionRequest
import org.burgas.module
import org.burgas.security.CsrfToken
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfessionRouterTest {

    @Test
    fun `test profession endpoints`() = testApplication {
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

        val identityEntity = newSuspendedTransaction(
            db = DatabaseFactory.POSTGRES_MASTER,
            context = Dispatchers.Default,
            transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
        ) {
            IdentityEntity.new {
                this.authority = Authority.ADMIN
                this.email = "admin@gmail.com"
                this.password = "admin"
                this.phone = "+79456321547"
                this.enabled = true
                this.firstname = "Admin"
                this.lastname = "Admin"
                this.patronymic = "Admin"
                this.birthdate = LocalDate(1990, 12, 12)
                this.about = "Admin about"
            }
        }

        httpClient.post("/api/v1/professions/create") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
            val professionRequest = ProfessionRequest(
                name = "Test Profession",
                description = "Description test profession"
            )
            setBody(professionRequest)
        }
            .apply {
                assertEquals(HttpStatusCode.Found, this.status)
            }

        val professionEntity = newSuspendedTransaction(
            db = DatabaseFactory.POSTGRES_REPLICA, readOnly = true, context = Dispatchers.Default
        ) {
            ProfessionEntity.find { ProfessionTable.name eq "Test Profession" }.single()
        }

        httpClient.post("/api/v1/professions/update") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
            val professionRequest = ProfessionRequest(
                id = professionEntity.id.value,
                description = "Description about Test Profession"
            )
            setBody(professionRequest)
        }
            .apply {
                assertEquals(HttpStatusCode.Found, this.status)
            }

        httpClient.get("/api/v1/professions/by-id") {
            parameter("professionId", professionEntity.id.value)
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }

        httpClient.delete("/api/v1/professions/delete") {
            parameter("professionId", professionEntity.id.value)
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }

        newSuspendedTransaction(
            db = DatabaseFactory.POSTGRES_MASTER,
            context = Dispatchers.Default,
            transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
        ) {
            identityEntity.delete()
        }
    }
}