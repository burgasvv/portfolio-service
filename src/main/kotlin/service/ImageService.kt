package org.burgas.service

import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.io.readByteArray
import org.burgas.database.DatabaseFactory
import org.burgas.database.ImageEntity
import org.burgas.service.contract.FileService
import org.burgas.service.contract.FileSingle
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class ImageService : FileService<ImageEntity>, FileSingle<ImageEntity> {

    override suspend fun findEntity(id: UUID): ImageEntity = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_REPLICA, context = Dispatchers.Default, readOnly = true
    ) {
        val imageEntity = ImageEntity.findById(id) ?: throw IllegalArgumentException("Image not found")
        if (imageEntity.contentType.startsWith("image")) {
            imageEntity
        } else {
            throw IllegalArgumentException("File is not image")
        }
    }

    @OptIn(InternalAPI::class)
    override suspend fun uploadMultiple(multiPartData: MultiPartData): List<ImageEntity> = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val images: MutableList<ImageEntity> = mutableListOf()
        multiPartData.forEachPart { partData ->
            if (partData.contentType!!.contentType.startsWith("image")) {

                if (partData is PartData.FileItem) {
                    val imageEntity = ImageEntity.new {
                        this.name = partData.originalFileName!!
                        this.contentType =
                            "${partData.contentType!!.contentType}/${partData.contentType!!.contentSubtype}"
                        this.preview = false
                        this.data = ExposedBlob(partData.provider().readBuffer.readByteArray())
                    }
                    images.add(imageEntity)
                } else {
                    throw IllegalArgumentException("Part dat is not file item")
                }

            } else {
                throw IllegalArgumentException("Wrong file type")
            }
        }
        images
    }

    override suspend fun removeMultiple(fileIds: List<UUID>) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        ImageEntity.forIds(fileIds).forEach { imageEntity -> imageEntity.delete() }
    }

    @OptIn(InternalAPI::class)
    override suspend fun uploadSingle(multiPartData: MultiPartData): ImageEntity = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val partData = multiPartData.readPart() ?: throw IllegalArgumentException("File part not found")
        if (partData.contentType!!.contentType.startsWith("image")) {

            if (partData is PartData.FileItem) {
                val imageEntity = ImageEntity.new {
                    this.name = partData.originalFileName!!
                    this.contentType = "${partData.contentType!!.contentType}/${partData.contentType!!.contentSubtype}"
                    this.preview = true
                    this.data = ExposedBlob(partData.provider().readBuffer.readByteArray())
                }
                imageEntity

            } else {
                throw IllegalArgumentException("Part dat is not file item")
            }

        } else {
            throw IllegalArgumentException("Wrong file type")
        }
    }

    override suspend fun removeSingle(fileId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        (ImageEntity.findById(fileId) ?: throw IllegalArgumentException("Image not found")).delete()
    }
}