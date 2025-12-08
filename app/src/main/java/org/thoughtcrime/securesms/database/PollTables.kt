package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import androidx.core.content.contentValuesOf
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.exists
import org.signal.core.util.forEach
import org.signal.core.util.groupBy
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.readToMap
import org.signal.core.util.readToSingleBoolean
import org.signal.core.util.readToSingleInt
import org.signal.core.util.readToSingleLong
import org.signal.core.util.readToSingleLongOrNull
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.polls.Poll
import org.thoughtcrime.securesms.polls.PollOption
import org.thoughtcrime.securesms.polls.PollRecord
import org.thoughtcrime.securesms.polls.PollVote
import org.thoughtcrime.securesms.polls.VoteState
import org.thoughtcrime.securesms.polls.Voter
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Database table for polls
 *
 * Voting:
 * [VOTE_COUNT] tracks how often someone has voted and is specific per poll and user.
 * The first time Alice votes in Poll 1, the count is 1, the next time is 2. Removing a vote will also bump it (to 3).
 * If Alice votes in Poll 2, her vote count will start at 1. If Bob votes, his own vote count starts at 1 (so no interactions between other polls or people)
 * We track vote count because the server can reorder messages and we don't want to process an older vote count than what we have.
 *
 * For example, in three rounds of voting (in the same poll):
 * 1. Alice votes for option a -> we send (a) with vote count of 1
 * 2. Alice votes for option b -> we send (a,b) with vote count of 2
 * 3. Alice removes option b -> we send (a) with vote count of 3
 *
 * If we get and process #3 before receiving #2, we will drop #2. This can be done because the voting message always contains the full state of all your votes.
 *
 * [VOTE_STATE] tracks the lifecycle of a single vote. Example below with added (remove is very similar).
 * UI:         Alice votes for Option A     -> Pending Spinner on Option A    -> Option A is checked/Option B is removed if single-vote poll.
 * BTS:                   PollVoteJob runs (PENDING_ADD)         PollVoteJob finishes (ADDED)
 */
class PollTables(context: Context?, databaseHelper: SignalDatabase?) : DatabaseTable(context, databaseHelper), RecipientIdDatabaseReference {

  companion object {
    private val TAG = Log.tag(PollTables::class.java)

    @JvmField
    val CREATE_TABLE: Array<String> = arrayOf(PollTable.CREATE_TABLE, PollOptionTable.CREATE_TABLE, PollVoteTable.CREATE_TABLE)

    @JvmField
    val CREATE_INDEXES: Array<String> = PollTable.CREATE_INDEXES + PollOptionTable.CREATE_INDEXES + PollVoteTable.CREATE_INDEXES
  }

  /**
   * Table containing general poll information (name, deleted status, etc.)
   */
  object PollTable {
    const val TABLE_NAME = "poll"

