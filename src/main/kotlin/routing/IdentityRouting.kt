package org.burgas.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.UserPasswordCredential
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Dispatchers
import org.burgas.database.DatabaseFactory
import org.burgas.database.IdentityEntity
import org.burgas.database.IdentityTable
import org.burgas.dto.IdentityRequest
import org.burgas.service.IdentityService
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

fun Application.configureIdentityRouting() {

    val identityService = IdentityService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {
            if (
                call.request.path().equals("/api/v1/identities/delete", false) ||
                call.request.path().equals("/api/v1/identities/upload-image", false) ||
                call.request.path().equals("/api/v1/identities/remove-image", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")
                val identityId = UUID.fromString(call.parameters["identityId"])

                val identityEntity = newSuspendedTransaction(
                    db = DatabaseFactory.POSTGRES_REPLICA,
                    context = Dispatchers.Default,
                    readOnly = true
                ) {
                    IdentityEntity.find { IdentityTable.email eq principal.name }.singleOrNull()
                }
                if (identityEntity != null) {

                    if (identityEntity.id.value == identityId) {
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }

                } else {
                    throw IllegalArgumentException("Identity not authenticated")
                }

            } else if (
                call.request.path().equals("/api/v1/identities/update", false) ||
                call.request.path().equals("/api/v1/identities/change-password", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")
                val identityRequest = call.receive(IdentityRequest::class)
                val identityId = identityRequest.id ?: throw IllegalArgumentException("identity id is null")

                val identityEntity = newSuspendedTransaction(
                    db = DatabaseFactory.POSTGRES_REPLICA,
                    context = Dispatchers.Default,
                    readOnly = true
                ) {
                    IdentityEntity.find { IdentityTable.email eq principal.name }.singleOrNull()
                }
                if (identityEntity != null) {

                    if (identityEntity.id.value == identityId) {
                        call.attributes[AttributeKey<IdentityRequest>("identityRequest")] = identityRequest
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }

                } else {
                    throw IllegalArgumentException("Identity not authenticated")
                }

            } else if (call.request.path().equals("/api/v1/identities/change-status", false)) {
                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")
                val identityRequest = call.receive(IdentityRequest::class)
                val identityId = identityRequest.id ?: throw IllegalArgumentException("identity id is null")

                val identityEntity = newSuspendedTransaction(
                    db = DatabaseFactory.POSTGRES_REPLICA,
                    context = Dispatchers.Default,
                    readOnly = true
                ) {
                    IdentityEntity.find { IdentityTable.email eq principal.name }.singleOrNull()
                }

                if (identityEntity != null) {

                    if (identityEntity.id.value != identityId) {
                        call.attributes[AttributeKey<IdentityRequest>("identityRequest")] = identityRequest
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized, can't be deactivated by himself")
                    }

                } else {
                    throw IllegalArgumentException("Identity not authenticated")
                }

            } else {
                proceed()
            }
        }

        route("/api/v1/identities") {

            post("/create") {
                val identityRequest = call.receive(IdentityRequest::class)
                val identityFullResponse = identityService.create(identityRequest)
                call.respondRedirect("/api/v1/identities/by-id?identityId=${identityFullResponse.id}", false)
            }

            get {
                call.respond(HttpStatusCode.OK, identityService.findAll())
            }

            get("/by-id") {
                val identityId = UUID.fromString(call.parameters["identityId"])
                call.respond(HttpStatusCode.OK, identityService.findById(identityId))
            }

            authenticate("basic-auth-admin") {

                put("/change-status") {
                    val identityRequest = call.attributes[AttributeKey<IdentityRequest>("identityRequest")]
                    identityService.changeStatus(identityRequest)
                    call.respond(HttpStatusCode.OK)
                }
            }

            authenticate("basic-auth-all") {

                post("/update") {
                    val identityRequest = call.attributes[AttributeKey<IdentityRequest>("identityRequest")]
                    val identityFullResponse = identityService.update(identityRequest)
                    call.respondRedirect("/api/v1/identities/by-id?identityId=${identityFullResponse.id}", false)
                }

                delete("/delete") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    identityService.delete(identityId)
                    call.respond(HttpStatusCode.OK)
                }

                post("/change-password") {
                    val identityRequest = call.attributes[AttributeKey<IdentityRequest>("identityRequest")]
                    val identityEntity = identityService.changePassword(identityRequest)
                    call.respondRedirect("/api/v1/identities/by-id?identityId=${identityEntity.id.value}", false)
                }

                post("/upload-image") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    val identityEntity = identityService.uploadImage(identityId, call.receiveMultipart(Long.MAX_VALUE))
                    call.respondRedirect("/api/v1/identities/by-id?identityId=${identityEntity.id.value}", false)
                }

                post("/remove-image") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    val identityEntity = identityService.removeImage(identityId)
                    call.respondRedirect("/api/v1/identities/by-id?identityId=${identityEntity.id.value}", false)
                }
            }
        }
    }
}