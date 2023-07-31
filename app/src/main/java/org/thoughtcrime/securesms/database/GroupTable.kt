package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.text.TextUtils
import androidx.annotation.WorkerThread
import androidx.core.content.contentValuesOf
import org.intellij.lang.annotations.Language
import org.signal.core.util.SetUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.SqlUtil.appendArg
import org.signal.core.util.SqlUtil.buildArgs
import org.signal.core.util.SqlUtil.buildCaseInsensitiveGlobPattern
import org.signal.core.util.SqlUtil.buildCollectionQuery
import org.signal.core.util.delete
import org.signal.core.util.exists
import org.signal.core.util.isAbsent
import org.signal.core.util.logging.Log
import org.signal.core.util.optionalString
import org.signal.core.util.readToList
import org.signal.core.util.readToSingleInt
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.storageservice.protos.groups.Member
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.thoughtcrime.securesms.contacts.paged.ContactSearchSortOrder
import org.thoughtcrime.securesms.contacts.paged.collections.ContactSearchIterator
import org.thoughtcrime.securesms.crypto.SenderKeyUtil
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.messages
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.recipients
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.groups.BadGroupIdException
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupId.Push
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange
import org.thoughtcrime.securesms.groups.GroupsV1MigratedCache
import org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil
import org.whispersystems.signalservice.api.groupsv2.GroupChangeReconstruct
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.util.UuidUtil
import java.io.Closeable
import java.security.SecureRandom
import java.util.Optional
import java.util.stream.Collectors
import javax.annotation.CheckReturnValue

class GroupTable(context: Context?, databaseHelper: SignalDatabase?) : DatabaseTable(context, databaseHelper), RecipientIdDatabaseReference {

