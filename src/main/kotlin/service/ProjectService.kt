package org.burgas.service

import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.cache.CacheUtil
import org.burgas.cache.RedisCacheHandler
import org.burgas.database.*
import org.burgas.dto.FileRequest
import org.burgas.dto.ProjectFullResponse
import org.burgas.dto.ProjectRequest
import org.burgas.dto.ProjectShortResponse
import org.burgas.service.contract.CrudService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class ProjectService : CrudService<ProjectRequest, ProjectShortResponse, ProjectFullResponse>,
    RedisCacheHandler<ProjectEntity> {

    private val imageService = ImageService()
    private val videoService = VideoService()
    private val documentService = DocumentService()

    override suspend fun findById(id: UUID): ProjectFullResponse = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_REPLICA, context = Dispatchers.Default, readOnly = true
    ) {
        val projectKey = CacheUtil.PROJECT_KEY.format(id)
        if (CacheUtil.REDIS.exists(projectKey)) {
            val projectString = CacheUtil.REDIS.get(projectKey)
            Json.decodeFromString<ProjectFullResponse>(projectString)
        } else {
            val projectFullResponse =
                (ProjectEntity.findById(id) ?: throw IllegalArgumentException("Project not found"))
                    .load(
                        ProjectEntity::portfolio,
                        ProjectEntity::images,
                        ProjectEntity::videos,
                        ProjectEntity::documents
                    )
                    .toProjectFullResponse()
            CacheUtil.REDIS.set(projectKey, Json.encodeToString(projectFullResponse))
            projectFullResponse
        }
    }

    override suspend fun findAll(): List<ProjectShortResponse> = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_REPLICA, context = Dispatchers.Default, readOnly = true
    ) {
        ProjectEntity.all()
            .with(ProjectEntity::images, ProjectEntity::portfolio)
            .map { it.toProjectShortResponse() }
    }

    override suspend fun create(request: ProjectRequest): ProjectFullResponse = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val project = ProjectEntity.new { this.insert(request) }
            .load(
                ProjectEntity::portfolio,
                ProjectEntity::images,
                ProjectEntity::videos,
                ProjectEntity::documents
            )
        handleCache(project)
        val projectFullResponse = project.toProjectFullResponse()
        val projectKey = CacheUtil.PROJECT_KEY.format(project.id)
        CacheUtil.REDIS.set(projectKey, Json.encodeToString(projectFullResponse))
        projectFullResponse
    }

    override suspend fun update(request: ProjectRequest): ProjectFullResponse = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val projectEntity = (ProjectEntity.findByIdAndUpdate(
            request.id ?: throw IllegalArgumentException("Project id is null")
        ) { it.update(request) } ?: throw IllegalArgumentException("Project not found"))
            .load(
                ProjectEntity::portfolio,
                ProjectEntity::images,
                ProjectEntity::videos,
                ProjectEntity::documents
            )
        handleCache(projectEntity)
        projectEntity.toProjectFullResponse()
    }

    override suspend fun delete(id: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val projectEntity = ProjectEntity.findById(id) ?: throw IllegalArgumentException("Project not found")
        projectEntity.images.forEach { it.delete() }
        projectEntity.videos.forEach { it.delete() }
        projectEntity.documents.forEach { it.delete() }
        projectEntity.delete()
        handleCache(projectEntity)
    }

    suspend fun uploadImage(projectId: UUID, multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val entity = ProjectEntity.findById(projectId) ?: throw IllegalArgumentException("Project not found")
        if (entity.image == null) {
            val imageEntity = imageService.uploadSingle(multiPartData)
            entity.apply { this.image = imageEntity }
            entity
        } else {
            throw IllegalArgumentException("Project main image already is not null")
        }
    }

    suspend fun removeImage(projectId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val entity = ProjectEntity.findById(projectId) ?: throw IllegalArgumentException("Project not found")
        val imageEntity = entity.image
        if (imageEntity != null) {
            entity.apply { this.image = null }
            imageService.removeSingle(imageEntity.id.value)
            entity
        } else {
            throw IllegalArgumentException("Project image is already null")
        }
    }

    suspend fun uploadImages(projectId: UUID, multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val entity = ProjectEntity.findById(projectId) ?: throw IllegalArgumentException("Project not found")
        val images = imageService.uploadMultiple(multiPartData)
        entity.images = SizedCollection(entity.images + images)
        handleCache(entity)
        entity
    }

    suspend fun removeImages(projectId: UUID, fileRequest: FileRequest) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val entity = ProjectEntity.findById(projectId) ?: throw IllegalArgumentException("Project not found")
        val imageEntities = ImageEntity.forIds(fileRequest.fileIds)
        entity.images = SizedCollection(entity.images - imageEntities)
        imageEntities.forEach { it.delete() }
        handleCache(entity)
        entity
    }

    suspend fun uploadVideos(projectId: UUID, multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val entity = ProjectEntity.findById(projectId) ?: throw IllegalArgumentException("Project not found")
        val videos = videoService.uploadMultiple(multiPartData)
        entity.videos = SizedCollection(entity.videos + videos)
        handleCache(entity)
        entity
    }

    suspend fun removeVideos(projectId: UUID, fileRequest: FileRequest) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val entity = ProjectEntity.findById(projectId) ?: throw IllegalArgumentException("Project not found")
        val videoEntities = VideoEntity.forIds(fileRequest.fileIds)
        entity.videos = SizedCollection(entity.videos - videoEntities)
        videoEntities.forEach { it.delete() }
        handleCache(entity)
        entity
    }

    suspend fun uploadDocuments(projectId: UUID, multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val entity = ProjectEntity.findById(projectId) ?: throw IllegalArgumentException("Project not found")
        val documents = documentService.uploadMultiple(multiPartData)
        entity.documents = SizedCollection(entity.documents + documents)
        handleCache(entity)
        entity
    }

    suspend fun removeDocuments(projectId: UUID, fileRequest: FileRequest) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val entity = ProjectEntity.findById(projectId) ?: throw IllegalArgumentException("Project not found")
        val documentEntities = DocumentEntity.forIds(fileRequest.fileIds)
        entity.documents = SizedCollection(entity.documents - documentEntities)
        documentEntities.forEach { it.delete() }
        handleCache(entity)
        entity
    }

    override fun handleCache(entity: ProjectEntity) {
        val projectKey = CacheUtil.PROJECT_KEY.format(entity.id.value)
        if (CacheUtil.REDIS.exists(projectKey)) CacheUtil.REDIS.del(projectKey)

        val portfolioKey = CacheUtil.PORTFOLIO_KEY.format(entity.portfolio.id.value)
        if (CacheUtil.REDIS.exists(portfolioKey)) CacheUtil.REDIS.del(portfolioKey)
    }
}