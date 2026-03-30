package org.burgas.service

import io.ktor.http.content.MultiPartData
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.cache.CacheUtil
import org.burgas.cache.RedisHandler
import org.burgas.database.DatabaseFactory
import org.burgas.database.PortfolioEntity
import org.burgas.dto.PortfolioFullResponse
import org.burgas.dto.PortfolioRequest
import org.burgas.dto.PortfolioShortResponse
import org.burgas.service.contract.CrudService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class PortfolioService : CrudService<PortfolioRequest, PortfolioShortResponse, PortfolioFullResponse>,
    RedisHandler<PortfolioEntity> {

    private val imageService = ImageService()

    override suspend fun findById(id: UUID): PortfolioFullResponse = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_REPLICA, context = Dispatchers.Default, readOnly = true
    ) {
        val portfolioKey = CacheUtil.PORTFOLIO_KEY.format(id)
        if (CacheUtil.REDIS.exists(portfolioKey)) {
            val portfolioString = CacheUtil.REDIS.get(portfolioKey)
            Json.decodeFromString<PortfolioFullResponse>(portfolioString)
        } else {
            val portfolioFullResponse =
                (PortfolioEntity.findById(id) ?: throw IllegalArgumentException("Portfolio not found"))
                    .load(
                        PortfolioEntity::image,
                        PortfolioEntity::identity,
                        PortfolioEntity::profession,
                        PortfolioEntity::projects
                    )
                    .toPortfolioFullResponse()
            CacheUtil.REDIS.set(portfolioKey, Json.encodeToString(portfolioFullResponse))
            portfolioFullResponse
        }
    }

    override suspend fun findAll(): List<PortfolioShortResponse> = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_REPLICA, context = Dispatchers.Default, readOnly = true
    ) {
        PortfolioEntity.all()
            .with(PortfolioEntity::image, PortfolioEntity::identity, PortfolioEntity::profession)
            .map { it.toPortfolioShortResponse() }
    }

    override suspend fun create(request: PortfolioRequest): PortfolioFullResponse = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val portfolioFullResponse = PortfolioEntity.new { this.insert(request) }
            .load(
                PortfolioEntity::image,
                PortfolioEntity::identity,
                PortfolioEntity::profession,
                PortfolioEntity::projects
            )
            .toPortfolioFullResponse()
        val portfolioKey = CacheUtil.PORTFOLIO_KEY.format(portfolioFullResponse.id)
        CacheUtil.REDIS.set(portfolioKey, Json.encodeToString(portfolioFullResponse))
        portfolioFullResponse
    }

    override suspend fun update(request: PortfolioRequest): PortfolioFullResponse = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val portfolioEntity = (PortfolioEntity.findByIdAndUpdate(
            request.id ?: throw IllegalArgumentException("Portfolio id is null")
        ) { it.update(request) } ?: throw IllegalArgumentException("Portfolio not found and not updated"))
            .load(
                PortfolioEntity::image,
                PortfolioEntity::identity,
                PortfolioEntity::profession,
                PortfolioEntity::projects
            )
        handleCache(portfolioEntity)
        portfolioEntity.toPortfolioFullResponse()
    }

    override suspend fun delete(id: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val portfolioEntity = PortfolioEntity.findById(id) ?: throw IllegalArgumentException("Portfolio not found")
        portfolioEntity.image?.delete()
        portfolioEntity.delete()
        handleCache(portfolioEntity)
    }

    suspend fun uploadImage(portfolioId: UUID, multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val portfolioEntity = PortfolioEntity.findById(portfolioId) ?: throw IllegalArgumentException("Portfolio not found")
        if (portfolioEntity.image == null) {
            val imageEntity = imageService.uploadSingle(multiPartData)
            portfolioEntity.apply { this.image = imageEntity }
            handleCache(portfolioEntity)
        } else {
            throw IllegalArgumentException("Portfolio image is already set")
        }
    }

    suspend fun removeImage(portfolioId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val portfolioEntity = PortfolioEntity.findById(portfolioId) ?: throw IllegalArgumentException("Portfolio not found")
        val imageEntity = portfolioEntity.image ?: throw IllegalArgumentException("Portfolio image not found")
        portfolioEntity.apply { this.image = null }
        imageService.removeSingle(imageEntity.id.value)
        handleCache(portfolioEntity)
    }

    override fun handleCache(entity: PortfolioEntity) {
        val portfolioKey = CacheUtil.PORTFOLIO_KEY.format(entity.id.value)
        if (CacheUtil.REDIS.exists(portfolioKey)) CacheUtil.REDIS.del(portfolioKey)

        val identityKey = CacheUtil.IDENTITY_KEY.format(entity.identity.id.value)
        if (CacheUtil.REDIS.exists(identityKey)) CacheUtil.REDIS.del(identityKey)

        val profession = entity.profession
        if (profession != null) {
            val professionKey = CacheUtil.PROFESSION_KEY.format(profession.id.value)
            if (CacheUtil.REDIS.exists(professionKey)) CacheUtil.REDIS.del(professionKey)
        }
        if (!entity.projects.empty()) {
            entity.projects.forEach { projectEntity ->
                val projectKey = CacheUtil.PROJECT_KEY.format(projectEntity.id.value)
                if (CacheUtil.REDIS.exists(projectKey)) CacheUtil.REDIS.del(projectKey)
            }
        }
    }
}