    const val ID = "_id"
    const val AUTHOR_ID = "author_id"
    const val MESSAGE_ID = "message_id"
    const val QUESTION = "question"
    const val ALLOW_MULTIPLE_VOTES = "allow_multiple_votes"
    const val END_MESSAGE_ID = "end_message_id"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $AUTHOR_ID INTEGER NOT NULL REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE,
        $MESSAGE_ID INTEGER NOT NULL REFERENCES ${MessageTable.TABLE_NAME} (${MessageTable.ID}) ON DELETE CASCADE,
        $QUESTION TEXT,
        $ALLOW_MULTIPLE_VOTES INTEGER DEFAULT 0,
        $END_MESSAGE_ID INTEGER DEFAULT 0
      )
    """

    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX poll_author_id_index ON $TABLE_NAME ($AUTHOR_ID)",
      "CREATE INDEX poll_message_id_index ON $TABLE_NAME ($MESSAGE_ID)"
    )
  }

  /**
   * Table containing the options within a given poll
   */
  object PollOptionTable {
    const val TABLE_NAME = "poll_option"

    const val ID = "_id"
    const val POLL_ID = "poll_id"
    const val OPTION_TEXT = "option_text"
    const val OPTION_ORDER = "option_order"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $POLL_ID INTEGER NOT NULL REFERENCES ${PollTable.TABLE_NAME} (${PollTable.ID}) ON DELETE CASCADE,
        $OPTION_TEXT TEXT,
        $OPTION_ORDER INTEGER
      )
    """

    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX poll_option_poll_id_index ON $TABLE_NAME ($POLL_ID)"
    )
  }

  /**
   * Table containing the votes of a given poll
   */
  object PollVoteTable {
    const val TABLE_NAME = "poll_vote"

    const val ID = "_id"
    const val POLL_ID = "poll_id"
    const val POLL_OPTION_ID = "poll_option_id"
    const val VOTER_ID = "voter_id"
    const val VOTE_COUNT = "vote_count"
    const val DATE_RECEIVED = "date_received"
    const val VOTE_STATE = "vote_state"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $POLL_ID INTEGER NOT NULL REFERENCES ${PollTable.TABLE_NAME} (${PollTable.ID}) ON DELETE CASCADE,
        $POLL_OPTION_ID INTEGER DEFAULT NULL REFERENCES ${PollOptionTable.TABLE_NAME} (${PollOptionTable.ID}) ON DELETE CASCADE,
        $VOTER_ID INTEGER NOT NULL REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE,
        $VOTE_COUNT INTEGER,
        $DATE_RECEIVED INTEGER DEFAULT 0,
        $VOTE_STATE INTEGER DEFAULT 0
      )
    """

    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX poll_vote_poll_id_index ON $TABLE_NAME ($POLL_ID)",
      "CREATE INDEX poll_vote_poll_option_id_index ON $TABLE_NAME ($POLL_OPTION_ID)",
      "CREATE INDEX poll_vote_voter_id_index ON $TABLE_NAME ($VOTER_ID)"
    )
  }

  override fun remapRecipient(fromId: RecipientId, toId: RecipientId) {
    val countFromPoll = writableDatabase
      .update(PollTable.TABLE_NAME)
      .values(PollTable.AUTHOR_ID to toId.serialize())
      .where("${PollTable.AUTHOR_ID} = ?", fromId)
      .run()

    val countFromVotes = writableDatabase
      .update(PollVoteTable.TABLE_NAME)
      .values(PollVoteTable.VOTER_ID to toId.serialize())
      .where("${PollVoteTable.VOTER_ID} = ?", fromId)
      .run()

    Log.d(TAG, "Remapped $fromId to $toId. count from polls: $countFromPoll from poll votes: $countFromVotes")
  }

  /**
   * Inserts a newly created poll with its options. Returns the newly created row id
   */
  fun insertPoll(question: String, allowMultipleVotes: Boolean, options: List<String>, authorId: Long, messageId: Long): Long {
    return writableDatabase.withinTransaction { db ->
      val pollId = db.insertInto(PollTable.TABLE_NAME)
        .values(
          contentValuesOf(
            PollTable.QUESTION to question,
            PollTable.ALLOW_MULTIPLE_VOTES to allowMultipleVotes,
            PollTable.AUTHOR_ID to authorId,
            PollTable.MESSAGE_ID to messageId
          )
        )
        .run()

      SqlUtil.buildBulkInsert(
        PollOptionTable.TABLE_NAME,
        arrayOf(PollOptionTable.POLL_ID, PollOptionTable.OPTION_TEXT, PollOptionTable.OPTION_ORDER),
        options.toPollContentValues(pollId)
      ).forEach {
        db.execSQL(it.where, it.whereArgs)
      }
      pollId
    }
  }

  /**
   * Inserts a poll option and voters for that option. Called when restoring polls from backups.
   */
  fun addPollVotes(pollId: Long, optionId: Long, voters: List<Voter>) {
    writableDatabase.withinTransaction { db ->
      SqlUtil.buildBulkInsert(
        PollVoteTable.TABLE_NAME,
        arrayOf(PollVoteTable.POLL_ID, PollVoteTable.POLL_OPTION_ID, PollVoteTable.VOTER_ID, PollVoteTable.VOTE_COUNT, PollVoteTable.DATE_RECEIVED, PollVoteTable.VOTE_STATE),
        voters.map { voter ->
          contentValuesOf(
            PollVoteTable.POLL_ID to pollId,
            PollVoteTable.POLL_OPTION_ID to optionId,
            PollVoteTable.VOTER_ID to voter.id,
            PollVoteTable.VOTE_COUNT to voter.voteCount,
            PollVoteTable.VOTE_STATE to VoteState.ADDED.value
          )
        }
      ).forEach {
        db.execSQL(it.where, it.whereArgs)
      }
    }
  }

  /**
   * Inserts a vote in a poll and increases the vote count by 1.
   * Status is marked as [VoteState.PENDING_ADD] here and then once it successfully sends, it will get updated to [VoteState.ADDED] in [markPendingAsAdded]
   * If the latest state is already pending for this vote, it will just update the pending state.
   * However, if there is a resolved state eg [VoteState.ADDED], we add a new entry so that if the pending entry fails, we can revert back to a known state.
   */
  fun insertVote(poll: PollRecord, pollOption: PollOption): Int {
    val self = Recipient.self().id.toLong()
    var voteCount = 0

    writableDatabase.withinTransaction { db ->
      voteCount = getCurrentPollVoteCount(poll.id, self) + 1

      if (isPending(poll.id, pollOption.id, self)) {
        db.update(PollVoteTable.TABLE_NAME)
          .values(
            PollVoteTable.VOTE_STATE to VoteState.PENDING_ADD.value,
            PollVoteTable.VOTE_COUNT to voteCount
          )
          .where(
            """  
            ${PollVoteTable.POLL_ID} = ? AND 
            ${PollVoteTable.POLL_OPTION_ID} = ? AND 
            ${PollVoteTable.VOTER_ID} = ? AND 
            (${PollVoteTable.VOTE_STATE} = ${VoteState.PENDING_ADD.value} OR ${PollVoteTable.VOTE_STATE} = ${VoteState.PENDING_REMOVE.value})
            """.trimIndent(),
            poll.id,
            pollOption.id,
            self
          )
          .run()
      } else {
        db.insertInto(PollVoteTable.TABLE_NAME)
          .values(
            PollVoteTable.POLL_ID to poll.id,
            PollVoteTable.POLL_OPTION_ID to pollOption.id,
            PollVoteTable.VOTER_ID to self,
            PollVoteTable.VOTE_COUNT to voteCount,
            PollVoteTable.VOTE_STATE to VoteState.PENDING_ADD.value
          )
          .run(SQLiteDatabase.CONFLICT_REPLACE)
      }
    }

    AppDependencies.databaseObserver.notifyMessageUpdateObservers(MessageId(poll.messageId))
    return voteCount
  }

  /**
   * Once a vote is sent to at least one person, we can update the [VoteState.PENDING_ADD] state to [VoteState.ADDED].
   * If the poll only allows one vote, it also clears out any old votes.
   */
  fun markPendingAsAdded(pollId: Long, voterId: Long, voteCount: Int, messageId: Long, optionId: Long) {
    val poll = SignalDatabase.polls.getPollFromId(pollId)
    if (poll == null) {
      Log.w(TAG, "Cannot find poll anymore $pollId")
      return
    }

    writableDatabase.withinTransaction { db ->
      // Clear out any old voting history for this specific voter option combination
      db.delete(PollVoteTable.TABLE_NAME)
        .where(
          """
            ${PollVoteTable.POLL_ID} = ? AND 
            ${PollVoteTable.POLL_OPTION_ID} = ? AND 
            ${PollVoteTable.VOTER_ID} = ? AND 
            ${PollVoteTable.VOTE_COUNT} < ?
          """.trimIndent(),
          pollId,
          optionId,
          voterId,
          voteCount
        )
        .run()

      db.updateWithOnConflict(
        PollVoteTable.TABLE_NAME,
        contentValuesOf(PollVoteTable.VOTE_STATE to VoteState.ADDED.value),
        "${PollVoteTable.POLL_ID} = ? AND ${PollVoteTable.VOTER_ID} = ? AND ${PollVoteTable.VOTE_COUNT} = ? AND ${PollVoteTable.VOTE_STATE} = ${VoteState.PENDING_ADD.value}",
        SqlUtil.buildArgs(pollId, voterId, voteCount),
        SQLiteDatabase.CONFLICT_REPLACE
      )

      if (!poll.allowMultipleVotes) {
        db.delete(PollVoteTable.TABLE_NAME)
          .where("${PollVoteTable.POLL_ID} = ? AND ${PollVoteTable.VOTER_ID} = ? AND ${PollVoteTable.VOTE_COUNT} < ?", poll.id, Recipient.self().id, voteCount)
          .run()
      }
    }
    AppDependencies.databaseObserver.notifyMessageUpdateObservers(MessageId(messageId))
  }

  /**
   * Removes vote from a poll. This also increases the vote count because removal of a vote, is technically a type of vote.
   * Status is marked as [VoteState.PENDING_REMOVE] here and then once it successfully sends, it will get updated to [VoteState.REMOVED] in [markPendingAsRemoved]
   * If the latest state is already a pending state for this vote, it will just update the pending state.
   * However, if there is a resolved state eg [VoteState.REMOVED], we add a new entry so that if the pending entry fails, we can revert back to a known state.
   */
  fun removeVote(poll: PollRecord, pollOption: PollOption): Int {
    val self = Recipient.self().id.toLong()
    var voteCount = 0

    writableDatabase.withinTransaction { db ->
      voteCount = getCurrentPollVoteCount(poll.id, self) + 1
      if (isPending(poll.id, pollOption.id, self)) {
        db.update(PollVoteTable.TABLE_NAME)
          .values(
            PollVoteTable.VOTE_STATE to VoteState.PENDING_REMOVE.value,
            PollVoteTable.VOTE_COUNT to voteCount
          )
          .where(
            "${PollVoteTable.POLL_ID} = ? AND ${PollVoteTable.POLL_OPTION_ID} = ? AND ${PollVoteTable.VOTER_ID} = ? AND (${PollVoteTable.VOTE_STATE} = ${VoteState.PENDING_ADD.value} OR ${PollVoteTable.VOTE_STATE} = ${VoteState.PENDING_REMOVE.value})",
            poll.id,
            pollOption.id,
            self
          )
          .run()
      } else {
        db.insertInto(PollVoteTable.TABLE_NAME)
          .values(
            PollVoteTable.POLL_ID to poll.id,
            PollVoteTable.POLL_OPTION_ID to pollOption.id,
            PollVoteTable.VOTER_ID to self,
            PollVoteTable.VOTE_COUNT to voteCount,
            PollVoteTable.VOTE_STATE to VoteState.PENDING_REMOVE.value
          )
          .run()
      }
    }

    AppDependencies.databaseObserver.notifyMessageUpdateObservers(MessageId(poll.messageId))
    return voteCount
  }

  /**
   * Once a vote is sent to at least one person, we can update the [VoteState.PENDING_REMOVE] state to [VoteState.REMOVED].
   */
  fun markPendingAsRemoved(pollId: Long, voterId: Long, voteCount: Int, messageId: Long, optionId: Long) {
    writableDatabase.withinTransaction { db ->
      // Clear out any old voting history for this specific voter option combination
      db.delete(PollVoteTable.TABLE_NAME)
        .where(
          """
            ${PollVoteTable.POLL_ID} = ? AND 
            ${PollVoteTable.VOTER_ID} = ? AND 
            ${PollVoteTable.POLL_OPTION_ID} = ? AND 
            ${PollVoteTable.VOTE_COUNT} < ?
          """.trimIndent(),
          pollId,
          voterId,
          optionId,
          voteCount
        )
        .run()

      db.updateWithOnConflict(
        PollVoteTable.TABLE_NAME,
        contentValuesOf(PollVoteTable.VOTE_STATE to VoteState.REMOVED.value),
        "${PollVoteTable.POLL_ID} = ? AND ${PollVoteTable.VOTER_ID} = ? AND ${PollVoteTable.VOTE_COUNT} = ? AND ${PollVoteTable.VOTE_STATE} = ${VoteState.PENDING_REMOVE.value}",
        SqlUtil.buildArgs(pollId, voterId, voteCount),
        SQLiteDatabase.CONFLICT_REPLACE
      )
    }
    AppDependencies.databaseObserver.notifyMessageUpdateObservers(MessageId(messageId))
  }

  /**
   * For a given poll, returns the option indexes that the person has voted for.
   * Because we store both pending and resolved states of votes, we need to make
   * sure that when getting votes, we only get the latest vote. We do this by
   * sorting our query by vote count (higher vote counts are the more recent) and
   * then for each option, only getting the latest such vote if applicable.
   */
  fun getVotes(pollId: Long, allowMultipleVotes: Boolean, voteCount: Int): List<Int> {
    val voteQuery = if (allowMultipleVotes) {
      "${PollVoteTable.VOTE_COUNT} <= ?"
    } else {
      "${PollVoteTable.VOTE_COUNT} = ?"
    }

    val votesStates = readableDatabase
      .select(PollOptionTable.OPTION_ORDER, PollVoteTable.VOTE_STATE)
      .from("${PollVoteTable.TABLE_NAME} LEFT JOIN ${PollOptionTable.TABLE_NAME} ON ${PollVoteTable.TABLE_NAME}.${PollVoteTable.POLL_OPTION_ID} = ${PollOptionTable.TABLE_NAME}.${PollOptionTable.ID}")
      .where(
        """
        ${PollVoteTable.TABLE_NAME}.${PollVoteTable.POLL_ID} = ? AND 
        ${PollVoteTable.VOTER_ID} = ? AND 
        ${PollVoteTable.POLL_OPTION_ID} IS NOT NULL AND 
        $voteQuery
        """,
        pollId,
        Recipient.self().id.toLong(),
        voteCount
      )
      .orderBy("${PollVoteTable.VOTE_COUNT} DESC")
      .run()
      .groupBy { cursor ->
        cursor.requireInt(PollOptionTable.OPTION_ORDER) to cursor.requireInt(PollVoteTable.VOTE_STATE)
      }

    val votes = votesStates.filter {
      if (allowMultipleVotes) {
        it.value.first() == VoteState.PENDING_ADD.value || it.value.first() == VoteState.ADDED.value
      } else {
        it.value.first() == VoteState.PENDING_ADD.value
      }
    }

    return votes.keys.toList()
  }

  /**
   * For a given poll, returns who has voted in the poll. If a person has voted for multiple options, only count their most recent vote.
   */
  fun getAllVotes(messageId: Long): List<PollVote> {
    return readableDatabase
      .select()
      .from("${PollTable.TABLE_NAME} INNER JOIN ${PollVoteTable.TABLE_NAME} ON ${PollTable.TABLE_NAME}.${PollTable.ID} = ${PollVoteTable.TABLE_NAME}.${PollVoteTable.POLL_ID}")
      .where("${PollTable.MESSAGE_ID} = ?", messageId)
      .orderBy("${PollVoteTable.DATE_RECEIVED} DESC")
      .run()
      .readToList { cursor ->
        PollVote(
          pollId = cursor.requireLong(PollVoteTable.POLL_ID),
          question = cursor.requireNonNullString(PollTable.QUESTION),
          voterId = RecipientId.from(cursor.requireLong(PollVoteTable.VOTER_ID)),
          dateReceived = cursor.requireLong(PollVoteTable.DATE_RECEIVED)
        )
      }
      .distinctBy { it.pollId to it.voterId }
  }

  /**
   * When a vote job fails, delete the pending vote so that it falls back to the most recently resolved (not pending)
   * [VoteState] prior to this voting session. If the pending vote was the only entry, its deletion is equal to having not voted.
   */
  fun removePendingVote(pollId: Long, optionId: Long, voteCount: Int, messageId: Long) {
    Log.w(TAG, "Pending vote failed, reverting vote at $voteCount")

    writableDatabase.withinTransaction { db ->
      db.delete(PollVoteTable.TABLE_NAME)
        .where(
          """
          ${PollVoteTable.POLL_ID} = ? AND 
          ${PollVoteTable.POLL_OPTION_ID} = ? AND 
          ${PollVoteTable.VOTER_ID} = ? AND 
          ${PollVoteTable.VOTE_COUNT} = ?  
          """.trimIndent(),
          pollId,
          optionId,
          Recipient.self().id.toLong(),
          voteCount
        )
        .run()
    }
    AppDependencies.databaseObserver.notifyMessageUpdateObservers(MessageId(messageId))
  }

  /**
   * Inserts all of the votes a person has made on a poll. Clears out any old data if they voted previously.
   */
  fun insertVotes(pollId: Long, pollOptionIds: List<Long>, voterId: Long, voteCount: Long, messageId: MessageId) {
    writableDatabase.withinTransaction { db ->
      // Delete any previous votes they had on the poll
      db.delete(PollVoteTable.TABLE_NAME)
        .where("${PollVoteTable.VOTER_ID} = ? AND ${PollVoteTable.POLL_ID} = ?", voterId, pollId)
        .run()

      SqlUtil.buildBulkInsert(
        PollVoteTable.TABLE_NAME,
        arrayOf(PollVoteTable.POLL_ID, PollVoteTable.POLL_OPTION_ID, PollVoteTable.VOTER_ID, PollVoteTable.VOTE_COUNT, PollVoteTable.DATE_RECEIVED, PollVoteTable.VOTE_STATE),
        pollOptionIds.toPollVoteContentValues(pollId, voterId, voteCount)
      ).forEach {
        db.execSQL(it.where, it.whereArgs)
      }
    }

    AppDependencies.databaseObserver.notifyMessageUpdateObservers(messageId)
    SignalDatabase.messages.updateVotesUnread(writableDatabase, messageId.id, hasVotes(pollId), pollOptionIds.isEmpty())
  }

  private fun hasVotes(pollId: Long): Boolean {
    return readableDatabase
      .exists(PollVoteTable.TABLE_NAME)
      .where("${PollVoteTable.POLL_ID} = ?", pollId)
      .run()
  }

  /**
   * If a poll has ended, returns the message id of the poll end message. Otherwise, return -1.
   */
  fun getPollTerminateMessageId(messageId: Long): Long {
    return readableDatabase
      .select(PollTable.END_MESSAGE_ID)
      .from(PollTable.TABLE_NAME)
      .where("${PollTable.MESSAGE_ID} = ?", messageId)
      .run()
      .readToSingleLong(-1)
  }

  /**
   * Ends a poll
   */
  fun endPoll(pollId: Long, endingMessageId: Long) {
    val messageId = getMessageId(pollId)
    if (messageId == null) {
      Log.w(TAG, "Unable to find the poll to end.")
      return
    }
    writableDatabase.withinTransaction { db ->
      db.update(PollTable.TABLE_NAME)
        .values(PollTable.END_MESSAGE_ID to endingMessageId)
        .where("${PollTable.ID} = ?", pollId)
        .run()
    }
    AppDependencies.databaseObserver.notifyMessageUpdateObservers(MessageId(messageId))
  }

  /**
   * Returns the poll id if associated with a given message id
   */
  fun getPollId(messageId: Long): Long? {
    return readableDatabase
      .select(PollTable.ID)
      .from(PollTable.TABLE_NAME)
      .where("${PollTable.MESSAGE_ID} = ?", messageId)
      .run()
      .readToSingleLongOrNull()
  }

  /**
   * Returns the message id for a poll id
   */
  fun getMessageId(pollId: Long): Long? {
    return readableDatabase
      .select(PollTable.MESSAGE_ID)
      .from(PollTable.TABLE_NAME)
      .where("${PollTable.ID} = ?", pollId)
      .run()
      .readToSingleLongOrNull()
  }

  /**
   * Returns a poll record for a given poll id
   */
  fun getPollFromId(pollId: Long): PollRecord? {
    return getPoll(getMessageId(pollId))
  }

  /**
   * Returns the minimum amount necessary to create a poll for a message id
   */
  fun getPollForOutgoingMessage(messageId: Long): Poll? {
    return readableDatabase.withinTransaction { db ->
      db.select(PollTable.ID, PollTable.QUESTION, PollTable.ALLOW_MULTIPLE_VOTES)
        .from(PollTable.TABLE_NAME)
        .where("${PollTable.MESSAGE_ID} = ?", messageId)
        .run()
        .readToSingleObject { cursor ->
          val pollId = cursor.requireLong(PollTable.ID)
          Poll(
            question = cursor.requireString(PollTable.QUESTION) ?: "",
            allowMultipleVotes = cursor.requireBoolean(PollTable.ALLOW_MULTIPLE_VOTES),
            pollOptions = getPollOptionText(pollId),
            authorId = Recipient.self().id.toLong()
          )
        }
    }
  }

  /**
   * Returns the poll if associated with a given message id
   */
  fun getPoll(messageId: Long?): PollRecord? {
    return if (messageId != null) {
      getPollsForMessages(listOf(messageId))[messageId]
    } else {
      null
    }
  }

  /**
   * Maps message ids to its associated poll (if it exists)
   */
  fun getPollsForMessages(messageIds: Collection<Long>, includePending: Boolean = true): Map<Long, PollRecord> {
    if (messageIds.isEmpty() || !Recipient.isSelfSet) {
      return emptyMap()
    }

    val self = Recipient.self().id.toLong()
    val query = SqlUtil.buildFastCollectionQuery(PollTable.MESSAGE_ID, messageIds)
    return readableDatabase
      .select(PollTable.ID, PollTable.MESSAGE_ID, PollTable.QUESTION, PollTable.ALLOW_MULTIPLE_VOTES, PollTable.END_MESSAGE_ID, PollTable.AUTHOR_ID, PollTable.MESSAGE_ID)
      .from(PollTable.TABLE_NAME)
      .where(query.where, query.whereArgs)
      .run()
      .readToMap { cursor ->
        val pollId = cursor.requireLong(PollTable.ID)
        val pollVotes = getPollVotes(pollId)
        val (pendingAdds, pendingRemoves) = getPendingVotes(pollId)
        val pollOptions = getPollOptions(pollId).map { option ->
          val voters = pollVotes[option.key] ?: emptyList()
          val voteState = if (includePending && pendingAdds.contains(option.key)) {
            VoteState.PENDING_ADD
          } else if (includePending && pendingRemoves.contains(option.key)) {
            VoteState.PENDING_REMOVE
          } else if (voters.any { it.id == self }) {
            VoteState.ADDED
          } else {
            VoteState.NONE
          }
          PollOption(id = option.key, text = option.value, voters = voters, voteState = voteState)
        }
        val poll = PollRecord(
          id = pollId,
          question = cursor.requireNonNullString(PollTable.QUESTION),
          pollOptions = pollOptions,
          allowMultipleVotes = cursor.requireBoolean(PollTable.ALLOW_MULTIPLE_VOTES),
          hasEnded = cursor.requireBoolean(PollTable.END_MESSAGE_ID),
          authorId = cursor.requireLong(PollTable.AUTHOR_ID),
          messageId = cursor.requireLong(PollTable.MESSAGE_ID)
        )
        cursor.requireLong(PollTable.MESSAGE_ID) to poll
      }
  }

  /**
   * Given a poll id, returns a list of all of the ids of its options
   */
  fun getPollOptionIds(pollId: Long): List<Long> {
    return readableDatabase
      .select(PollOptionTable.ID)
      .from(PollOptionTable.TABLE_NAME)
      .where("${PollVoteTable.POLL_ID} = ?", pollId)
      .orderBy(PollOptionTable.OPTION_ORDER)
      .run()
      .readToList { cursor ->
        cursor.requireLong(PollOptionTable.ID)
      }
  }

  /**
   * Given a poll id and a voter id, return their vote count (how many times they have voted)
   */
  fun getCurrentPollVoteCount(pollId: Long, voterId: Long): Int {
    return readableDatabase
      .select("MAX(${PollVoteTable.VOTE_COUNT})")
      .from(PollVoteTable.TABLE_NAME)
      .where("${PollVoteTable.POLL_ID} = ? AND ${PollVoteTable.VOTER_ID} = ?", pollId, voterId)
      .run()
      .readToSingleInt(-1)
  }

  /**
   * Return if the poll supports multiple votes for options
   */
  fun canAllowMultipleVotes(pollId: Long): Boolean {
    return readableDatabase
      .select(PollTable.ALLOW_MULTIPLE_VOTES)
      .from(PollTable.TABLE_NAME)
      .where("${PollTable.ID} = ? ", pollId)
      .run()
      .readToSingleBoolean()
  }

  /**
   * Returns whether the poll has ended
   */
  fun hasEnded(pollId: Long): Boolean {
    return readableDatabase
      .select(PollTable.END_MESSAGE_ID)
      .from(PollTable.TABLE_NAME)
      .where("${PollTable.ID} = ? ", pollId)
      .run()
      .readToSingleBoolean()
  }

  fun deletePoll(messageId: Long) {
    writableDatabase
      .delete(PollTable.TABLE_NAME)
      .where("${PollTable.MESSAGE_ID} = ?", messageId)
      .run()
  }

  private fun isPending(pollId: Long, optionId: Long, voterId: Long): Boolean {
    return readableDatabase
      .exists(PollVoteTable.TABLE_NAME)
      .where(
        """
         ${PollVoteTable.POLL_ID} = ? AND
         ${PollVoteTable.POLL_OPTION_ID} = ? AND 
         ${PollVoteTable.VOTER_ID} = ? AND 
         (${PollVoteTable.VOTE_STATE} = ${VoteState.PENDING_ADD.value} OR ${PollVoteTable.VOTE_STATE} = ${VoteState.PENDING_REMOVE.value})
         """,
        pollId,
        optionId,
        voterId
      )
      .run()
  }

  private fun getPollOptions(pollId: Long): Map<Long, String> {
    return readableDatabase
      .select(PollOptionTable.ID, PollOptionTable.OPTION_TEXT)
      .from(PollOptionTable.TABLE_NAME)
      .where("${PollVoteTable.POLL_ID} = ?", pollId)
      .run()
      .readToMap { cursor ->
        cursor.requireLong(PollOptionTable.ID) to cursor.requireNonNullString(PollOptionTable.OPTION_TEXT)
      }
  }

  private fun getPollVotes(pollId: Long): Map<Long, List<Voter>> {
    return readableDatabase
      .select(PollVoteTable.POLL_OPTION_ID, PollVoteTable.VOTER_ID, PollVoteTable.VOTE_COUNT)
      .from(PollVoteTable.TABLE_NAME)
      .where("${PollVoteTable.POLL_ID} = ? AND ${PollVoteTable.VOTE_STATE} = ${VoteState.ADDED.value}", pollId)
      .run()
      .groupBy { cursor ->
        cursor.requireLong(PollVoteTable.POLL_OPTION_ID) to Voter(id = cursor.requireLong(PollVoteTable.VOTER_ID), voteCount = cursor.requireInt(PollVoteTable.VOTE_COUNT))
      }
  }

  private fun getPendingVotes(pollId: Long): Pair<List<Long>, List<Long>> {
    val pendingAdds = mutableListOf<Long>()
    val pendingRemoves = mutableListOf<Long>()
    readableDatabase
      .select(PollVoteTable.POLL_OPTION_ID, PollVoteTable.VOTE_STATE)
      .from(PollVoteTable.TABLE_NAME)
      .where(
        "${PollVoteTable.POLL_ID} = ? AND ${PollVoteTable.VOTER_ID} = ? AND (${PollVoteTable.VOTE_STATE} = ${VoteState.PENDING_ADD.value} OR ${PollVoteTable.VOTE_STATE} = ${VoteState.PENDING_REMOVE.value})",
        pollId,
        Recipient.self().id
      )
      .run()
      .forEach { cursor ->
        if (cursor.requireInt(PollVoteTable.VOTE_STATE) == VoteState.PENDING_ADD.value) {
          pendingAdds += cursor.requireLong(PollVoteTable.POLL_OPTION_ID)
        } else {
          pendingRemoves += cursor.requireLong(PollVoteTable.POLL_OPTION_ID)
        }
      }
    return Pair(pendingAdds, pendingRemoves)
  }

  private fun getPollOptionText(pollId: Long): List<String> {
    return readableDatabase
      .select(PollOptionTable.OPTION_TEXT)
      .from(PollOptionTable.TABLE_NAME)
      .where("${PollOptionTable.POLL_ID} = ?", pollId)
      .run()
      .readToList { it.requireString(PollOptionTable.OPTION_TEXT)!! }
  }

  private fun <E> Collection<E>.toPollContentValues(pollId: Long): List<ContentValues> {
    return this.mapIndexed { index, option ->
      contentValuesOf(
        PollOptionTable.POLL_ID to pollId,
        PollOptionTable.OPTION_TEXT to option,
        PollOptionTable.OPTION_ORDER to index
      )
    }
  }

  private fun <E> Collection<E>.toPollVoteContentValues(pollId: Long, voterId: Long, voteCount: Long): List<ContentValues> {
    return this.map {
      contentValuesOf(
        PollVoteTable.POLL_ID to pollId,
        PollVoteTable.POLL_OPTION_ID to it,
        PollVoteTable.VOTER_ID to voterId,
        PollVoteTable.VOTE_COUNT to voteCount,
        PollVoteTable.DATE_RECEIVED to System.currentTimeMillis(),
        PollVoteTable.VOTE_STATE to VoteState.ADDED.value
      )
    }
  }
}
