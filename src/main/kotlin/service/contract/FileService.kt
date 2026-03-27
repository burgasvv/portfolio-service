package org.burgas.service.contract

import io.ktor.http.content.MultiPartData
import org.burgas.database.FileEntity
import java.util.UUID

interface FileService<E : FileEntity> {

    suspend fun findEntity(id: UUID): E

    suspend fun uploadMultiple(multiPartData: MultiPartData): List<E>

    suspend fun removeMultiple(fileIds: List<UUID>)
}