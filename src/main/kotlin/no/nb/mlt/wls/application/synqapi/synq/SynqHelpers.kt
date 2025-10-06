package no.nb.mlt.wls.application.synqapi.synq

import no.nb.mlt.wls.domain.model.AssociatedStorage

/**
 * Computes the associated storage from the Synq-specific location.
 * Smelly code is inevitable...
 */
fun computeAssociatedStorage(location: String): AssociatedStorage =
    if (location.uppercase() == "AUTOSTORE_WAREHOUSE") AssociatedStorage.AUTOSTORE else AssociatedStorage.SYNQ
