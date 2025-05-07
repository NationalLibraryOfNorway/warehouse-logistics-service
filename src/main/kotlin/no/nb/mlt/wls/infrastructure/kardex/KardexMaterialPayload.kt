package no.nb.mlt.wls.infrastructure.kardex

import jakarta.validation.constraints.Min
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory

data class KardexMaterialPayload(
    val materialName: String,
    val info1: String,
    val info2: String,
    val info3: String,
    val info4: String,
    val info5: String,
    val unitOfMeasure: String = "",
    val isBlocked: Boolean = false,
    val propertyName: String,
    val storageRules: List<StorageRule>?
)

fun Item.toKardexPayload(): KardexMaterialPayload {
    return KardexMaterialPayload(
        materialName = hostId,
        info1 = description,
        info2 = "",
        info3 = "",
        info4 = "",
        info5 = hostName.name,
        propertyName = fromItemCategory(itemCategory),
        storageRules = listOf()
    )
}

fun fromItemCategory(itemCategory: ItemCategory): String {
    return when (itemCategory) {
        ItemCategory.PAPER -> "PAPIR"
        ItemCategory.FILM -> "FILM"
        else -> "UNAVAILABLE"
    }
}

data class StorageRule(
    val description: String,
    val binName: String,
    @field:Min(value = 14)
    val maxStockPerBin: Double,
    @field:Min(value = 1)
    val minStockPerBin: Double,
    @field:Min(value = 1)
    val requiredCapacity: Int,
    val isDefaultBin: Boolean,
    val alwaysUseNewLocation: Boolean
)
