package com.platform.content.domain.model

import java.time.Instant
import java.util.UUID

data class Author(
    val id: UUID,
    val name: String,
    val email: String,
    val bio: String?,
    val createdAt: Instant
)
