package org.thoughtcrime.securesms.crypto.storage

import android.content.Context
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.model.IdentityStoreRecord
import org.whispersystems.signalservice.test.LibSignalLibraryUtil.assumeLibSignalSupportedOnOS

class SignalBaseIdentityKeyStoreTest {

  companion object {
    private const val ADDRESS = "address1"
  }

  @Before
  fun ensureNativeSupported() {
    assumeLibSignalSupportedOnOS()
  }

  @Test
  fun `getIdentity() hits disk on first retrieve but not the second`() {
    val mockDb = mock(IdentityTable::class.java)
    val subject = SignalBaseIdentityKeyStore(mock(Context::class.java), mockDb)
    val identityKey = IdentityKey(ECPublicKey.fromPublicKeyBytes(ByteArray(32)))
    val record = mockRecord(ADDRESS, identityKey)

    `when`(mockDb.getIdentityStoreRecord(ADDRESS)).thenReturn(record)

    assertEquals(identityKey, subject.getIdentity(SignalProtocolAddress(ADDRESS, 1)))
    verify(mockDb, times(1)).getIdentityStoreRecord(ADDRESS)

    assertEquals(identityKey, subject.getIdentity(SignalProtocolAddress(ADDRESS, 1)))
    verify(mockDb, times(1)).getIdentityStoreRecord(ADDRESS)
  }

  @Test
  fun `invalidate() evicts cache entry`() {
    val mockDb = mock(IdentityTable::class.java)
    val subject = SignalBaseIdentityKeyStore(mock(Context::class.java), mockDb)
    val identityKey = IdentityKey(ECPublicKey.fromPublicKeyBytes(ByteArray(32)))
    val record = mockRecord(ADDRESS, identityKey)

    `when`(mockDb.getIdentityStoreRecord(ADDRESS)).thenReturn(record)

    assertEquals(identityKey, subject.getIdentity(SignalProtocolAddress(ADDRESS, 1)))
    verify(mockDb, times(1)).getIdentityStoreRecord(ADDRESS)

    subject.invalidate(ADDRESS)

    assertEquals(identityKey, subject.getIdentity(SignalProtocolAddress(ADDRESS, 1)))
    verify(mockDb, times(2)).getIdentityStoreRecord(ADDRESS)
  }

  private fun mockRecord(addressName: String, identityKey: IdentityKey): IdentityStoreRecord {
    return IdentityStoreRecord(
      addressName = addressName,
      identityKey = identityKey,
      verifiedStatus = IdentityTable.VerifiedStatus.DEFAULT,
      firstUse = false,
      timestamp = 1,
      nonblockingApproval = true
    )
  }
}
