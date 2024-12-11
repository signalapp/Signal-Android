package org.thoughtcrime.securesms.contacts.paged

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import io.reactivex.rxjava3.core.Single
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.thoughtcrime.securesms.util.IdentityUtil
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
  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private val profileService = mockk<ProfileService>()
  private val aciIdentityStore = mockk<SignalIdentityKeyStore>()

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
    mockkStatic(IdentityUtil::class)

    mockkObject(Recipient)
    mockkStatic(Recipient::class)

    now = System.currentTimeMillis()
    repository = SafetyNumberRepository(profileService, aciIdentityStore)

    recipientPool = mutableListOf()
    identityPool = mutableMapOf()

    for (id in 1L until 12) {
      val recipient = RecipientDatabaseTestUtils.createRecipient(resolved = true, recipientId = RecipientId.from(id))
      every { Recipient.resolved(RecipientId.from(id)) } returns recipient
      recipientPool.add(recipient)

      val record = IdentityRecord(
        recipientId = recipient.id,
        identityKey = IdentityKeyUtil.generateIdentityKeyPair().publicKey,
        verifiedStatus = IdentityTable.VerifiedStatus.DEFAULT,
        firstUse = false,
        timestamp = 0,
        nonblockingApproval = false
      )
      every { aciIdentityStore.getIdentityRecord(recipient.id) } returns Optional.of(record)
      identityPool[recipient] = record
    }

    every { Recipient.self() } returns recipientPool[0]
  }

  /**
   * Batch request for a current identity key should return an empty list and not perform any identity key updates.
   */
  @Test
  fun batchSafetyNumberCheckSync_batchOf1_noChanges() {
    val other = recipientPool[1]
    val keys = listOf(ContactSearchKey.RecipientSearchKey(other.id, false))

    every {
      Recipient.resolvedList(
        match { list ->
          list.containsAll(
            keys.map { key ->
              key.recipientId
            }
          )
        }
      )
    } returns listOf(other)

    every {
      profileService.performIdentityCheck(
        mapOf(other.requireServiceId() to identityPool[other]!!.identityKey)
      )
    } returns Single.just(
      ServiceResponse.forResult(
        IdentityCheckResponse(listOf()),
        200,
        ""
      )
    )

    repository.batchSafetyNumberCheckSync(keys, now)

    verify(exactly = 0) { IdentityUtil.saveIdentity(any(), any()) }
  }

  /**
   * Batch request for an out-of-date identity key should return the new identity key and update the store.
   */
  @Test
  fun batchSafetyNumberCheckSync_batchOf1_oneChange() {
    val other = recipientPool[1]
    val otherAci = other.requireAci()
    val otherNewIdentityKey = IdentityKeyUtil.generateIdentityKeyPair().publicKey
    val keys = listOf(ContactSearchKey.RecipientSearchKey(other.id, false))

    every {
      Recipient.resolvedList(match { list -> list.containsAll(keys.map { key -> key.recipientId }) })
    } returns listOf(other)

    every {
      profileService.performIdentityCheck(
        mapOf(other.requireServiceId() to identityPool[other]!!.identityKey)
      )
    } returns Single.just(
      ServiceResponse.forResult(
        IdentityCheckResponse(
          listOf(IdentityCheckResponse.ServiceIdentityPair(otherAci, otherNewIdentityKey))
        ),
        200,
        ""
      )
    )

    repository.batchSafetyNumberCheckSync(keys, now)

    verify { IdentityUtil.saveIdentity(otherAci.toString(), otherNewIdentityKey) }
  }

  /**
   * Batch request for an out-of-date identity key should return the new identity key and update the store.
   */
  @Test
  fun batchSafetyNumberCheckSync_batchOf2_oneChange() {
    val other = recipientPool[1]
    val secondOther = recipientPool[2]
    val otherAci = other.requireAci()
    val otherNewIdentityKey = IdentityKeyUtil.generateIdentityKeyPair().publicKey
    val keys = listOf(ContactSearchKey.RecipientSearchKey(other.id, false), ContactSearchKey.RecipientSearchKey(secondOther.id, false))

    every {
      Recipient.resolvedList(match { list -> list.containsAll(keys.map { key -> key.recipientId }) })
    } returns listOf(other, secondOther)

    every {
      profileService.performIdentityCheck(
        mapOf(
          other.requireServiceId() to identityPool[other]!!.identityKey,
          secondOther.requireServiceId() to identityPool[secondOther]!!.identityKey
        )
      )
    } returns Single.just(
      ServiceResponse.forResult(
        IdentityCheckResponse(
          listOf(IdentityCheckResponse.ServiceIdentityPair(otherAci, otherNewIdentityKey))
        ),
        200,
        ""
      )
    )

    repository.batchSafetyNumberCheckSync(keys, now)

    verify { IdentityUtil.saveIdentity(otherAci.toString(), otherNewIdentityKey) }
  }

  /**
   * Batch request for a current identity key should previously checked should abort checking.
   */
  @Test
  fun batchSafetyNumberCheckSync_batchOf1_abortOnPriorRecentCheck() {
    val other = recipientPool[1]
    val keys = listOf(ContactSearchKey.RecipientSearchKey(other.id, false))

    every {
      Recipient.resolvedList(match { list -> list.containsAll(keys.map { key -> key.recipientId }) })
    } returns listOf(other)

    every {
      profileService.performIdentityCheck(mapOf(other.requireServiceId() to identityPool[other]!!.identityKey))
    } returns Single.just(ServiceResponse.forResult(IdentityCheckResponse(listOf()), 200, ""))

    repository.batchSafetyNumberCheckSync(keys, now)
    verify(exactly = 1) { profileService.performIdentityCheck(any()) }

    repository.batchSafetyNumberCheckSync(keys, now + TimeUnit.SECONDS.toMillis(10))
    verify(exactly = 1) { profileService.performIdentityCheck(any()) }

    repository.batchSafetyNumberCheckSync(keys, now + TimeUnit.SECONDS.toMillis(31))
    verify(exactly = 2) { profileService.performIdentityCheck(any()) }

    verify(exactly = 0) { IdentityUtil.saveIdentity(any(), any()) }
  }

  /**
   * Batch request for a current identity keys should return an empty list and not perform any identity key updates.
   */
  @Test
  fun batchSafetyNumberCheckSync_batchOf10WithSmallBatchSize_noChanges() {
    val keys = recipientPool.map { receipient -> ContactSearchKey.RecipientSearchKey(receipient.id, false) }
    val others = recipientPool.subList(1, recipientPool.lastIndex)

    every {
      Recipient.resolvedList(match { list -> list.containsAll(others.map { key -> key.id }) })
    } returns others

    every {
      profileService.performIdentityCheck(any())
    } answers {
      Single.just(ServiceResponse.forResult(IdentityCheckResponse(listOf()), 200, ""))
    }

    repository.batchSafetyNumberCheckSync(keys, now, 2)

    verify(exactly = 0) { IdentityUtil.saveIdentity(any(), any()) }
  }

  @Test
  fun batchSafetyNumberCheckSync_serverError() {
    val other = recipientPool[1]
    val keys = listOf(ContactSearchKey.RecipientSearchKey(other.id, false))

    every {
      Recipient.resolvedList(match { list -> list.containsAll(keys.map { key -> key.recipientId }) })
    } returns listOf(other)

    every {
      profileService.performIdentityCheck(
        mapOf(other.requireServiceId() to identityPool[other]!!.identityKey)
      )
    } returns Single.just(
      ServiceResponse.forApplicationError(NonSuccessfulResponseCodeException(400), 400, "")
    )

    repository.batchSafetyNumberCheckSync(keys, now)

    verify(exactly = 0) { IdentityUtil.saveIdentity(any(), any()) }
  }

  @Test
  fun batchSafetyNumberCheckSync_networkError() {
    val other = recipientPool[1]
    val keys = listOf(ContactSearchKey.RecipientSearchKey(other.id, false))

    every {
      Recipient.resolvedList(match { list -> list.containsAll(keys.map { key -> key.recipientId }) })
    } returns listOf(other)

    every {
      profileService.performIdentityCheck(
        mapOf(other.requireServiceId() to identityPool[other]!!.identityKey)
      )
    } returns Single.just(
      ServiceResponse.forUnknownError(IOException())
    )

    repository.batchSafetyNumberCheckSync(keys, now)

    verify(exactly = 0) { IdentityUtil.saveIdentity(any(), any()) }
  }

  @Test
  fun batchSafetyNumberCheckSync_badJson() {
    val other = recipientPool[1]
    val keys = listOf(ContactSearchKey.RecipientSearchKey(other.id, false))

    every {
      Recipient.resolvedList(match { list -> list.containsAll(keys.map { key -> key.recipientId }) })
    } returns listOf(other)

    every {
      profileService.performIdentityCheck(
        mapOf(other.requireServiceId() to identityPool[other]!!.identityKey)
      )
    } returns Single.just(
      ServiceResponse.forResult(
        IdentityCheckResponse(),
        200,
        ""
      )
    )

    repository.batchSafetyNumberCheckSync(keys, now)

    verify(exactly = 0) { IdentityUtil.saveIdentity(any(), any()) }
  }
}
