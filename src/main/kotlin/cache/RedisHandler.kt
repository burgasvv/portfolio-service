package org.burgas.cache

import org.jetbrains.exposed.dao.UUIDEntity

interface RedisHandler<E : UUIDEntity> {

    fun handleCache(entity: E)
}