package org.thoughtcrime.securesms.registration.v2.testdata

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import org.thoughtcrime.securesms.testutil.HexDeserializer

data class KbsTestVector(
  @JsonProperty("backup_id")
  @JsonDeserialize(using = HexDeserializer::class)
  val backupId: ByteArray,

  @JsonProperty("argon2_hash")
  @JsonDeserialize(using = HexDeserializer::class)
  val argon2Hash: ByteArray,

  @JsonProperty("pin")
  val pin: String? = null,

  @JsonProperty("registration_lock")
  val registrationLock: String? = null,

  @JsonProperty("master_key")
  @JsonDeserialize(using = HexDeserializer::class)
  val masterKey: ByteArray,

  @JsonProperty("kbs_access_key")
  @JsonDeserialize(using = HexDeserializer::class)
  val kbsAccessKey: ByteArray,

  @JsonProperty("iv_and_cipher")
  @JsonDeserialize(using = HexDeserializer::class)
  val ivAndCipher: ByteArray
) {
  // equals() and hashCode() are still recommended on data class because of ByteArray usage
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as KbsTestVector

    if (!backupId.contentEquals(other.backupId)) return false
    if (!argon2Hash.contentEquals(other.argon2Hash)) return false
    if (pin != other.pin) return false
    if (registrationLock != other.registrationLock) return false
    if (!masterKey.contentEquals(other.masterKey)) return false
    if (!kbsAccessKey.contentEquals(other.kbsAccessKey)) return false
    if (!ivAndCipher.contentEquals(other.ivAndCipher)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = backupId.contentHashCode()
    result = 31 * result + argon2Hash.contentHashCode()
    result = 31 * result + (pin?.hashCode() ?: 0)
    result = 31 * result + (registrationLock?.hashCode() ?: 0)
    result = 31 * result + masterKey.contentHashCode()
    result = 31 * result + kbsAccessKey.contentHashCode()
    result = 31 * result + ivAndCipher.contentHashCode()
    return result
  }
}
