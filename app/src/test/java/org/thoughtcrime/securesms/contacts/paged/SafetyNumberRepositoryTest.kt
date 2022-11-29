package org.thoughtcrime.securesms.contacts.paged

import android.app.Application
import io.reactivex.rxjava3.core.Single
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.internal.configuration.plugins.Plugins
import org.mockito.internal.junit.JUnitRule
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.storage.SignalIdentityKeyStore
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.RecipientDatabaseTestUtils
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.thoughtcrime.securesms.util.IdentityUtil
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.services.ProfileService
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.push.IdentityCheckResponse
import java.io.IOException
import java.util.Optional
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SafetyNumberRepositoryTest {

  @Rule
  @JvmField
  val mockitoRule: MockitoRule = JUnitRule(Plugins.getMockitoLogger(), Strictness.STRICT_STUBS)

  @Mock
  lateinit var profileService: ProfileService

  @Mock(lenient = true)
  lateinit var aciIdentityStore: SignalIdentityKeyStore

  @Mock
  lateinit var staticIdentityUtil: MockedStatic<IdentityUtil>

  @Mock
  lateinit var staticRecipient: MockedStatic<Recipient>

  private var now: Long = System.currentTimeMillis()

  private lateinit var recipientPool: MutableList<Recipient>
  private lateinit var identityPool: MutableMap<Recipient, IdentityRecord>

  private lateinit var repository: SafetyNumberRepository

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @Before
  fun setUp() {
    now = System.currentTimeMillis()
    repository = SafetyNumberRepository(profileService, aciIdentityStore)

    recipientPool = mutableListOf()
    identityPool = mutableMapOf()

    for (id in 1L until 12) {
      val recipient = RecipientDatabaseTestUtils.createRecipient(resolved = true, recipientId = RecipientId.from(id))
      staticRecipient.`when`<Recipient> { Recipient.resolved(RecipientId.from(id)) }.thenReturn(recipient)
      recipientPool.add(recipient)

      val record = IdentityRecord(
        recipientId = recipient.id,
        identityKey = IdentityKeyUtil.generateIdentityKeyPair().publicKey,
        verifiedStatus = IdentityTable.VerifiedStatus.DEFAULT,
        firstUse = false,
        timestamp = 0,
        nonblockingApproval = false
      )
      whenever(aciIdentityStore.getIdentityRecord(recipient.id)).thenReturn(Optional.of(record))
      identityPool[recipient] = record
    }

    staticRecipient.`when`<Recipient> { Recipient.self() }.thenReturn(recipientPool[0])
  }

  /**
   * Batch request for a current identity key should return an empty list and not perform any identity key updates.
   */
  @Test
  fun batchSafetyNumberCheckSync_batchOf1_noChanges() {
    val other = recipientPool[1]
    val keys = listOf(ContactSearchKey.RecipientSearchKey.KnownRecipient(other.id))

    staticRecipient.`when`<List<Recipient>> { Recipient.resolvedList(argThat { containsAll(keys.map { it.recipientId }) }) }.thenReturn(listOf(other))
    whenever(profileService.performIdentityCheck(mapOf(other.requireServiceId() to identityPool[other]!!.identityKey)))
      .thenReturn(Single.just(ServiceResponse.forResult(IdentityCheckResponse(listOf()), 200, "")))

    repository.batchSafetyNumberCheckSync(keys, now)

    staticIdentityUtil.verifyNoInteractions()
  }

  /**
   * Batch request for an out-of-date identity key should return the new identity key and update the store.
   */
  @Test
  fun batchSafetyNumberCheckSync_batchOf1_oneChange() {
    val other = recipientPool[1]
    val otherAci = ACI.from(other.requireServiceId())
    val otherNewIdentityKey = IdentityKeyUtil.generateIdentityKeyPair().publicKey
    val keys = listOf(ContactSearchKey.RecipientSearchKey.KnownRecipient(other.id))

    staticRecipient.`when`<List<Recipient>> { Recipient.resolvedList(argThat { containsAll(keys.map { it.recipientId }) }) }.thenReturn(listOf(other))
    whenever(profileService.performIdentityCheck(mapOf(other.requireServiceId() to identityPool[other]!!.identityKey)))
      .thenReturn(Single.just(ServiceResponse.forResult(IdentityCheckResponse(listOf(IdentityCheckResponse.AciIdentityPair(otherAci, otherNewIdentityKey))), 200, "")))

    repository.batchSafetyNumberCheckSync(keys, now)

    staticIdentityUtil.verify { IdentityUtil.saveIdentity(otherAci.toString(), otherNewIdentityKey) }
    staticIdentityUtil.verifyNoMoreInteractions()
  }

  /**
   * Batch request for an out-of-date identity key should return the new identity key and update the store.
   */
  @Test
  fun batchSafetyNumberCheckSync_batchOf2_oneChange() {
    val other = recipientPool[1]
    val secondOther = recipientPool[2]
    val otherAci = ACI.from(other.requireServiceId())
    val otherNewIdentityKey = IdentityKeyUtil.generateIdentityKeyPair().publicKey
    val keys = listOf(ContactSearchKey.RecipientSearchKey.KnownRecipient(other.id), ContactSearchKey.RecipientSearchKey.KnownRecipient(secondOther.id))

    staticRecipient.`when`<List<Recipient>> { Recipient.resolvedList(argThat { containsAll(keys.map { it.recipientId }) }) }.thenReturn(listOf(other, secondOther))
    whenever(profileService.performIdentityCheck(mapOf(other.requireServiceId() to identityPool[other]!!.identityKey, secondOther.requireServiceId() to identityPool[secondOther]!!.identityKey)))
      .thenReturn(Single.just(ServiceResponse.forResult(IdentityCheckResponse(listOf(IdentityCheckResponse.AciIdentityPair(otherAci, otherNewIdentityKey))), 200, "")))

    repository.batchSafetyNumberCheckSync(keys, now)

    staticIdentityUtil.verify { IdentityUtil.saveIdentity(otherAci.toString(), otherNewIdentityKey) }
    staticIdentityUtil.verifyNoMoreInteractions()
  }

  /**
   * Batch request for a current identity key should previously checked should abort checking.
   */
  @Test
  fun batchSafetyNumberCheckSync_batchOf1_abortOnPriorRecentCheck() {
    val other = recipientPool[1]
    val keys = listOf(ContactSearchKey.RecipientSearchKey.KnownRecipient(other.id))

    staticRecipient.`when`<List<Recipient>> { Recipient.resolvedList(argThat { containsAll(keys.map { it.recipientId }) }) }.thenReturn(listOf(other))
    whenever(profileService.performIdentityCheck(mapOf(other.requireServiceId() to identityPool[other]!!.identityKey)))
      .thenReturn(Single.just(ServiceResponse.forResult(IdentityCheckResponse(listOf()), 200, "")))

    repository.batchSafetyNumberCheckSync(keys, now)
    verify(profileService, times(1)).performIdentityCheck(any())
    repository.batchSafetyNumberCheckSync(keys, now + TimeUnit.SECONDS.toMillis(10))
    verify(profileService, times(1)).performIdentityCheck(any())
    repository.batchSafetyNumberCheckSync(keys, now + TimeUnit.SECONDS.toMillis(31))
    verify(profileService, times(2)).performIdentityCheck(any())

    staticIdentityUtil.verifyNoInteractions()
  }

  /**
   * Batch request for a current identity keys should return an empty list and not perform any identity key updates.
   */
  @Test
  fun batchSafetyNumberCheckSync_batchOf10WithSmallBatchSize_noChanges() {
    val keys = recipientPool.map { ContactSearchKey.RecipientSearchKey.KnownRecipient(it.id) }
    val others = recipientPool.subList(1, recipientPool.lastIndex)

    staticRecipient.`when`<List<Recipient>> { Recipient.resolvedList(argThat { containsAll(others.map { it.id }) }) }.thenReturn(others)

    for (chunk in others.chunked(2)) {
      whenever(profileService.performIdentityCheck(chunk.associate { it.requireServiceId() to identityPool[it]!!.identityKey }))
        .thenReturn(Single.just(ServiceResponse.forResult(IdentityCheckResponse(listOf()), 200, "")))
    }

    repository.batchSafetyNumberCheckSync(keys, now, 2)

    staticIdentityUtil.verifyNoInteractions()
  }

  @Test
  fun batchSafetyNumberCheckSync_serverError() {
    val other = recipientPool[1]
    val keys = listOf(ContactSearchKey.RecipientSearchKey.KnownRecipient(other.id))

    staticRecipient.`when`<List<Recipient>> { Recipient.resolvedList(argThat { containsAll(keys.map { it.recipientId }) }) }.thenReturn(listOf(other))
    whenever(profileService.performIdentityCheck(mapOf(other.requireServiceId() to identityPool[other]!!.identityKey)))
      .thenReturn(Single.just(ServiceResponse.forApplicationError(NonSuccessfulResponseCodeException(400), 400, "")))

    repository.batchSafetyNumberCheckSync(keys, now)

    staticIdentityUtil.verifyNoInteractions()
  }

  @Test
  fun batchSafetyNumberCheckSync_networkError() {
    val other = recipientPool[1]
    val keys = listOf(ContactSearchKey.RecipientSearchKey.KnownRecipient(other.id))

    staticRecipient.`when`<List<Recipient>> { Recipient.resolvedList(argThat { containsAll(keys.map { it.recipientId }) }) }.thenReturn(listOf(other))
    whenever(profileService.performIdentityCheck(mapOf(other.requireServiceId() to identityPool[other]!!.identityKey)))
      .thenReturn(Single.just(ServiceResponse.forUnknownError(IOException())))

    repository.batchSafetyNumberCheckSync(keys, now)

    staticIdentityUtil.verifyNoInteractions()
  }

  @Test
  fun batchSafetyNumberCheckSync_badJson() {
    val other = recipientPool[1]
    val keys = listOf(ContactSearchKey.RecipientSearchKey.KnownRecipient(other.id))

    staticRecipient.`when`<List<Recipient>> { Recipient.resolvedList(argThat { containsAll(keys.map { it.recipientId }) }) }.thenReturn(listOf(other))
    whenever(profileService.performIdentityCheck(mapOf(other.requireServiceId() to identityPool[other]!!.identityKey)))
      .thenReturn(Single.just(ServiceResponse.forResult(IdentityCheckResponse(), 200, "")))

    repository.batchSafetyNumberCheckSync(keys, now)

    staticIdentityUtil.verifyNoInteractions()
  }
}
