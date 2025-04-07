package no.nb.mlt.wls.domain

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * The annotated element must not be blank.
 *
 */
@Target(AnnotationTarget.TYPE, AnnotationTarget.FIELD)
@Retention
@Constraint(validatedBy = [NullableNotBlankImpl::class])
annotation class NullableNotBlank(
    val message: String = "Validation for field failed",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class NullableNotBlankImpl : ConstraintValidator<NullableNotBlank, String> {
    override fun isValid(
        value: String?,
        context: ConstraintValidatorContext?
    ): Boolean {
        return value == null || value.isNotBlank()
    }
}
