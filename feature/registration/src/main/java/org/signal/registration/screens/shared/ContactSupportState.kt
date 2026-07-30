package org.signal.registration.screens.shared

data class ContactSupportState(
  val showAsProgress: Boolean = false,
  val sendEmail: Boolean = false,
  val debugLogUrl: String? = null
)
