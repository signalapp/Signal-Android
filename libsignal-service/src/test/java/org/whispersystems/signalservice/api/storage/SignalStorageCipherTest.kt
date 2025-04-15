package org.whispersystems.signalservice.api.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.signal.libsignal.protocol.InvalidKeyException
import org.whispersystems.signalservice.internal.util.Util

class SignalStorageCipherTest {

  @Test
  @Throws(InvalidKeyException::class)
  fun symmetry() {
    val key = StorageItemKey(Util.getSecretBytes(32))
    val data = Util.getSecretBytes(1337)

    val ciphertext = SignalStorageCipher.encrypt(key, data)
    val plaintext = SignalStorageCipher.decrypt(key, ciphertext)

    assertArrayEquals(data, plaintext)
  }

  @Test(expected = InvalidKeyException::class)
  @Throws(InvalidKeyException::class)
  fun badKeyOnDecrypt() {
    val key = StorageItemKey(Util.getSecretBytes(32))
    val data = Util.getSecretBytes(1337)

    val badKey = key.serialize().clone()
    badKey[0] = (badKey[0] + 1).toByte()

    val ciphertext = SignalStorageCipher.encrypt(key, data)
    SignalStorageCipher.decrypt(StorageItemKey(badKey), ciphertext)
  }
}
