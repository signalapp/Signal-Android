/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testutil

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.helpers.SignalDatabaseMigrations
import org.thoughtcrime.securesms.testing.TestSignalSQLiteDatabase

class SignalDatabaseMigrationRule(private val upgradedVersion: Int = 286) : ExternalResource() {

  lateinit var database: SQLiteDatabase

  override fun before() {
    database = inMemoryUpgradedDatabase()

    SignalDatabaseMigrations.migrate(
      context = ApplicationProvider.getApplicationContext(),
      db = database,
      oldVersion = 286,
      newVersion = upgradedVersion
    )
  }

  override fun after() {
    database.close()
  }

  data class Statement(
    val name: String,
    val sql: String
  )

  companion object {
    /**
     * Create an in-memory only database of a snapshot of V286. This includes
     * all non-FTS tables, indexes, and triggers.
     */
    private fun inMemoryUpgradedDatabase(): SQLiteDatabase {
      val configuration = SupportSQLiteOpenHelper.Configuration(
        context = ApplicationProvider.getApplicationContext(),
        name = "snapshot",
        callback = object : SupportSQLiteOpenHelper.Callback(286) {
          override fun onCreate(db: SupportSQLiteDatabase) {
            SNAPSHOT_V286.forEach { db.execSQL(it.sql) }
          }
          override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        },
        useNoBackupDirectory = false,
        allowDataLossOnRecovery = true
      )

      val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
      return TestSignalSQLiteDatabase(helper.writableDatabase)
    }

    private val SNAPSHOT_V286 = listOf(
      Statement(
        name = "attachment",
        sql = "CREATE TABLE attachment (_id INTEGER PRIMARY KEY AUTOINCREMENT, message_id INTEGER, content_type TEXT, remote_key TEXT, remote_location TEXT, remote_digest BLOB, remote_incremental_digest BLOB, remote_incremental_digest_chunk_size INTEGER DEFAULT 0, cdn_number INTEGER DEFAULT 0, transfer_state INTEGER, transfer_file TEXT DEFAULT NULL, data_file TEXT, data_size INTEGER, data_random BLOB, file_name TEXT, fast_preflight_id TEXT, voice_note INTEGER DEFAULT 0, borderless INTEGER DEFAULT 0, video_gif INTEGER DEFAULT 0, quote INTEGER DEFAULT 0, width INTEGER DEFAULT 0, height INTEGER DEFAULT 0, caption TEXT DEFAULT NULL, sticker_pack_id TEXT DEFAULT NULL, sticker_pack_key DEFAULT NULL, sticker_id INTEGER DEFAULT -1, sticker_emoji STRING DEFAULT NULL, blur_hash TEXT DEFAULT NULL, transform_properties TEXT DEFAULT NULL, display_order INTEGER DEFAULT 0, upload_timestamp INTEGER DEFAULT 0, data_hash_start TEXT DEFAULT NULL, data_hash_end TEXT DEFAULT NULL, archive_cdn INTEGER DEFAULT NULL, archive_transfer_state INTEGER DEFAULT 0, thumbnail_file TEXT DEFAULT NULL, thumbnail_random BLOB DEFAULT NULL, thumbnail_restore_state INTEGER DEFAULT 0, attachment_uuid TEXT DEFAULT NULL, offload_restored_at INTEGER DEFAULT 0)"
      ),
      Statement(
        name = "attachment_archive_transfer_state",
        sql = "CREATE INDEX attachment_archive_transfer_state ON attachment (archive_transfer_state)"
      ),
      Statement(
        name = "avatar_picker",
        sql = "CREATE TABLE avatar_picker (_id INTEGER PRIMARY KEY AUTOINCREMENT, last_used INTEGER DEFAULT 0, group_id TEXT DEFAULT NULL, avatar BLOB NOT NULL)"
      ),
      Statement(
        name = "backup_media_snapshot",
        sql = "CREATE TABLE backup_media_snapshot (_id INTEGER PRIMARY KEY, media_id TEXT NOT NULL UNIQUE, cdn INTEGER, snapshot_version INTEGER NOT NULL DEFAULT -1, is_pending INTEGER NOT NULL DEFAULT 0, is_thumbnail INTEGER NOT NULL DEFAULT 0, plaintext_hash BLOB NOT NULL, remote_key BLOB NOT NULL, last_seen_on_remote_snapshot_version INTEGER NOT NULL DEFAULT 0)"
      ),
      Statement(
        name = "call",
        sql = "CREATE TABLE call (_id INTEGER PRIMARY KEY, call_id INTEGER NOT NULL, message_id INTEGER DEFAULT NULL REFERENCES message (_id) ON DELETE SET NULL, peer INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE, type INTEGER NOT NULL, direction INTEGER NOT NULL, event INTEGER NOT NULL, timestamp INTEGER NOT NULL, ringer INTEGER DEFAULT NULL, deletion_timestamp INTEGER DEFAULT 0, read INTEGER DEFAULT 1, local_joined INTEGER DEFAULT 0, group_call_active INTEGER DEFAULT 0, UNIQUE (call_id, peer) ON CONFLICT FAIL)"
      ),
      Statement(
        name = "call_link",
        sql = "CREATE TABLE call_link (_id INTEGER PRIMARY KEY, root_key BLOB, room_id TEXT NOT NULL UNIQUE, admin_key BLOB, name TEXT NOT NULL, restrictions INTEGER NOT NULL, revoked INTEGER NOT NULL, expiration INTEGER NOT NULL, recipient_id INTEGER UNIQUE REFERENCES recipient (_id) ON DELETE CASCADE, deletion_timestamp INTEGER DEFAULT 0 NOT NULL, epoch BLOB DEFAULT NULL)"
      ),
      Statement(
        name = "cds",
        sql = "CREATE TABLE cds (_id INTEGER PRIMARY KEY, e164 TEXT NOT NULL UNIQUE ON CONFLICT IGNORE, last_seen_at INTEGER DEFAULT 0)"
      ),
      Statement(
        name = "chat_colors",
        sql = "CREATE TABLE chat_colors (_id INTEGER PRIMARY KEY AUTOINCREMENT, chat_colors BLOB)"
      ),
      Statement(
        name = "chat_folder",
        sql = "CREATE TABLE chat_folder (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT DEFAULT NULL, position INTEGER DEFAULT 0, show_unread INTEGER DEFAULT 0, show_muted INTEGER DEFAULT 0, show_individual INTEGER DEFAULT 0, show_groups INTEGER DEFAULT 0, folder_type INTEGER DEFAULT 4, chat_folder_id TEXT DEFAULT NULL, storage_service_id TEXT DEFAULT NULL, storage_service_proto TEXT DEFAULT NULL, deleted_timestamp_ms INTEGER DEFAULT 0)"
      ),
      Statement(
        name = "chat_folder_membership",
        sql = "CREATE TABLE chat_folder_membership (_id INTEGER PRIMARY KEY AUTOINCREMENT, chat_folder_id INTEGER NOT NULL REFERENCES chat_folder (_id) ON DELETE CASCADE, thread_id INTEGER NOT NULL REFERENCES thread (_id) ON DELETE CASCADE, membership_type INTEGER DEFAULT 1, UNIQUE(chat_folder_id, thread_id) ON CONFLICT REPLACE)"
      ),
      Statement(
        name = "distribution_list",
        sql = "CREATE TABLE distribution_list (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, distribution_id TEXT UNIQUE NOT NULL, recipient_id INTEGER UNIQUE REFERENCES recipient (_id) ON DELETE CASCADE, allows_replies INTEGER DEFAULT 1, deletion_timestamp INTEGER DEFAULT 0, is_unknown INTEGER DEFAULT 0, privacy_mode INTEGER DEFAULT 0)"
      ),
      Statement(
        name = "distribution_list_member",
        sql = "CREATE TABLE distribution_list_member (_id INTEGER PRIMARY KEY AUTOINCREMENT, list_id INTEGER NOT NULL REFERENCES distribution_list (_id) ON DELETE CASCADE, recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE, privacy_mode INTEGER DEFAULT 0)"
      ),
      Statement(
        name = "donation_receipt",
        sql = "CREATE TABLE donation_receipt (_id INTEGER PRIMARY KEY AUTOINCREMENT, receipt_type TEXT NOT NULL, receipt_date INTEGER NOT NULL, amount TEXT NOT NULL, currency TEXT NOT NULL, subscription_level INTEGER NOT NULL)"
      ),
      Statement(
        name = "drafts",
        sql = "CREATE TABLE drafts (_id INTEGER PRIMARY KEY, thread_id INTEGER, type TEXT, value TEXT)"
      ),
      Statement(
        name = "emoji_search",
        sql = "CREATE TABLE emoji_search (_id INTEGER PRIMARY KEY, label TEXT NOT NULL, emoji TEXT NOT NULL, rank INTEGER DEFAULT 2147483647)"
      ),
      Statement(
        name = "group_membership",
        sql = "CREATE TABLE group_membership (_id INTEGER PRIMARY KEY, group_id TEXT NOT NULL REFERENCES groups (group_id) ON DELETE CASCADE, recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE, endorsement BLOB DEFAULT NULL, UNIQUE(group_id, recipient_id))"
      ),
      Statement(
        name = "group_receipts",
        sql = "CREATE TABLE group_receipts (_id INTEGER PRIMARY KEY, mms_id INTEGER, address INTEGER, status INTEGER, timestamp INTEGER, unidentified INTEGER DEFAULT 0)"
      ),
      Statement(
        name = "groups",
        sql = "CREATE TABLE groups (_id INTEGER PRIMARY KEY, group_id TEXT NOT NULL UNIQUE, recipient_id INTEGER NOT NULL UNIQUE REFERENCES recipient (_id) ON DELETE CASCADE, title TEXT DEFAULT NULL, avatar_id INTEGER DEFAULT 0, avatar_key BLOB DEFAULT NULL, avatar_content_type TEXT DEFAULT NULL, avatar_digest BLOB DEFAULT NULL, timestamp INTEGER DEFAULT 0, active INTEGER DEFAULT 1, mms INTEGER DEFAULT 0, master_key BLOB DEFAULT NULL, revision BLOB DEFAULT NULL, decrypted_group BLOB DEFAULT NULL, expected_v2_id TEXT UNIQUE DEFAULT NULL, unmigrated_v1_members TEXT DEFAULT NULL, distribution_id TEXT UNIQUE DEFAULT NULL, show_as_story_state INTEGER DEFAULT 0, last_force_update_timestamp INTEGER DEFAULT 0, group_send_endorsements_expiration INTEGER DEFAULT 0)"
      ),
      Statement(
        name = "identities",
        sql = "CREATE TABLE identities (_id INTEGER PRIMARY KEY AUTOINCREMENT, address INTEGER UNIQUE, identity_key TEXT, first_use INTEGER DEFAULT 0, timestamp INTEGER DEFAULT 0, verified INTEGER DEFAULT 0, nonblocking_approval INTEGER DEFAULT 0)"
      ),
      Statement(
        name = "in_app_payment",
        sql = "CREATE TABLE in_app_payment (_id INTEGER PRIMARY KEY, type INTEGER NOT NULL, state INTEGER NOT NULL, inserted_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, notified INTEGER DEFAULT 1, subscriber_id TEXT, end_of_period INTEGER DEFAULT 0, data BLOB NOT NULL)"
      ),
      Statement(
        name = "in_app_payment_subscriber",
        sql = "CREATE TABLE in_app_payment_subscriber (_id INTEGER PRIMARY KEY, subscriber_id TEXT NOT NULL UNIQUE, currency_code TEXT NOT NULL, type INTEGER NOT NULL, requires_cancel INTEGER DEFAULT 0, payment_method_type INTEGER DEFAULT 0, purchase_token TEXT, original_transaction_id INTEGER, UNIQUE(currency_code, type), CHECK ((currency_code != '' AND purchase_token IS NULL AND original_transaction_id IS NULL AND type = 0) OR (currency_code = '' AND purchase_token IS NOT NULL AND original_transaction_id IS NULL AND type = 1) OR (currency_code = '' AND purchase_token IS NULL AND original_transaction_id IS NOT NULL AND type = 1)))"
      ),
      Statement(
        name = "kyber_prekey",
        sql = "CREATE TABLE kyber_prekey (_id INTEGER PRIMARY KEY, account_id TEXT NOT NULL, key_id INTEGER NOT NULL, timestamp INTEGER NOT NULL, last_resort INTEGER NOT NULL, serialized BLOB NOT NULL, stale_timestamp INTEGER NOT NULL DEFAULT 0, UNIQUE(account_id, key_id))"
      ),
      Statement(
        name = "mention",
        sql = "CREATE TABLE mention(_id INTEGER PRIMARY KEY AUTOINCREMENT, thread_id INTEGER, message_id INTEGER, recipient_id INTEGER, range_start INTEGER, range_length INTEGER)"
      ),
      Statement(
        name = "message",
        sql = "CREATE TABLE message (_id INTEGER PRIMARY KEY AUTOINCREMENT, date_sent INTEGER NOT NULL, date_received INTEGER NOT NULL, date_server INTEGER DEFAULT -1, thread_id INTEGER NOT NULL REFERENCES thread (_id) ON DELETE CASCADE, from_recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE, from_device_id INTEGER, to_recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE, type INTEGER NOT NULL, body TEXT, read INTEGER DEFAULT 0, ct_l TEXT, exp INTEGER, m_type INTEGER, m_size INTEGER, st INTEGER, tr_id TEXT, subscription_id INTEGER DEFAULT -1, receipt_timestamp INTEGER DEFAULT -1, has_delivery_receipt INTEGER DEFAULT 0, has_read_receipt INTEGER DEFAULT 0, viewed INTEGER DEFAULT 0, mismatched_identities TEXT DEFAULT NULL, network_failures TEXT DEFAULT NULL, expires_in INTEGER DEFAULT 0, expire_started INTEGER DEFAULT 0, notified INTEGER DEFAULT 0, quote_id INTEGER DEFAULT 0, quote_author INTEGER DEFAULT 0, quote_body TEXT DEFAULT NULL, quote_missing INTEGER DEFAULT 0, quote_mentions BLOB DEFAULT NULL, quote_type INTEGER DEFAULT 0, shared_contacts TEXT DEFAULT NULL, unidentified INTEGER DEFAULT 0, link_previews TEXT DEFAULT NULL, view_once INTEGER DEFAULT 0, reactions_unread INTEGER DEFAULT 0, reactions_last_seen INTEGER DEFAULT -1, remote_deleted INTEGER DEFAULT 0, mentions_self INTEGER DEFAULT 0, notified_timestamp INTEGER DEFAULT 0, server_guid TEXT DEFAULT NULL, message_ranges BLOB DEFAULT NULL, story_type INTEGER DEFAULT 0, parent_story_id INTEGER DEFAULT 0, export_state BLOB DEFAULT NULL, exported INTEGER DEFAULT 0, scheduled_date INTEGER DEFAULT -1, latest_revision_id INTEGER DEFAULT NULL REFERENCES message (_id) ON DELETE CASCADE, original_message_id INTEGER DEFAULT NULL REFERENCES message (_id) ON DELETE CASCADE, revision_number INTEGER DEFAULT 0, message_extras BLOB DEFAULT NULL, expire_timer_version INTEGER DEFAULT 1 NOT NULL)"
      ),
      Statement(
        name = "msl_message",
        sql = "CREATE TABLE msl_message (_id INTEGER PRIMARY KEY, payload_id INTEGER NOT NULL REFERENCES msl_payload (_id) ON DELETE CASCADE, message_id INTEGER NOT NULL)"
      ),
      Statement(
        name = "msl_payload",
        sql = "CREATE TABLE msl_payload (_id INTEGER PRIMARY KEY, date_sent INTEGER NOT NULL, content BLOB NOT NULL, content_hint INTEGER NOT NULL, urgent INTEGER NOT NULL DEFAULT 1)"
      ),
      Statement(
        name = "msl_recipient",
        sql = "CREATE TABLE msl_recipient (_id INTEGER PRIMARY KEY, payload_id INTEGER NOT NULL REFERENCES msl_payload (_id) ON DELETE CASCADE, recipient_id INTEGER NOT NULL, device INTEGER NOT NULL)"
      ),
      Statement(
        name = "name_collision",
        sql = "CREATE TABLE name_collision (_id INTEGER PRIMARY KEY AUTOINCREMENT, thread_id INTEGER UNIQUE NOT NULL, dismissed INTEGER DEFAULT 0, hash STRING DEFAULT NULL)"
      ),
      Statement(
        name = "name_collision_membership",
        sql = "CREATE TABLE name_collision_membership (_id INTEGER PRIMARY KEY AUTOINCREMENT, collision_id INTEGER NOT NULL REFERENCES name_collision (_id) ON DELETE CASCADE, recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE, profile_change_details BLOB DEFAULT NULL, UNIQUE (collision_id, recipient_id))"
      ),
      Statement(
        name = "notification_profile",
        sql = "CREATE TABLE notification_profile (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, emoji TEXT NOT NULL, color TEXT NOT NULL, created_at INTEGER NOT NULL, allow_all_calls INTEGER NOT NULL DEFAULT 0, allow_all_mentions INTEGER NOT NULL DEFAULT 0, notification_profile_id TEXT DEFAULT NULL, deleted_timestamp_ms INTEGER DEFAULT 0, storage_service_id TEXT DEFAULT NULL, storage_service_proto TEXT DEFAULT NULL)"
      ),
      Statement(
        name = "notification_profile_allowed_members",
        sql = "CREATE TABLE notification_profile_allowed_members (_id INTEGER PRIMARY KEY AUTOINCREMENT, notification_profile_id INTEGER NOT NULL REFERENCES notification_profile (_id) ON DELETE CASCADE, recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE, UNIQUE(notification_profile_id, recipient_id) ON CONFLICT REPLACE)"
      ),
      Statement(
        name = "notification_profile_schedule",
        sql = "CREATE TABLE notification_profile_schedule (_id INTEGER PRIMARY KEY AUTOINCREMENT, notification_profile_id INTEGER NOT NULL REFERENCES notification_profile (_id) ON DELETE CASCADE, enabled INTEGER NOT NULL DEFAULT 0, start INTEGER NOT NULL, end INTEGER NOT NULL, days_enabled TEXT NOT NULL)"
      ),
      Statement(
        name = "one_time_prekeys",
        sql = "CREATE TABLE one_time_prekeys (_id INTEGER PRIMARY KEY, account_id TEXT NOT NULL, key_id INTEGER NOT NULL, public_key TEXT NOT NULL, private_key TEXT NOT NULL, stale_timestamp INTEGER NOT NULL DEFAULT 0, UNIQUE(account_id, key_id))"
      ),
      Statement(
        name = "payments",
        sql = "CREATE TABLE payments(_id INTEGER PRIMARY KEY, uuid TEXT DEFAULT NULL, recipient INTEGER DEFAULT 0, recipient_address TEXT DEFAULT NULL, timestamp INTEGER, note TEXT DEFAULT NULL, direction INTEGER, state INTEGER, failure_reason INTEGER, amount BLOB NOT NULL, fee BLOB NOT NULL, transaction_record BLOB DEFAULT NULL, receipt BLOB DEFAULT NULL, payment_metadata BLOB DEFAULT NULL, receipt_public_key TEXT DEFAULT NULL, block_index INTEGER DEFAULT 0, block_timestamp INTEGER DEFAULT 0, seen INTEGER, UNIQUE(uuid) ON CONFLICT ABORT)"
      ),
      Statement(
        name = "pending_pni_signature_message",
        sql = "CREATE TABLE pending_pni_signature_message (_id INTEGER PRIMARY KEY, recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE, sent_timestamp INTEGER NOT NULL, device_id INTEGER NOT NULL)"
      ),
      Statement(
        name = "pending_retry_receipts",
        sql = "CREATE TABLE pending_retry_receipts(_id INTEGER PRIMARY KEY AUTOINCREMENT, author TEXT NOT NULL, device INTEGER NOT NULL, sent_timestamp INTEGER NOT NULL, received_timestamp TEXT NOT NULL, thread_id INTEGER NOT NULL, UNIQUE(author, sent_timestamp) ON CONFLICT REPLACE)"
      ),
      Statement(
        name = "reaction",
        sql = "CREATE TABLE reaction (_id INTEGER PRIMARY KEY, message_id INTEGER NOT NULL REFERENCES message (_id) ON DELETE CASCADE, author_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE, emoji TEXT NOT NULL, date_sent INTEGER NOT NULL, date_received INTEGER NOT NULL, UNIQUE(message_id, author_id) ON CONFLICT REPLACE)"
      ),
      Statement(
        name = "recipient",
        sql = "CREATE TABLE recipient (_id INTEGER PRIMARY KEY AUTOINCREMENT, type INTEGER DEFAULT 0, e164 TEXT UNIQUE DEFAULT NULL, aci TEXT UNIQUE DEFAULT NULL, pni TEXT UNIQUE DEFAULT NULL CHECK (pni LIKE 'PNI:%'), username TEXT UNIQUE DEFAULT NULL, email TEXT UNIQUE DEFAULT NULL, group_id TEXT UNIQUE DEFAULT NULL, distribution_list_id INTEGER DEFAULT NULL, call_link_room_id TEXT DEFAULT NULL, registered INTEGER DEFAULT 0, unregistered_timestamp INTEGER DEFAULT 0, blocked INTEGER DEFAULT 0, hidden INTEGER DEFAULT 0, profile_key TEXT DEFAULT NULL, profile_key_credential TEXT DEFAULT NULL, profile_sharing INTEGER DEFAULT 0, profile_given_name TEXT DEFAULT NULL, profile_family_name TEXT DEFAULT NULL, profile_joined_name TEXT DEFAULT NULL, profile_avatar TEXT DEFAULT NULL, last_profile_fetch INTEGER DEFAULT 0, system_given_name TEXT DEFAULT NULL, system_family_name TEXT DEFAULT NULL, system_joined_name TEXT DEFAULT NULL, system_nickname TEXT DEFAULT NULL, system_photo_uri TEXT DEFAULT NULL, system_phone_label TEXT DEFAULT NULL, system_phone_type INTEGER DEFAULT -1, system_contact_uri TEXT DEFAULT NULL, system_info_pending INTEGER DEFAULT 0, notification_channel TEXT DEFAULT NULL, message_ringtone TEXT DEFAULT NULL, message_vibrate INTEGER DEFAULT 0, call_ringtone TEXT DEFAULT NULL, call_vibrate INTEGER DEFAULT 0, mute_until INTEGER DEFAULT 0, message_expiration_time INTEGER DEFAULT 0, sealed_sender_mode INTEGER DEFAULT 0, storage_service_id TEXT UNIQUE DEFAULT NULL, storage_service_proto TEXT DEFAULT NULL, mention_setting INTEGER DEFAULT 0, capabilities INTEGER DEFAULT 0, last_session_reset BLOB DEFAULT NULL, wallpaper BLOB DEFAULT NULL, wallpaper_uri TEXT DEFAULT NULL, about TEXT DEFAULT NULL, about_emoji TEXT DEFAULT NULL, extras BLOB DEFAULT NULL, groups_in_common INTEGER DEFAULT 0, avatar_color TEXT DEFAULT NULL, chat_colors BLOB DEFAULT NULL, custom_chat_colors_id INTEGER DEFAULT 0, badges BLOB DEFAULT NULL, needs_pni_signature INTEGER DEFAULT 0, reporting_token BLOB DEFAULT NULL, phone_number_sharing INTEGER DEFAULT 0, phone_number_discoverable INTEGER DEFAULT 0, pni_signature_verified INTEGER DEFAULT 0, nickname_given_name TEXT DEFAULT NULL, nickname_family_name TEXT DEFAULT NULL, nickname_joined_name TEXT DEFAULT NULL, note TEXT DEFAULT NULL, message_expiration_time_version INTEGER DEFAULT 1 NOT NULL)"
      ),
      Statement(
        name = "remapped_recipients",
        sql = "CREATE TABLE remapped_recipients (_id INTEGER PRIMARY KEY AUTOINCREMENT, old_id INTEGER UNIQUE, new_id INTEGER)"
      ),
      Statement(
        name = "remapped_threads",
        sql = "CREATE TABLE remapped_threads (_id INTEGER PRIMARY KEY AUTOINCREMENT, old_id INTEGER UNIQUE, new_id INTEGER)"
      ),
      Statement(
        name = "remote_megaphone",
        sql = "CREATE TABLE remote_megaphone (_id INTEGER PRIMARY KEY, uuid TEXT UNIQUE NOT NULL, priority INTEGER NOT NULL, countries TEXT, minimum_version INTEGER NOT NULL, dont_show_before INTEGER NOT NULL, dont_show_after INTEGER NOT NULL, show_for_days INTEGER NOT NULL, conditional_id TEXT, primary_action_id TEXT, secondary_action_id TEXT, image_url TEXT, image_uri TEXT DEFAULT NULL, title TEXT NOT NULL, body TEXT NOT NULL, primary_action_text TEXT, secondary_action_text TEXT, shown_at INTEGER DEFAULT 0, finished_at INTEGER DEFAULT 0, primary_action_data TEXT DEFAULT NULL, secondary_action_data TEXT DEFAULT NULL, snoozed_at INTEGER DEFAULT 0, seen_count INTEGER DEFAULT 0)"
      ),
      Statement(
        name = "sender_key_shared",
        sql = "CREATE TABLE sender_key_shared (_id INTEGER PRIMARY KEY AUTOINCREMENT, distribution_id TEXT NOT NULL, address TEXT NOT NULL, device INTEGER NOT NULL, timestamp INTEGER DEFAULT 0, UNIQUE(distribution_id, address, device) ON CONFLICT REPLACE)"
      ),
      Statement(
        name = "sender_keys",
        sql = "CREATE TABLE sender_keys (_id INTEGER PRIMARY KEY AUTOINCREMENT, address TEXT NOT NULL, device INTEGER NOT NULL, distribution_id TEXT NOT NULL, record BLOB NOT NULL, created_at INTEGER NOT NULL, UNIQUE(address, device, distribution_id) ON CONFLICT REPLACE)"
      ),
      Statement(
        name = "sessions",
        sql = "CREATE TABLE sessions (_id INTEGER PRIMARY KEY AUTOINCREMENT, account_id TEXT NOT NULL, address TEXT NOT NULL, device INTEGER NOT NULL, record BLOB NOT NULL, UNIQUE(account_id, address, device))"
      ),
      Statement(
        name = "signed_prekeys",
        sql = "CREATE TABLE signed_prekeys (_id INTEGER PRIMARY KEY, account_id TEXT NOT NULL, key_id INTEGER NOT NULL, public_key TEXT NOT NULL, private_key TEXT NOT NULL, signature TEXT NOT NULL, timestamp INTEGER DEFAULT 0, UNIQUE(account_id, key_id))"
      ),
      Statement(
        name = "sticker",
        sql = "CREATE TABLE sticker (_id INTEGER PRIMARY KEY AUTOINCREMENT, pack_id TEXT NOT NULL, pack_key TEXT NOT NULL, pack_title TEXT NOT NULL, pack_author TEXT NOT NULL, sticker_id INTEGER, cover INTEGER, pack_order INTEGER, emoji TEXT NOT NULL, content_type TEXT DEFAULT NULL, last_used INTEGER, installed INTEGER, file_path TEXT NOT NULL, file_length INTEGER, file_random BLOB, UNIQUE(pack_id, sticker_id, cover) ON CONFLICT IGNORE)"
      ),
      Statement(
        name = "storage_key",
        sql = "CREATE TABLE storage_key (_id INTEGER PRIMARY KEY AUTOINCREMENT, type INTEGER, key TEXT UNIQUE)"
      ),
      Statement(
        name = "story_sends",
        sql = "CREATE TABLE story_sends (_id INTEGER PRIMARY KEY, message_id INTEGER NOT NULL REFERENCES message (_id) ON DELETE CASCADE, recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE, sent_timestamp INTEGER NOT NULL, allows_replies INTEGER NOT NULL, distribution_id TEXT NOT NULL REFERENCES distribution_list (distribution_id) ON DELETE CASCADE)"
      ),
      Statement(
        name = "thread",
        sql = "CREATE TABLE thread (_id INTEGER PRIMARY KEY AUTOINCREMENT, date INTEGER DEFAULT 0, meaningful_messages INTEGER DEFAULT 0, recipient_id INTEGER NOT NULL UNIQUE REFERENCES recipient (_id) ON DELETE CASCADE, read INTEGER DEFAULT 1, type INTEGER DEFAULT 0, error INTEGER DEFAULT 0, snippet TEXT, snippet_type INTEGER DEFAULT 0, snippet_uri TEXT DEFAULT NULL, snippet_content_type TEXT DEFAULT NULL, snippet_extras TEXT DEFAULT NULL, unread_count INTEGER DEFAULT 0, archived INTEGER DEFAULT 0, status INTEGER DEFAULT 0, has_delivery_receipt INTEGER DEFAULT 0, has_read_receipt INTEGER DEFAULT 0, expires_in INTEGER DEFAULT 0, last_seen INTEGER DEFAULT 0, has_sent INTEGER DEFAULT 0, last_scrolled INTEGER DEFAULT 0, pinned_order INTEGER UNIQUE DEFAULT NULL, unread_self_mention_count INTEGER DEFAULT 0, active INTEGER DEFAULT 0, snippet_message_extras BLOB DEFAULT NULL, snippet_message_id INTEGER DEFAULT 0)"
      ),
      Statement(
        name = "sticker_pack_id_index",
        sql = "CREATE INDEX sticker_pack_id_index ON sticker (pack_id)"
      ),
      Statement(
        name = "sticker_sticker_id_index",
        sql = "CREATE INDEX sticker_sticker_id_index ON sticker (sticker_id)"
      ),
      Statement(
        name = "storage_key_type_index",
        sql = "CREATE INDEX storage_key_type_index ON storage_key (type)"
      ),
      Statement(
        name = "story_sends_message_id_distribution_id_index",
        sql = "CREATE INDEX story_sends_message_id_distribution_id_index ON story_sends (message_id, distribution_id)"
      ),
      Statement(
        name = "story_sends_recipient_id_sent_timestamp_allows_replies_index",
        sql = "CREATE INDEX story_sends_recipient_id_sent_timestamp_allows_replies_index ON story_sends (recipient_id, sent_timestamp, allows_replies)"
      ),
      Statement(
        name = "thread_active",
        sql = "CREATE INDEX thread_active ON thread (active)"
      ),
      Statement(
        name = "thread_pinned_index",
        sql = "CREATE INDEX thread_pinned_index ON thread (pinned_order)"
      ),
      Statement(
        name = "thread_read",
        sql = "CREATE INDEX thread_read ON thread (read)"
      ),
      Statement(
        name = "thread_recipient_id_index",
        sql = "CREATE INDEX thread_recipient_id_index ON thread (recipient_id, active)"
      ),
      Statement(
        name = "timestamp_direction_index",
        sql = "CREATE INDEX timestamp_direction_index ON payments (timestamp, direction)"
      ),
      Statement(
        name = "timestamp_index",
        sql = "CREATE INDEX timestamp_index ON payments (timestamp)"
      ),
      Statement(
        name = "archived_count_index",
        sql = "CREATE INDEX archived_count_index ON thread (active, archived, meaningful_messages, pinned_order)"
      ),
      Statement(
        name = "attachment_data_hash_end_remote_key_index",
        sql = "CREATE INDEX attachment_data_hash_end_remote_key_index ON attachment (data_hash_end, remote_key)"
      ),
      Statement(
        name = "attachment_data_hash_start_index",
        sql = "CREATE INDEX attachment_data_hash_start_index ON attachment (data_hash_start)"
      ),
      Statement(
        name = "attachment_data_index",
        sql = "CREATE INDEX attachment_data_index ON attachment (data_file)"
      ),
      Statement(
        name = "attachment_message_id_index",
        sql = "CREATE INDEX attachment_message_id_index ON attachment (message_id)"
      ),
      Statement(
        name = "attachment_remote_digest_index",
        sql = "CREATE INDEX attachment_remote_digest_index ON attachment (remote_digest)"
      ),
      Statement(
        name = "attachment_sticker_pack_id_index",
        sql = "CREATE INDEX attachment_sticker_pack_id_index ON attachment (sticker_pack_id)"
      ),
      Statement(
        name = "attachment_transfer_state_index",
        sql = "CREATE INDEX attachment_transfer_state_index ON attachment (transfer_state)"
      ),
      Statement(
        name = "backup_snapshot_version_index",
        sql = "CREATE INDEX backup_snapshot_version_index ON backup_media_snapshot (snapshot_version DESC) WHERE snapshot_version != -1"
      ),
      Statement(
        name = "call_call_id_index",
        sql = "CREATE INDEX call_call_id_index ON call (call_id)"
      ),
      Statement(
        name = "call_log_index",
        sql = "CREATE INDEX call_log_index ON call (timestamp, peer, event, type, deletion_timestamp)"
      ),
      Statement(
        name = "call_message_id_index",
        sql = "CREATE INDEX call_message_id_index ON call (message_id)"
      ),
      Statement(
        name = "call_peer_index",
        sql = "CREATE INDEX call_peer_index ON call (peer)"
      ),
      Statement(
        name = "reaction_author_id_index",
        sql = "CREATE INDEX reaction_author_id_index ON reaction (author_id)"
      ),
      Statement(
        name = "receipt_public_key_index",
        sql = "CREATE UNIQUE INDEX receipt_public_key_index ON payments (receipt_public_key)"
      ),
      Statement(
        name = "recipient_aci_profile_key_index",
        sql = "CREATE INDEX recipient_aci_profile_key_index ON recipient (aci, profile_key) WHERE aci NOT NULL AND profile_key NOT NULL"
      ),
      Statement(
        name = "recipient_type_index",
        sql = "CREATE INDEX recipient_type_index ON recipient (type)"
      ),
      Statement(
        name = "msl_recipient_payload_index",
        sql = "CREATE INDEX msl_recipient_payload_index ON msl_recipient (payload_id)"
      ),
      Statement(
        name = "msl_recipient_recipient_index",
        sql = "CREATE INDEX msl_recipient_recipient_index ON msl_recipient (recipient_id, device, payload_id)"
      ),
      Statement(
        name = "name_collision_membership_collision_id_index",
        sql = "CREATE INDEX name_collision_membership_collision_id_index ON name_collision_membership (collision_id)"
      ),
      Statement(
        name = "name_collision_membership_recipient_id_index",
        sql = "CREATE INDEX name_collision_membership_recipient_id_index ON name_collision_membership (recipient_id)"
      ),
      Statement(
        name = "notification_profile_allowed_members_profile_index",
        sql = "CREATE INDEX notification_profile_allowed_members_profile_index ON notification_profile_allowed_members (notification_profile_id)"
      ),
      Statement(
        name = "notification_profile_allowed_members_recipient_index",
        sql = "CREATE INDEX notification_profile_allowed_members_recipient_index ON notification_profile_allowed_members (recipient_id)"
      ),
      Statement(
        name = "notification_profile_schedule_profile_index",
        sql = "CREATE INDEX notification_profile_schedule_profile_index ON notification_profile_schedule (notification_profile_id)"
      ),
      Statement(
        name = "pending_pni_recipient_sent_device_index",
        sql = "CREATE UNIQUE INDEX pending_pni_recipient_sent_device_index ON pending_pni_signature_message (recipient_id, sent_timestamp, device_id)"
      ),
      Statement(
        name = "message_date_sent_from_to_thread_index",
        sql = "CREATE INDEX message_date_sent_from_to_thread_index ON message (date_sent, from_recipient_id, to_recipient_id, thread_id)"
      ),
      Statement(
        name = "message_date_server_index",
        sql = "CREATE INDEX message_date_server_index ON message (date_server)"
      ),
      Statement(
        name = "message_exported_index",
        sql = "CREATE INDEX message_exported_index ON message (exported)"
      ),
      Statement(
        name = "message_from_recipient_id_index",
        sql = "CREATE INDEX message_from_recipient_id_index ON message (from_recipient_id)"
      ),
      Statement(
        name = "message_id_type_payment_transactions_index",
        sql = "CREATE INDEX message_id_type_payment_transactions_index ON message (_id, type) WHERE type & 12884901888 != 0"
      ),
      Statement(
        name = "message_latest_revision_id_index",
        sql = "CREATE INDEX message_latest_revision_id_index ON message (latest_revision_id)"
      ),
      Statement(
        name = "message_original_message_id_index",
        sql = "CREATE INDEX message_original_message_id_index ON message (original_message_id)"
      ),
      Statement(
        name = "message_parent_story_id_index",
        sql = "CREATE INDEX message_parent_story_id_index ON message (parent_story_id)"
      ),
      Statement(
        name = "message_quote_id_quote_author_scheduled_date_latest_revision_id_index",
        sql = "CREATE INDEX message_quote_id_quote_author_scheduled_date_latest_revision_id_index ON message (quote_id, quote_author, scheduled_date, latest_revision_id)"
      ),
      Statement(
        name = "message_reactions_unread_index",
        sql = "CREATE INDEX message_reactions_unread_index ON message (reactions_unread)"
      ),
      Statement(
        name = "message_read_and_notified_and_thread_id_index",
        sql = "CREATE INDEX message_read_and_notified_and_thread_id_index ON message (read, notified, thread_id)"
      ),
      Statement(
        name = "message_story_type_index",
        sql = "CREATE INDEX message_story_type_index ON message (story_type)"
      ),
      Statement(
        name = "message_thread_count_index",
        sql = "CREATE INDEX message_thread_count_index ON message (thread_id) WHERE story_type = 0 AND parent_story_id <= 0 AND scheduled_date = -1 AND latest_revision_id IS NULL"
      ),
      Statement(
        name = "message_thread_story_parent_story_scheduled_date_latest_revision_id_index",
        sql = "CREATE INDEX message_thread_story_parent_story_scheduled_date_latest_revision_id_index ON message (thread_id, date_received, story_type, parent_story_id, scheduled_date, latest_revision_id)"
      ),
      Statement(
        name = "message_thread_unread_count_index",
        sql = "CREATE INDEX message_thread_unread_count_index ON message (thread_id) WHERE story_type = 0 AND parent_story_id <= 0 AND scheduled_date = -1 AND original_message_id IS NULL AND read = 0"
      ),
      Statement(
        name = "message_to_recipient_id_index",
        sql = "CREATE INDEX message_to_recipient_id_index ON message (to_recipient_id)"
      ),
      Statement(
        name = "message_type_index",
        sql = "CREATE INDEX message_type_index ON message (type)"
      ),
      Statement(
        name = "message_unique_sent_from_thread",
        sql = "CREATE UNIQUE INDEX message_unique_sent_from_thread ON message (date_sent, from_recipient_id, thread_id)"
      ),
      Statement(
        name = "msl_message_message_index",
        sql = "CREATE INDEX msl_message_message_index ON msl_message (message_id, payload_id)"
      ),
      Statement(
        name = "msl_message_payload_index",
        sql = "CREATE INDEX msl_message_payload_index ON msl_message (payload_id)"
      ),
      Statement(
        name = "msl_payload_date_sent_index",
        sql = "CREATE INDEX msl_payload_date_sent_index ON msl_payload (date_sent)"
      ),
      Statement(
        name = "distribution_list_member_list_id_recipient_id_privacy_mode_index",
        sql = "CREATE UNIQUE INDEX distribution_list_member_list_id_recipient_id_privacy_mode_index ON distribution_list_member (list_id, recipient_id, privacy_mode)"
      ),
      Statement(
        name = "distribution_list_member_recipient_id",
        sql = "CREATE INDEX distribution_list_member_recipient_id ON distribution_list_member (recipient_id)"
      ),
      Statement(
        name = "donation_receipt_date_index",
        sql = "CREATE INDEX donation_receipt_date_index ON donation_receipt (receipt_date)"
      ),
      Statement(
        name = "donation_receipt_type_index",
        sql = "CREATE INDEX donation_receipt_type_index ON donation_receipt (receipt_type)"
      ),
      Statement(
        name = "draft_thread_index",
        sql = "CREATE INDEX draft_thread_index ON drafts (thread_id)"
      ),
      Statement(
        name = "group_membership_recipient_id",
        sql = "CREATE INDEX group_membership_recipient_id ON group_membership (recipient_id)"
      ),
      Statement(
        name = "group_receipt_mms_id_index",
        sql = "CREATE INDEX group_receipt_mms_id_index ON group_receipts (mms_id)"
      ),
      Statement(
        name = "kyber_account_id_key_id",
        sql = "CREATE INDEX kyber_account_id_key_id ON kyber_prekey (account_id, key_id, last_resort, serialized)"
      ),
      Statement(
        name = "mention_message_id_index",
        sql = "CREATE INDEX mention_message_id_index ON mention (message_id)"
      ),
      Statement(
        name = "mention_recipient_id_thread_id_index",
        sql = "CREATE INDEX mention_recipient_id_thread_id_index ON mention (recipient_id, thread_id)"
      ),
      Statement(
        name = "chat_folder_membership_membership_type_index",
        sql = "CREATE INDEX chat_folder_membership_membership_type_index ON chat_folder_membership (membership_type)"
      ),
      Statement(
        name = "chat_folder_membership_thread_id_index",
        sql = "CREATE INDEX chat_folder_membership_thread_id_index ON chat_folder_membership (thread_id)"
      ),
      Statement(
        name = "chat_folder_position_index",
        sql = "CREATE INDEX chat_folder_position_index ON chat_folder (position)"
      ),
      Statement(
        name = "msl_attachment_delete",
        sql = "CREATE TRIGGER msl_attachment_delete AFTER DELETE ON attachment BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE msl_message.message_id = old.message_id); END"
      ),
      Statement(
        name = "msl_message_delete",
        sql = "CREATE TRIGGER msl_message_delete AFTER DELETE ON message BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old._id); END"
      )
    )
  }
}
