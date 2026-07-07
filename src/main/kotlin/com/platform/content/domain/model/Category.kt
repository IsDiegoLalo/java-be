package com.platform.content.domain.model

import java.util.UUID

data class Category(
    val id: UUID,
    val name: String,
    val description: String?,
    val slug: String
)
