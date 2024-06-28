package no.nb.mlt.wls.core.dto

import no.nb.mlt.wls.core.data.Packaging

@JvmRecord
data class InnerProductDTO(val category: String, val description: String, val packaging: Packaging, val id: String)
