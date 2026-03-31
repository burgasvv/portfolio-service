package org.burgas.service

import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.io.readByteArray
import org.burgas.database.DatabaseFactory
import org.burgas.database.VideoEntity
import org.burgas.service.contract.FileService
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class VideoService : FileService<VideoEntity> {

    override suspend fun findEntity(id: UUID): VideoEntity = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_REPLICA,
        context = Dispatchers.Default,
        readOnly = true
    ) {
        val videoEntity = VideoEntity.findById(id) ?: throw IllegalArgumentException("Video not found")
        if (videoEntity.contentType.startsWith("video")) {
            videoEntity
        } else {
            throw IllegalArgumentException("File is not video")
        }
    }

    @OptIn(InternalAPI::class)
    override suspend fun uploadMultiple(multiPartData: MultiPartData): List<VideoEntity> = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val videos: MutableList<VideoEntity> = mutableListOf()
        multiPartData.forEachPart { partData ->
            if (partData.contentType!!.contentType.startsWith("video")) {

                if (partData is PartData.FileItem) {
                    val videoEntity = VideoEntity.new {
                        this.name = partData.originalFileName!!
                        this.contentType = "${partData.contentType!!.contentType}/${partData.contentType!!.contentSubtype}"
                        this.data = ExposedBlob(partData.provider().readBuffer.readByteArray())
                    }
                    videos.add(videoEntity)

                } else {
                    throw IllegalArgumentException("Part data is not FileItem")
                }

            } else {
                throw IllegalArgumentException("part data content type is wrong")
            }
        }
        videos
    }

    override suspend fun removeMultiple(fileIds: List<UUID>) = newSuspendedTransaction(
        db = DatabaseFactory.POSTGRES_MASTER,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        VideoEntity.forIds(fileIds).forEach { videoEntity -> videoEntity.delete() }
    }
}