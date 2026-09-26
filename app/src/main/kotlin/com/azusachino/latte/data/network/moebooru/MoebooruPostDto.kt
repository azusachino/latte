package com.azusachino.latte.data.network.moebooru

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MoebooruPostDto(
    val id: Long,
    val tags: String = "",
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("creator_id") val creatorId: Long? = null,
    val author: String? = null,
    val source: String? = null,
    val score: Int = 0,
    val md5: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("file_ext") val fileExt: String? = null,
    @SerialName("file_url") val fileUrl: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("preview_width") val previewWidth: Int? = null,
    @SerialName("preview_height") val previewHeight: Int? = null,
    @SerialName("sample_url") val sampleUrl: String? = null,
    @SerialName("sample_width") val sampleWidth: Int? = null,
    @SerialName("sample_height") val sampleHeight: Int? = null,
    @SerialName("sample_file_size") val sampleFileSize: Long? = null,
    @SerialName("jpeg_url") val jpegUrl: String? = null,
    @SerialName("jpeg_width") val jpegWidth: Int? = null,
    @SerialName("jpeg_height") val jpegHeight: Int? = null,
    @SerialName("jpeg_file_size") val jpegFileSize: Long? = null,
    val rating: String = "s",
    @SerialName("has_children") val hasChildren: Boolean = false,
    @SerialName("parent_id") val parentId: Long? = null,
    val width: Int = 0,
    val height: Int = 0,
)
