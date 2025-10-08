package no.nb.mlt.wls.application.synqapi.synq

import no.nb.mlt.wls.domain.model.AssociatedStorage

/**
 * Computes the associated storage from the Synq-specific location.
 * Copilot complaining is inevitable...
 */
fun computeAssociatedStorage(location: String): AssociatedStorage =
    if (location.uppercase().contains("AUTOSTORE")) AssociatedStorage.AUTOSTORE else AssociatedStorage.SYNQ
