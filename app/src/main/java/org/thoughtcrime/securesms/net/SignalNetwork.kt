/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.net

import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.whispersystems.signalservice.api.account.AccountApi
import org.whispersystems.signalservice.api.archive.ArchiveApi
import org.whispersystems.signalservice.api.attachment.AttachmentApi
import org.whispersystems.signalservice.api.calling.CallingApi
import org.whispersystems.signalservice.api.cds.CdsApi
import org.whispersystems.signalservice.api.keys.KeysApi
import org.whispersystems.signalservice.api.link.LinkDeviceApi
import org.whispersystems.signalservice.api.message.MessageApi
import org.whispersystems.signalservice.api.payments.PaymentsApi
import org.whispersystems.signalservice.api.ratelimit.RateLimitChallengeApi
import org.whispersystems.signalservice.api.storage.StorageServiceApi
import org.whispersystems.signalservice.api.username.UsernameApi

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
  @get:JvmName("rateLimitChallenge")
  val rateLimitChallenge: RateLimitChallengeApi
    get() = AppDependencies.rateLimitChallengeApi

  val storageService: StorageServiceApi
    get() = AppDependencies.storageServiceApi

  @JvmStatic
  @get:JvmName("username")
  val username: UsernameApi
    get() = AppDependencies.usernameApi
}
