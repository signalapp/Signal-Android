package org.signal.registration.screens.shared

sealed class ContactSupportEvents {
  data object SubmitWithDebugLog : ContactSupportEvents()
  data object SubmitWithoutDebugLog : ContactSupportEvents()
  data object Cancel : ContactSupportEvents()
}
