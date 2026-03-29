package org.burgas.service

import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.cache.CacheUtil
import org.burgas.cache.RedisHandler
import org.burgas.database.DatabaseFactory
import org.burgas.database.ProfessionEntity
import org.burgas.dto.ProfessionFullResponse
import org.burgas.dto.ProfessionRequest
import org.burgas.dto.ProfessionShortResponse
import org.burgas.service.contract.CrudService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class ProfessionService : CrudService<ProfessionRequest, ProfessionShortResponse, ProfessionFullResponse>, RedisHandler<ProfessionEntity> {

    private val imageService = ImageService()

    override suspend fun findById(id: UUID): ProfessionFullResponse = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_REPLICA, context = Dispatchers.Default, readOnly = true
    ) {
        val professionKey = CacheUtil.PROFESSION_KEY.format(id)
        if (CacheUtil.REDIS.exists(professionKey)) {
            val professionString = CacheUtil.REDIS.get(professionKey)
            Json.decodeFromString<ProfessionFullResponse>(professionString)
        } else {
            val professionFullResponse =
                (ProfessionEntity.findById(id) ?: throw IllegalArgumentException("Profession not found"))
                    .load(ProfessionEntity::image, ProfessionEntity::portfolios)
                    .toProfessionFullResponse()
            CacheUtil.REDIS.set(professionKey, Json.encodeToString(professionFullResponse))
            professionFullResponse
        }
    }

    override suspend fun findAll(): List<ProfessionShortResponse> = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_REPLICA, context = Dispatchers.Default, readOnly = true
    ) {
        ProfessionEntity.all().with(ProfessionEntity::image).map { it.toProfessionShortResponse() }
    }

    override suspend fun create(request: ProfessionRequest): ProfessionFullResponse = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val professionFullResponse = ProfessionEntity.new { this.insert(request) }
            .load(ProfessionEntity::image, ProfessionEntity::portfolios)
            .toProfessionFullResponse()
        val professionKey = CacheUtil.PROFESSION_KEY.format(professionFullResponse.id)
        CacheUtil.REDIS.set(professionKey, Json.encodeToString(professionFullResponse))
        professionFullResponse
    }

    override suspend fun update(request: ProfessionRequest): ProfessionFullResponse = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val professionEntity =
            (ProfessionEntity.findByIdAndUpdate(request.id ?: throw IllegalArgumentException("Profession id is null"))
            { it.update(request) } ?: throw IllegalArgumentException("Profession not found and not updated"))
                .load(ProfessionEntity::image, ProfessionEntity::portfolios)
        handleCache(professionEntity)
        professionEntity.toProfessionFullResponse()
    }

    override suspend fun delete(id: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val professionEntity = ProfessionEntity.findById(id) ?: throw IllegalArgumentException("Profession not found")
        professionEntity.delete()
        handleCache(professionEntity)
    }

    suspend fun uploadImage(professionId: UUID, multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val professionEntity = ProfessionEntity.findById(professionId) ?: throw IllegalArgumentException("Profession not found")
        if (professionEntity.image == null) {
            val imageEntity = imageService.uploadSingle(multiPartData)
            professionEntity.apply { this.image = imageEntity }
            handleCache(professionEntity)
        } else {
            throw IllegalArgumentException("Profession image is already set")
        }
    }

    suspend fun removeImage(professionId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val professionEntity = ProfessionEntity.findById(professionId) ?: throw IllegalArgumentException("Profession not found")
        val imageEntity = professionEntity.image ?: throw IllegalArgumentException("Profession image not found")
        professionEntity.apply { this.image = null }
        imageService.removeSingle(imageEntity.id.value)
        handleCache(professionEntity)
    }

    override fun handleCache(entity: ProfessionEntity) {
        val professionKey = CacheUtil.PROFESSION_KEY.format(entity.id.value)
        if (CacheUtil.REDIS.exists(professionKey)) CacheUtil.REDIS.del(professionKey)

        if (!entity.portfolios.empty()) {
            entity.portfolios.forEach { portfolioEntity ->
                val portfolioKey = CacheUtil.PORTFOLIO_KEY.format(portfolioEntity.id.value)
                if (CacheUtil.REDIS.exists(portfolioKey)) CacheUtil.REDIS.del(portfolioKey)
            }
        }
    }
}