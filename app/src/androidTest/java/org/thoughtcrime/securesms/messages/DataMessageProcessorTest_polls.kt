package org.thoughtcrime.securesms.messages

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.GroupTestingUtils
import org.thoughtcrime.securesms.testing.GroupTestingUtils.asMember
import org.thoughtcrime.securesms.testing.MessageContentFuzzer
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.internal.push.DataMessage

@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class DataMessageProcessorTest_polls {

  @get:Rule
  val harness = SignalActivityRule(createGroup = true)

  private lateinit var alice: Recipient
  private lateinit var bob: Recipient
  private lateinit var charlie: Recipient
  private lateinit var groupId: GroupId.V2
  private lateinit var groupRecipientId: RecipientId

  @Before
  fun setUp() {
    alice = Recipient.resolved(harness.others[0])
    bob = Recipient.resolved(harness.others[1])
    charlie = Recipient.resolved(harness.others[2])

    val groupInfo = GroupTestingUtils.insertGroup(revision = 0, harness.self.asMember(), alice.asMember(), bob.asMember())
    groupId = groupInfo.groupId
    groupRecipientId = groupInfo.recipientId
  }

  @Test
  fun handlePollCreate_whenIHaveAValidPollProto_createPoll() {
    val insertResult = handlePollCreate(
      pollCreate = DataMessage.PollCreate(question = "question?", options = listOf("a", "b", "c"), allowMultiple = false),
      senderRecipient = alice,
      threadRecipient = Recipient.resolved(groupRecipientId),
      groupId = groupId
    )

    assert(insertResult != null)
    val poll = SignalDatabase.polls.getPoll(insertResult!!.messageId)
    assert(poll != null)
    assertThat(poll!!.question).isEqualTo("question?")
    assertThat(poll.pollOptions.size).isEqualTo(3)
    assertThat(poll.allowMultipleVotes).isEqualTo(false)
    assertThat(poll.hasEnded).isEqualTo(false)
  }

  @Test
  fun handlePollCreate_whenSenderIsNotInGroup_dropMessage() {
    val insertResult = handlePollCreate(
      pollCreate = DataMessage.PollCreate(question = "question?", options = listOf("a", "b", "c"), allowMultiple = false),
      senderRecipient = charlie,
      threadRecipient = Recipient.resolved(groupRecipientId),
      groupId = groupId
    )

    assert(insertResult == null)
  }

  @Test
  fun handlePollCreate_whenTargetRecipientIsNotAGroup_dropMessage() {
    val insertResult = handlePollCreate(
      pollCreate = DataMessage.PollCreate(question = "question?", options = listOf("a", "b", "c"), allowMultiple = false),
      senderRecipient = alice,
      threadRecipient = bob,
      groupId = null
    )

    assert(insertResult == null)
  }

  @Test
  fun handlePollTerminate_whenIHaveValidProto_endPoll() {
    val pollMessageId = insertPoll()

    val insertResult = DataMessageProcessor.handlePollTerminate(
      context = ApplicationProvider.getApplicationContext(),
      envelope = MessageContentFuzzer.envelope(200),
      message = DataMessage(pollTerminate = DataMessage.PollTerminate(targetSentTimestamp = 100)),
      senderRecipient = alice,
      metadata = EnvelopeMetadata(alice.requireServiceId(), null, 1, false, null, harness.self.requireServiceId()),
      threadRecipient = bob,
      groupId = groupId,
      receivedTime = 200
    )

    assert(insertResult?.messageId != null)
    val poll = SignalDatabase.polls.getPoll(pollMessageId)
    assert(poll != null)
    assert(poll!!.hasEnded)
  }

  @Test
  fun handlePollTerminate_whenIHaveDifferentTimestamp_dropMessage() {
    insertPoll()

    val insertResult = DataMessageProcessor.handlePollTerminate(
      context = ApplicationProvider.getApplicationContext(),
      envelope = MessageContentFuzzer.envelope(200),
      message = DataMessage(pollTerminate = DataMessage.PollTerminate(200)),
      senderRecipient = alice,
      metadata = EnvelopeMetadata(alice.requireServiceId(), null, 1, false, null, harness.self.requireServiceId()),
      threadRecipient = bob,
      groupId = groupId,
      receivedTime = 200
    )

    assert(insertResult == null)
  }

  @Test
  fun handlePollTerminate_whenMessageIsNotFromCreatorOfPoll_dropMessage() {
    insertPoll()

    val insertResult = DataMessageProcessor.handlePollTerminate(
      context = ApplicationProvider.getApplicationContext(),
      envelope = MessageContentFuzzer.envelope(200),
      message = DataMessage(pollTerminate = DataMessage.PollTerminate(100)),
      senderRecipient = bob,
      metadata = EnvelopeMetadata(alice.requireServiceId(), null, 1, false, null, harness.self.requireServiceId()),
      threadRecipient = bob,
      groupId = groupId,
      receivedTime = 200
    )

    assert(insertResult == null)
  }

  @Test
  fun handlePollTerminate_whenPollDoesNotExist_dropMessage() {
    val insertResult = DataMessageProcessor.handlePollTerminate(
      context = ApplicationProvider.getApplicationContext(),
      envelope = MessageContentFuzzer.envelope(200),
      message = DataMessage(pollTerminate = DataMessage.PollTerminate(100)),
      senderRecipient = alice,
      metadata = EnvelopeMetadata(alice.requireServiceId(), null, 1, false, null, harness.self.requireServiceId()),
      threadRecipient = bob,
      groupId = groupId,
      receivedTime = 200
    )

    assert(insertResult == null)
  }

  @Test
  fun handlePollVote_whenValidPollVote_processVote() {
    insertPoll()

    val messageId = handlePollVote(
      DataMessage.PollVote(
        targetAuthorAciBinary = alice.asMember().aciBytes,
        targetSentTimestamp = 100,
        optionIndexes = listOf(0),
        voteCount = 1
      ),
      bob
    )

    assert(messageId != null)
    assertThat(messageId!!.id).isEqualTo(1)
    val poll = SignalDatabase.polls.getPoll(messageId.id)
    assert(poll != null)
    assertThat(poll!!.pollOptions[0].voterIds).isEqualTo(listOf(bob.id.toLong()))
  }

  @Test
  fun handlePollVote_whenMultipleVoteAllowed_processAllVote() {
    insertPoll()

    val messageId = handlePollVote(
      DataMessage.PollVote(
        targetAuthorAciBinary = alice.asMember().aciBytes,
        targetSentTimestamp = 100,
        optionIndexes = listOf(0, 1, 2),
        voteCount = 1
      ),
      bob
    )

    assert(messageId != null)
    val poll = SignalDatabase.polls.getPoll(messageId!!.id)
    assert(poll != null)
    assertThat(poll!!.pollOptions[0].voterIds).isEqualTo(listOf(bob.id.toLong()))
    assertThat(poll.pollOptions[1].voterIds).isEqualTo(listOf(bob.id.toLong()))
    assertThat(poll.pollOptions[2].voterIds).isEqualTo(listOf(bob.id.toLong()))
  }

  @Test
  fun handlePollVote_whenMultipleVoteSentToSingleVotePolls_dropMessage() {
    insertPoll(false)

    val messageId = handlePollVote(
      DataMessage.PollVote(
        targetAuthorAciBinary = alice.asMember().aciBytes,
        targetSentTimestamp = 100,
        optionIndexes = listOf(0, 1, 2),
        voteCount = 1
      ),
      bob
    )

    assert(messageId == null)
  }

  @Test
  fun handlePollVote_whenVoteCountIsNotHigher_dropMessage() {
    insertPoll()

    val messageId = handlePollVote(
      DataMessage.PollVote(
        targetAuthorAciBinary = alice.asMember().aciBytes,
        targetSentTimestamp = 100,
        optionIndexes = listOf(0, 1, 2),
        voteCount = -1
      ),
      bob
    )

    assert(messageId == null)
  }

  @Test
  fun handlePollVote_whenVoteOptionDoesNotExist_dropMessage() {
    insertPoll()

    val messageId = handlePollVote(
      DataMessage.PollVote(
        targetAuthorAciBinary = alice.asMember().aciBytes,
        targetSentTimestamp = 100,
        optionIndexes = listOf(5),
        voteCount = 1
      ),
      bob
    )

    assert(messageId == null)
  }

  @Test
  fun handlePollVote_whenVoterNotInGroup_dropMessage() {
    insertPoll()

    val messageId = handlePollVote(
      DataMessage.PollVote(
        targetAuthorAciBinary = alice.asMember().aciBytes,
        targetSentTimestamp = 100,
        optionIndexes = listOf(0, 1, 2),
        voteCount = 1

      ),
      charlie
    )

    assert(messageId == null)
  }

  @Test
  fun handlePollVote_whenPollDoesNotExist_dropMessage() {
    val messageId = handlePollVote(
      DataMessage.PollVote(
        targetAuthorAciBinary = alice.asMember().aciBytes,
        targetSentTimestamp = 100,
        optionIndexes = listOf(0, 1, 2),
        voteCount = 1
      ),
      bob
    )

    assert(messageId == null)
  }

  private fun handlePollCreate(pollCreate: DataMessage.PollCreate, senderRecipient: Recipient, threadRecipient: Recipient, groupId: GroupId.V2?): MessageTable.InsertResult? {
    return DataMessageProcessor.handlePollCreate(
      envelope = MessageContentFuzzer.envelope(100),
      message = DataMessage(pollCreate = pollCreate),
      senderRecipient = senderRecipient,
      threadRecipient = threadRecipient,
      groupId = groupId,
      receivedTime = 0,
      context = ApplicationProvider.getApplicationContext(),
      metadata = EnvelopeMetadata(alice.requireServiceId(), null, 1, false, null, harness.self.requireServiceId())
    )
  }

  private fun handlePollVote(pollVote: DataMessage.PollVote, senderRecipient: Recipient): MessageId? {
    return DataMessageProcessor.handlePollVote(
      context = ApplicationProvider.getApplicationContext(),
      envelope = MessageContentFuzzer.envelope(100),
      message = DataMessage(pollVote = pollVote),
      senderRecipient = senderRecipient,
      earlyMessageCacheEntry = null
    )
  }

  private fun insertPoll(allowMultiple: Boolean = true): Long {
    val envelope = MessageContentFuzzer.envelope(100)
    val pollMessage = IncomingMessage(type = MessageType.NORMAL, from = alice.id, sentTimeMillis = envelope.timestamp!!, serverTimeMillis = envelope.serverTimestamp!!, receivedTimeMillis = 0, groupId = groupId)
    val messageId = SignalDatabase.messages.insertMessageInbox(pollMessage).get()
    SignalDatabase.polls.insertPoll("question?", allowMultiple, listOf("a", "b", "c"), alice.id.toLong(), messageId.messageId)
    return messageId.messageId
  }
}
