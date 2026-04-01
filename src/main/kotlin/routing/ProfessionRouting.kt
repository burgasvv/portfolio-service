package org.burgas.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.burgas.dto.ProfessionRequest
import org.burgas.service.ProfessionService
import java.util.UUID

fun Application.configureProfessionRouting() {

    val professionService = ProfessionService()

    routing {

        route("/api/v1/professions") {

            get {
                call.respond(HttpStatusCode.OK, professionService.findAll())
            }

            get("/by-id") {
                val professionId = UUID.fromString(call.parameters["professionId"])
                call.respond(HttpStatusCode.OK, professionService.findById(professionId))
            }

            authenticate("basic-auth-admin") {

                post("/create") {
                    val professionRequest = call.receive(ProfessionRequest::class)
                    val professionFullResponse = professionService.create(professionRequest)
                    call.respondRedirect("/api/v1/professions/by-id?professionId=${professionFullResponse.id}")
                }

                post("/update") {
                    val professionRequest = call.receive(ProfessionRequest::class)
                    val professionFullResponse = professionService.update(professionRequest)
                    call.respondRedirect("/api/v1/professions/by-id?professionId=${professionFullResponse.id}")
                }

                delete("/delete") {
                    val professionId = UUID.fromString(call.parameters["professionId"])
                    professionService.delete(professionId)
                    call.respond(HttpStatusCode.OK)
                }

                post("/upload-image") {
                    val professionId = UUID.fromString(call.parameters["professionId"])
                    val professionEntity =
                        professionService.uploadImage(professionId, call.receiveMultipart(Long.MAX_VALUE))
                    call.respondRedirect("/api/v1/professions/by-id?professionId=${professionEntity.id.value}")
                }

                post("/remove-image") {
                    val professionId = UUID.fromString(call.parameters["professionId"])
                    val professionEntity = professionService.removeImage(professionId)
                    call.respondRedirect("/api/v1/professions/by-id?professionId=${professionEntity.id.value}")
                }
            }
        }
    }
}