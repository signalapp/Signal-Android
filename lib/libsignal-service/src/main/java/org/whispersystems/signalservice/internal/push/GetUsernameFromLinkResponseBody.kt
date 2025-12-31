package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonProperty

/** Response body for looking up a username by link from the service. */
data class GetUsernameFromLinkResponseBody(@JsonProperty val usernameLinkEncryptedValue: String)
