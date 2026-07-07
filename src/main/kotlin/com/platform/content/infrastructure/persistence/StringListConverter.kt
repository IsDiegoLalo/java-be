package com.platform.content.infrastructure.persistence

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA converter for List<String> ↔ PostgreSQL TEXT[] stored as comma-separated string.
 * This converter handles the mapping between a Kotlin List<String> and the
 * database representation. For PostgreSQL TEXT[] arrays, Hibernate with the
 * appropriate dialect handles the native array type. This converter provides
 * a portable fallback using comma-separated storage.
 *
 * Note: For true PostgreSQL TEXT[] support, the entity field uses
 * columnDefinition = "text[]" and this converter handles serialization.
 */
@Converter(autoApply = false)
class StringListConverter : AttributeConverter<List<String>, String> {

    override fun convertToDatabaseColumn(attribute: List<String>?): String? {
        if (attribute == null) return null
        if (attribute.isEmpty()) return "{}"
        return "{${attribute.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}}"
    }

    override fun convertToEntityAttribute(dbData: String?): List<String>? {
        if (dbData == null) return null
        if (dbData == "{}" || dbData.isBlank()) return emptyList()
        // Parse PostgreSQL array format: {"val1","val2"} or {val1,val2}
        val content = dbData.removeSurrounding("{", "}")
        if (content.isEmpty()) return emptyList()
        return parsePostgresArray(content)
    }

    private fun parsePostgresArray(content: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < content.length) {
            if (content[i] == '"') {
                // Quoted value
                i++ // skip opening quote
                val sb = StringBuilder()
                while (i < content.length && content[i] != '"') {
                    if (content[i] == '\\' && i + 1 < content.length) {
                        i++ // skip escape char
                    }
                    sb.append(content[i])
                    i++
                }
                result.add(sb.toString())
                i++ // skip closing quote
                if (i < content.length && content[i] == ',') i++ // skip comma
            } else {
                // Unquoted value
                val end = content.indexOf(',', i)
                if (end == -1) {
                    result.add(content.substring(i))
                    i = content.length
                } else {
                    result.add(content.substring(i, end))
                    i = end + 1
                }
            }
        }
        return result
    }
}
