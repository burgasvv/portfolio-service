package org.burgas.dto

import kotlinx.serialization.Serializable
import org.burgas.database.Authority
import org.burgas.serialization.UUIDSerializer
import java.util.*

@Serializable
data class ImageResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val contentType: String? = null,
    val preview: Boolean? = null
)

@Serializable
data class VideoResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val contentType: String? = null
)

@Serializable
data class DocumentResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val contentType: String? = null
)

@Serializable
data class IdentityRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val authority: Authority? = null,
    val email: String? = null,
    val password: String? = null,
    val enabled: Boolean? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null
)

@Serializable
data class IdentityShortResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val email: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val image: ImageResponse? = null
)

@Serializable
data class IdentityFullResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val email: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val image: ImageResponse? = null,
    val portfolios: List<PortfolioWithoutIdentityResponse>? = null
)

@Serializable
data class ProfessionRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null
)

@Serializable
data class ProfessionShortResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val image: ImageResponse? = null
)

@Serializable
data class ProfessionFullResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val image: ImageResponse? = null,
    val portfolios: List<PortfolioWithoutProfessionResponse>? = null
)

@Serializable
data class PortfolioRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val identityId: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val professionId: UUID? = null,
    val opened: Boolean? = null
)

@Serializable
data class PortfolioShortResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val image: ImageResponse? = null,
    val identity: IdentityShortResponse? = null,
    val profession: ProfessionShortResponse? = null,
    val opened: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class PortfolioWithoutProfessionResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val image: ImageResponse? = null,
    val identity: IdentityShortResponse? = null,
    val opened: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class PortfolioWithoutIdentityResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val image: ImageResponse? = null,
    val profession: ProfessionShortResponse? = null,
    val opened: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class PortfolioFullResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val image: ImageResponse? = null,
    val identity: IdentityShortResponse? = null,
    val profession: ProfessionShortResponse? = null,
    val opened: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val projects: List<ProjectWithoutPortfolioResponse>? = null
)

@Serializable
data class ProjectRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val portfolioId: UUID? = null
)

@Serializable
data class ProjectShortResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val portfolio: PortfolioShortResponse? = null
)

@Serializable
data class ProjectWithoutPortfolioResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val images: List<ImageResponse>? = null
)

@Serializable
data class ProjectFullResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val portfolio: PortfolioShortResponse? = null,
    val images: List<ImageResponse>? = null,
    val videos: List<VideoResponse>? = null,
    val documents: List<DocumentResponse>? = null
)