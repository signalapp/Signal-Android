/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber

fun PhoneNumber.toE164(): String {
  return PhoneNumberUtil.getInstance().format(this, PhoneNumberUtil.PhoneNumberFormat.E164)
}

fun Fragment.isBindingInvalid(): Boolean {
  return viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)
}
