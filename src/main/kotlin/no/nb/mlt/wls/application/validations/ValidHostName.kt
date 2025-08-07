package no.nb.mlt.wls.application.validations

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import no.nb.mlt.wls.domain.model.HostName
import kotlin.reflect.KClass

/**
 * Annotation for testing whether a given string is a valid HostName
 *
 * @see HostName
 */
@Target(AnnotationTarget.TYPE, AnnotationTarget.FIELD)
@Retention
@Constraint(validatedBy = [ValidHostNameImpl::class])
annotation class ValidHostName(
    val message: String = "HostName is invalid, or is unsupported by WLS",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class ValidHostNameImpl : ConstraintValidator<ValidHostName, String> {
    override fun isValid(
        value: String?,
        context: ConstraintValidatorContext?
    ): Boolean {
        if (value == null) {
            return false
        }
        try {
            HostName.fromString(value)
            return true
        } catch (_: Exception) {
            return false
        }
        return true
    }
}
