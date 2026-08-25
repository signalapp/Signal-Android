/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.backup.MediaRootBackupKey
import org.signal.core.models.backup.MessageBackupKey
import org.whispersystems.signalservice.api.archive.ArchiveServiceCredential
import org.whispersystems.signalservice.api.archive.GetArchiveCdnCredentialsResponse
import kotlin.time.Duration

/**
 * The persisted state [ArchiveService] needs in order to avoid re-fetching things it already has.
 *
 * Archive credentials are deliberately fetched in advance on a schedule rather than on-demand, so almost every operation reads them from here rather than from
 * the network. See [org.signal.network.api.ArchiveApiV2.getServiceCredentials] for why that matters.
 *
 * [onNotEntitled] exists because a 403 means more to the app than it does to this layer -- the implementor gets to decide what it says about the user's account
 * state. A 401 needs no such hook, since resetting the caches below is the entirety of the response to one.
 */
interface ArchiveCacheStore {

  val aci: ACI

  val messageBackupKey: MessageBackupKey

  val mediaRootBackupKey: MediaRootBackupKey

  val messageCredentials: CredentialCache

  val mediaCredentials: CredentialCache

  /** Whether the message backupId has been reserved and the public key set. See [ArchiveService.getArchiveServiceAccess]. */
  var messageBackupInitialized: Boolean

  /** Whether the media backupId has been reserved and the public key set. See [ArchiveService.getArchiveServiceAccess]. */
  var mediaBackupInitialized: Boolean

  /** The `{backupDir}/{mediaDir}` path media lives under on the cdn. Changes whenever the backup is reset. */
  var cachedMediaCdnPath: String?

  /** Linked devices never initialize backups themselves -- the primary does it. */
  val isLinkedDevice: Boolean

  /** True while registration is still waiting on the user's restore decision, before the real keys are known. */
  val isPreRestoreDuringRegistration: Boolean

  /**
   * The server says this account isn't entitled to the operation (403), e.g. a free-tier user performing a media operation.
   *
   * Only the media-listing endpoint can reach this today, since it's the one anonymous archive request we still issue ourselves. libsignal collapses 401 and 403
   * into a single "unauthorized" error, so everything routed through it reports [ArchiveError.CredentialError.Unauthorized] instead.
   */
  fun onNotEntitled()

  /**
   * The credentials for a single archive type (messages or media), keyed by the day they're valid for.
   */
  interface CredentialCache {
    /** The credential valid for [currentTime], or null if we don't have one. */
    fun getForTime(currentTime: Duration): ArchiveServiceCredential?

    /** Adds the given credentials to the existing set. */
    fun add(credentials: List<ArchiveServiceCredential>)

    /** Trims out credentials for days older than [startOfDayInSeconds]. */
    fun clearOlderThan(startOfDayInSeconds: Long)

    /** Clears all credentials, including [cdnReadCredentials]. */
    fun clearAll()

    /** Short-lived headers for reading from the archive cdn. Null once expired. */
    var cdnReadCredentials: GetArchiveCdnCredentialsResponse?
  }
}
