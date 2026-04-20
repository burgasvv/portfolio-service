package org.burgas.cache

import org.jetbrains.exposed.dao.UUIDEntity

interface RedisCacheHandler<E : UUIDEntity> {

    fun handleCache(entity: E)
}