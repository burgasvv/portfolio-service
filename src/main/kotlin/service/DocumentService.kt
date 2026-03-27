package org.burgas.service

import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.io.readByteArray
import org.burgas.database.DatabaseFactory
import org.burgas.database.DocumentEntity
import org.burgas.service.contract.FileService
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class DocumentService : FileService<DocumentEntity> {

    override suspend fun findEntity(id: UUID): DocumentEntity = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_REPLICA,
        context = Dispatchers.Default,
        readOnly = true
    ) {
        DocumentEntity.findById(id) ?: throw IllegalArgumentException("Document not found")
    }

    @OptIn(InternalAPI::class)
    override suspend fun uploadMultiple(multiPartData: MultiPartData): List<DocumentEntity> = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val documents: MutableList<DocumentEntity> = mutableListOf()
        multiPartData.forEachPart { partData ->
            if (partData.contentType!!.contentType.startsWith("application")) {

                if (partData is PartData.FileItem) {
                    val documentEntity = DocumentEntity.new {
                        this.name = partData.originalFileName!!
                        this.contentType = "${partData.contentType!!.contentType}/${partData.contentType!!.contentSubtype}"
                        this.data = ExposedBlob(partData.provider().readBuffer.readByteArray())
                    }
                    documents.add(documentEntity)

                } else {
                    throw IllegalArgumentException("Part data is not FileItem type")
                }

            } else {
                throw IllegalArgumentException("Wrong content type of part data")
            }
        }
        documents
    }

    override suspend fun removeMultiple(fileIds: List<UUID>) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        DocumentEntity.forIds(fileIds).forEach { documentEntity -> documentEntity.delete() }
    }
}