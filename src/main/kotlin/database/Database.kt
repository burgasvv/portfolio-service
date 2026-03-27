package org.burgas.database

import io.ktor.server.application.*
import io.ktor.server.config.*
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.burgas.dto.*
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
import org.mindrot.jbcrypt.BCrypt
import redis.clients.jedis.Jedis
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

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

interface FileEntity

object ImageTable : UUIDTable("image") {
    val name = varchar("name", 250)
    val contentType = varchar("content_type", 250)
    val preview = bool("preview").default(false)
    val data = blob("data")
}

class ImageEntity(id: EntityID<UUID>) : UUIDEntity(id), FileEntity {
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

class VideoEntity(id: EntityID<UUID>) : UUIDEntity(id), FileEntity {
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

class DocumentEntity(id: EntityID<UUID>) : UUIDEntity(id), FileEntity {
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
    val image by ImageEntity optionalReferencedOn IdentityTable.imageId
    val portfolios by PortfolioEntity referrersOn PortfolioTable.identityId

    fun insert(identityRequest: IdentityRequest) {
        this.authority = identityRequest.authority ?: Authority.USER
        this.email = identityRequest.email ?: throw IllegalArgumentException("Identity email is null")
        this.password = if (identityRequest.password.isNullOrEmpty())
            throw IllegalArgumentException("Identity password is null or empty") else BCrypt.hashpw(
            identityRequest.password, BCrypt.gensalt()
        )
        this.enabled = identityRequest.enabled ?: true
        this.firstname = identityRequest.firstname ?: throw IllegalArgumentException("Identity firstname is null")
        this.lastname = identityRequest.lastname ?: throw IllegalArgumentException("Identity lastname is null")
        this.patronymic = identityRequest.patronymic ?: throw IllegalArgumentException("Identity patronymic is null")
    }

    fun update(identityRequest: IdentityRequest) {
        this.authority = identityRequest.authority ?: this.authority
        this.email = identityRequest.email ?: this.email
        this.firstname = identityRequest.firstname ?: this.firstname
        this.lastname = identityRequest.lastname ?: this.lastname
        this.patronymic = identityRequest.patronymic ?: this.patronymic
    }

    fun toIdentityShortResponse(): IdentityShortResponse {
        return IdentityShortResponse(
            id = this.id.value,
            email = this.email,
            firstname = this.firstname,
            lastname = this.lastname,
            patronymic = this.patronymic,
            image = this.image?.toImageResponse()
        )
    }

    fun toIdentityFullResponse(): IdentityFullResponse {
        return IdentityFullResponse(
            id = this.id.value,
            email = this.email,
            firstname = this.firstname,
            lastname = this.lastname,
            patronymic = this.patronymic,
            image = this.image?.toImageResponse(),
            portfolios = this.portfolios.map { it.toPortfolioWithoutIdentityResponse() }
        )
    }
}

object ProfessionTable : UUIDTable("profession") {
    val name = varchar("name", 250).uniqueIndex()
    val description = text("description").uniqueIndex()
    val imageId = optReference(
        name = "image_id", refColumn = ImageTable.id,
        onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.CASCADE
    ).uniqueIndex()
}

class ProfessionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, ProfessionEntity>(ProfessionTable)

    var name by ProfessionTable.name
    var description by ProfessionTable.description
    val image by ImageEntity optionalReferencedOn ProfessionTable.imageId
    val portfolios by PortfolioEntity optionalReferrersOn PortfolioTable.professionId

    fun insert(professionRequest: ProfessionRequest) {
        this.name = professionRequest.name ?: throw IllegalArgumentException("Profession name is null")
        this.description =
            professionRequest.description ?: throw IllegalArgumentException("Profession description is null")
    }

    fun update(professionRequest: ProfessionRequest) {
        this.name = professionRequest.name ?: this.name
        this.description = professionRequest.description ?: this.description
    }

    fun toProfessionShortResponse(): ProfessionShortResponse {
        return ProfessionShortResponse(
            id = this.id.value,
            name = this.name,
            description = this.description,
            image = this.image?.toImageResponse()
        )
    }

