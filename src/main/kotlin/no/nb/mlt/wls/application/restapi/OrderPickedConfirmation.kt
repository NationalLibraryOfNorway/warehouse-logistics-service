package no.nb.mlt.wls.application.restapi

data class OrderPickedConfirmation(
    val orderLine: List<OrderPickedItem>,
    val operator: String,
    val warehouse: String
)

data class OrderPickedItem(
    val confidentialProduct: Boolean,
    val hostName: String,
    val orderLineNumber: Int,
    val orderTuId: Int,
    val orderTuType: String,
    val productId: String,
    val productVersionId: String,
    val quantity: Double,
    val attributeValue: List<Map<String, String>>
)
