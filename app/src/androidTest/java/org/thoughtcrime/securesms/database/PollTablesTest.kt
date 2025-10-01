package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.deleteAll
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.polls.PollOption
import org.thoughtcrime.securesms.polls.PollRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.SignalActivityRule

@RunWith(AndroidJUnit4::class)
class PollTablesTest {

  @get:Rule
  val harness = SignalActivityRule()

  private lateinit var poll1: PollRecord

  @Before
  fun setUp() {
    poll1 = PollRecord(
      id = 1,
      question = "how do you feel about unit testing?",
      pollOptions = listOf(
        PollOption(1, "yay", listOf(1)),
        PollOption(2, "ok", emptyList()),
        PollOption(3, "nay", emptyList())
      ),
      allowMultipleVotes = false,
      hasEnded = false,
      authorId = 1,
      messageId = 1
    )

    SignalDatabase.polls.writableDatabase.deleteAll(PollTables.PollTable.TABLE_NAME)
    SignalDatabase.polls.writableDatabase.deleteAll(PollTables.PollOptionTable.TABLE_NAME)
    SignalDatabase.polls.writableDatabase.deleteAll(PollTables.PollVoteTable.TABLE_NAME)

    val message = IncomingMessage(type = MessageType.NORMAL, from = harness.others[0], sentTimeMillis = 100, serverTimeMillis = 100, receivedTimeMillis = 100)
    SignalDatabase.messages.insertMessageInbox(message, SignalDatabase.threads.getOrCreateThreadIdFor(harness.others[0], isGroup = false))
  }

  @Test
  fun givenAPollWithVoting_whenIGetPoll_thenIExpectThatPoll() {
    SignalDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    SignalDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(1), voterId = 1, voteCount = 1, messageId = MessageId(1))

    assertEquals(poll1, SignalDatabase.polls.getPoll(1))
  }

  @Test
  fun givenAPoll_whenIGetItsOptionIds_thenIExpectAllOptionsIds() {
    SignalDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    assertEquals(poll1.pollOptions.map { it.id }, SignalDatabase.polls.getPollOptionIds(1))
  }

  @Test
  fun givenAPollAndVoter_whenIGetItsVoteCount_thenIExpectTheCorrectVoterCount() {
    SignalDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    SignalDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(1), voterId = 1, voteCount = 1, messageId = MessageId(1))
    SignalDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(2), voterId = 2, voteCount = 2, messageId = MessageId(1))
    SignalDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(3), voterId = 3, voteCount = 3, messageId = MessageId(1))

    assertEquals(1, SignalDatabase.polls.getCurrentPollVoteCount(1, 1))
    assertEquals(2, SignalDatabase.polls.getCurrentPollVoteCount(1, 2))
    assertEquals(3, SignalDatabase.polls.getCurrentPollVoteCount(1, 3))
  }

  @Test
  fun givenMultipleRoundsOfVoting_whenIGetItsCount_thenIExpectTheMostRecentResults() {
    SignalDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    SignalDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(2), voterId = 1, voteCount = 1, messageId = MessageId(1))
    SignalDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(3), voterId = 1, voteCount = 2, messageId = MessageId(1))
    SignalDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(1), voterId = 1, voteCount = 3, messageId = MessageId(1))

    assertEquals(poll1, SignalDatabase.polls.getPoll(1))
  }

  @Test
  fun givenAPoll_whenITerminateIt_thenIExpectItToEnd() {
    SignalDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    SignalDatabase.polls.endPoll(1, System.currentTimeMillis())

    assertEquals(true, SignalDatabase.polls.getPoll(1)!!.hasEnded)
  }

  @Test
  fun givenAPoll_whenIIVote_thenIExpectThatVote() {
    SignalDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    val poll = SignalDatabase.polls.getPoll(1)!!
    val pollOption = poll.pollOptions.first()

    val voteCount = SignalDatabase.polls.insertVote(poll, pollOption)

    assertEquals(1, voteCount)
    assertEquals(listOf(0), SignalDatabase.polls.getVotes(poll.id, false))
  }

  @Test
  fun givenAPoll_whenIRemoveVote_thenVoteIsCleared() {
    SignalDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    val poll = SignalDatabase.polls.getPoll(1)!!
    val pollOption = poll.pollOptions.first()

    val voteCount = SignalDatabase.polls.removeVote(poll, pollOption)
    SignalDatabase.polls.markPendingAsRemoved(poll.id, Recipient.self().id.toLong(), voteCount, 1)

    assertEquals(1, voteCount)
    val status = SignalDatabase.polls.getPollVoteStateForGivenVote(poll.id, voteCount)
    assertEquals(PollTables.VoteState.REMOVED, status)
  }

  @Test
  fun givenAVote_whenISetPollOptionId_thenOptionIdIsUpdated() {
    SignalDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    val poll = SignalDatabase.polls.getPoll(1)!!
    val option = poll.pollOptions.first()

    SignalDatabase.polls.insertVotes(poll.id, listOf(option.id), Recipient.self().id.toLong(), 5, MessageId(1))
    SignalDatabase.polls.setPollVoteStateForGivenVote(poll.id, Recipient.self().id.toLong(), 5, 1, true)
    val status = SignalDatabase.polls.getPollVoteStateForGivenVote(poll.id, 5)

    assertEquals(PollTables.VoteState.ADDED, status)
  }
}
