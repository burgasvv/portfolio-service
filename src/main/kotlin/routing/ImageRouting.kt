package org.burgas.routing

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.burgas.service.ImageService
import java.util.UUID

fun Application.configureImageRouting() {

    val imageService = ImageService()

    routing {

        route("/api/v1/images") {

            get("/by-id") {
                val imageId = UUID.fromString(call.parameters["imageId"])
                val imageEntity = imageService.findEntity(imageId)
                call.respondBytes(
                    ContentType.parse(imageEntity.contentType),
                    HttpStatusCode.OK
                ) { imageEntity.data.bytes }
            }
        }
    }
}