    fun toProfessionFullResponse(): ProfessionFullResponse {
        return ProfessionFullResponse(
            id = this.id.value,
            name = this.name,
            description = this.description,
            image = this.image?.toImageResponse(),
            portfolios = this.portfolios.map { it.toPortfolioWithoutProfessionResponse() }
        )
    }
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
    val image by ImageEntity optionalReferencedOn PortfolioTable.imageId
    var identity by IdentityEntity referencedOn PortfolioTable.identityId
    var profession by ProfessionEntity optionalReferencedOn PortfolioTable.professionId
    var opened by PortfolioTable.opened
    val projects by ProjectEntity referrersOn ProjectTable.portfolioId
    var createdAt by PortfolioTable.createdAt
    var updatedAt by PortfolioTable.updatedAt

    fun insert(portfolioRequest: PortfolioRequest) {
        this.name = portfolioRequest.name ?: throw IllegalArgumentException("Portfolio name is null")
        this.description =
            portfolioRequest.description ?: throw IllegalArgumentException("Portfolio description is null")
        this.identity = IdentityEntity.findById(
            portfolioRequest.identityId ?: throw IllegalArgumentException("Portfolio identityId is null")
        ) ?: throw IllegalArgumentException("Portfolio identity not found")
        this.profession = ProfessionEntity.findById(
            portfolioRequest.professionId ?: throw IllegalArgumentException("Portfolio professionId is null")
        ) ?: throw IllegalArgumentException("Portfolio profession not found")
        this.opened = portfolioRequest.opened ?: true
        this.createdAt = LocalDateTime.now().toKotlinLocalDateTime()
        this.updatedAt = LocalDateTime.now().toKotlinLocalDateTime()
    }

    fun update(portfolioRequest: PortfolioRequest) {
        this.name = portfolioRequest.name ?: this.name
        this.description = portfolioRequest.description ?: this.description
        this.identity = IdentityEntity.findById(portfolioRequest.identityId ?: UUID(0, 0)) ?: this.identity
        this.profession = ProfessionEntity.findById(portfolioRequest.professionId ?: UUID(0, 0)) ?: this.profession
        this.opened = portfolioRequest.opened ?: this.opened
        this.updatedAt = LocalDateTime.now().toKotlinLocalDateTime()
    }

    fun toPortfolioWithoutIdentityResponse(): PortfolioWithoutIdentityResponse {
        return PortfolioWithoutIdentityResponse(
            id = this.id.value,
            name = this.name,
            description = this.description,
            image = this.image?.toImageResponse(),
            profession = this.profession?.toProfessionShortResponse(),
            opened = this.opened,
            createdAt = this.createdAt.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
            updatedAt = this.updatedAt.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm"))
        )
    }

    fun toPortfolioWithoutProfessionResponse(): PortfolioWithoutProfessionResponse {
        return PortfolioWithoutProfessionResponse(
            id = this.id.value,
            name = this.name,
            description = this.description,
            image = this.image?.toImageResponse(),
            identity = this.identity.toIdentityShortResponse(),
            opened = this.opened,
            createdAt = this.createdAt.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
            updatedAt = this.updatedAt.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm"))
        )
    }

    fun toPortfolioShortResponse(): PortfolioShortResponse {
        return PortfolioShortResponse(
            id = this.id.value,
            name = this.name,
            description = this.description,
            image = this.image?.toImageResponse(),
            identity = this.identity.toIdentityShortResponse(),
            profession = this.profession?.toProfessionShortResponse(),
            opened = this.opened,
            createdAt = this.createdAt.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
            updatedAt = this.updatedAt.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm"))
        )
    }

    fun toPortfolioFullResponse(): PortfolioFullResponse {
        return PortfolioFullResponse(
            id = this.id.value,
            name = this.name,
            description = this.description,
            image = this.image?.toImageResponse(),
            identity = this.identity.toIdentityShortResponse(),
            profession = this.profession?.toProfessionShortResponse(),
            opened = this.opened,
            createdAt = this.createdAt.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
            updatedAt = this.updatedAt.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
            projects = this.projects.map { it.toProjectWithoutPortfolioResponse() }
        )
    }
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
    var createdAt by ProjectTable.createdAt
    var updatedAt by ProjectTable.updatedAt
    val images by ImageEntity via ImageTable
    val videos by VideoEntity via VideoTable
    val documents by DocumentEntity via DocumentTable

