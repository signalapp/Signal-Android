/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.phonenumber

/**
 * Enter phone number mode to determine if verification is needed or just e164 input is necessary.
 */
enum class EnterPhoneNumberMode {
  /** Normal registration start, collect number to verify */
  NORMAL,

  /** User pre-selected restore/transfer flow, collect number to re-register and restore with */
  COLLECT_FOR_MANUAL_SIGNAL_BACKUPS_RESTORE,

  /** User reversed decision on restore and needs to resume normal re-register but automatically start verify */
  RESTART_AFTER_COLLECTION
}
