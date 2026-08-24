package org.thoughtcrime.securesms.profiles.spoofing

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.DefaultValueLiveData
import org.thoughtcrime.securesms.util.livedata.LiveDataRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ReviewCardViewModelTest {

  @get:Rule
  val liveDataRule = LiveDataRule()

  private val repository = FakeReviewCardRepository()

  /**
   * Regression test for AND-9680: an impersonator sending a one-to-one message request has no groups in
   * common with us by construction, and must not be filtered out of the review.
   */
  @Test
  fun `given a one-to-one thread, when a similarly named recipient has no groups in common, then it still gets a card`() {
    val impersonator = repository.addReviewRecipient(groupsInCommon = 0)
    val contact = repository.addReviewRecipient(groupsInCommon = 4, systemContact = true)

    val cards = loadCards(isGroupThread = false)

    assertThat(cards.map { it.reviewRecipient }).containsExactly(impersonator.recipient, contact.recipient)
    assertThat(cards[0].inCommonGroupsCount).isEqualTo(0)
    assertThat(cards[0].cardType).isEqualTo(ReviewCard.CardType.REQUEST)
    assertThat(cards[1].inCommonGroupsCount).isEqualTo(4)
    assertThat(cards[1].cardType).isEqualTo(ReviewCard.CardType.YOUR_CONTACT)
  }

  /**
   * The card count excludes the group being reviewed, so a member with no groups in common would render as
   * "-1 other groups in common". Those members stay filtered out.
   */
  @Test
  fun `given a group thread, when a similarly named member has no groups in common, then it does not get a card`() {
    repository.addReviewRecipient(groupsInCommon = 0)
    val member = repository.addReviewRecipient(groupsInCommon = 2)

    val cards = loadCards(isGroupThread = true)

    assertThat(cards.map { it.reviewRecipient }).containsExactly(member.recipient)
    assertThat(cards[0].inCommonGroupsCount).isEqualTo(1)
    assertThat(cards[0].cardType).isEqualTo(ReviewCard.CardType.MEMBER)
  }

  private fun loadCards(isGroupThread: Boolean): List<ReviewCard> {
    val viewModel = ReviewCardViewModel(repository, isGroupThread, DefaultValueLiveData(false))

    return viewModel.reviewCards.observeNextValue { repository.emitLoadedRecipients() }
  }

  private fun <T> LiveData<T>.observeNextValue(trigger: () -> Unit): T {
    val value = AtomicReference<T>()
    val latch = CountDownLatch(1)
    val observer = Observer<T> {
      value.set(it)
      latch.countDown()
    }

    observeForever(observer)
    trigger()

    assertThat(latch.await(10, TimeUnit.SECONDS), "Timed out waiting for a value").isTrue()
    removeObserver(observer)

    return value.get()
  }

  private class FakeReviewCardRepository : ReviewCardRepository(mockk<Context>(relaxed = true), RecipientId.from(1)) {

    private val groupsInCommonCounts = LinkedHashMap<ReviewRecipient, Int>()
    private var loadedListener: OnRecipientsLoadedListener? = null

    fun addReviewRecipient(groupsInCommon: Int, systemContact: Boolean = false): ReviewRecipient {
      val recipient = ReviewRecipient(
        mockk<Recipient> {
          every { isSystemContact } returns systemContact
        }
      )

      groupsInCommonCounts[recipient] = groupsInCommon

      return recipient
    }

    fun emitLoadedRecipients() {
      loadedListener!!.onRecipientsLoaded(groupsInCommonCounts.keys.toList())
    }

    public override fun loadRecipients(onRecipientsLoadedListener: OnRecipientsLoadedListener) {
      loadedListener = onRecipientsLoadedListener
    }

    public override fun loadGroupsInCommonCount(reviewRecipient: ReviewRecipient): Int {
      return groupsInCommonCounts.getValue(reviewRecipient)
    }
  }
}
