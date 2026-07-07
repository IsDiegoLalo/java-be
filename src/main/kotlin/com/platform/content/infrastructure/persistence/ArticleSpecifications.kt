package com.platform.content.infrastructure.persistence

import com.platform.content.domain.model.ArticleStatus
import com.platform.content.domain.port.ArticleFilter
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification

/**
 * JPA Specification builder for article filtering.
 * Constructs dynamic WHERE clauses based on ArticleFilter criteria.
 * Uses the Specification pattern for composable, reusable query predicates.
 */
object ArticleSpecifications {

    /**
     * Builds a combined Specification from the given ArticleFilter.
     * Only non-null filter fields contribute predicates; all predicates are ANDed together.
     */
    fun fromFilter(filter: ArticleFilter): Specification<ArticleEntity> {
        return Specification { root: Root<ArticleEntity>,
                              query: CriteriaQuery<*>?,
                              criteriaBuilder: CriteriaBuilder ->
            val predicates = mutableListOf<Predicate>()

            filter.authorId?.let { authorId ->
                predicates.add(criteriaBuilder.equal(root.get<Any>("authorId"), authorId))
            }

            filter.categoryId?.let { categoryId ->
                predicates.add(criteriaBuilder.equal(root.get<Any>("categoryId"), categoryId))
            }

            filter.status?.let { status ->
                predicates.add(criteriaBuilder.equal(root.get<ArticleStatus>("status"), status))
            }

            filter.tags?.let { tags ->
                if (tags.isNotEmpty()) {
                    // Tags are stored as PostgreSQL text[] serialized via StringListConverter.
                    // Use LIKE on the serialized representation to check containment.
                    tags.forEach { tag ->
                        predicates.add(
                            criteriaBuilder.like(
                                root.get("tags"),
                                "%\"$tag\"%"
                            )
                        )
                    }
                }
            }

            if (predicates.isEmpty()) {
                criteriaBuilder.conjunction()
            } else {
                criteriaBuilder.and(*predicates.toTypedArray())
            }
        }
    }
}
