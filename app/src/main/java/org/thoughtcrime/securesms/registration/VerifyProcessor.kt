package org.thoughtcrime.securesms.registration

interface VerifyProcessor {
  fun hasResult(): Boolean
  fun isServerSentError(): Boolean
}
