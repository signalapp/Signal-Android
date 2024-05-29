package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * POST /v1/archives/redeem-receipt
 *
 * Request object for redeeming a receipt from a donation transaction.
 *
 * @param receiptCredentialPresentation base64-encoded no-newlines standard-character-set with-padding of the bytes of a [ReceiptCredentialPresentation] object
 */
internal class RedeemArchivesReceiptRequest @JsonCreator constructor(@param:JsonProperty("receiptCredentialPresentation") val receiptCredentialPresentation: String)
