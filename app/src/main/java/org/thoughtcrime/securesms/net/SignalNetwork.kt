/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.net

import org.signal.network.api.ArchiveApi
import org.signal.network.api.CallingApi
import org.signal.network.api.CdsApi
import org.signal.network.api.CertificateApi
import org.signal.network.api.LinkDeviceApi
import org.signal.network.api.PaymentsApi
import org.signal.network.api.ProvisioningApi
import org.signal.network.api.RateLimitChallengeApi
import org.signal.network.api.RemoteConfigApi
import org.signal.network.api.SvrBApi
import org.signal.network.api.UsernameApi
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.dependencies.KeyTransparencyApi
import org.whispersystems.signalservice.api.account.AccountApi
import org.whispersystems.signalservice.api.attachment.AttachmentApi
import org.whispersystems.signalservice.api.keys.KeysApi
import org.whispersystems.signalservice.api.message.MessageApi
import org.whispersystems.signalservice.api.profiles.ProfileApi
import org.whispersystems.signalservice.api.storage.StorageServiceApi

/**
 * A convenient way to access network operations, similar to [org.thoughtcrime.securesms.database.SignalDatabase] and [org.thoughtcrime.securesms.keyvalue.SignalStore].
 */
object SignalNetwork {
  @JvmStatic
  @get:JvmName("account")
  val account: AccountApi
    get() = AppDependencies.accountApi

  val archive: ArchiveApi
    get() = AppDependencies.archiveApi

  val attachments: AttachmentApi
    get() = AppDependencies.attachmentApi

  @JvmStatic
  @get:JvmName("calling")
  val calling: CallingApi
    get() = AppDependencies.callingApi

  val cdsApi: CdsApi
    get() = AppDependencies.cdsApi

  @JvmStatic
  @get:JvmName("certificate")
  val certificate: CertificateApi
    get() = AppDependencies.certificateApi

  @JvmStatic
  @get:JvmName("keys")
  val keys: KeysApi
    get() = AppDependencies.keysApi

  val linkDevice: LinkDeviceApi
    get() = AppDependencies.linkDeviceApi

  @JvmStatic
  @get:JvmName("message")
  val message: MessageApi
    get() = AppDependencies.messageApi

  @JvmStatic
  @get:JvmName("payments")
  val payments: PaymentsApi
    get() = AppDependencies.paymentsApi

  @JvmStatic
  @get:JvmName("profile")
  val profile: ProfileApi
    get() = AppDependencies.profileApi

  val provisioning: ProvisioningApi
    get() = AppDependencies.provisioningApi

  @JvmStatic
  @get:JvmName("rateLimitChallenge")
  val rateLimitChallenge: RateLimitChallengeApi
    get() = AppDependencies.rateLimitChallengeApi

  @JvmStatic
  @get:JvmName("remoteConfig")
  val remoteConfig: RemoteConfigApi
    get() = AppDependencies.remoteConfigApi

  val storageService: StorageServiceApi
    get() = AppDependencies.storageServiceApi

  @JvmStatic
  @get:JvmName("username")
  val username: UsernameApi
    get() = AppDependencies.usernameApi

  val svrB: SvrBApi
    get() = AppDependencies.svrBApi

  val keyTransparency: KeyTransparencyApi
    get() = AppDependencies.keyTransparencyApi
}
