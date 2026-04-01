package org.burgas.dto

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.burgas.database.Authority
import org.burgas.serialization.UUIDSerializer
import java.util.*

interface Request {
    val id: UUID?
}

interface Response {
    val id: UUID?
}

@Serializable
data class ImageResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val contentType: String? = null,
    val preview: Boolean? = null
) : Response

@Serializable
data class VideoResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val contentType: String? = null
) : Response

@Serializable
data class DocumentResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val contentType: String? = null
) : Response

@Serializable
data class IdentityRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val authority: Authority? = null,
    val email: String? = null,
    val password: String? = null,
    val phone: String? = null,
    val enabled: Boolean? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val birthdate: LocalDate? = null,
    val telegram: String? = null,
    val whatsUp: String? = null,
    val max: String? = null,
    val about: String? = null
) : Request

@Serializable
data class IdentityShortResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val email: String? = null,
    val phone: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val birthdate: String? = null,
    val telegram: String? = null,
    val whatsUp: String? = null,
    val max: String? = null,
    val about: String? = null,
    val image: ImageResponse? = null
) : Response

@Serializable
data class IdentityFullResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val email: String? = null,
    val phone: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val birthdate: String? = null,
    val telegram: String? = null,
    val whatsUp: String? = null,
    val max: String? = null,
    val about: String? = null,
    val image: ImageResponse? = null,
    val portfolios: List<PortfolioWithoutIdentityResponse>? = null
) : Response

@Serializable
data class ProfessionRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null
) : Request

@Serializable
data class ProfessionShortResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val image: ImageResponse? = null
) : Response

@Serializable
data class ProfessionFullResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val image: ImageResponse? = null,
    val portfolios: List<PortfolioWithoutProfessionResponse>? = null
) : Response

@Serializable
data class PortfolioRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val identityId: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val professionId: UUID? = null,
    val opened: Boolean? = null
) : Request

@Serializable
data class PortfolioShortResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val image: ImageResponse? = null,
    val identity: IdentityShortResponse? = null,
    val profession: ProfessionShortResponse? = null,
    val opened: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
) : Response

@Serializable
data class PortfolioWithoutProfessionResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val image: ImageResponse? = null,
    val identity: IdentityShortResponse? = null,
    val opened: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
) : Response

@Serializable
data class PortfolioWithoutIdentityResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val image: ImageResponse? = null,
    val profession: ProfessionShortResponse? = null,
    val opened: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
) : Response

@Serializable
data class PortfolioFullResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val image: ImageResponse? = null,
    val identity: IdentityShortResponse? = null,
    val profession: ProfessionShortResponse? = null,
    val opened: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val projects: List<ProjectWithoutPortfolioResponse>? = null
) : Response

@Serializable
data class ProjectRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val portfolioId: UUID? = null
) : Request

@Serializable
data class ProjectShortResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val image: ImageResponse? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val portfolio: PortfolioShortResponse? = null
) : Response

@Serializable
data class ProjectWithoutPortfolioResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val image: ImageResponse? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
) : Response

@Serializable
data class ProjectFullResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val image: ImageResponse? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val portfolio: PortfolioShortResponse? = null,
    val images: List<ImageResponse>? = null,
    val videos: List<VideoResponse>? = null,
    val documents: List<DocumentResponse>? = null
) : Response

@Serializable
data class FileResponse(
    val fileIds: List<@Serializable(with = UUIDSerializer::class) UUID>
)