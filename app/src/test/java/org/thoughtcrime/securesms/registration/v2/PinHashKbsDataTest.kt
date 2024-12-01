package org.thoughtcrime.securesms.registration.v2

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.core.util.StreamUtil
import org.signal.libsignal.svr2.PinHash
import org.thoughtcrime.securesms.registration.v2.testdata.KbsTestVector
import org.thoughtcrime.securesms.testutil.SecureRandomTestUtil
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.api.kbs.PinHashUtil.createNewKbsData
import org.whispersystems.signalservice.api.kbs.PinHashUtil.decryptSvrDataIVCipherText
import org.whispersystems.signalservice.internal.util.JsonUtil

class PinHashKbsDataTest {
  @Test
  fun vectors_createNewKbsData() {
    for (vector in kbsTestVectorList) {
      val pinHash = fromArgon2Hash(vector.argon2Hash)

      val kbsData = createNewKbsData(pinHash, MasterKey.createNew(SecureRandomTestUtil.mockRandom(vector.masterKey)))

      assertArrayEquals(vector.masterKey, kbsData.masterKey.serialize())
      assertArrayEquals(vector.ivAndCipher, kbsData.cipherText)
      assertArrayEquals(vector.kbsAccessKey, kbsData.kbsAccessKey)
      assertEquals(vector.registrationLock, kbsData.masterKey.deriveRegistrationLock())
    }
  }

  @Test
  fun vectors_decryptKbsDataIVCipherText() {
    for (vector in kbsTestVectorList) {
      val hashedPin = fromArgon2Hash(vector.argon2Hash)

      val kbsData = decryptSvrDataIVCipherText(hashedPin, vector.ivAndCipher)

      assertArrayEquals(vector.masterKey, kbsData.masterKey.serialize())
      assertArrayEquals(vector.ivAndCipher, kbsData.cipherText)
      assertArrayEquals(vector.kbsAccessKey, kbsData.kbsAccessKey)
      assertEquals(vector.registrationLock, kbsData.masterKey.deriveRegistrationLock())
    }
  }

  companion object {
    private val kbsTestVectorList: Array<KbsTestVector>
      get() {
        ClassLoader.getSystemClassLoader().getResourceAsStream("data/kbs_vectors.json").use { resourceAsStream ->
          val data: Array<KbsTestVector> = JsonUtil.fromJson(
            StreamUtil.readFullyAsString(resourceAsStream),
            Array<KbsTestVector>::class.java
          )
          assertTrue(data.isNotEmpty())
          return data
        }
      }

    fun fromArgon2Hash(argon2Hash64: ByteArray): PinHash {
      if (argon2Hash64.size != 64) throw AssertionError()

      val k = argon2Hash64.copyOfRange(0, 32)
      val kbsAccessKey = argon2Hash64.copyOfRange(32, 64)

      return mockk<PinHash> {
        every { encryptionKey() } returns k
        every { accessKey() } returns kbsAccessKey
      }
    }
  }
}
