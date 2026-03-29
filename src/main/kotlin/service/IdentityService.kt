package org.burgas.service

import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.cache.CacheUtil
import org.burgas.cache.RedisHandler
import org.burgas.database.DatabaseFactory
import org.burgas.database.IdentityEntity
import org.burgas.dto.IdentityFullResponse
import org.burgas.dto.IdentityRequest
import org.burgas.dto.IdentityShortResponse
import org.burgas.service.contract.CrudService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.mindrot.jbcrypt.BCrypt
import java.sql.Connection
import java.util.*

class IdentityService : CrudService<IdentityRequest, IdentityShortResponse, IdentityFullResponse>, RedisHandler<IdentityEntity> {

    private val imageService = ImageService()

    override suspend fun findById(id: UUID): IdentityFullResponse = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_REPLICA, context = Dispatchers.Default, readOnly = true
    ) {
        val identityKey = CacheUtil.IDENTITY_KEY.format(id)
        if (CacheUtil.REDIS.exists(identityKey)) {
            val identityString = CacheUtil.REDIS.get(identityKey)
            Json.decodeFromString<IdentityFullResponse>(identityString)
        } else {
            val identityFullResponse =
                (IdentityEntity.findById(id) ?: throw IllegalArgumentException("Identity not found"))
                    .load(IdentityEntity::image, IdentityEntity::portfolios)
                    .toIdentityFullResponse()
            CacheUtil.REDIS.set(identityKey, Json.encodeToString(identityFullResponse))
            identityFullResponse
        }
    }

    override suspend fun findAll(): List<IdentityShortResponse> = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_REPLICA, context = Dispatchers.Default, readOnly = true
    ) {
        IdentityEntity.all().with(IdentityEntity::image).map { it.toIdentityShortResponse() }
    }

    override suspend fun create(request: IdentityRequest): IdentityFullResponse = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityFullResponse = IdentityEntity.new { this.insert(request) }
            .load(IdentityEntity::image, IdentityEntity::portfolios)
            .toIdentityFullResponse()
        val identityKey = CacheUtil.IDENTITY_KEY.format(identityFullResponse.id)
        CacheUtil.REDIS.set(identityKey, Json.encodeToString(identityFullResponse))
        identityFullResponse
    }

    override suspend fun update(request: IdentityRequest): IdentityFullResponse = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity =
            (IdentityEntity.findByIdAndUpdate(request.id ?: throw IllegalArgumentException("Request id is null"))
            { it.update(request) }
                ?: throw IllegalArgumentException("Identity not found and not updated"))
                .load(IdentityEntity::image, IdentityEntity::portfolios)
        handleCache(identityEntity)
        identityEntity.toIdentityFullResponse()
    }

    override suspend fun delete(id: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = IdentityEntity.findById(id) ?: throw IllegalArgumentException("Identity not found")
        identityEntity.delete()
        handleCache(identityEntity)
    }

    suspend fun changePassword(identityRequest: IdentityRequest) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        if (identityRequest.id == null) throw IllegalArgumentException("Identity id is null")
        if (identityRequest.password == null) throw IllegalArgumentException("Identity password is null")

        val identityEntity = IdentityEntity.findById(identityRequest.id) ?: throw IllegalArgumentException("Identity not found")
        if (BCrypt.checkpw(identityRequest.password, identityEntity.password))
            throw IllegalArgumentException("Identity password and new password matched")

        identityEntity.apply { this.password = BCrypt.hashpw(identityRequest.password, BCrypt.gensalt()) }
    }

    suspend fun changeStatus(identityRequest: IdentityRequest) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        if (identityRequest.id == null) throw IllegalArgumentException("Identity id is null")
        if (identityRequest.enabled == null) throw IllegalArgumentException("Identity status is null")

        val identityEntity = IdentityEntity.findById(identityRequest.id) ?: throw IllegalArgumentException("Identity not found")
        if (identityEntity.enabled == identityRequest.enabled)
            throw IllegalArgumentException("Identity status and request status matched")

        identityEntity.apply { this.enabled = identityRequest.enabled }
    }

    suspend fun uploadImage(identityId: UUID, multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = IdentityEntity.findById(identityId) ?: throw IllegalArgumentException("Identity not found")
        if (identityEntity.image == null) {
            val imageEntity = imageService.uploadSingle(multiPartData)
            identityEntity.apply { this.image = imageEntity }
            handleCache(identityEntity)
        } else {
            throw IllegalArgumentException("Identity image already set")
        }
    }

    suspend fun removeImage(identityId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = IdentityEntity.findById(identityId) ?: throw IllegalArgumentException("Identity not found")
        val imageEntity = identityEntity.image ?: throw IllegalArgumentException("Identity image not found")
        identityEntity.apply { this.image = null }
        imageService.removeSingle(imageEntity.id.value)
        handleCache(identityEntity)
    }

    override fun handleCache(entity: IdentityEntity) {
        val identityKey = CacheUtil.IDENTITY_KEY.format(entity.id.value)
        if (CacheUtil.REDIS.exists(identityKey)) CacheUtil.REDIS.del(identityKey)
        if (!entity.portfolios.empty()) {
            entity.portfolios.forEach { portfolioEntity ->
                val portfolioKey = CacheUtil.PORTFOLIO_KEY.format(portfolioEntity.id.value)
                if (CacheUtil.REDIS.exists(portfolioKey)) CacheUtil.REDIS.del(portfolioKey)
            }
        }
    }
}