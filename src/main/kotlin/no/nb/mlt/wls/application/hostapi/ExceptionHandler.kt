package no.nb.mlt.wls.application.hostapi

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.ports.inbound.IllegalOrderStateException
import no.nb.mlt.wls.domain.ports.inbound.ItemNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.ServerException
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import org.springframework.core.codec.DecodingException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException

@RestControllerAdvice
class ExceptionHandler {
    @ExceptionHandler(WebExchangeBindException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleMethodArgumentNotValidException(exception: WebExchangeBindException): ResponseEntity<ErrorMessage> {
        val message =
            exception.bindingResult.fieldErrors.joinToString {
                it.field + ":" + it.defaultMessage
            }

        return ResponseEntity
            .badRequest()
            .body(ErrorMessage(message))
    }

    @ExceptionHandler(DecodingException::class)
    fun handleDecodingException(e: DecodingException): ResponseEntity<ErrorMessage> =
        ResponseEntity
            .badRequest()
            .body(ErrorMessage(e.message ?: "Error decoding request body. Likely missing a field."))

    @ExceptionHandler(ValidationException::class)
    fun handleValidationException(e: ValidationException): ResponseEntity<ErrorMessage> =
        ResponseEntity
            .badRequest()
            .body(ErrorMessage(e.message))

    @ExceptionHandler(IllegalOrderStateException::class)
    fun handleIllegalOrderStateException(e: IllegalOrderStateException): ResponseEntity<ErrorMessage> =
        ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorMessage(e.message))

    @ExceptionHandler(ServerException::class)
    fun handleServerException(e: ServerException): ResponseEntity<ErrorMessage> =
        ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorMessage(e.message))

    @ExceptionHandler(OrderNotFoundException::class)
    fun handleOrderNotFoundException(e: OrderNotFoundException): ResponseEntity<ErrorMessage> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorMessage(e.message))

    @ExceptionHandler(ItemNotFoundException::class)
    fun handleItemNotFoundException(e: ItemNotFoundException): ResponseEntity<ErrorMessage> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorMessage(e.message))
}

@Schema(
    description = """Object containing an error message that occurred during processing of a request in Hermes WLS.""",
    example = """
    {
      "message": "The item's 'hostId' is required, and it cannot be blank"
    }
    """
)
data class ErrorMessage(
    @Schema(
        description = """Message describing the error.""",
        example = "The item's 'hostId' is required, and it cannot be blank"
    )
    val message: String
)
