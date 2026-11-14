package org.thoughtcrime.securesms.sharing.v2

/**
 * Reasons a share could not be resolved into shareable data.
 */
enum class ShareError {
  /** The shared content could not be read because the sending app did not grant URI access. */
  ACCESS_DENIED,

  /** Any other failure while trying to read the shared content. */
  UNKNOWN
}
