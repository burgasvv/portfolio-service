package org.burgas.service

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
import java.sql.Connection
import java.util.*

class IdentityService : CrudService<IdentityRequest, IdentityShortResponse, IdentityFullResponse>,
    RedisHandler<IdentityEntity> {

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