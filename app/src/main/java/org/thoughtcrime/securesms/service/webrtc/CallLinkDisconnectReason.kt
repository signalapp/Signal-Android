/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

/**
 * Describes why a user was not able to join a call link.
 *
 * Note: postedAt is kept as a long to ensure Java compatibility.
 */
sealed interface CallLinkDisconnectReason {
  val postedAt: Long

  data class RemovedFromCall(override val postedAt: Long = System.currentTimeMillis()) : CallLinkDisconnectReason
  data class DeniedRequestToJoinCall(override val postedAt: Long = System.currentTimeMillis()) : CallLinkDisconnectReason
}
