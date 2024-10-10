/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.core.content.contentValuesOf
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.signal.core.util.Base64
import org.signal.core.util.Hex
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.exists
import org.signal.core.util.insertInto
import org.signal.core.util.orNull
import org.signal.core.util.readToList
import org.signal.core.util.readToSet
import org.signal.core.util.readToSingleLong
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.toInt
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileChangeDetails
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupId.V2
import org.thoughtcrime.securesms.profiles.spoofing.ReviewRecipient
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Optional
import kotlin.time.Duration.Companion.days

/**
 * Tables to help manage the state of name collisions.
 */
class NameCollisionTables(
  context: Context,
  database: SignalDatabase
) : DatabaseTable(context, database) {

  companion object {
    private const val ID = "_id"

    private val PROFILE_CHANGE_TIMEOUT = 1.days

    val CREATE_TABLE = arrayOf(
      NameCollisionTable.CREATE_TABLE,
      NameCollisionMembershipTable.CREATE_TABLE
    )

    val CREATE_INDEXES = NameCollisionMembershipTable.CREATE_INDEXES
  }

  /**
   * Represents a detected name collision which can involve one or more recipients.
   */
  private object NameCollisionTable {
    const val TABLE_NAME = "name_collision"

    /**
     * The thread id of the conversation to display this collision for.
     */
    const val THREAD_ID = "thread_id"

    /**
     * Whether the user has manually dismissed the collision.
     */
    const val DISMISSED = "dismissed"

    /**
     * The hash representing the latest known display name state.
     */
    const val HASH = "hash"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $THREAD_ID INTEGER UNIQUE NOT NULL,
        $DISMISSED INTEGER DEFAULT 0,
        $HASH STRING DEFAULT NULL
      )
    """
  }

  /**
   * Represents a recipient who is involved in a name collision.
   */
  private object NameCollisionMembershipTable {
    const val TABLE_NAME = "name_collision_membership"

    /**
     * FK Reference to a name_collision
     */
    const val COLLISION_ID = "collision_id"

    /**
     * FK Reference to the recipient involved
     */
    const val RECIPIENT_ID = "recipient_id"

    /**
     * Proto containing group profile change details. Only present for entries tied to group collisions.
     */
    const val PROFILE_CHANGE_DETAILS = "profile_change_details"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $COLLISION_ID INTEGER NOT NULL REFERENCES ${NameCollisionTable.TABLE_NAME} ($ID) ON DELETE CASCADE,
        $RECIPIENT_ID INTEGER NOT NULL REFERENCES ${RecipientTable.TABLE_NAME} ($ID) ON DELETE CASCADE,
        $PROFILE_CHANGE_DETAILS BLOB DEFAULT NULL,
        UNIQUE ($COLLISION_ID, $RECIPIENT_ID)
      )
    """

    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX name_collision_membership_collision_id_index ON $TABLE_NAME ($COLLISION_ID)",
      "CREATE INDEX name_collision_membership_recipient_id_index ON $TABLE_NAME ($RECIPIENT_ID)"
    )
  }

  /**
   * Marks the relevant collisions dismissed according to the given thread recipient.
   */
  @WorkerThread
  fun markCollisionsForThreadRecipientDismissed(threadRecipientId: RecipientId) {
    writableDatabase.withinTransaction { db ->
      val threadId = SignalDatabase.threads.getThreadIdFor(threadRecipientId) ?: return@withinTransaction

      db.update(NameCollisionTable.TABLE_NAME)
        .values(NameCollisionTable.DISMISSED to 1)
        .where("${NameCollisionTable.THREAD_ID} = ?", threadId)
        .run()
    }
  }

  /**
   * @return A flattened list of similar recipients.
   */
  @WorkerThread
  fun getCollisionsForThreadRecipientId(recipientId: RecipientId): List<ReviewRecipient> {
    val threadId = SignalDatabase.threads.getThreadIdFor(recipientId) ?: return emptyList()
    val collisionId = readableDatabase
      .select(ID)
      .from(NameCollisionTable.TABLE_NAME)
      .where("${NameCollisionTable.THREAD_ID} = ? AND ${NameCollisionTable.DISMISSED} = 0", threadId)
      .run()
      .readToSingleLong()

    if (collisionId <= 0) {
      return emptyList()
    }

    val collisions: Set<ReviewRecipient> = readableDatabase
      .select()
      .from(NameCollisionMembershipTable.TABLE_NAME)
      .where("${NameCollisionMembershipTable.COLLISION_ID} = ?", collisionId)
      .run()
      .readToList { cursor ->
        ReviewRecipient(
          Recipient.resolved(RecipientId.from(cursor.requireLong(NameCollisionMembershipTable.RECIPIENT_ID))),
          cursor.requireBlob(NameCollisionMembershipTable.PROFILE_CHANGE_DETAILS)?.let { ProfileChangeDetails.ADAPTER.decode(it) }
        )
      }.toSet()

    val groupMembers: Optional<List<RecipientId>> = SignalDatabase.groups.getGroup(recipientId).map { it.members }
    val invalidCollisions: Set<ReviewRecipient> = collisions.filter {
      groupMembers.isPresent && (it.recipient.id !in groupMembers.get())
    }.toSet()

    val groups = (collisions - invalidCollisions).groupBy { SqlUtil.buildCaseInsensitiveGlobPattern(it.recipient.getDisplayName(context)) }
    val toDelete: Set<ReviewRecipient> = invalidCollisions + groups.values.filter { it.size < 2 }.flatten().toSet()
    val toReturn: Set<ReviewRecipient> = groups.values.filter { it.size >= 2 }.flatten().toSet()

    if (toDelete.isNotEmpty()) {
      writableDatabase.withinTransaction { db ->
        val queries = SqlUtil.buildCollectionQuery(
          column = NameCollisionMembershipTable.RECIPIENT_ID,
          values = toDelete.map { it.recipient.id }
        )

        for (query in queries) {
          db.delete(NameCollisionMembershipTable.TABLE_NAME)
            .where("${NameCollisionMembershipTable.COLLISION_ID} = ? AND ${query.where}", SqlUtil.appendArgs(arrayOf(collisionId.toString()), query.whereArgs))
            .run()
        }

        pruneCollisions()
      }
    }

    return toReturn.toList()
  }

  /**
   * Update the collision *only* for the given individual.
   */
  @WorkerThread
  fun handleIndividualNameCollision(recipientId: RecipientId) {
    writableDatabase.withinTransaction { db ->
      val similarRecipients = SignalDatabase.recipients.getSimilarRecipientIds(Recipient.resolved(recipientId))

      db.delete(NameCollisionMembershipTable.TABLE_NAME)
        .where("${NameCollisionMembershipTable.RECIPIENT_ID} = ?", recipientId)
        .run()

      if (similarRecipients.size == 1) {
        val threadId = SignalDatabase.threads.getThreadIdFor(recipientId) ?: -1
        if (threadId > 0L) {
          db.delete(NameCollisionTable.TABLE_NAME)
            .where("${NameCollisionTable.THREAD_ID} = ?", threadId)
            .run()
        }
      }

      similarRecipients.forEach { threadRecipientId ->
        handleNameCollisions(
          threadRecipientId = threadRecipientId,
          getCollisionRecipients = {
            val recipients = Recipient.resolvedList(similarRecipients)

            recipients.map { ReviewRecipient(it) }.toSet()
          }
        )
      }

      pruneCollisions()
    }
  }

  /**
   * Update the collisions for the given group
   */
  @WorkerThread
  fun handleGroupNameCollisions(groupId: GroupId.V2, changed: Set<RecipientId>) {
    writableDatabase.withinTransaction {
      val threadRecipientId = SignalDatabase.recipients.getByGroupId(groupId).orNull() ?: return@withinTransaction
      handleNameCollisions(
        threadRecipientId = threadRecipientId,
        getCollisionRecipients = { getDuplicatedGroupRecipients(groupId, changed).toSet() }
      )

      pruneCollisions()
    }
  }

  private fun handleNameCollisions(
    threadRecipientId: RecipientId,
    getCollisionRecipients: () -> Set<ReviewRecipient>
  ) {
    check(writableDatabase.inTransaction())

    val resolved = Recipient.resolved(threadRecipientId)
    val collisionRecipients: Set<ReviewRecipient> = getCollisionRecipients()

    if (collisionRecipients.size < 2 && !collisionExists(threadRecipientId)) {
      return
    }

    val collision: NameCollision = getOrCreateCollision(resolved)
    val hash: String = calculateHash(collisionRecipients)

    updateCollision(
      collision.copy(
        members = collisionRecipients,
        hash = hash,
        dismissed = if (!collision.dismissed) false else collision.hash == hash
      )
    )
  }

  private fun collisionExists(threadRecipientId: RecipientId): Boolean {
    val threadId = SignalDatabase.threads.getThreadIdFor(threadRecipientId) ?: return false
    return writableDatabase
      .exists(NameCollisionTable.TABLE_NAME)
      .where("${NameCollisionTable.THREAD_ID} = ?", threadId)
      .run()
  }

  private fun getOrCreateCollision(threadRecipient: Recipient): NameCollision {
    check(writableDatabase.inTransaction())
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(threadRecipient)

    val collision = writableDatabase
      .select()
      .from(NameCollisionTable.TABLE_NAME)
      .where("${NameCollisionTable.THREAD_ID} = ?", threadId)
      .run()
      .readToSingleObject { nameCollisionCursor ->
        NameCollision(
          id = nameCollisionCursor.requireLong(ID),
          threadId = threadId,
          members = writableDatabase
            .select(NameCollisionMembershipTable.RECIPIENT_ID, NameCollisionMembershipTable.PROFILE_CHANGE_DETAILS)
            .from(NameCollisionMembershipTable.TABLE_NAME)
            .where("${NameCollisionMembershipTable.COLLISION_ID} = ?", nameCollisionCursor.requireInt(ID))
            .run()
            .readToSet {
              val id = RecipientId.from(it.requireLong(NameCollisionMembershipTable.RECIPIENT_ID))
              val rawProfileChangeDetails = it.requireBlob(NameCollisionMembershipTable.PROFILE_CHANGE_DETAILS)
              val profileChangeDetails = if (rawProfileChangeDetails != null) {
                ProfileChangeDetails.ADAPTER.decode(rawProfileChangeDetails)
              } else {
                null
              }

              ReviewRecipient(
                Recipient.resolved(id),
                profileChangeDetails
              )
            },
          dismissed = nameCollisionCursor.requireBoolean(NameCollisionTable.DISMISSED),
          hash = nameCollisionCursor.requireString(NameCollisionTable.HASH) ?: ""
        )
      }

    return if (collision == null) {
      val rowId = writableDatabase
        .insertInto(NameCollisionTable.TABLE_NAME)
        .values(
          contentValuesOf(
            NameCollisionTable.THREAD_ID to threadId,
            NameCollisionTable.DISMISSED to 0,
            NameCollisionTable.HASH to null
          )
        )
        .run()

      NameCollision(id = rowId, threadId = threadId, members = emptySet(), dismissed = false, hash = "")
    } else {
      collision
    }
  }

  private fun updateCollision(collision: NameCollision) {
    check(writableDatabase.inTransaction())

    writableDatabase
      .update(NameCollisionTable.TABLE_NAME)
      .values(
        contentValuesOf(
          NameCollisionTable.DISMISSED to collision.dismissed.toInt(),
          NameCollisionTable.THREAD_ID to collision.threadId,
          NameCollisionTable.HASH to collision.hash
        )
      )
      .where("$ID = ?", collision.id)
      .run()

    writableDatabase
      .delete(NameCollisionMembershipTable.TABLE_NAME)
      .where("${NameCollisionMembershipTable.COLLISION_ID} = ?")
      .run()

    if (collision.members.size < 2) {
      return
    }

    collision.members.forEach { member ->
      writableDatabase
        .insertInto(NameCollisionMembershipTable.TABLE_NAME)
        .values(
          NameCollisionMembershipTable.RECIPIENT_ID to member.recipient.id.toLong(),
          NameCollisionMembershipTable.COLLISION_ID to collision.id,
          NameCollisionMembershipTable.PROFILE_CHANGE_DETAILS to member.profileChangeDetails?.encode()
        )
        .run(conflictStrategy = org.thoughtcrime.securesms.database.SQLiteDatabase.CONFLICT_IGNORE)
    }
  }

  private fun calculateHash(collisionRecipients: Set<ReviewRecipient>): String {
    if (collisionRecipients.isEmpty()) {
      return ""
    }

    return try {
      val digest = MessageDigest.getInstance("MD5")
      val names = collisionRecipients.map { it.recipient.getDisplayName(context) }
      names.forEach { digest.update(it.encodeToByteArray()) }
      Hex.toStringCondensed(digest.digest())
    } catch (e: NoSuchAlgorithmException) {
      ""
    }
  }

  /**
   * Remove any collision for which there is only a single member.
   */
  private fun pruneCollisions() {
    check(writableDatabase.inTransaction())

    writableDatabase.execSQL(
      """
      DELETE FROM ${NameCollisionTable.TABLE_NAME}
      WHERE ${NameCollisionTable.TABLE_NAME}.$ID IN (
          SELECT ${NameCollisionMembershipTable.COLLISION_ID}
          FROM ${NameCollisionMembershipTable.TABLE_NAME}
          GROUP BY ${NameCollisionMembershipTable.COLLISION_ID}
          HAVING COUNT($ID) < 2
      )
      """.trimIndent()
    )
  }

  private fun getDuplicatedGroupRecipients(groupId: V2, toCheck: Set<RecipientId>): List<ReviewRecipient> {
    if (toCheck.isEmpty()) {
      return emptyList()
    }

    val profileChangeRecords: Map<RecipientId, MessageRecord> = getProfileChangeRecordsForGroup(groupId).associateBy { it.fromRecipient.id }
    val members: MutableList<Recipient> = SignalDatabase.groups.getGroupMembers(groupId, GroupTable.MemberSet.FULL_MEMBERS_INCLUDING_SELF).toMutableList()
    val changed: List<ReviewRecipient> = Recipient.resolvedList(toCheck)
      .map { recipient -> ReviewRecipient(recipient.resolve(), profileChangeRecords[recipient.id]?.let { getProfileChangeDetails(it) }) }
      .filter { !it.recipient.isSystemContact && it.recipient.nickname.isEmpty }

    val results = mutableListOf<ReviewRecipient>()

    for (reviewRecipient in changed) {
      if (results.contains(reviewRecipient)) {
        continue
      }

      members.remove(reviewRecipient.recipient)

      for (member in members) {
        if (member.getDisplayName(context) == reviewRecipient.recipient.getDisplayName(context)) {
          results.add(reviewRecipient)
          results.add(ReviewRecipient(member))
        }
      }
    }

    return results
  }

  private fun getProfileChangeRecordsForGroup(groupId: V2): List<MessageRecord> {
    val groupRecipientId = SignalDatabase.recipients.getByGroupId(groupId).get()
    val groupThreadId = SignalDatabase.threads.getThreadIdFor(groupRecipientId)

    return if (groupThreadId == null) {
      emptyList()
    } else {
      SignalDatabase.messages.getProfileChangeDetailsRecords(
        groupThreadId,
        System.currentTimeMillis() - PROFILE_CHANGE_TIMEOUT.inWholeMilliseconds
      )
    }
  }

  private fun getProfileChangeDetails(record: MessageRecord): ProfileChangeDetails {
    try {
      return ProfileChangeDetails.ADAPTER.decode(Base64.decode(record.body))
    } catch (e: IOException) {
      throw IllegalArgumentException(e)
    }
  }

  private data class NameCollision(
    val id: Long,
    val threadId: Long,
    val members: Set<ReviewRecipient>,
    val dismissed: Boolean,
    val hash: String
  )
}
