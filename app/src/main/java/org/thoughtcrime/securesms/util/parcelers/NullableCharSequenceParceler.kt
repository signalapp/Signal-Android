/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util.parcelers

import android.os.Parcel
import android.text.TextUtils
import kotlinx.parcelize.Parceler

/**
 * Parceler for a nullable [CharSequence], preserving any spans (e.g. mention and styling annotations).
 */
object NullableCharSequenceParceler : Parceler<CharSequence?> {
  override fun create(parcel: Parcel): CharSequence? {
    return TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel)
  }

  override fun CharSequence?.write(parcel: Parcel, flags: Int) {
    TextUtils.writeToParcel(this, parcel, flags)
  }
}
