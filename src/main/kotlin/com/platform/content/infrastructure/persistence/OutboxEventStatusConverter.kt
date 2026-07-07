package com.platform.content.infrastructure.persistence

import com.platform.content.domain.model.OutboxEventStatus
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA converter for OutboxEventStatus enum ↔ VARCHAR.
 * Stores the status as its lowercase string value in the database.
 */
@Converter(autoApply = false)
class OutboxEventStatusConverter : AttributeConverter<OutboxEventStatus, String> {

    override fun convertToDatabaseColumn(attribute: OutboxEventStatus?): String? {
        return attribute?.name?.lowercase()
    }

    override fun convertToEntityAttribute(dbData: String?): OutboxEventStatus? {
        return dbData?.let { OutboxEventStatus.valueOf(it.uppercase()) }
    }
}
