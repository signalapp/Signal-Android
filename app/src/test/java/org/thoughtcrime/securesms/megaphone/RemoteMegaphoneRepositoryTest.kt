package org.thoughtcrime.securesms.megaphone

import android.app.Application
import android.net.Uri
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.json.JSONObject
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.SignalStoreRule
import org.thoughtcrime.securesms.database.RemoteMegaphoneTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.RemoteMegaphoneRecord
import org.thoughtcrime.securesms.util.toMillis
import java.time.LocalDateTime
import java.util.UUID

/**
 * [RemoteMegaphoneRepository] is an Kotlin Object, which means it's like a singleton and thus maintains
 * state and dependencies across tests. You must be aware of this when mocking/testing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RemoteMegaphoneRepositoryTest {

  @get:Rule
  val signalStore: SignalStoreRule = SignalStoreRule()

  @Before
  fun setUp() {
  }

  @After
  fun tearDown() {
    clearMocks(remoteMegaphoneTable)
  }

  /** Should return null if no megaphones in database. */
  @Test
  fun getRemoteMegaphoneToShow_noMegaphones() {
    // GIVEN
    every { remoteMegaphoneTable.getPotentialMegaphonesAndClearOld(any()) } returns emptyList()

    // WHEN
    val record = RemoteMegaphoneRepository.getRemoteMegaphoneToShow(0)

    // THEN
    assertThat(record, nullValue())
  }

  @Test
  fun getRemoteMegaphoneToShow_oneMegaphone() {
    // GIVEN
    every { remoteMegaphoneTable.getPotentialMegaphonesAndClearOld(any()) } returns listOf(megaphone(1))

    // WHEN
    val record = RemoteMegaphoneRepository.getRemoteMegaphoneToShow(0)

    // THEN
    assertThat(record, notNullValue())
  }

  @Test
  fun getRemoteMegaphoneToShow_snoozedMegaphone() {
    // GIVEN
    val snoozed = megaphone(
      id = 1,
      seenCount = 1,
      snoozedAt = now.minusDays(1).toMillis(),
      secondaryActionId = RemoteMegaphoneRecord.ActionId.SNOOZE,
      secondaryActionData = JSONObject("{\"snoozeDurationDays\":[3]}")
    )

    every { remoteMegaphoneTable.getPotentialMegaphonesAndClearOld(now.toMillis()) } returns listOf(snoozed)

    // WHEN
    val record = RemoteMegaphoneRepository.getRemoteMegaphoneToShow(now.toMillis())

    // THEN
    assertThat(record, nullValue())
  }

  @Test
  fun getRemoteMegaphoneToShow_oldSnoozedMegaphone() {
    // GIVEN
    val snoozed = megaphone(
      id = 1,
      seenCount = 1,
      snoozedAt = now.minusDays(5).toMillis(),
      secondaryActionId = RemoteMegaphoneRecord.ActionId.SNOOZE,
      secondaryActionData = JSONObject("{\"snoozeDurationDays\":[3]}")
    )

    every { remoteMegaphoneTable.getPotentialMegaphonesAndClearOld(now.toMillis()) } returns listOf(snoozed)

    // WHEN
    val record = RemoteMegaphoneRepository.getRemoteMegaphoneToShow(now.toMillis())

    // THEN
    assertThat(record, notNullValue())
  }

  @Test
  fun getRemoteMegaphoneToShow_multipleOldSnoozedMegaphone() {
    // GIVEN
    val snoozed = megaphone(
      id = 1,
      seenCount = 5,
      snoozedAt = now.minusDays(8).toMillis(),
      secondaryActionId = RemoteMegaphoneRecord.ActionId.SNOOZE,
      secondaryActionData = JSONObject("{\"snoozeDurationDays\":[3, 5, 7]}")
    )

    every { remoteMegaphoneTable.getPotentialMegaphonesAndClearOld(now.toMillis()) } returns listOf(snoozed)

    // WHEN
    val record = RemoteMegaphoneRepository.getRemoteMegaphoneToShow(now.toMillis())

    // THEN
    assertThat(record, notNullValue())
  }

  companion object {
    private val now = LocalDateTime.of(2021, 11, 5, 12, 0)

    private val remoteMegaphoneTable: RemoteMegaphoneTable = mockk()

    @BeforeClass
    @JvmStatic
    fun classSetup() {
      mockkObject(SignalDatabase.Companion)
      every { SignalDatabase.remoteMegaphones } returns remoteMegaphoneTable
    }

    @AfterClass
    @JvmStatic
    fun classCleanup() {
      unmockkObject(SignalDatabase.Companion)
    }

    fun megaphone(
      id: Long,
      priority: Long = 100,
      uuid: String = UUID.randomUUID().toString(),
      countries: String? = null,
      minimumVersion: Int = 100,
      doNotShowBefore: Long = 0,
      doNotShowAfter: Long = Long.MAX_VALUE,
      showForNumberOfDays: Long = Long.MAX_VALUE,
      conditionalId: String? = null,
      primaryActionId: RemoteMegaphoneRecord.ActionId? = null,
      secondaryActionId: RemoteMegaphoneRecord.ActionId? = null,
      imageUrl: String? = null,
      imageUri: Uri? = null,
      title: String = "",
      body: String = "",
      primaryActionText: String? = null,
      secondaryActionText: String? = null,
      shownAt: Long = 0,
      finishedAt: Long = 0,
      primaryActionData: JSONObject? = null,
      secondaryActionData: JSONObject? = null,
      snoozedAt: Long = 0,
      seenCount: Int = 0
    ): RemoteMegaphoneRecord {
      return RemoteMegaphoneRecord(
        id,
        priority,
        uuid,
        countries,
        minimumVersion,
        doNotShowBefore,
        doNotShowAfter,
        showForNumberOfDays,
        conditionalId,
        primaryActionId,
        secondaryActionId,
        imageUrl,
        imageUri,
        title,
        body,
        primaryActionText,
        secondaryActionText,
        shownAt,
        finishedAt,
        primaryActionData,
        secondaryActionData,
        snoozedAt,
        seenCount,
      )
    }
  }
}
