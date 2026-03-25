package org.burgas.database

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.burgas.dto.DocumentResponse
import org.burgas.dto.ImageResponse
import org.burgas.dto.VideoResponse
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentDateTime
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import redis.clients.jedis.Jedis
import java.util.*

class DatabaseFactory {

    companion object {

        private val config = ApplicationConfig("application.yaml")

        val POSTGRES_MASTER: Database = Database.connect(
            driver = config.property("ktor.postgres.driver").getString(),
            url = config.property("ktor.postgres.url").getString(),
            user = config.property("ktor.postgres.user").getString(),
            password = config.property("ktor.postgres.password").getString()
        )

        val POSTGRES_REPLICA: Database = Database.connect(
            driver = config.property("ktor.postgres.driver").getString(),
            url = config.property("ktor.postgres.url").getString(),
            user = config.property("ktor.postgres.user").getString(),
            password = config.property("ktor.postgres.password").getString()
        )

        val REDIS = Jedis(
            config.property("ktor.redis.host").getString(),
            config.property("ktor.redis.port").getString().toInt()
        )
    }
}

object ImageTable : UUIDTable("image") {
    val name = varchar("name", 250)
    val contentType = varchar("content_type", 250)
    val preview = bool("preview").default(false)
    val data = blob("data")
}

class ImageEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, ImageEntity>(ImageTable)

    var name by ImageTable.name
    var contentType by ImageTable.contentType
    var preview by ImageTable.preview
    var data by ImageTable.data

    fun toImageResponse(): ImageResponse {
        return ImageResponse(
            id = this.id.value,
            name = this.name,
            contentType = this.contentType,
            preview = this.preview
        )
    }
}

object VideoTable : UUIDTable("video") {
    val name = varchar("name", 250)
    val contentType = varchar("content_type", 250)
    val data = blob("data")
}

class VideoEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, VideoEntity>(VideoTable)

    var name by VideoTable.name
    var contentType by VideoTable.contentType
    var data by VideoTable.data

    fun toVideoResponse(): VideoResponse {
        return VideoResponse(
            id = this.id.value,
            name = this.name,
            contentType = this.contentType
        )
    }
}

object DocumentTable : UUIDTable("document") {
    val name = varchar("name", 250)
    val contentType = varchar("content_type", 250)
    val data = blob("data")
}

class DocumentEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, DocumentEntity>(DocumentTable)

    var name by DocumentTable.name
    var contentType by DocumentTable.contentType
    var data by DocumentTable.data

    fun toDocumentResponse(): DocumentResponse {
        return DocumentResponse(
            id = this.id.value,
            name = this.name,
            contentType = this.contentType
        )
    }
}

@Suppress("unused")
enum class Authority {
    ADMIN, USER
}

object IdentityTable : UUIDTable("identity") {
    val authority = enumerationByName<Authority>("authority", 250)
    val email = varchar("email", 250).uniqueIndex()
    val password = varchar("password", 250)
    val enabled = bool("enabled").default(true)
    val firstname = varchar("firstname", 250)
    val lastname = varchar("lastname", 250)
    val patronymic = varchar("patronymic", 250)
    val imageId = optReference(
        name = "image_id", refColumn = ImageTable.id,
        onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.CASCADE
    ).uniqueIndex()
}

class IdentityEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, IdentityEntity>(IdentityTable)

    var authority by IdentityTable.authority
    var email by IdentityTable.email
    var password by IdentityTable.password
    var enabled by IdentityTable.enabled
    var firstname by IdentityTable.firstname
    var lastname by IdentityTable.lastname
    var patronymic by IdentityTable.patronymic
    val image by ImageEntity optionalBackReferencedOn IdentityTable.imageId
    val portfolios by PortfolioEntity referrersOn PortfolioTable.identityId
}

object ProfessionTable : UUIDTable("profession") {
    val name = varchar("name", 250).uniqueIndex()
    val description = text("description").uniqueIndex()
    val imageId = optReference(
        name = "image_id", refColumn = ImageTable.id,
        onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.CASCADE
    )
}

class ProfessionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, ProfessionEntity>(ProfessionTable)

    var name by ProfessionTable.name
    var description by ProfessionTable.description
    val image by ImageEntity optionalBackReferencedOn ProfessionTable.imageId
    val portfolios by PortfolioEntity optionalReferrersOn PortfolioTable.professionId
}

object PortfolioTable : UUIDTable("portfolio") {
    val name = varchar("name", 250)
    val description = text("description")
    val imageId = optReference(
        name = "image_id", refColumn = ImageTable.id,
        onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.CASCADE
    ).uniqueIndex()
    val identityId = reference(
        name = "identity_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val professionId = optReference(
        name = "profession_id", refColumn = ProfessionTable.id,
        onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.CASCADE
    )
    val opened = bool("opened").default(true)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

class PortfolioEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, PortfolioEntity>(PortfolioTable)

    var name by PortfolioTable.name
    var description by PortfolioTable.description
    val image by ImageEntity optionalBackReferencedOn PortfolioTable.imageId
    var identity by IdentityEntity referencedOn PortfolioTable.identityId
    var profession by ProfessionEntity optionalReferencedOn PortfolioTable.professionId
    var opened by PortfolioTable.opened
    val projects by ProjectEntity referrersOn ProjectTable.portfolioId
    var createdAt by PortfolioTable.createdAt
    var updatedAt by PortfolioTable.updatedAt
}

object ProjectTable : UUIDTable("project") {
    val name = varchar("name", 250)
    val description = text("description")
    val portfolioId = reference(
        name = "portfolio_id", refColumn = PortfolioTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

class ProjectEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, ProjectEntity>(ProjectTable)

    var name by ProjectTable.name
    var description by ProjectTable.description
    var portfolio by PortfolioEntity referencedOn ProjectTable.portfolioId
    var createAt by ProjectTable.createdAt
    var updatedAt by ProjectTable.updatedAt
    val images by ImageEntity via ImageTable
    val videos by VideoEntity via VideoTable
    val documents by DocumentEntity via DocumentTable
}

object ProjectImageTable : Table("project_image") {
    val projectId = reference(
        name = "project_id", refColumn = ProjectTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val imageId = reference(
        name = "image_id", refColumn = ImageTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
}

object ProjectVideoTable : Table("project_video") {
    val projectId = reference(
        name = "project_id", refColumn = ProjectTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val videoId = reference(
        name = "video_id", refColumn = VideoTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
}

object ProjectDocumentTable : Table("project_document") {
    val projectId = reference(
        name = "project_id", refColumn = ProjectTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val documentId = reference(
        name = "document_id", refColumn = DocumentTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
}

@Suppress("UnusedReceiverParameter")
fun Application.configureDatabase() {

    transaction(db = DatabaseFactory.POSTGRES_MASTER) {
        SchemaUtils.create(
            ImageTable, VideoTable, DocumentTable, IdentityTable, ProfessionTable,
            PortfolioTable, ProjectTable, ProjectImageTable, ProjectVideoTable, ProjectDocumentTable
        )
    }
}