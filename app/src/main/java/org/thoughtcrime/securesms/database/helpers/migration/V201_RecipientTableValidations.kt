/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * This migration rebuilds the recipient table to drop some deprecated columns, rename others to match their intended name, and some new constraints.
 * Specifically, we add a CHECK constraint to the `pni` column to ensure that we only put serialized PNI's in there.
 */
@Suppress("ClassName")
object V201_RecipientTableValidations : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE recipient_tmp (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        type INTEGER DEFAULT 0,
        e164 TEXT UNIQUE DEFAULT NULL,
        aci TEXT UNIQUE DEFAULT NULL,
        pni TEXT UNIQUE DEFAULT NULL CHECK (pni LIKE 'PNI:%'),
        username TEXT UNIQUE DEFAULT NULL,
        email TEXT UNIQUE DEFAULT NULL,
        group_id TEXT UNIQUE DEFAULT NULL,
        distribution_list_id INTEGER DEFAULT NULL,
        call_link_room_id TEXT DEFAULT NULL,
        registered INTEGER DEFAULT 0,
        unregistered_timestamp INTEGER DEFAULT 0,
        blocked INTEGER DEFAULT 0,
        hidden INTEGER DEFAULT 0,
        profile_key TEXT DEFAULT NULL, 
        profile_key_credential TEXT DEFAULT NULL, 
        profile_sharing INTEGER DEFAULT 0, 
        profile_given_name TEXT DEFAULT NULL, 
        profile_family_name TEXT DEFAULT NULL, 
        profile_joined_name TEXT DEFAULT NULL, 
        profile_avatar TEXT DEFAULT NULL, 
        last_profile_fetch INTEGER DEFAULT 0, 
        system_given_name TEXT DEFAULT NULL, 
        system_family_name TEXT DEFAULT NULL, 
        system_joined_name TEXT DEFAULT NULL, 
        system_nickname TEXT DEFAULT NULL,
        system_photo_uri TEXT DEFAULT NULL, 
        system_phone_label TEXT DEFAULT NULL, 
        system_phone_type INTEGER DEFAULT -1, 
        system_contact_uri TEXT DEFAULT NULL, 
        system_info_pending INTEGER DEFAULT 0, 
        notification_channel TEXT DEFAULT NULL, 
        message_ringtone TEXT DEFAULT NULL, 
        message_vibrate INTEGER DEFAULT 0, 
        call_ringtone TEXT DEFAULT NULL, 
        call_vibrate INTEGER DEFAULT 0, 
        mute_until INTEGER DEFAULT 0, 
        message_expiration_time INTEGER DEFAULT 0,
        sealed_sender_mode INTEGER DEFAULT 0, 
        storage_service_id TEXT UNIQUE DEFAULT NULL, 
        storage_service_proto TEXT DEFAULT NULL,
        mention_setting INTEGER DEFAULT 0, 
        capabilities INTEGER DEFAULT 0,
        last_session_reset BLOB DEFAULT NULL,
        wallpaper BLOB DEFAULT NULL,
        wallpaper_uri TEXT DEFAULT NULL,
        about TEXT DEFAULT NULL,
        about_emoji TEXT DEFAULT NULL,
        extras BLOB DEFAULT NULL,
        groups_in_common INTEGER DEFAULT 0,
        avatar_color TEXT DEFAULT NULL, 
        chat_colors BLOB DEFAULT NULL,
        custom_chat_colors_id INTEGER DEFAULT 0,
        badges BLOB DEFAULT NULL,
        needs_pni_signature INTEGER DEFAULT 0,
        reporting_token BLOB DEFAULT NULL
      )
      """
    )

    db.execSQL(
      """
        INSERT INTO recipient_tmp SELECT 
          _id,
          group_type,
          phone,
          aci,
          pni,
          username,
          email,
          group_id,
          distribution_list_id,
          call_link_room_id,
          registered,
          unregistered_timestamp,
          blocked,
          hidden,
          profile_key, 
          profile_key_credential, 
          profile_sharing, 
          signal_profile_name, 
          profile_family_name, 
          profile_joined_name, 
          signal_profile_avatar, 
          last_profile_fetch, 
          system_given_name, 
          system_family_name, 
          system_display_name, 
          system_nickname,
          system_photo_uri, 
          system_phone_label, 
          system_phone_type, 
          system_contact_uri, 
          system_info_pending, 
          notification_channel, 
          message_ringtone, 
          message_vibrate, 
          call_ringtone, 
          call_vibrate, 
          mute_until, 
          message_expiration_time,
          unidentified_access_mode, 
          storage_service_key, 
          storage_proto,
          mention_setting, 
          capabilities,
          last_session_reset,
          wallpaper,
          wallpaper_file,
          about,
          about_emoji,
          extras,
          groups_in_common,
          color, 
          chat_colors,
          custom_chat_colors_id,
          badges,
          needs_pni_signature,
          reporting_token
        FROM 
          recipient
      """
    )

    db.execSQL("DROP TABLE recipient")
    db.execSQL("ALTER TABLE recipient_tmp RENAME TO recipient")

    db.execSQL("CREATE INDEX recipient_type_index ON recipient (type)")
    db.execSQL("CREATE INDEX recipient_aci_profile_key_index ON recipient (aci, profile_key) WHERE aci NOT NULL AND profile_key NOT NULL")
  }
}
