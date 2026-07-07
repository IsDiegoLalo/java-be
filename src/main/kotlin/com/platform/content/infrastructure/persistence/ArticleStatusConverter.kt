package com.platform.content.infrastructure.persistence

import com.platform.content.domain.model.ArticleStatus
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA converter for ArticleStatus sealed class ↔ VARCHAR.
 * Stores the status as its lowercase string value in the database.
 */
@Converter(autoApply = false)
class ArticleStatusConverter : AttributeConverter<ArticleStatus, String> {

    override fun convertToDatabaseColumn(attribute: ArticleStatus?): String? {
        return attribute?.value
    }

    override fun convertToEntityAttribute(dbData: String?): ArticleStatus? {
        return dbData?.let { ArticleStatus.fromString(it) }
    }
}
