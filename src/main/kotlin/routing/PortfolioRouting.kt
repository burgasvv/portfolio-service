package org.burgas.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import org.burgas.database.DatabaseFactory
import org.burgas.database.IdentityEntity
import org.burgas.database.IdentityTable
import org.burgas.database.PortfolioEntity
import org.burgas.dto.PortfolioRequest
import org.burgas.service.PortfolioService
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun Application.configurePortfolioRouting() {

    val portfolioService = PortfolioService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {

            if (call.request.path().equals("/api/v1/portfolios/create", false)) {
                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")

                val portfolioRequest = call.receive(PortfolioRequest::class)
                val identityId = portfolioRequest.identityId
                    ?: throw IllegalArgumentException("Portfolio identityId is null for authentication")

                val identityEntity = newSuspendedTransaction(
                    db = DatabaseFactory.POSTGRES_REPLICA,
                    context = Dispatchers.Default,
                    readOnly = true
                ) {
                    IdentityEntity.find { IdentityTable.email eq principal.name }.singleOrNull()
                }
                if (identityEntity != null) {

                    if (identityEntity.id.value == identityId) {
                        call.attributes[AttributeKey<PortfolioRequest>("portfolioRequest")] = portfolioRequest
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }

                } else {
                    throw IllegalArgumentException("Identity not authenticated")
                }

            } else if (call.request.path().equals("/api/v1/portfolios/update", false)) {
                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")

                val portfolioRequest = call.receive(PortfolioRequest::class)
                val portfolioId =
                    portfolioRequest.id ?: throw IllegalArgumentException("Portfolio id is null for authentication")

                newSuspendedTransaction(
                    db = DatabaseFactory.POSTGRES_REPLICA,
                    context = Dispatchers.Default,
                    readOnly = true
                ) {
                    val portfolioEntity = PortfolioEntity.findById(portfolioId)
                        ?: throw IllegalArgumentException("Portfolio not found for authentication")

                    if (portfolioEntity.identity.email == principal.name) {
                        call.attributes[AttributeKey<PortfolioRequest>("portfolioRequest")] = portfolioRequest
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (
                call.request.path().equals("/api/v1/portfolios/delete", false) ||
                call.request.path().equals("/api/v1/portfolios/upload-image", false) ||
                call.request.path().equals("/api/v1/portfolios/remove-image", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")
                val portfolioId = UUID.fromString(call.parameters["portfolioId"])

                newSuspendedTransaction(
                    db = DatabaseFactory.POSTGRES_REPLICA,
                    context = Dispatchers.Default,
                    readOnly = true
                ) {
                    val portfolioEntity = (PortfolioEntity.findById(portfolioId)
                        ?: throw IllegalArgumentException("Portfolio not found for authentication"))

                    if (portfolioEntity.identity.email == principal.name) {
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else {
                proceed()
            }
        }

        route("/api/v1/portfolios") {

            get {
                call.respond(HttpStatusCode.OK, portfolioService.findAll())
            }

            get("/by-id") {
                val portfolioId = UUID.fromString(call.parameters["portfolioId"])
                call.respond(HttpStatusCode.OK, portfolioService.findById(portfolioId))
            }

            authenticate("basic-auth-all") {

                post("/create") {
                    val portfolioRequest = call.attributes[AttributeKey<PortfolioRequest>("portfolioRequest")]
                    val portfolioFullResponse = portfolioService.create(portfolioRequest)
                    call.respondRedirect("/api/v1/portfolios/by-id?portfolioId=${portfolioFullResponse.id}")
                }

                post("/update") {
                    val portfolioRequest = call.attributes[AttributeKey<PortfolioRequest>("portfolioRequest")]
                    val portfolioFullResponse = portfolioService.update(portfolioRequest)
                    call.respondRedirect("/api/v1/portfolios/by-id?portfolioId=${portfolioFullResponse.id}")
                }

                delete("/delete") {
                    val portfolioId = UUID.fromString(call.parameters["portfolioId"])
                    portfolioService.delete(portfolioId)
                    call.respond(HttpStatusCode.OK)
                }

                post("/upload-image") {
                    val portfolioId = UUID.fromString(call.parameters["portfolioId"])
                    val portfolioEntity = portfolioService.uploadImage(portfolioId, call.receiveMultipart())
                    call.respondRedirect("/api/v1/portfolios/by-id?portfolioId=${portfolioEntity.id.value}")
                }

                post("/remove-image") {
                    val portfolioId = UUID.fromString(call.parameters["portfolioId"])
                    val portfolioEntity = portfolioService.removeImage(portfolioId)
                    call.respondRedirect("/api/v1/portfolios/by-id?portfolioId=${portfolioEntity.id.value}")
                }
            }
        }
    }
}