package com.platform.content.api.error

import com.platform.content.domain.ConflictException
import com.platform.content.domain.EntityNotFoundException
import com.platform.content.domain.InvalidTransitionException
import com.platform.content.domain.ValidationException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Global exception handler that maps domain and framework exceptions
 * to RFC 7807 Problem Details responses.
 *
 * Each handler method maps a specific exception type to the appropriate
 * HTTP status code and problem type URI (SRP per handler method).
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleNotFound(
        ex: EntityNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetailResponse> {
        val problem = ProblemDetailResponse(
            type = "/problems/not-found",
            title = "Not Found",
            status = HttpStatus.NOT_FOUND.value(),
            detail = ex.message ?: "Requested resource not found",
            instance = request.requestURI
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem)
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(
        ex: ConflictException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetailResponse> {
        val problem = ProblemDetailResponse(
            type = "/problems/conflict",
            title = "Conflict",
            status = HttpStatus.CONFLICT.value(),
            detail = ex.message ?: "A conflict occurred with the current state",
            instance = request.requestURI
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem)
    }

    @ExceptionHandler(InvalidTransitionException::class)
    fun handleInvalidTransition(
        ex: InvalidTransitionException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetailResponse> {
        val problem = ProblemDetailResponse(
            type = "/problems/invalid-transition",
            title = "Invalid State Transition",
            status = HttpStatus.UNPROCESSABLE_ENTITY.value(),
            detail = ex.message ?: "The requested state transition is not allowed",
            instance = request.requestURI
        )
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetailResponse> {
        val cause = ex.cause
        val detail = when {
            cause is com.fasterxml.jackson.databind.exc.MismatchedInputException && cause.path.isNotEmpty() -> {
                val fieldName = cause.path.joinToString(".") { it.fieldName ?: "[${it.index}]" }
                "Missing required field: '$fieldName'"
            }
            ex.message?.contains("Required request body is missing") == true ->
                "Request body is required"
            else ->
                "Malformed or unreadable request body"
        }
        val problem = ProblemDetailResponse(
            type = "/problems/validation-error",
            title = "Validation Error",
            status = HttpStatus.UNPROCESSABLE_ENTITY.value(),
            detail = detail,
            instance = request.requestURI
        )
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetailResponse> {
        val fieldErrors = ex.bindingResult.fieldErrors.map { fieldError ->
            FieldErrorResponse(
                field = fieldError.field,
                message = fieldError.defaultMessage ?: "Invalid value"
            )
        }
        val problem = ProblemDetailResponse(
            type = "/problems/validation-error",
            title = "Validation Error",
            status = HttpStatus.UNPROCESSABLE_ENTITY.value(),
            detail = "One or more fields failed validation",
            instance = request.requestURI,
            fieldErrors = fieldErrors
        )
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem)
    }

    @ExceptionHandler(ValidationException::class)
    fun handleDomainValidation(
        ex: ValidationException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetailResponse> {
        val fieldErrors = ex.fieldErrors.map { FieldErrorResponse.fromDomain(it) }
        val problem = ProblemDetailResponse(
            type = "/problems/validation-error",
            title = "Validation Error",
            status = HttpStatus.UNPROCESSABLE_ENTITY.value(),
            detail = ex.message ?: "Validation failed",
            instance = request.requestURI,
            fieldErrors = fieldErrors
        )
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetailResponse> {
        logger.error("Unexpected error processing request to {}: {}", request.requestURI, ex.message, ex)
        val problem = ProblemDetailResponse(
            type = "/problems/internal-error",
            title = "Internal Server Error",
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            detail = "An unexpected error occurred",
            instance = request.requestURI
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem)
    }
}
