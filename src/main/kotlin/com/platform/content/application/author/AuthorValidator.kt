package com.platform.content.application.author

import com.platform.content.domain.FieldError
import com.platform.content.domain.ValidationException
import org.springframework.stereotype.Component

/**
 * Validates Author fields at the domain/application level.
 * Email uniqueness is NOT checked here — that responsibility
 * belongs to the AuthorService which has access to the repository.
 */
@Component
class AuthorValidator {

    companion object {
        private const val NAME_MAX_LENGTH = 100
        private const val EMAIL_MAX_LENGTH = 255
        private const val BIO_MAX_LENGTH = 500

        /**
         * RFC 5322 compliant email pattern.
         */
        private val EMAIL_PATTERN = Regex(
            """^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"""
        )
    }

    /**
     * Validates author fields and throws [ValidationException] if any violations are found.
     *
     * @param name the author's name (required, 1–100 characters, non-blank)
     * @param email the author's email (required, RFC 5322 format, max 255 characters)
     * @param bio the author's bio (optional, max 500 characters)
     * @throws ValidationException if one or more field validations fail
     */
    fun validate(name: String, email: String, bio: String?) {
        val errors = mutableListOf<FieldError>()

        validateName(name, errors)
        validateEmail(email, errors)
        validateBio(bio, errors)

        if (errors.isNotEmpty()) {
            throw ValidationException(errors)
        }
    }

    private fun validateName(name: String, errors: MutableList<FieldError>) {
        if (name.isBlank()) {
            errors.add(FieldError("name", "Name must not be blank"))
        } else if (name.length > NAME_MAX_LENGTH) {
            errors.add(FieldError("name", "Name must not exceed $NAME_MAX_LENGTH characters"))
        }
    }

    private fun validateEmail(email: String, errors: MutableList<FieldError>) {
        if (email.isBlank()) {
            errors.add(FieldError("email", "Email must not be blank"))
        } else {
            if (email.length > EMAIL_MAX_LENGTH) {
                errors.add(FieldError("email", "Email must not exceed $EMAIL_MAX_LENGTH characters"))
            }
            if (!EMAIL_PATTERN.matches(email)) {
                errors.add(FieldError("email", "Email must be a valid RFC 5322 email address"))
            }
        }
    }

    private fun validateBio(bio: String?, errors: MutableList<FieldError>) {
        if (bio != null && bio.length > BIO_MAX_LENGTH) {
            errors.add(FieldError("bio", "Bio must not exceed $BIO_MAX_LENGTH characters"))
        }
    }
}
