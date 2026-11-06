package com.sitticun.orderapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class NoteModel(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("updated_at") val updatedAt: String = "",
    val revision: Long? = null
)