  companion object {
    private val TAG = Log.tag(GroupTable::class.java)

    const val MEMBER_GROUP_CONCAT = "member_group_concat"
    const val THREAD_DATE = "thread_date"

    const val TABLE_NAME = "groups"
    const val ID = "_id"
    const val GROUP_ID = "group_id"
    const val RECIPIENT_ID = "recipient_id"
    const val TITLE = "title"
    const val AVATAR_ID = "avatar_id"
    const val AVATAR_KEY = "avatar_key"
    const val AVATAR_CONTENT_TYPE = "avatar_content_type"
    const val AVATAR_RELAY = "avatar_relay"
    const val AVATAR_DIGEST = "avatar_digest"
    const val TIMESTAMP = "timestamp"
    const val ACTIVE = "active"
    const val MMS = "mms"
    const val EXPECTED_V2_ID = "expected_v2_id"
    const val UNMIGRATED_V1_MEMBERS = "former_v1_members"
    const val DISTRIBUTION_ID = "distribution_id"
    const val SHOW_AS_STORY_STATE = "display_as_story"
    const val LAST_FORCE_UPDATE_TIMESTAMP = "last_force_update_timestamp"

    /** 32 bytes serialized [GroupMasterKey]  */
    const val V2_MASTER_KEY = "master_key"

    /** Increments with every change to the group  */
    const val V2_REVISION = "revision"

    /** Serialized [DecryptedGroup] protobuf  */
    const val V2_DECRYPTED_GROUP = "decrypted_group"

    /** Was temporarily used for PNP accept by pni but is no longer needed/updated  */
    @Deprecated("")
    private val AUTH_SERVICE_ID = "auth_service_id"

    @JvmField
    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY, 
        $GROUP_ID TEXT, 
        $RECIPIENT_ID INTEGER,
        $TITLE TEXT,
        $AVATAR_ID INTEGER, 
        $AVATAR_KEY BLOB,
        $AVATAR_CONTENT_TYPE TEXT, 
        $AVATAR_RELAY TEXT,
        $TIMESTAMP INTEGER,
        $ACTIVE INTEGER DEFAULT 1,
        $AVATAR_DIGEST BLOB, 
        $MMS INTEGER DEFAULT 0, 
        $V2_MASTER_KEY BLOB, 
        $V2_REVISION BLOB, 
        $V2_DECRYPTED_GROUP BLOB, 
        $EXPECTED_V2_ID TEXT DEFAULT NULL, 
        $UNMIGRATED_V1_MEMBERS TEXT DEFAULT NULL, 
        $DISTRIBUTION_ID TEXT DEFAULT NULL, 
        $SHOW_AS_STORY_STATE INTEGER DEFAULT 0, 
        $AUTH_SERVICE_ID TEXT DEFAULT NULL, 
        $LAST_FORCE_UPDATE_TIMESTAMP INTEGER DEFAULT 0
      )
    """

    @JvmField
    val CREATE_INDEXS = arrayOf(
      "CREATE UNIQUE INDEX IF NOT EXISTS group_id_index ON $TABLE_NAME ($GROUP_ID);",
      "CREATE UNIQUE INDEX IF NOT EXISTS group_recipient_id_index ON $TABLE_NAME ($RECIPIENT_ID);",
      "CREATE UNIQUE INDEX IF NOT EXISTS expected_v2_id_index ON $TABLE_NAME ($EXPECTED_V2_ID);",
      "CREATE UNIQUE INDEX IF NOT EXISTS group_distribution_id_index ON $TABLE_NAME($DISTRIBUTION_ID);"
    ) + MembershipTable.CREATE_INDEXES

    private val GROUP_PROJECTION = arrayOf(
      GROUP_ID,
      RECIPIENT_ID,
      TITLE,
      UNMIGRATED_V1_MEMBERS,
      AVATAR_ID,
      AVATAR_KEY,
      AVATAR_CONTENT_TYPE,
      AVATAR_RELAY,
      AVATAR_DIGEST,
      TIMESTAMP,
      ACTIVE,
      MMS,
      V2_MASTER_KEY,
      V2_REVISION,
      V2_DECRYPTED_GROUP,
      LAST_FORCE_UPDATE_TIMESTAMP
    )

    val TYPED_GROUP_PROJECTION = GROUP_PROJECTION
      .filterNot { it == RECIPIENT_ID }
      .map { columnName: String -> "$TABLE_NAME.$columnName" }
      .toList()

    //language=sql
    private val JOINED_GROUP_SELECT = """
      SELECT 
        DISTINCT $TABLE_NAME.*, 
        (
            SELECT GROUP_CONCAT(${MembershipTable.TABLE_NAME}.${MembershipTable.RECIPIENT_ID})
            FROM ${MembershipTable.TABLE_NAME} 
            WHERE ${MembershipTable.TABLE_NAME}.${MembershipTable.GROUP_ID} = $TABLE_NAME.$GROUP_ID
        ) as $MEMBER_GROUP_CONCAT
      FROM $TABLE_NAME          
    """

    val CREATE_TABLES = arrayOf(CREATE_TABLE, MembershipTable.CREATE_TABLE)
  }

  class MembershipTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {
    companion object {
      const val TABLE_NAME = "group_membership"

      const val ID = "_id"
      const val GROUP_ID = "group_id"
      const val RECIPIENT_ID = "recipient_id"

      //language=sql
      @JvmField
      val CREATE_TABLE = """
        CREATE TABLE $TABLE_NAME (
            $ID INTEGER PRIMARY KEY,
            $GROUP_ID TEXT NOT NULL,
            $RECIPIENT_ID INTEGER NOT NULL REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE,
            UNIQUE($GROUP_ID, $RECIPIENT_ID)
        )
      """

      val CREATE_INDEXES = arrayOf(
        "CREATE INDEX IF NOT EXISTS group_membership_recipient_id ON $TABLE_NAME ($RECIPIENT_ID)"
      )
    }
  }

  fun getGroup(recipientId: RecipientId): Optional<GroupRecord> {
    return getGroup(SqlUtil.Query("$TABLE_NAME.$RECIPIENT_ID = ?", buildArgs(recipientId)))
  }

  fun getGroup(groupId: GroupId): Optional<GroupRecord> {
    return getGroup(SqlUtil.Query("$TABLE_NAME.$GROUP_ID = ?", buildArgs(groupId)))
  }

  private fun getGroup(query: SqlUtil.Query): Optional<GroupRecord> {
    //language=sql
    val select = "$JOINED_GROUP_SELECT WHERE ${query.where}"

    readableDatabase
      .query(select, query.whereArgs)
      .use { cursor ->
        return if (cursor.moveToFirst()) {
          val groupRecord = getGroup(cursor)
          if (groupRecord.isPresent && RemappedRecords.getInstance().areAnyRemapped(groupRecord.get().members)) {
            val groupId = groupRecord.get().id
            val remaps = RemappedRecords.getInstance().buildRemapDescription(groupRecord.get().members)
            Log.w(TAG, "Found a group with remapped recipients in it's membership list! Updating the list. GroupId: $groupId, Remaps: $remaps", true)

            val oldToNew: List<Pair<RecipientId, RecipientId>> = groupRecord.get().members
              .map { it to RemappedRecords.getInstance().getRecipient(it).orElse(null) }
              .filterNot { (old, new) -> new == null || old == new }

            if (oldToNew.isNotEmpty()) {
              writableDatabase.withinTransaction { db ->
                oldToNew.forEach { remapRecipient(it.first, it.second) }
              }
            }

            readableDatabase.query(select, query.whereArgs).use { refreshedCursor ->
              if (refreshedCursor.moveToFirst()) {
                getGroup(refreshedCursor)
              } else {
                Optional.empty()
              }
            }
          } else {
            getGroup(cursor)
          }
        } else {
          Optional.empty()
        }
      }
  }

  /**
   * Call if you are sure this group should exist.
   * Finds group and throws if it cannot.
   */
  fun requireGroup(groupId: GroupId): GroupRecord {
    val group = getGroup(groupId)
    if (!group.isPresent) {
      throw AssertionError("Group not found")
    }
    return group.get()
  }

  fun groupExists(groupId: GroupId): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$GROUP_ID = ?", groupId.toString())
      .run()
  }

  /**
   * @return A gv1 group whose expected v2 ID matches the one provided.
   */
  fun getGroupV1ByExpectedV2(gv2Id: GroupId.V2): Optional<GroupRecord> {
    return getGroup(SqlUtil.Query("$TABLE_NAME.$EXPECTED_V2_ID = ?", buildArgs(gv2Id)))
  }

  /**
   * @return A gv1 group whose expected v2 ID matches the one provided or a gv2 group whose ID matches the one provided.
   *
   * If a gv1 group is present, it will be returned first.
   */
  fun getGroupV1OrV2ByExpectedV2(gv2Id: GroupId.V2): Optional<GroupRecord> {
    return getGroup(SqlUtil.Query("$TABLE_NAME.$EXPECTED_V2_ID = ? OR $TABLE_NAME.$GROUP_ID = ? ORDER BY $TABLE_NAME.$EXPECTED_V2_ID DESC", buildArgs(gv2Id, gv2Id)))
  }

  fun getGroupByDistributionId(distributionId: DistributionId): Optional<GroupRecord> {
    return getGroup(SqlUtil.Query("$TABLE_NAME.$DISTRIBUTION_ID = ?", buildArgs(distributionId)))
  }

  fun removeUnmigratedV1Members(id: GroupId.V2) {
    val group = getGroup(id)
    if (!group.isPresent) {
      Log.w(TAG, "Couldn't find the group!", Throwable())
      return
    }

    removeUnmigratedV1Members(id, group.get().unmigratedV1Members)
  }

  /**
   * Removes the specified members from the list of 'unmigrated V1 members' -- the list of members
   * that were either dropped or had to be invited when migrating the group from V1->V2.
   */
  fun removeUnmigratedV1Members(id: GroupId.V2, toRemove: List<RecipientId>) {
    val group = getGroup(id)
    if (group.isAbsent()) {
      Log.w(TAG, "Couldn't find the group!", Throwable())
      return
    }

    val newUnmigrated = group.get().unmigratedV1Members - toRemove.toSet()

    writableDatabase
      .update(TABLE_NAME)
      .values(UNMIGRATED_V1_MEMBERS to if (newUnmigrated.isEmpty()) null else newUnmigrated.serialize())
      .where("$GROUP_ID = ?", id)
      .run()

    Recipient.live(Recipient.externalGroupExact(id).id).refresh()
  }

  private fun getGroup(cursor: Cursor?): Optional<GroupRecord> {
    val reader = Reader(cursor)
    return Optional.ofNullable(reader.getCurrent())
  }

  /**
   * @return local db group revision or -1 if not present.
   */
  fun getGroupV2Revision(groupId: GroupId.V2): Int {
    readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$GROUP_ID = ?", groupId.toString())
      .run()
      .use { cursor ->
        return if (cursor.moveToNext()) {
          cursor.getInt(cursor.getColumnIndexOrThrow(V2_REVISION))
        } else {
          -1
        }
      }
  }

  fun isUnknownGroup(groupId: GroupId): Boolean {
    return isUnknownGroup(getGroup(groupId))
  }

  fun isUnknownGroup(group: Optional<GroupRecord>): Boolean {
    if (!group.isPresent) {
      return true
    }

    val noMetadata = !group.get().hasAvatar() && group.get().title.isNullOrEmpty()
    val noMembers = group.get().members.isEmpty() || group.get().members.size == 1 && group.get().members.contains(Recipient.self().id)

    return noMetadata && noMembers
  }

  fun queryGroupsByMemberName(inputQuery: String): Cursor {
    val subquery = recipients.getAllContactsSubquery(inputQuery)
    val statement = """
      SELECT 
        DISTINCT $TABLE_NAME.*, 
        GROUP_CONCAT(${MembershipTable.TABLE_NAME}.${MembershipTable.RECIPIENT_ID}) as $MEMBER_GROUP_CONCAT,
        ${ThreadTable.TABLE_NAME}.${ThreadTable.DATE} as $THREAD_DATE
      FROM $TABLE_NAME          
      INNER JOIN ${MembershipTable.TABLE_NAME} ON ${MembershipTable.TABLE_NAME}.${MembershipTable.GROUP_ID} = $TABLE_NAME.$GROUP_ID
      INNER JOIN ${ThreadTable.TABLE_NAME} ON ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} = $TABLE_NAME.$RECIPIENT_ID
      WHERE $TABLE_NAME.$ACTIVE = 1 AND ${MembershipTable.TABLE_NAME}.${MembershipTable.RECIPIENT_ID} IN (${subquery.where})
      GROUP BY ${MembershipTable.TABLE_NAME}.${MembershipTable.GROUP_ID}
      ORDER BY $TITLE COLLATE NOCASE ASC
    """

    return databaseHelper.signalReadableDatabase.query(statement, subquery.whereArgs)
  }

  fun queryGroupsByTitle(inputQuery: String, includeInactive: Boolean, excludeV1: Boolean, excludeMms: Boolean): Reader {
    val query = getGroupQueryWhereStatement(inputQuery, includeInactive, excludeV1, excludeMms)
    //language=sql
    val statement = """
      $JOINED_GROUP_SELECT
      WHERE ${query.where}
      ORDER BY $TITLE COLLATE NOCASE ASC
    """

    val cursor = databaseHelper.signalReadableDatabase.query(statement, query.whereArgs)
    return Reader(cursor)
  }

  fun queryGroupsByMembership(recipientIds: Set<RecipientId>, includeInactive: Boolean, excludeV1: Boolean, excludeMms: Boolean): Reader {
    var recipientIds = recipientIds
    if (recipientIds.isEmpty()) {
      return Reader(null)
    }

    if (recipientIds.size > 30) {
      Log.w(TAG, "[queryGroupsByMembership] Large set of recipientIds (${recipientIds.size})! Using the first 30.")
      recipientIds = recipientIds.take(30).toSet()
    }

    val membershipQuery = SqlUtil.buildSingleCollectionQuery("${MembershipTable.TABLE_NAME}.${MembershipTable.RECIPIENT_ID}", recipientIds)

    var query: String
    val queryArgs: Array<String>

    if (includeInactive) {
      query = "${membershipQuery.where} AND ($TABLE_NAME.$ACTIVE = ? OR $TABLE_NAME.$RECIPIENT_ID IN (SELECT ${ThreadTable.RECIPIENT_ID} FROM ${ThreadTable.TABLE_NAME} WHERE ${ThreadTable.TABLE_NAME}.${ThreadTable.ACTIVE} = 1))"
      queryArgs = membershipQuery.whereArgs + buildArgs(1)
    } else {
      query = "${membershipQuery.where} AND $TABLE_NAME.$ACTIVE = ?"
      queryArgs = membershipQuery.whereArgs + buildArgs(1)
    }

    if (excludeV1) {
      query += " AND $EXPECTED_V2_ID IS NULL"
    }

    if (excludeMms) {
      query += " AND $MMS = 0"
    }

    val selection = """
      SELECT DISTINCT
                $TABLE_NAME.*, 
                (
                    SELECT GROUP_CONCAT(${MembershipTable.TABLE_NAME}.${MembershipTable.RECIPIENT_ID})
                    FROM ${MembershipTable.TABLE_NAME} 
                    WHERE ${MembershipTable.TABLE_NAME}.${MembershipTable.GROUP_ID} = $TABLE_NAME.$GROUP_ID
                ) as $MEMBER_GROUP_CONCAT
      FROM ${MembershipTable.TABLE_NAME}
      INNER JOIN $TABLE_NAME ON ${MembershipTable.TABLE_NAME}.${MembershipTable.GROUP_ID} = $TABLE_NAME.$GROUP_ID
      WHERE $query
    """

    return Reader(readableDatabase.query(selection, queryArgs))
  }

  private fun queryGroupsByRecency(groupQuery: GroupQuery): Reader {
    val query = getGroupQueryWhereStatement(groupQuery.searchQuery, groupQuery.includeInactive, !groupQuery.includeV1, !groupQuery.includeMms)
    val sql = """
      $JOINED_GROUP_SELECT
      INNER JOIN ${ThreadTable.TABLE_NAME} ON ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} = $TABLE_NAME.$RECIPIENT_ID
      WHERE ${query.where} 
      ORDER BY ${ThreadTable.TABLE_NAME}.${ThreadTable.DATE} DESC
    """

    return Reader(databaseHelper.signalReadableDatabase.rawQuery(sql, query.whereArgs))
  }

  fun queryGroups(groupQuery: GroupQuery): Reader {
    return if (groupQuery.sortOrder === ContactSearchSortOrder.NATURAL) {
      queryGroupsByTitle(groupQuery.searchQuery, groupQuery.includeInactive, !groupQuery.includeV1, !groupQuery.includeMms)
    } else {
      queryGroupsByRecency(groupQuery)
    }
  }

  private fun getGroupQueryWhereStatement(inputQuery: String, includeInactive: Boolean, excludeV1: Boolean, excludeMms: Boolean): SqlUtil.Query {
    var query: String
    val queryArgs: Array<String>
    val caseInsensitiveQuery = buildCaseInsensitiveGlobPattern(inputQuery)

    if (includeInactive) {
      query = "$TITLE GLOB ? AND ($TABLE_NAME.$ACTIVE = ? OR $TABLE_NAME.$RECIPIENT_ID IN (SELECT ${ThreadTable.RECIPIENT_ID} FROM ${ThreadTable.TABLE_NAME} WHERE ${ThreadTable.TABLE_NAME}.${ThreadTable.ACTIVE} = 1))"
      queryArgs = buildArgs(caseInsensitiveQuery, 1)
    } else {
      query = "$TITLE GLOB ? AND $TABLE_NAME.$ACTIVE = ?"
      queryArgs = buildArgs(caseInsensitiveQuery, 1)
    }

    if (excludeV1) {
      query += " AND $EXPECTED_V2_ID IS NULL"
    }

    if (excludeMms) {
      query += " AND $MMS = 0"
    }

    return SqlUtil.Query(query, queryArgs)
  }

  fun getOrCreateDistributionId(groupId: GroupId.V2): DistributionId {
    readableDatabase
      .select(DISTRIBUTION_ID)
      .from(TABLE_NAME)
      .where("$GROUP_ID = ?", groupId)
      .run()
      .use { cursor ->
        return if (cursor.moveToFirst()) {
          val serialized = cursor.optionalString(DISTRIBUTION_ID)
          if (serialized.isPresent) {
            DistributionId.from(serialized.get())
          } else {
            Log.w(TAG, "Missing distributionId! Creating one.")
            val distributionId = DistributionId.create()

            val count = writableDatabase
              .update(TABLE_NAME)
              .values(DISTRIBUTION_ID to distributionId.toString())
              .where("$GROUP_ID = ?", groupId)
              .run()

            check(count >= 1) { "Tried to create a distributionId for $groupId, but it doesn't exist!" }

            distributionId
          }
        } else {
          throw IllegalStateException("Group $groupId doesn't exist!")
        }
      }
  }

  fun getOrCreateMmsGroupForMembers(members: Set<RecipientId>): GroupId.Mms {
    val joinedTestMembers = members
      .toList()
      .map { it.toLong() }
      .sorted()
      .joinToString(separator = ",")

    //language=sql
    val statement = """
      SELECT 
        $TABLE_NAME.$GROUP_ID as gid,
        (
            SELECT GROUP_CONCAT(${MembershipTable.RECIPIENT_ID}, ',')
            FROM (
              SELECT ${MembershipTable.TABLE_NAME}.${MembershipTable.RECIPIENT_ID}
              FROM ${MembershipTable.TABLE_NAME}
              WHERE ${MembershipTable.TABLE_NAME}.${MembershipTable.GROUP_ID} = $TABLE_NAME.$GROUP_ID
              ORDER BY ${MembershipTable.TABLE_NAME}.${MembershipTable.RECIPIENT_ID} ASC
            )
        ) as $MEMBER_GROUP_CONCAT
        FROM $TABLE_NAME
        WHERE $MEMBER_GROUP_CONCAT = ?
    """

    return readableDatabase.rawQuery(statement, buildArgs(joinedTestMembers)).use { cursor ->
      if (cursor.moveToNext()) {
        return GroupId.parseOrThrow(cursor.requireNonNullString("gid")).requireMms()
      } else {
        val groupId = GroupId.createMms(SecureRandom())
        create(groupId, null, members)
        groupId
      }
    }
  }

  @WorkerThread
  fun getPushGroupNamesContainingMember(recipientId: RecipientId): List<String> {
    return getPushGroupsContainingMember(recipientId)
      .map { groupRecord -> Recipient.resolved(groupRecord.recipientId).getDisplayName(context) }
      .toList()
  }

  @WorkerThread
  fun getPushGroupsContainingMember(recipientId: RecipientId): List<GroupRecord> {
    return getGroupsContainingMember(recipientId, true)
  }

  fun getGroupsContainingMember(recipientId: RecipientId, pushOnly: Boolean): List<GroupRecord> {
    return getGroupsContainingMember(recipientId, pushOnly, false)
  }

  @WorkerThread
  fun getGroupsContainingMember(recipientId: RecipientId, pushOnly: Boolean, includeInactive: Boolean): List<GroupRecord> {
    //language=sql
    val table = """
      SELECT 
        DISTINCT $TABLE_NAME.*, 
        (
          SELECT GROUP_CONCAT(${MembershipTable.TABLE_NAME}.${MembershipTable.RECIPIENT_ID})
          FROM ${MembershipTable.TABLE_NAME} 
          WHERE ${MembershipTable.TABLE_NAME}.${MembershipTable.GROUP_ID} = $TABLE_NAME.$GROUP_ID
        ) as $MEMBER_GROUP_CONCAT
      FROM ${MembershipTable.TABLE_NAME}
      INNER JOIN $TABLE_NAME ON ${MembershipTable.TABLE_NAME}.${MembershipTable.GROUP_ID} = $TABLE_NAME.$GROUP_ID
      LEFT JOIN ${ThreadTable.TABLE_NAME} ON $TABLE_NAME.$RECIPIENT_ID = ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID}
    """

    var query = "${MembershipTable.TABLE_NAME}.${MembershipTable.RECIPIENT_ID} = ?"
    var args = buildArgs(recipientId)
    val orderBy = "${ThreadTable.TABLE_NAME}.${ThreadTable.DATE} DESC"

    if (pushOnly) {
      query += " AND $MMS = ?"
      args = appendArg(args, "0")
    }

    if (!includeInactive) {
      query += " AND $TABLE_NAME.$ACTIVE = ?"
      args = appendArg(args, "1")
    }

    return readableDatabase
      .query("$table WHERE $query ORDER BY $orderBy", args)
      .readToList { cursor ->
        getGroup(cursor).get()
      }
  }

  fun getGroups(): Reader {
    val cursor = readableDatabase.query(JOINED_GROUP_SELECT)
    return Reader(cursor)
  }

  fun getActiveGroupCount(): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$ACTIVE = ?", 1)
      .run()
      .readToSingleInt(0)
  }

  @WorkerThread
  fun getGroupMemberIds(groupId: GroupId, memberSet: MemberSet): List<RecipientId> {
    return if (groupId.isV2) {
      getGroup(groupId)
        .map { it.requireV2GroupProperties().getMemberRecipientIds(memberSet) }
        .orElse(emptyList())
    } else {
      val currentMembers: MutableList<RecipientId> = getCurrentMembers(groupId)
      if (!memberSet.includeSelf) {
        currentMembers -= Recipient.self().id
      }
      currentMembers
    }
  }

  @WorkerThread
  fun getGroupMembers(groupId: GroupId, memberSet: MemberSet): List<Recipient> {
    return if (groupId.isV2) {
      getGroup(groupId)
        .map { it.requireV2GroupProperties().getMemberRecipients(memberSet) }
        .orElse(emptyList())
    } else {
      val currentMembers: List<RecipientId> = getCurrentMembers(groupId)
      val recipients: MutableList<Recipient> = ArrayList(currentMembers.size)

      for (member in currentMembers) {
        val resolved = Recipient.resolved(member)
        if (memberSet.includeSelf || !resolved.isSelf) {
          recipients += resolved
        }
      }

      recipients
    }
  }

  @CheckReturnValue
  fun create(groupId: GroupId.V1, title: String?, members: Collection<RecipientId>, avatar: SignalServiceAttachmentPointer?, relay: String?): Boolean {
    if (groupExists(groupId.deriveV2MigrationGroupId())) {
      throw LegacyGroupInsertException(groupId)
    }

    return create(groupId, title, members, avatar, relay, null, null)
  }

  @CheckReturnValue
  fun create(groupId: GroupId.Mms, title: String?, members: Collection<RecipientId>): Boolean {
    return create(groupId, if (title.isNullOrEmpty()) null else title, members, null, null, null, null)
  }

  @JvmOverloads
  @CheckReturnValue
  fun create(groupMasterKey: GroupMasterKey, groupState: DecryptedGroup, force: Boolean = false): GroupId.V2? {
    val groupId = GroupId.v2(groupMasterKey)

    if (!force && GroupsV1MigratedCache.hasV1Group(groupId)) {
      throw MissedGroupMigrationInsertException(groupId)
    } else if (force) {
      Log.w(TAG, "Forcing the creation of a group even though we already have a V1 ID!")
    }

    return if (create(groupId = groupId, title = groupState.title, memberCollection = emptyList(), avatar = null, relay = null, groupMasterKey = groupMasterKey, groupState = groupState)) {
      groupId
    } else {
      null
    }
  }

  /**
   * There was a point in time where we weren't properly responding to group creates on linked devices. This would result in us having a Recipient entry for the
   * group, but we'd either be missing the group entry, or that entry would be missing a master key. This method fixes this scenario.
   */
  fun fixMissingMasterKey(groupMasterKey: GroupMasterKey) {
    val groupId = GroupId.v2(groupMasterKey)
    if (GroupsV1MigratedCache.hasV1Group(groupId)) {
      Log.w(TAG, "There already exists a V1 group that should be migrated into this group. But if the recipient already exists, there's not much we can do here.")
    }

    writableDatabase.withinTransaction { db ->
      val updated = db
        .update(TABLE_NAME)
        .values(V2_MASTER_KEY to groupMasterKey.serialize())
        .where("$GROUP_ID = ?", groupId)
        .run()

      if (updated < 1) {
        Log.w(TAG, "No group entry. Creating restore placeholder for $groupId")
        create(
          groupMasterKey,
          DecryptedGroup.newBuilder()
            .setRevision(GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION)
            .build(),
          true
        )
      } else {
        Log.w(TAG, "Had a group entry, but it was missing a master key. Updated.")
      }
    }

    Log.w(TAG, "Scheduling request for latest group info for $groupId")
    ApplicationDependencies.getJobManager().add(RequestGroupV2InfoJob(groupId))
  }

  /**
   * @param groupMasterKey null for V1, must be non-null for V2 (presence dictates group version).
   */
  @CheckReturnValue
  private fun create(
    groupId: GroupId,
    title: String?,
    memberCollection: Collection<RecipientId>,
    avatar: SignalServiceAttachmentPointer?,
    relay: String?,
    groupMasterKey: GroupMasterKey?,
    groupState: DecryptedGroup?
  ): Boolean {
    val membershipValues = mutableListOf<ContentValues>()
    val groupRecipientId = recipients.getOrInsertFromGroupId(groupId)
    val members: List<RecipientId> = memberCollection.toSet().sorted()
    var groupMembers: List<RecipientId> = members

    val values = ContentValues()

    values.put(RECIPIENT_ID, groupRecipientId.serialize())
    values.put(GROUP_ID, groupId.toString())
    values.put(TITLE, title)
    membershipValues.addAll(members.toContentValues(groupId))
    values.put(MMS, groupId.isMms)

    if (avatar != null) {
      values.put(AVATAR_ID, avatar.remoteId.v2.get())
      values.put(AVATAR_KEY, avatar.key)
      values.put(AVATAR_CONTENT_TYPE, avatar.contentType)
      values.put(AVATAR_DIGEST, avatar.digest.orElse(null))
    } else {
      values.put(AVATAR_ID, 0)
    }

    values.put(AVATAR_RELAY, relay)
    values.put(TIMESTAMP, System.currentTimeMillis())

    if (groupId.isV2) {
      values.put(ACTIVE, if (groupState != null && gv2GroupActive(groupState)) 1 else 0)
      values.put(DISTRIBUTION_ID, DistributionId.create().toString())
    } else if (groupId.isV1) {
      values.put(ACTIVE, 1)
      values.put(EXPECTED_V2_ID, groupId.requireV1().deriveV2MigrationGroupId().toString())
    } else {
      values.put(ACTIVE, 1)
    }

    if (groupMasterKey != null) {
      if (groupState == null) {
        throw AssertionError("V2 master key but no group state")
      }

      groupId.requireV2()
      groupMembers = getV2GroupMembers(groupState, true)

      values.put(V2_MASTER_KEY, groupMasterKey.serialize())
      values.put(V2_REVISION, groupState.revision)
      values.put(V2_DECRYPTED_GROUP, groupState.toByteArray())
      membershipValues.clear()
      membershipValues.addAll(groupMembers.toContentValues(groupId))
    } else {
      if (groupId.isV2) {
        throw AssertionError("V2 group id but no master key")
      }
    }

    writableDatabase.beginTransaction()
    try {
      val result: Long = writableDatabase.insert(TABLE_NAME, null, values)
      if (result < 1) {
        Log.w(TAG, "Unable to create group, group record already exists")
        return false
      }

      for (query in SqlUtil.buildBulkInsert(MembershipTable.TABLE_NAME, arrayOf(MembershipTable.GROUP_ID, MembershipTable.RECIPIENT_ID), membershipValues)) {
        writableDatabase.execSQL(query.where, query.whereArgs)
      }
      writableDatabase.setTransactionSuccessful()
    } finally {
      writableDatabase.endTransaction()
    }

    if (groupState != null && groupState.hasDisappearingMessagesTimer()) {
      recipients.setExpireMessages(groupRecipientId, groupState.disappearingMessagesTimer.duration)
    }

    if (groupId.isMms || Recipient.resolved(groupRecipientId).isProfileSharing) {
      recipients.setHasGroupsInCommon(groupMembers)
    }

    Recipient.live(groupRecipientId).refresh()
    notifyConversationListListeners()

    return true
  }

  fun update(groupId: GroupId.V1, title: String?, avatar: SignalServiceAttachmentPointer?) {
    val contentValues = ContentValues().apply {
      if (title != null) {
        put(TITLE, title)
      }

      if (avatar != null) {
        put(AVATAR_ID, avatar.remoteId.v2.get())
        put(AVATAR_CONTENT_TYPE, avatar.contentType)
        put(AVATAR_KEY, avatar.key)
        put(AVATAR_DIGEST, avatar.digest.orElse(null))
      } else {
        put(AVATAR_ID, 0)
      }
    }

    writableDatabase
      .update(TABLE_NAME)
      .values(contentValues)
      .where("$GROUP_ID = ?", groupId)
      .run()

    val groupRecipient = recipients.getOrInsertFromGroupId(groupId)

    Recipient.live(groupRecipient).refresh()
    notifyConversationListListeners()
  }

  /**
   * Migrates a V1 group to a V2 group.
   *
   * @param decryptedGroup The state that represents the group on the server. This will be used to
   * determine if we need to save our old membership list and stuff.
   */
  fun migrateToV2(
    threadId: Long,
    groupIdV1: GroupId.V1,
    decryptedGroup: DecryptedGroup
  ): GroupId.V2 {
    val groupIdV2 = groupIdV1.deriveV2MigrationGroupId()
    val groupMasterKey = groupIdV1.deriveV2MigrationMasterKey()

    writableDatabase.withinTransaction { db ->
      val record = getGroup(groupIdV1).get()

      val newMembers: MutableList<RecipientId> = DecryptedGroupUtil.membersToServiceIdList(decryptedGroup.membersList).toRecipientIds()
      val pendingMembers: List<RecipientId> = DecryptedGroupUtil.pendingToServiceIdList(decryptedGroup.pendingMembersList).toRecipientIds()
      newMembers.addAll(pendingMembers)

      val droppedMembers: List<RecipientId> = SetUtil.difference(record.members, newMembers).toList()
      val unmigratedMembers: List<RecipientId> = pendingMembers + droppedMembers

      val updated: Int = db.update(TABLE_NAME)
        .values(
          GROUP_ID to groupIdV2.toString(),
          V2_MASTER_KEY to groupMasterKey.serialize(),
          DISTRIBUTION_ID to DistributionId.create().toString(),
          EXPECTED_V2_ID to null,
          UNMIGRATED_V1_MEMBERS to if (unmigratedMembers.isEmpty()) null else unmigratedMembers.serialize()
        )
        .where("$GROUP_ID = ?", groupIdV1)
        .run()

      if (updated != 1) {
        throw AssertionError()
      }

      recipients.updateGroupId(groupIdV1, groupIdV2)
      update(groupMasterKey, decryptedGroup)
      messages.insertGroupV1MigrationEvents(
        record.recipientId,
        threadId,
        GroupMigrationMembershipChange(pendingMembers, droppedMembers)
      )
    }

    return groupIdV2
  }

  fun update(groupMasterKey: GroupMasterKey, decryptedGroup: DecryptedGroup) {
    update(GroupId.v2(groupMasterKey), decryptedGroup)
  }

  fun update(groupId: GroupId.V2, decryptedGroup: DecryptedGroup) {
    val groupRecipientId: RecipientId = recipients.getOrInsertFromGroupId(groupId)
    val existingGroup: Optional<GroupRecord> = getGroup(groupId)
    val title: String = decryptedGroup.title

    val contentValues = ContentValues()
    contentValues.put(TITLE, title)
    contentValues.put(V2_REVISION, decryptedGroup.revision)
    contentValues.put(V2_DECRYPTED_GROUP, decryptedGroup.toByteArray())
    contentValues.put(ACTIVE, if (gv2GroupActive(decryptedGroup)) 1 else 0)

    if (existingGroup.isPresent && existingGroup.get().unmigratedV1Members.isNotEmpty() && existingGroup.get().isV2Group) {
      val unmigratedV1Members: MutableSet<RecipientId> = existingGroup.get().unmigratedV1Members.toMutableSet()

      val change = GroupChangeReconstruct.reconstructGroupChange(existingGroup.get().requireV2GroupProperties().decryptedGroup, decryptedGroup)

      val addedMembers: Set<RecipientId> = DecryptedGroupUtil.membersToServiceIdList(change.newMembersList).toRecipientIds().toSet()
      val removedMembers: Set<RecipientId> = DecryptedGroupUtil.removedMembersServiceIdList(change).toRecipientIds().toSet()
      val addedInvites: Set<RecipientId> = DecryptedGroupUtil.pendingToServiceIdList(change.newPendingMembersList).toRecipientIds().toSet()
      val removedInvites: Set<RecipientId> = DecryptedGroupUtil.removedPendingMembersServiceIdList(change).toRecipientIds().toSet()
      val acceptedInvites: Set<RecipientId> = DecryptedGroupUtil.membersToServiceIdList(change.promotePendingMembersList).toRecipientIds().toSet()

      unmigratedV1Members -= addedMembers
      unmigratedV1Members -= removedMembers
      unmigratedV1Members -= addedInvites
      unmigratedV1Members -= removedInvites
      unmigratedV1Members -= acceptedInvites

      contentValues.put(UNMIGRATED_V1_MEMBERS, if (unmigratedV1Members.isEmpty()) null else unmigratedV1Members.serialize())
    }

    val groupMembers = getV2GroupMembers(decryptedGroup, true)

    if (existingGroup.isPresent && existingGroup.get().isV2Group) {
      val change = GroupChangeReconstruct.reconstructGroupChange(existingGroup.get().requireV2GroupProperties().decryptedGroup, decryptedGroup)
      val removed: List<ServiceId> = DecryptedGroupUtil.removedMembersServiceIdList(change)

      if (removed.isNotEmpty()) {
        val distributionId = existingGroup.get().distributionId!!
        Log.i(TAG, removed.size.toString() + " members were removed from group " + groupId + ". Rotating the DistributionId " + distributionId)
        SenderKeyUtil.rotateOurKey(distributionId)
      }
    }

    writableDatabase.withinTransaction { database ->
      database
        .update(TABLE_NAME)
        .values(contentValues)
        .where("$GROUP_ID = ?", groupId.toString())
        .run()

      performMembershipUpdate(database, groupId, groupMembers)
    }

    if (decryptedGroup.hasDisappearingMessagesTimer()) {
      recipients.setExpireMessages(groupRecipientId, decryptedGroup.disappearingMessagesTimer.duration)
    }

    if (groupId.isMms || Recipient.resolved(groupRecipientId).isProfileSharing) {
      recipients.setHasGroupsInCommon(groupMembers)
    }

    Recipient.live(groupRecipientId).refresh()
    notifyConversationListListeners()
  }

  fun updateTitle(groupId: GroupId.V1, title: String?) {
    updateTitle(groupId as GroupId, title)
  }

  fun updateTitle(groupId: GroupId.Mms, title: String?) {
    updateTitle(groupId as GroupId, if (title.isNullOrEmpty()) null else title)
  }

  private fun updateTitle(groupId: GroupId, title: String?) {
    if (!groupId.isV1 && !groupId.isMms) {
      throw AssertionError()
    }

    writableDatabase
      .update(TABLE_NAME)
      .values(TITLE to title)
      .where("$GROUP_ID = ?", groupId)
      .run()

    val groupRecipient = recipients.getOrInsertFromGroupId(groupId)
    Recipient.live(groupRecipient).refresh()
  }

  /**
   * Used to bust the Glide cache when an avatar changes.
   */
  fun onAvatarUpdated(groupId: GroupId, hasAvatar: Boolean) {
    writableDatabase
      .update(TABLE_NAME)
      .values(AVATAR_ID to if (hasAvatar) Math.abs(SecureRandom().nextLong()) else 0)
      .where("$GROUP_ID = ?", groupId)
      .run()

    val groupRecipient = recipients.getOrInsertFromGroupId(groupId)
    Recipient.live(groupRecipient).refresh()
  }

  fun updateMembers(groupId: GroupId, members: List<RecipientId>) {
    writableDatabase.withinTransaction { database ->
      database
        .update(TABLE_NAME)
        .values(ACTIVE to 1)
        .where("$GROUP_ID = ?", groupId)
        .run()

      performMembershipUpdate(database, groupId, members)
    }

    val groupRecipient = recipients.getOrInsertFromGroupId(groupId)
    Recipient.live(groupRecipient).refresh()
  }

  fun remove(groupId: GroupId, source: RecipientId) {
    writableDatabase
      .delete(MembershipTable.TABLE_NAME)
      .where("${MembershipTable.GROUP_ID} = ? AND ${MembershipTable.RECIPIENT_ID} = ?", groupId, source)
      .run()

    val groupRecipient = recipients.getOrInsertFromGroupId(groupId)
    Recipient.live(groupRecipient).refresh()
  }

  private fun getCurrentMembers(groupId: GroupId): MutableList<RecipientId> {
    return readableDatabase
      .select(MembershipTable.RECIPIENT_ID)
      .from(MembershipTable.TABLE_NAME)
      .where("${MembershipTable.GROUP_ID} = ?", groupId)
      .run()
      .readToList { cursor ->
        RecipientId.from(cursor.requireLong(MembershipTable.RECIPIENT_ID))
      }
      .toMutableList()
  }

  private fun performMembershipUpdate(database: SQLiteDatabase, groupId: GroupId, members: Collection<RecipientId>) {
    check(database.inTransaction())
    database
      .delete(MembershipTable.TABLE_NAME)
      .where("${MembershipTable.GROUP_ID} = ?", groupId)
      .run()

    val inserts = SqlUtil.buildBulkInsert(
      MembershipTable.TABLE_NAME,
      arrayOf(MembershipTable.GROUP_ID, MembershipTable.RECIPIENT_ID),
      members.toContentValues(groupId)
    )

    inserts.forEach {
      database.execSQL(it.where, it.whereArgs)
    }
  }

  fun isActive(groupId: GroupId): Boolean {
    val record = getGroup(groupId)
    return record.isPresent && record.get().isActive
  }

  fun setActive(groupId: GroupId, active: Boolean) {
    writableDatabase
      .update(TABLE_NAME)
      .values(ACTIVE to if (active) 1 else 0)
      .where("$GROUP_ID = ?", groupId)
      .run()
  }

  fun setLastForceUpdateTimestamp(groupId: GroupId, timestamp: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(LAST_FORCE_UPDATE_TIMESTAMP to timestamp)
      .where("$GROUP_ID = ?", groupId)
      .run()
  }

  @WorkerThread
  fun isCurrentMember(groupId: Push, recipientId: RecipientId): Boolean {
    return readableDatabase
      .exists(MembershipTable.TABLE_NAME)
      .where("${MembershipTable.GROUP_ID} = ? AND ${MembershipTable.RECIPIENT_ID} = ?", groupId, recipientId)
      .run()
  }

  fun getAllGroupV2Ids(): List<GroupId.V2> {
    return readableDatabase
      .select(GROUP_ID)
      .from(TABLE_NAME)
      .run()
      .readToList { GroupId.parseOrThrow(it.requireNonNullString(GROUP_ID)) }
      .filter { it.isV2 }
      .map { it.requireV2() }
  }

  /**
   * Key: The 'expected' V2 ID (i.e. what a V1 ID would map to when migrated)
   * Value: The matching V1 ID
   */
  fun getAllExpectedV2Ids(): Map<GroupId.V2, GroupId.V1> {
    return readableDatabase
      .select(GROUP_ID, EXPECTED_V2_ID)
      .from(TABLE_NAME)
      .where("$EXPECTED_V2_ID NOT NULL")
      .run()
      .readToList { cursor ->
        val groupId = GroupId.parseOrThrow(cursor.requireNonNullString(GROUP_ID)).requireV1()
        val expectedId = GroupId.parseOrThrow(cursor.requireNonNullString(EXPECTED_V2_ID)).requireV2()
        expectedId to groupId
      }
      .toMap()
  }

  override fun remapRecipient(fromId: RecipientId, toId: RecipientId) {
    // Remap all recipients that would not result in conflicts
    writableDatabase.execSQL(
      """
        UPDATE ${MembershipTable.TABLE_NAME} AS parent
        SET ${MembershipTable.RECIPIENT_ID} = ?
        WHERE
          ${MembershipTable.RECIPIENT_ID} = ?
          AND NOT EXISTS (
            SELECT 1
            FROM ${MembershipTable.TABLE_NAME} child
            WHERE 
              child.${MembershipTable.RECIPIENT_ID} = ?
              AND parent.${MembershipTable.GROUP_ID} = child.${MembershipTable.GROUP_ID}
          )
      """,
      buildArgs(toId, fromId, toId)
    )

    // Delete the remaining fromId's (the only remaining ones should be those in groups where the toId is already present)
    writableDatabase
      .delete(MembershipTable.TABLE_NAME)
      .where("${MembershipTable.RECIPIENT_ID} = ?", fromId)
      .run()

    for (group in getGroupsContainingMember(fromId, pushOnly = false, includeInactive = true)) {
      if (group.isV2Group) {
        removeUnmigratedV1Members(group.id.requireV2(), listOf(fromId))
      }
    }
  }

  class Reader(val cursor: Cursor?) : Closeable, ContactSearchIterator<GroupRecord> {

    fun getNext(): GroupRecord? {
      return if (cursor == null || !cursor.moveToNext()) {
        null
      } else {
        getCurrent()
      }
    }

    override fun getCount(): Int {
      return cursor?.count ?: 0
    }

    fun getCurrent(): GroupRecord? {
      return if (cursor == null || cursor.requireString(GROUP_ID) == null || cursor.requireLong(RECIPIENT_ID) == 0L) {
        null
      } else {
        GroupRecord(
          id = GroupId.parseOrThrow(cursor.requireNonNullString(GROUP_ID)),
          recipientId = RecipientId.from(cursor.requireNonNullString(RECIPIENT_ID)),
          title = cursor.requireString(TITLE),
          serializedMembers = cursor.requireString(MEMBER_GROUP_CONCAT),
          serializedUnmigratedV1Members = cursor.requireString(UNMIGRATED_V1_MEMBERS),
          avatarId = cursor.requireLong(AVATAR_ID),
          avatarKey = cursor.requireBlob(AVATAR_KEY),
          avatarContentType = cursor.requireString(AVATAR_CONTENT_TYPE),
          relay = cursor.requireString(AVATAR_RELAY),
          isActive = cursor.requireBoolean(ACTIVE),
          avatarDigest = cursor.requireBlob(AVATAR_DIGEST),
          isMms = cursor.requireBoolean(MMS),
          groupMasterKeyBytes = cursor.requireBlob(V2_MASTER_KEY),
          groupRevision = cursor.requireInt(V2_REVISION),
          decryptedGroupBytes = cursor.requireBlob(V2_DECRYPTED_GROUP),
          distributionId = cursor.optionalString(DISTRIBUTION_ID).map { id -> DistributionId.from(id) }.orElse(null),
          lastForceUpdateTimestamp = cursor.requireLong(LAST_FORCE_UPDATE_TIMESTAMP)
        )
      }
    }

    override fun close() {
      cursor?.close()
    }

    override fun moveToPosition(n: Int) {
      cursor!!.moveToPosition(n)
    }

    override fun hasNext(): Boolean {
      return cursor != null && !cursor.isLast && !cursor.isAfterLast
    }

    override fun next(): GroupRecord {
      return getNext()!!
    }
  }

  class V2GroupProperties(val groupMasterKey: GroupMasterKey, val groupRevision: Int, val decryptedGroupBytes: ByteArray) {
    val decryptedGroup: DecryptedGroup by lazy {
      DecryptedGroup.parseFrom(decryptedGroupBytes)
    }

    val bannedMembers: Set<ServiceId> by lazy {
      DecryptedGroupUtil.bannedMembersToServiceIdSet(decryptedGroup.bannedMembersList)
    }

    fun isAdmin(recipient: Recipient): Boolean {
      val serviceId = recipient.serviceId

      return if (serviceId.isPresent) {
        DecryptedGroupUtil.findMemberByUuid(decryptedGroup.membersList, serviceId.get().rawUuid)
          .map { it.role == Member.Role.ADMINISTRATOR }
          .orElse(false)
      } else {
        false
      }
    }

    fun getAdmins(members: List<Recipient>): List<Recipient> {
      return members.stream().filter { recipient: Recipient -> isAdmin(recipient) }.collect(Collectors.toList())
    }

    fun memberLevel(serviceId: Optional<ServiceId>): MemberLevel {
      if (!serviceId.isPresent) {
        return MemberLevel.NOT_A_MEMBER
      }

      var memberLevel: Optional<MemberLevel> = DecryptedGroupUtil.findMemberByUuid(decryptedGroup.membersList, serviceId.get().rawUuid)
        .map { member ->
          if (member.role == Member.Role.ADMINISTRATOR) {
            MemberLevel.ADMINISTRATOR
          } else {
            MemberLevel.FULL_MEMBER
          }
        }

      if (memberLevel.isAbsent()) {
        memberLevel = DecryptedGroupUtil.findPendingByServiceId(decryptedGroup.pendingMembersList, serviceId.get())
          .map { MemberLevel.PENDING_MEMBER }
      }

      if (memberLevel.isAbsent()) {
        memberLevel = DecryptedGroupUtil.findRequestingByUuid(decryptedGroup.requestingMembersList, serviceId.get().rawUuid)
          .map { _ -> MemberLevel.REQUESTING_MEMBER }
      }

      return if (memberLevel.isPresent) {
        memberLevel.get()
      } else {
        MemberLevel.NOT_A_MEMBER
      }
    }

    fun getMemberRecipients(memberSet: MemberSet): List<Recipient> {
      return Recipient.resolvedList(getMemberRecipientIds(memberSet))
    }

    fun getMemberRecipientIds(memberSet: MemberSet): List<RecipientId> {
      val includeSelf = memberSet.includeSelf
      val selfAci = SignalStore.account().requireAci()
      val selfAciUuid = selfAci.rawUuid
      val recipients: MutableList<RecipientId> = ArrayList(decryptedGroup.membersCount + decryptedGroup.pendingMembersCount)

      var unknownMembers = 0
      var unknownPending = 0

      for (uuid in DecryptedGroupUtil.toUuidList(decryptedGroup.membersList)) {
        if (UuidUtil.UNKNOWN_UUID == uuid) {
          unknownMembers++
        } else if (includeSelf || selfAciUuid != uuid) {
          recipients += RecipientId.from(ACI.from(uuid))
        }
      }

      if (memberSet.includePending) {
        for (serviceId in DecryptedGroupUtil.pendingToServiceIdList(decryptedGroup.pendingMembersList)) {
          if (serviceId.isUnknown) {
            unknownPending++
          } else if (includeSelf || selfAci != serviceId) {
            recipients += RecipientId.from(serviceId)
          }
        }
      }

      if (unknownMembers + unknownPending > 0) {
        Log.w(TAG, "Group contains $unknownPending unknown pending and $unknownMembers unknown full members")
      }

      return recipients
    }

    fun getMemberServiceIds(): List<ServiceId> {
      return decryptedGroup
        .membersList
        .asSequence()
        .map { UuidUtil.fromByteStringOrNull(it.uuid) }
        .filterNotNull()
        .map { ACI.from(it) }
        .sortedBy { it.toString() }
        .toList()
    }
  }

  @Throws(BadGroupIdException::class)
  fun getGroupsToDisplayAsStories(): List<GroupId> {
    @Language("sql")
    val query = """
      SELECT 
        $GROUP_ID, 
        (
          SELECT ${MessageTable.TABLE_NAME}.${MessageTable.DATE_RECEIVED} 
          FROM ${MessageTable.TABLE_NAME} 
          WHERE 
           ${MessageTable.TABLE_NAME}.${MessageTable.FROM_RECIPIENT_ID} = ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} AND 
           ${MessageTable.STORY_TYPE} > 1 
          ORDER BY ${MessageTable.TABLE_NAME}.${MessageTable.DATE_RECEIVED} DESC 
          LIMIT 1
        ) AS active_timestamp 
      FROM $TABLE_NAME INNER JOIN ${ThreadTable.TABLE_NAME} ON ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} = $TABLE_NAME.$RECIPIENT_ID 
      WHERE 
        $TABLE_NAME.$ACTIVE = 1 AND 
        (
          $SHOW_AS_STORY_STATE = ${ShowAsStoryState.ALWAYS.code} OR 
          (
            $SHOW_AS_STORY_STATE = ${ShowAsStoryState.IF_ACTIVE.code} AND 
            active_timestamp IS NOT NULL
          )
        ) 
      ORDER BY active_timestamp DESC
    """

    return readableDatabase
      .query(query)
      .readToList { cursor ->
        GroupId.parse(cursor.requireNonNullString(GROUP_ID))
      }
  }

  fun getShowAsStoryState(groupId: GroupId): ShowAsStoryState {
    return readableDatabase
      .select(SHOW_AS_STORY_STATE)
      .from(TABLE_NAME)
      .where("$GROUP_ID = ?", groupId)
      .run()
      .readToSingleObject { cursor ->
        val serializedState = cursor.requireInt(SHOW_AS_STORY_STATE)
        ShowAsStoryState.deserialize(serializedState)
      } ?: throw AssertionError("Group $groupId does not exist!")
  }

  fun setShowAsStoryState(groupId: GroupId, showAsStoryState: ShowAsStoryState) {
    writableDatabase
      .update(TABLE_NAME)
      .values(SHOW_AS_STORY_STATE to showAsStoryState.code)
      .where("$GROUP_ID = ?", groupId)
      .run()
  }

  fun setShowAsStoryState(recipientIds: Collection<RecipientId?>, showAsStoryState: ShowAsStoryState) {
    val queries = buildCollectionQuery(RECIPIENT_ID, recipientIds)
    val contentValues = contentValuesOf(SHOW_AS_STORY_STATE to showAsStoryState.code)

    writableDatabase.withinTransaction { db ->
      for (query in queries) {
        db.update(TABLE_NAME, contentValues, query.where, query.whereArgs)
      }
    }
  }

  private fun gv2GroupActive(decryptedGroup: DecryptedGroup): Boolean {
    val aci = SignalStore.account().requireAci()

    return DecryptedGroupUtil.findMemberByUuid(decryptedGroup.membersList, aci.rawUuid).isPresent ||
      DecryptedGroupUtil.findPendingByServiceId(decryptedGroup.pendingMembersList, aci).isPresent
  }

  private fun List<ServiceId>.toRecipientIds(): MutableList<RecipientId> {
    return serviceIdsToRecipientIds(this.asSequence())
  }

  private fun Collection<RecipientId>.serialize(): String {
    return RecipientId.toSerializedList(this)
  }

  private fun Collection<RecipientId>.toContentValues(groupId: GroupId): List<ContentValues> {
    return map {
      contentValuesOf(
        MembershipTable.GROUP_ID to groupId.serialize(),
        MembershipTable.RECIPIENT_ID to it.serialize()
      )
    }
  }

  private fun serviceIdsToRecipientIds(serviceIds: Sequence<ServiceId>): MutableList<RecipientId> {
    return serviceIds
      .map { serviceId ->
        if (serviceId.isUnknown) {
          Log.w(TAG, "Saw an unknown UUID when mapping to RecipientIds!")
          null
        } else {
          val id = RecipientId.from(serviceId)
          val remapped = RemappedRecords.getInstance().getRecipient(id)
          if (remapped.isPresent) {
            Log.w(TAG, "Saw that $id remapped to $remapped. Using the mapping.")
            remapped.get()
          } else {
            id
          }
        }
      }
      .filterNotNull()
      .sorted()
      .toMutableList()
  }

  private fun getV2GroupMembers(decryptedGroup: DecryptedGroup, shouldRetry: Boolean): List<RecipientId> {
    val ids: List<RecipientId> = DecryptedGroupUtil.membersToServiceIdList(decryptedGroup.membersList).toRecipientIds()

    return if (RemappedRecords.getInstance().areAnyRemapped(ids)) {
      if (shouldRetry) {
        Log.w(TAG, "Found remapped records where we shouldn't. Clearing cache and trying again.")
        RecipientId.clearCache()
        RemappedRecords.getInstance().resetCache()
        getV2GroupMembers(decryptedGroup, false)
      } else {
        throw IllegalStateException("Remapped records in group membership!")
      }
    } else {
      ids
    }
  }

  enum class MemberSet(val includeSelf: Boolean, val includePending: Boolean) {
    FULL_MEMBERS_INCLUDING_SELF(true, false), FULL_MEMBERS_EXCLUDING_SELF(false, false), FULL_MEMBERS_AND_PENDING_INCLUDING_SELF(true, true), FULL_MEMBERS_AND_PENDING_EXCLUDING_SELF(false, true)
  }

  /**
   * State object describing whether or not to display a story in a list.
   */
  enum class ShowAsStoryState(val code: Int) {
    /**
     * The default value. Display the group as a story if the group has stories in it currently.
     */
    IF_ACTIVE(0),

    /**
     * Always display the group as a story unless explicitly removed. This state is entered if the
     * user sends a story to a group or otherwise explicitly selects it to appear.
     */
    ALWAYS(1),

    /**
     * Never display the story as a group. This state is entered if the user removes the group from
     * their list, and is only navigated away from if the user explicitly adds the group again.
     */
    NEVER(2);

    companion object {
      fun deserialize(code: Int): ShowAsStoryState {
        return when (code) {
          0 -> IF_ACTIVE
          1 -> ALWAYS
          2 -> NEVER
          else -> throw IllegalArgumentException("Unknown code: $code")
        }
      }
    }
  }

  enum class MemberLevel(val isInGroup: Boolean) {
    NOT_A_MEMBER(false),
    PENDING_MEMBER(false),
    REQUESTING_MEMBER(false),
    FULL_MEMBER(true),
    ADMINISTRATOR(true)
  }

  class GroupQuery private constructor(builder: Builder) {
    val searchQuery: String
    val includeInactive: Boolean
    val includeV1: Boolean
    val includeMms: Boolean
    val sortOrder: ContactSearchSortOrder

    init {
      searchQuery = builder.searchQuery
      includeInactive = builder.includeInactive
      includeV1 = builder.includeV1
      includeMms = builder.includeMms
      sortOrder = builder.sortOrder
    }

    class Builder {
      var searchQuery = ""
      var includeInactive = false
      var includeV1 = false
      var includeMms = false
      var sortOrder = ContactSearchSortOrder.NATURAL
      fun withSearchQuery(query: String?): Builder {
        searchQuery = if (TextUtils.isEmpty(query)) "" else query!!
        return this
      }

      fun withInactiveGroups(includeInactive: Boolean): Builder {
        this.includeInactive = includeInactive
        return this
      }

      fun withV1Groups(includeV1Groups: Boolean): Builder {
        includeV1 = includeV1Groups
        return this
      }

      fun withMmsGroups(includeMmsGroups: Boolean): Builder {
        includeMms = includeMmsGroups
        return this
      }

      fun withSortOrder(sortOrder: ContactSearchSortOrder): Builder {
        this.sortOrder = sortOrder
        return this
      }

      fun build(): GroupQuery {
        return GroupQuery(this)
      }
    }
  }

  class LegacyGroupInsertException(id: GroupId?) : IllegalStateException("Tried to create a new GV1 entry when we already had a migrated GV2! $id")
  class MissedGroupMigrationInsertException(id: GroupId?) : IllegalStateException("Tried to create a new GV2 entry when we already had a V1 group that mapped to the new ID! $id")
}
