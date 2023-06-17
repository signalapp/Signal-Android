package org.thoughtcrime.securesms.crypto.storage

/**
 * Allows storing various metadata around prekey state.
 */
interface PreKeyMetadataStore {
  var nextSignedPreKeyId: Int
  var activeSignedPreKeyId: Int
  var isSignedPreKeyRegistered: Boolean
  var lastSignedPreKeyRotationTime: Long
  var nextEcOneTimePreKeyId: Int
  var nextKyberPreKeyId: Int
  var lastResortKyberPreKeyId: Int
  var lastResortKyberPreKeyRotationTime: Long
}
