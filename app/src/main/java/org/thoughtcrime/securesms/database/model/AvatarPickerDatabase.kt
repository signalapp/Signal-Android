package org.thoughtcrime.securesms.database.model

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.Avatars
import org.thoughtcrime.securesms.database.DatabaseTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.CustomAvatar
import org.thoughtcrime.securesms.groups.GroupId

/**
 * Database which manages the record keeping for custom created avatars.
 */
class AvatarPickerDatabase(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {

  companion object {
    const val TABLE_NAME = "avatar_picker"
    private const val ID = "_id"
    private const val LAST_USED = "last_used"
    private const val GROUP_ID = "group_id"
    private const val AVATAR = "avatar"

    //language=sql
    @JvmField
    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $LAST_USED INTEGER DEFAULT 0,
        $GROUP_ID TEXT DEFAULT NULL,
        $AVATAR BLOB NOT NULL
      )
    """.trimIndent()
  }

  fun saveAvatarForSelf(avatar: Avatar): Avatar {
    return saveAvatar(avatar, null)
  }

  fun saveAvatarForGroup(avatar: Avatar, groupId: GroupId): Avatar {
    return saveAvatar(avatar, groupId)
  }

  fun markUsage(avatar: Avatar) {
    val databaseId = avatar.databaseId
    if (databaseId !is Avatar.DatabaseId.Saved) {
      throw IllegalArgumentException("Must save this avatar before trying to mark usage.")
    }

    val db = databaseHelper.signalWritableDatabase
    val where = ID_WHERE
    val args = SqlUtil.buildArgs(databaseId.id)
    val values = ContentValues(1)

    values.put(LAST_USED, System.currentTimeMillis())
    db.update(TABLE_NAME, values, where, args)
  }

  fun update(avatar: Avatar) {
    val databaseId = avatar.databaseId
    if (databaseId !is Avatar.DatabaseId.Saved) {
      throw IllegalArgumentException("Cannot update an unsaved avatar")
    }

    val db = databaseHelper.signalWritableDatabase
    val where = ID_WHERE
    val values = ContentValues(1)

    values.put(AVATAR, avatar.toProto().toByteArray())
    db.update(TABLE_NAME, values, where, SqlUtil.buildArgs(databaseId.id))
  }

  fun deleteAvatar(avatar: Avatar) {
    val databaseId = avatar.databaseId
    if (databaseId !is Avatar.DatabaseId.Saved) {
      throw IllegalArgumentException("Cannot delete an unsaved avatar.")
    }

    val db = databaseHelper.signalWritableDatabase
    val where = ID_WHERE
    val args = SqlUtil.buildArgs(databaseId.id)

    db.delete(TABLE_NAME, where, args)
  }

  private fun saveAvatar(avatar: Avatar, groupId: GroupId?): Avatar {
    val db = databaseHelper.signalWritableDatabase
    val databaseId = avatar.databaseId

    if (databaseId is Avatar.DatabaseId.DoNotPersist) {
      throw IllegalArgumentException("Cannot persist this avatar")
    }

    if (databaseId is Avatar.DatabaseId.Saved) {
      val values = ContentValues(2)
      values.put(AVATAR, avatar.toProto().toByteArray())

      db.update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(databaseId.id))

      return avatar
    } else {
      val values = ContentValues(4)
      values.put(AVATAR, avatar.toProto().toByteArray())

      if (groupId != null) {
        values.put(GROUP_ID, groupId.toString())
      }

      val id = db.insert(TABLE_NAME, null, values)
      if (id == -1L) {
        throw AssertionError("Failed to save avatar")
      }

      return avatar.withDatabaseId(Avatar.DatabaseId.Saved(id))
    }
  }

  fun getAllAvatars(): List<Avatar> {
    val db = databaseHelper.signalReadableDatabase
    val results = mutableListOf<Avatar>()

    db.query(TABLE_NAME, SqlUtil.buildArgs(ID, AVATAR), null, null, null, null, null)?.use {
      while (it.moveToNext()) {
        val id = CursorUtil.requireLong(it, ID)
        val blob = CursorUtil.requireBlob(it, AVATAR)
        val proto = CustomAvatar.parseFrom(blob)
        results.add(proto.toAvatar(id))
      }
    }

    return results
  }

  fun getAvatarsForSelf(): List<Avatar> {
    return getAvatars(null)
  }

  fun getAvatarsForGroup(groupId: GroupId): List<Avatar> {
    return getAvatars(groupId)
  }

  private fun getAvatars(groupId: GroupId?): List<Avatar> {
    val db = databaseHelper.signalReadableDatabase
    val orderBy = "$LAST_USED DESC"
    val results = mutableListOf<Avatar>()

    val (where, args) = if (groupId == null) {
      Pair("$GROUP_ID is NULL", null)
    } else {
      Pair("$GROUP_ID = ?", SqlUtil.buildArgs(groupId))
    }

    db.query(TABLE_NAME, SqlUtil.buildArgs(ID, AVATAR), where, args, null, null, orderBy)?.use {
      while (it.moveToNext()) {
        val id = CursorUtil.requireLong(it, ID)
        val blob = CursorUtil.requireBlob(it, AVATAR)
        val proto = CustomAvatar.parseFrom(blob)
        results.add(proto.toAvatar(id))
      }
    }

    return results
  }

  private fun Avatar.toProto(): CustomAvatar {
    return when (this) {
      is Avatar.Photo -> CustomAvatar.newBuilder().setPhoto(CustomAvatar.Photo.newBuilder().setUri(this.uri.toString())).build()
      is Avatar.Text -> CustomAvatar.newBuilder().setText(CustomAvatar.Text.newBuilder().setText(this.text).setColors(this.color.code)).build()
      is Avatar.Vector -> CustomAvatar.newBuilder().setVector(CustomAvatar.Vector.newBuilder().setKey(this.key).setColors(this.color.code)).build()
      else -> throw AssertionError()
    }
  }

  private fun CustomAvatar.toAvatar(id: Long): Avatar {
    return when {
      hasPhoto() -> Avatar.Photo(Uri.parse(photo.uri), photo.size, Avatar.DatabaseId.Saved(id))
      hasText() -> Avatar.Text(text.text, Avatars.colorMap[text.colors] ?: Avatars.colors[0], Avatar.DatabaseId.Saved(id))
      hasVector() -> Avatar.Vector(vector.key, Avatars.colorMap[vector.colors] ?: Avatars.colors[0], Avatar.DatabaseId.Saved(id))
      else -> throw AssertionError()
    }
  }
}
