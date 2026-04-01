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
import org.burgas.database.PortfolioEntity
import org.burgas.database.ProjectEntity
import org.burgas.dto.FileRequest
import org.burgas.dto.ProjectRequest
import org.burgas.service.ProjectService
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun Application.configureProjectRouting() {

    val projectService = ProjectService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {

            if (call.request.path().equals("/api/v1/projects/create", false)) {
                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")

                val projectRequest = call.receive(ProjectRequest::class)
                val portfolioId = projectRequest.portfolioId
                    ?: throw IllegalArgumentException("Project portfolioId is null for authentication")

                newSuspendedTransaction(
                    db = DatabaseFactory.POSTGRES_REPLICA,
                    context = Dispatchers.Default,
                    readOnly = true
                ) {
                    val portfolioEntity = PortfolioEntity.findById(portfolioId)
                        ?: throw IllegalArgumentException("Project Portfolio not found for authentication")

                    if (portfolioEntity.identity.email == principal.name) {
                        call.attributes[AttributeKey<ProjectRequest>("projectRequest")] = projectRequest
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (call.request.path().equals("/api/v1/projects/update", false)) {
                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")

                val projectRequest = call.receive(ProjectRequest::class)
                val projectId =
                    projectRequest.id ?: throw IllegalArgumentException("Project id is null for authentication")

                newSuspendedTransaction(
                    db = DatabaseFactory.POSTGRES_REPLICA,
                    context = Dispatchers.Default,
                    readOnly = true
                ) {
                    val projectEntity = ProjectEntity.findById(projectId)
                        ?: throw IllegalArgumentException("Project not found for authentication")

                    if (projectEntity.portfolio.identity.email == principal.name) {
                        call.attributes[AttributeKey<ProjectRequest>("projectRequest")] = projectRequest
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (
                call.request.path().equals("/api/v1/projects/delete", false) ||
                call.request.path().equals("/api/v1/projects/upload-image", false) ||
                call.request.path().equals("/api/v1/projects/remove-image", false) ||
                call.request.path().equals("/api/v1/projects/upload-images", false) ||
                call.request.path().equals("/api/v1/projects/remove-images", false) ||
                call.request.path().equals("/api/v1/projects/upload-videos", false) ||
                call.request.path().equals("/api/v1/projects/remove-videos", false) ||
                call.request.path().equals("/api/v1/projects/upload-documents", false) ||
                call.request.path().equals("/api/v1/projects/remove-documents", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")

                val projectId = UUID.fromString(call.parameters["projectId"])

                newSuspendedTransaction(
                    db = DatabaseFactory.POSTGRES_REPLICA,
                    context = Dispatchers.Default,
                    readOnly = true
                ) {
                    val projectEntity = ProjectEntity.findById(projectId)
                        ?: throw IllegalArgumentException("Project not found for authentication")

                    if (projectEntity.portfolio.identity.email == principal.name) {
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else {
                proceed()
            }
        }

        route("/api/v1/projects") {

            get {
                call.respond(HttpStatusCode.OK, projectService.findAll())
            }

            get("/by-id") {
                val projectId = UUID.fromString(call.parameters["projectId"])
                call.respond(HttpStatusCode.OK, projectService.findById(projectId))
            }

            authenticate("basic-auth-all") {

                post("/create") {
                    val projectRequest = call.attributes[AttributeKey<ProjectRequest>("projectRequest")]
                    val projectFullResponse = projectService.create(projectRequest)
                    call.respondRedirect("/api/v1/projects/by-id?projectId=${projectFullResponse.id}")
                }

                post("/update") {
                    val projectRequest = call.attributes[AttributeKey<ProjectRequest>("projectRequest")]
                    val projectFullResponse = projectService.update(projectRequest)
                    call.respondRedirect("/api/v1/projects/by-id?projectId=${projectFullResponse.id}")
                }

                delete("/delete") {
                    val projectId = UUID.fromString(call.parameters["projectId"])
                    projectService.delete(projectId)
                    call.respond(HttpStatusCode.OK)
                }

                post("/upload-image") {
                    val projectId = UUID.fromString(call.parameters["projectId"])
                    val projectEntity = projectService.uploadImage(projectId, call.receiveMultipart(Long.MAX_VALUE))
                    call.respondRedirect("/api/v1/projects/by-id?projectId=${projectEntity.id.value}")
                }

                post("/remove-image") {
                    val projectId = UUID.fromString(call.parameters["projectId"])
                    val projectEntity = projectService.removeImage(projectId)
                    call.respondRedirect("/api/v1/projects/by-id?projectId=${projectEntity.id.value}")
                }

                post("/upload-images") {
                    val projectId = UUID.fromString(call.parameters["projectId"])
                    val projectEntity = projectService.uploadImages(projectId, call.receiveMultipart(Long.MAX_VALUE))
                    call.respondRedirect("/api/v1/projects/by-id?projectId=${projectEntity.id.value}")
                }

                post("/remove-images") {
                    val projectId = UUID.fromString(call.parameters["projectId"])
                    val fileRequest = call.receive(FileRequest::class)
                    val projectEntity = projectService.removeImages(projectId, fileRequest)
                    call.respondRedirect("/api/v1/projects/by-id?projectId=${projectEntity.id.value}")
                }

                post("/upload-videos") {
                    val projectId = UUID.fromString(call.parameters["projectId"])
                    val projectEntity = projectService.uploadVideos(projectId, call.receiveMultipart(Long.MAX_VALUE))
                    call.respondRedirect("/api/v1/projects/by-id?projectId=${projectEntity.id.value}")
                }

                post("/remove-videos") {
                    val projectId = UUID.fromString(call.parameters["projectId"])
                    val fileRequest = call.receive(FileRequest::class)
                    val projectEntity = projectService.removeVideos(projectId, fileRequest)
                    call.respondRedirect("/api/v1/projects/by-id?projectId=${projectEntity.id.value}")
                }

                post("/upload-documents") {
                    val projectId = UUID.fromString(call.parameters["projectId"])
                    val projectEntity = projectService.uploadDocuments(projectId, call.receiveMultipart(Long.MAX_VALUE))
                    call.respondRedirect("/api/v1/projects/by-id?projectId=${projectEntity.id.value}")
                }

                post("/remove-documents") {
                    val projectId = UUID.fromString(call.parameters["projectId"])
                    val fileRequest = call.receive(FileRequest::class)
                    val projectEntity = projectService.removeDocuments(projectId, fileRequest)
                    call.respondRedirect("/api/v1/projects/by-id?projectId=${projectEntity.id.value}")
                }
            }
        }
    }
}