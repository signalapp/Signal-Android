package org.whispersystems.signalservice.api.push.exceptions

/**
 * known possible values for @property[reason]:
 * providerRejected - indicates that the provider understood the request, but declined to deliver a verification SMS/call (potentially due to fraud prevention rules)
 * providerUnavailable - indicates that the provider could not be reached or did not respond to the request to send a verification code in a timely manner
 * illegalArgument - some part of the request was not understood or accepted by the provider (e.g. the provider did not recognize the phone number as a valid number for the selected transport)
 */
class ExternalServiceFailureException(val isPermanent: Boolean, val reason: String) : NonSuccessfulResponseCodeException(502)
