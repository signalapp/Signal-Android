/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.whispersystems.signalservice.api.services

import org.signal.libsignal.zkgroup.calllinks.CreateCallLinkCredentialRequest
import org.signal.libsignal.zkgroup.calllinks.CreateCallLinkCredentialResponse
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations
import org.whispersystems.signalservice.api.util.CredentialsProvider
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.io.IOException

class CallLinksService(
  configuration: SignalServiceConfiguration,
  credentialsProvider: CredentialsProvider,
  signalAgent: String,
  groupsV2Operations: GroupsV2Operations,
  automaticNetworkRetry: Boolean
) {

  private val pushServiceSocket = PushServiceSocket(
    configuration,
    credentialsProvider,
    signalAgent,
    groupsV2Operations.profileOperations,
    automaticNetworkRetry
  )

  fun getCreateCallLinkAuthCredential(request: CreateCallLinkCredentialRequest): ServiceResponse<CreateCallLinkCredentialResponse> {
    return try {
      ServiceResponse.forResult(pushServiceSocket.getCallLinkAuthResponse(request), 200, "")
    } catch (e: IOException) {
      ServiceResponse.forUnknownError(e)
    }
  }
}
