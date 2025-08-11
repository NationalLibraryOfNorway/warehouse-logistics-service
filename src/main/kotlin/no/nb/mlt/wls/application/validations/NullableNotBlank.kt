package no.nb.mlt.wls.application.validations

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * Annotation for ensuring that when a given field is not null, it's also not blank
 */
@Target(AnnotationTarget.TYPE, AnnotationTarget.FIELD)
@Retention
@Constraint(validatedBy = [NullableNotBlankImpl::class])
annotation class NullableNotBlank(
    val message: String = "This nullable field must not be blank if not null.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class NullableNotBlankImpl : ConstraintValidator<NullableNotBlank, String> {
    override fun isValid(
        value: String?,
        context: ConstraintValidatorContext?
    ): Boolean = value == null || value.isNotBlank()
}