    fun insert(projectRequest: ProjectRequest) {
        this.name = projectRequest.name ?: throw IllegalArgumentException("Project name is null")
        this.description = projectRequest.description ?: throw IllegalArgumentException("Project description is null")
        this.portfolio = PortfolioEntity.findById(
            projectRequest.portfolioId ?: throw IllegalArgumentException("Project portfolioId is null")
        ) ?: throw IllegalArgumentException("Project portfolio not found")
        this.createdAt = LocalDateTime.now().toKotlinLocalDateTime()
        this.updatedAt = LocalDateTime.now().toKotlinLocalDateTime()
    }

    fun update(projectRequest: ProjectRequest) {
        this.name = projectRequest.name ?: this.name
        this.description = projectRequest.description ?: this.description
        this.portfolio = PortfolioEntity.findById(projectRequest.portfolioId ?: UUID(0,0)) ?: this.portfolio
        this.updatedAt = LocalDateTime.now().toKotlinLocalDateTime()
    }

    fun toProjectShortResponse(): ProjectShortResponse {
        return ProjectShortResponse(
            id = this.id.value,
            name = this.name,
            description = this.description,
            createdAt = this.createdAt.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
            updatedAt = this.updatedAt.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
            portfolio = this.portfolio.toPortfolioShortResponse()
        )
    }

    fun toProjectWithoutPortfolioResponse(): ProjectWithoutPortfolioResponse {
        return ProjectWithoutPortfolioResponse(
            id = this.id.value,
            name = this.name,
            description = this.description,
            createdAt = this.createdAt.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy. hh:mm")),
            updatedAt = this.updatedAt.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
            images = this.images.map { it.toImageResponse() }
        )
    }

    fun toProjectFullResponse(): ProjectFullResponse {
        return ProjectFullResponse(
            id = this.id.value,
            name = this.name,
            description = this.description,
            createdAt = this.createdAt.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy. hh:mm")),
            updatedAt = this.updatedAt.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
            portfolio = this.portfolio.toPortfolioShortResponse(),
            images = this.images.map { it.toImageResponse() },
            videos = this.videos.map { it.toVideoResponse() },
            documents = this.documents.map { it.toDocumentResponse() }
        )
    }
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

@OptIn(ExperimentalUuidApi::class)
@Suppress("UnusedReceiverParameter")
fun Application.configureDatabase() {

    transaction(db = DatabaseFactory.POSTGRES_MASTER) {
        SchemaUtils.create(
            ImageTable, VideoTable, DocumentTable, IdentityTable, ProfessionTable,
            PortfolioTable, ProjectTable, ProjectImageTable, ProjectVideoTable, ProjectDocumentTable
        )

        val firstProfessionId = Uuid.parse("622827b6-fdde-4abd-a23a-feb2fb7a0649").toJavaUuid()
        ProfessionEntity.findById(firstProfessionId) ?: ProfessionEntity.new(firstProfessionId) {
            this.name = "Java Developer"
            this.description = "Описание профессии Java Developer"
        }
        val secondProfessionId = Uuid.parse("d87bcb68-b03d-4785-96d9-8a28b2bcd1a4").toJavaUuid()
        ProfessionEntity.findById(secondProfessionId) ?: ProfessionEntity.new(secondProfessionId) {
            this.name = "Frontend Developer"
            this.description = "Описание профессии Frontend Developer"
        }
        val thirdProfessionId = Uuid.parse("0d10abaf-64ee-49a6-aef4-16d804cb73f2").toJavaUuid()
        ProfessionEntity.findById(thirdProfessionId) ?: ProfessionEntity.new(thirdProfessionId) {
            this.name = "Backend Developer"
            this.description = "Описание профессии Backend Developer"
        }
        val fourthProfessionId = Uuid.parse("21406293-791d-42f5-b38a-44367603a9dd").toJavaUuid()
        ProfessionEntity.findById(fourthProfessionId) ?: ProfessionEntity.new(fourthProfessionId) {
            this.name = "Kotlin Developer"
            this.description = "Описание профессии Kotlin Developer"
        }
    }
}