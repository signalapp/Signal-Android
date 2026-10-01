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
import org.signal.network.service.ArchiveService
import org.signal.network.service.MessageService
import org.signal.network.service.StorageServiceService
import org.signal.network.service.UsernameService
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.dependencies.KeyTransparencyApi
import org.whispersystems.signalservice.api.account.AccountApi
import org.whispersystems.signalservice.api.keys.KeysApi
import org.whispersystems.signalservice.api.message.MessageApi
import org.whispersystems.signalservice.api.profiles.ProfileApi
import org.whispersystems.signalservice.api.services.DonationsService
import org.whispersystems.signalservice.api.services.ProfileService
import org.whispersystems.signalservice.api.storage.StorageServiceApi

/**
 * A convenient way to access network operations, similar to [org.thoughtcrime.securesms.database.SignalDatabase] and [org.thoughtcrime.securesms.keyvalue.SignalStore].
 *
 * Everything is served out of a single instance, which can be swapped out via [init] to inject custom implementations.
 */
open class SignalNetwork {

  open val accountApi: AccountApi
    get() = AppDependencies.accountApi

  open val accountApiV2: AccountApiV2
    get() = AppDependencies.accountApiV2

  open val archiveApi: ArchiveApi
    get() = AppDependencies.archiveApi

  open val archiveApiV2: ArchiveApiV2
    get() = AppDependencies.archiveApiV2

  open val attachmentApi: AttachmentApi
    get() = AppDependencies.attachmentApi

  open val callingApi: CallingApi
    get() = AppDependencies.callingApi

  open val cdsApi: CdsApi
    get() = AppDependencies.cdsApi

  open val certificateApi: CertificateApi
    get() = AppDependencies.certificateApi

  open val keysApi: KeysApi
    get() = AppDependencies.keysApi

  open val keyTransparencyApi: KeyTransparencyApi
    get() = AppDependencies.keyTransparencyApi

  open val linkDeviceApi: LinkDeviceApi
    get() = AppDependencies.linkDeviceApi

  open val messageApi: MessageApi
    get() = AppDependencies.messageApi

  open val paymentsApi: PaymentsApi
    get() = AppDependencies.paymentsApi

  open val profileApi: ProfileApi
    get() = AppDependencies.profileApi

  open val provisioningApi: ProvisioningApi
    get() = AppDependencies.provisioningApi

  open val rateLimitChallengeApi: RateLimitChallengeApi
    get() = AppDependencies.rateLimitChallengeApi

  open val remoteConfigApi: RemoteConfigApi
    get() = AppDependencies.remoteConfigApi

  open val storageApi: StorageServiceApi
    get() = AppDependencies.storageServiceApi

  open val svrBApi: SvrBApi
    get() = AppDependencies.svrBApi

  open val usernameApi: UsernameApi
    get() = AppDependencies.usernameApi

  open val archiveService: ArchiveService
    get() = AppDependencies.archiveService

  open val donationsService: DonationsService
    get() = AppDependencies.donationsService

  open val messageService: MessageService
    get() = AppDependencies.messageService

  open val profileService: ProfileService
    get() = AppDependencies.profileService

  open val storageService: StorageServiceService
    get() = AppDependencies.storageService

  open val usernameService: UsernameService
    get() = AppDependencies.usernameService

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
    @get:JvmName("accountApi")
    val accountApi: AccountApi
      get() = instance.accountApi

    @JvmStatic
    @get:JvmName("accountApiV2")
    val accountApiV2: AccountApiV2
      get() = instance.accountApiV2

    @JvmStatic
    @get:JvmName("archiveApi")
    val archiveApi: ArchiveApi
      get() = instance.archiveApi

    @JvmStatic
    @get:JvmName("archiveApiV2")
    val archiveApiV2: ArchiveApiV2
      get() = instance.archiveApiV2

    @JvmStatic
    @get:JvmName("attachmentApi")
    val attachmentApi: AttachmentApi
      get() = instance.attachmentApi

    @JvmStatic
    @get:JvmName("callingApi")
    val callingApi: CallingApi
      get() = instance.callingApi

    @JvmStatic
    @get:JvmName("cdsApi")
    val cdsApi: CdsApi
      get() = instance.cdsApi

    @JvmStatic
    @get:JvmName("certificateApi")
    val certificateApi: CertificateApi
      get() = instance.certificateApi

    @JvmStatic
    @get:JvmName("keysApi")
    val keysApi: KeysApi
      get() = instance.keysApi

    @JvmStatic
    @get:JvmName("keyTransparencyApi")
    val keyTransparencyApi: KeyTransparencyApi
      get() = instance.keyTransparencyApi

    @JvmStatic
    @get:JvmName("linkDeviceApi")
    val linkDeviceApi: LinkDeviceApi
      get() = instance.linkDeviceApi

    @JvmStatic
    @get:JvmName("messageApi")
    val messageApi: MessageApi
      get() = instance.messageApi

    @JvmStatic
    @get:JvmName("paymentsApi")
    val paymentsApi: PaymentsApi
      get() = instance.paymentsApi

    @JvmStatic
    @get:JvmName("profileApi")
    val profileApi: ProfileApi
      get() = instance.profileApi

    @JvmStatic
    @get:JvmName("provisioningApi")
    val provisioningApi: ProvisioningApi
      get() = instance.provisioningApi

    @JvmStatic
    @get:JvmName("rateLimitChallengeApi")
    val rateLimitChallengeApi: RateLimitChallengeApi
      get() = instance.rateLimitChallengeApi

    @JvmStatic
    @get:JvmName("remoteConfigApi")
    val remoteConfigApi: RemoteConfigApi
      get() = instance.remoteConfigApi

    @JvmStatic
    @get:JvmName("storageApi")
    val storageApi: StorageServiceApi
      get() = instance.storageApi

    @JvmStatic
    @get:JvmName("svrBApi")
    val svrBApi: SvrBApi
      get() = instance.svrBApi

    @JvmStatic
    @get:JvmName("usernameApi")
    val usernameApi: UsernameApi
      get() = instance.usernameApi

    @JvmStatic
    @get:JvmName("archiveService")
    val archiveService: ArchiveService
      get() = instance.archiveService

    @JvmStatic
    @get:JvmName("donationsService")
    val donationsService: DonationsService
      get() = instance.donationsService

    @JvmStatic
    @get:JvmName("messageService")
    val messageService: MessageService
      get() = instance.messageService

    @JvmStatic
    @get:JvmName("profileService")
    val profileService: ProfileService
      get() = instance.profileService

    @JvmStatic
    @get:JvmName("storageService")
    val storageService: StorageServiceService
      get() = instance.storageService

    @JvmStatic
    @get:JvmName("usernameService")
    val usernameService: UsernameService
      get() = instance.usernameService
  }
}
