package org.thoughtcrime.securesms.crypto.storage

/**
 * Allows storing various metadata around prekey state.
 */
interface PreKeyMetadataStore {
  var nextSignedPreKeyId: Int
  var activeSignedPreKeyId: Int
  var isSignedPreKeyRegistered: Boolean
  var signedPreKeyFailureCount: Int
  var nextOneTimePreKeyId: Int
}
