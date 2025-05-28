package no.nb.mlt.wls.infrastructure.kardex

data class KardexResponse(
    val message: String,
    val errors: List<KardexError>
) {
    fun isError(): Boolean = errors.isNotEmpty()
}

data class KardexError(
    val item: String,
    val errors: List<String>
)
