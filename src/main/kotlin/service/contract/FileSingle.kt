package org.burgas.service.contract

import io.ktor.http.content.MultiPartData
import org.burgas.database.FileEntity
import java.util.UUID

interface FileSingle<E : FileEntity> {

    suspend fun uploadSingle(multiPartData: MultiPartData): E

    suspend fun removeSingle(fileId: UUID)
}