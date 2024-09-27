package no.nb.mlt.wls.application.restapi

data class ItemUpdate(
    val orderLine: List<LoadUnit>,
    val user: String,
    val warehouse: String
)

data class LoadUnit(
    val confidentialProduct: Boolean,
    val hostName: String,
    val productId: String,
    val productOwner: String,
    val productVersionId: String,
    val quantityOnHand: Double,
    val suspect: Boolean,
    val attributeValue: List<Map<String, String>>,
    val position: List<Map<String, Int>>
)
