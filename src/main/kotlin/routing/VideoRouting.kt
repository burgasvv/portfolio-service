package org.burgas.routing

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.burgas.service.VideoService
import java.util.UUID

fun Application.configureVideoRouting() {

    val videoService = VideoService()

    routing {

        route("/api/v1/videos") {

            get("/by-id") {
                val videoId = UUID.fromString(call.parameters["videoId"])
                val videoEntity = videoService.findEntity(videoId)
                call.respondBytes(
                    ContentType.parse(videoEntity.contentType),
                    HttpStatusCode.OK
                ) { videoEntity.data.bytes }
            }
        }
    }
}