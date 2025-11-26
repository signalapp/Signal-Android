/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

/**
 * Describes what type of message something is. This serves as an abstraction layer over the bitmasks
 * in [MessageTypes]. Currently only used for [org.thoughtcrime.securesms.mms.IncomingMessage],
 * but will hopefully be used more widely in the future.
 */
enum class MessageType {
  /** A typical message with no special typing */
  NORMAL,

  /** A mobilecoin payment */
  PAYMENTS_NOTIFICATION,

  /** A request to activate mobilecoin payments */
  ACTIVATE_PAYMENTS_REQUEST,

  /** Mobilecoin payments have been activated (in response to a [ACTIVATE_PAYMENTS_REQUEST] */
  PAYMENTS_ACTIVATED,

  /** An emoji reaction to a story */
  STORY_REACTION,

  /** The chat's expiration timer has been updated */
  EXPIRATION_UPDATE,

  /** A new contact has joined Signal */
  CONTACT_JOINED,

  /** Any update to a group */
  GROUP_UPDATE,

  /** A user's identity/safety number has changed */
  IDENTITY_UPDATE,

  /** You verified a user's identity/safety number */
  IDENTITY_VERIFIED,

  /** You unverified a user's identity/safety number, resetting it to the default state */
  IDENTITY_DEFAULT,

  /** A manual session reset. This is no longer used and is only here for handling possible inbound/sync messages.  */
  END_SESSION,

  /** A poll has ended **/
  POLL_TERMINATE
}
