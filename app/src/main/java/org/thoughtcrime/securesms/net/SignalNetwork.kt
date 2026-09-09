/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.net

import org.signal.network.api.AccountApiV2
import org.signal.network.api.ArchiveApi
import org.signal.network.api.ArchiveApiV2
import org.signal.network.api.AttachmentApi
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
import org.whispersystems.signalservice.api.keys.KeysApi
import org.whispersystems.signalservice.api.message.MessageApi
import org.whispersystems.signalservice.api.profiles.ProfileApi
import org.whispersystems.signalservice.api.storage.StorageServiceApi

/**
 * A convenient way to access network operations, similar to [org.thoughtcrime.securesms.database.SignalDatabase] and [org.thoughtcrime.securesms.keyvalue.SignalStore].
 *
 * Everything is served out of a single instance, which can be swapped out via [init] to inject custom implementations.
 */
open class SignalNetwork {

  open val account: AccountApi
    get() = AppDependencies.accountApi

  open val accountV2: AccountApiV2
    get() = AppDependencies.accountApiV2

  open val archive: ArchiveApi
    get() = AppDependencies.archiveApi

  open val archiveV2: ArchiveApiV2
    get() = AppDependencies.archiveApiV2

  open val attachments: AttachmentApi
    get() = AppDependencies.attachmentApi

  open val calling: CallingApi
    get() = AppDependencies.callingApi

  open val cdsApi: CdsApi
    get() = AppDependencies.cdsApi

  open val certificate: CertificateApi
    get() = AppDependencies.certificateApi

  open val keys: KeysApi
    get() = AppDependencies.keysApi

  open val keyTransparency: KeyTransparencyApi
    get() = AppDependencies.keyTransparencyApi

  open val linkDevice: LinkDeviceApi
    get() = AppDependencies.linkDeviceApi

  open val message: MessageApi
    get() = AppDependencies.messageApi

  open val payments: PaymentsApi
    get() = AppDependencies.paymentsApi

  open val profile: ProfileApi
    get() = AppDependencies.profileApi

  open val provisioning: ProvisioningApi
    get() = AppDependencies.provisioningApi

  open val rateLimitChallenge: RateLimitChallengeApi
    get() = AppDependencies.rateLimitChallengeApi

  open val remoteConfig: RemoteConfigApi
    get() = AppDependencies.remoteConfigApi

  open val storageService: StorageServiceApi
    get() = AppDependencies.storageServiceApi

  open val svrB: SvrBApi
    get() = AppDependencies.svrBApi

  open val username: UsernameApi
    get() = AppDependencies.usernameApi

  companion object {

    private var instance: SignalNetwork = SignalNetwork()

    /**
     * Swaps out the instance that serves every api. Lets us inject custom implementations, including mocks in tests.
     */
    @JvmStatic
    @Synchronized
    fun init(instance: SignalNetwork) {
      Companion.instance = instance
    }

    @JvmStatic
    @get:JvmName("account")
    val account: AccountApi
      get() = instance.account

    @JvmStatic
    @get:JvmName("accountV2")
    val accountV2: AccountApiV2
      get() = instance.accountV2

    @JvmStatic
    @get:JvmName("archive")
    val archive: ArchiveApi
      get() = instance.archive

    @JvmStatic
    @get:JvmName("archiveV2")
    val archiveV2: ArchiveApiV2
      get() = instance.archiveV2

    @JvmStatic
    @get:JvmName("attachments")
    val attachments: AttachmentApi
      get() = instance.attachments

    @JvmStatic
    @get:JvmName("calling")
    val calling: CallingApi
      get() = instance.calling

    @JvmStatic
    @get:JvmName("cdsApi")
    val cdsApi: CdsApi
      get() = instance.cdsApi

    @JvmStatic
    @get:JvmName("certificate")
    val certificate: CertificateApi
      get() = instance.certificate

    @JvmStatic
    @get:JvmName("keys")
    val keys: KeysApi
      get() = instance.keys

    @JvmStatic
    @get:JvmName("keyTransparency")
    val keyTransparency: KeyTransparencyApi
      get() = instance.keyTransparency

    @JvmStatic
    @get:JvmName("linkDevice")
    val linkDevice: LinkDeviceApi
      get() = instance.linkDevice

    @JvmStatic
    @get:JvmName("message")
    val message: MessageApi
      get() = instance.message

    @JvmStatic
    @get:JvmName("payments")
    val payments: PaymentsApi
      get() = instance.payments

    @JvmStatic
    @get:JvmName("profile")
    val profile: ProfileApi
      get() = instance.profile

    @JvmStatic
    @get:JvmName("provisioning")
    val provisioning: ProvisioningApi
      get() = instance.provisioning

    @JvmStatic
    @get:JvmName("rateLimitChallenge")
    val rateLimitChallenge: RateLimitChallengeApi
      get() = instance.rateLimitChallenge

    @JvmStatic
    @get:JvmName("remoteConfig")
    val remoteConfig: RemoteConfigApi
      get() = instance.remoteConfig

    @JvmStatic
    @get:JvmName("storageService")
    val storageService: StorageServiceApi
      get() = instance.storageService

    @JvmStatic
    @get:JvmName("svrB")
    val svrB: SvrBApi
      get() = instance.svrB

    @JvmStatic
    @get:JvmName("username")
    val username: UsernameApi
      get() = instance.username
  }
}
