package org.thoughtcrime.securesms.database

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.assertTrue
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.readToList
import org.signal.core.util.requireNonNullString
import org.thoughtcrime.securesms.database.helpers.SignalDatabaseMigrations
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.testing.SignalActivityRule

/**
 * A test that guarantees that a freshly-created database looks the same as one that went through the upgrade path.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseConsistencyTest {

  @get:Rule
  val harness = SignalActivityRule()

  @Test
  fun test() {
    val currentVersionStatements = SignalDatabase.rawDatabase.getAllCreateStatements()
    val testHelper = InMemoryTestHelper(ApplicationDependencies.getApplication()).also {
      it.onUpgrade(it.writableDatabase, 181, SignalDatabaseMigrations.DATABASE_VERSION)
    }

    val upgradedStatements = testHelper.readableDatabase.getAllCreateStatements()

    if (currentVersionStatements != upgradedStatements) {
      var message = "\n"

      val currentByName = currentVersionStatements.associateBy { it.name }
      val upgradedByName = upgradedStatements.associateBy { it.name }

      if (currentByName.keys != upgradedByName.keys) {
        val exclusiveToCurrent = currentByName.keys - upgradedByName.keys
        val exclusiveToUpgrade = upgradedByName.keys - currentByName.keys

        message += "SQL entities exclusive to the newly-created database: $exclusiveToCurrent\n"
        message += "SQL entities exclusive to the upgraded database: $exclusiveToUpgrade\n\n"
      } else {
        for (currentEntry in currentByName) {
          val upgradedValue: Statement = upgradedByName[currentEntry.key]!!
          if (upgradedValue.sql != currentEntry.value.sql) {
            message += "Statement differed:\n"
            message += "newly-created:\n"
            message += "${currentEntry.value.sql}\n\n"
            message += "upgraded:\n"
            message += "${upgradedValue.sql}\n\n"
          }
        }
      }

      assertTrue(message, false)
    }
  }

  private data class Statement(
    val name: String,
    val sql: String
  )

  private fun SQLiteDatabase.getAllCreateStatements(): List<Statement> {
    return this.rawQuery("SELECT name, sql FROM sqlite_schema WHERE sql NOT NULL AND name != 'sqlite_sequence'")
      .readToList { cursor ->
        Statement(
          name = cursor.requireNonNullString("name"),
          sql = cursor.requireNonNullString("sql").normalizeSql()
        )
      }
      .sortedBy { it.name }
  }

  private fun String.normalizeSql(): String {
    return this
      .split("\n")
      .map { it.trim() }
      .joinToString(separator = " ")
      .replace(Regex.fromLiteral("( "), "(")
      .replace(Regex.fromLiteral(" )"), ")")
      .replace(Regex.fromLiteral("CREATE TABLE \"call\""), "CREATE TABLE call") // solves a specific weirdness with inconsequential quotes
  }

  private class InMemoryTestHelper(private val application: Application) : SQLiteOpenHelper(application, null, null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
      for (statement in SNAPSHOT_V181) {
        db.execSQL(statement.sql)
      }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
      SignalDatabaseMigrations.migrate(application, db, 181, SignalDatabaseMigrations.DATABASE_VERSION)
    }

    /**
     * This is the list of statements that existed at version 181. Never change this.
     */
    private val SNAPSHOT_V181 = listOf(
      Statement(
        name = "message",
        sql = "CREATE TABLE message (\n        _id INTEGER PRIMARY KEY AUTOINCREMENT,\n        date_sent INTEGER NOT NULL,\n        date_received INTEGER NOT NULL,\n        date_server INTEGER DEFAULT -1,\n        thread_id INTEGER NOT NULL REFERENCES thread (_id) ON DELETE CASCADE,\n        recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,\n        recipient_device_id INTEGER,\n        type INTEGER NOT NULL,\n        body TEXT,\n        read INTEGER DEFAULT 0,\n        ct_l TEXT,\n        exp INTEGER,\n        m_type INTEGER,\n        m_size INTEGER,\n        st INTEGER,\n        tr_id TEXT,\n        subscription_id INTEGER DEFAULT -1, \n        receipt_timestamp INTEGER DEFAULT -1, \n        delivery_receipt_count INTEGER DEFAULT 0, \n        read_receipt_count INTEGER DEFAULT 0, \n        viewed_receipt_count INTEGER DEFAULT 0,\n        mismatched_identities TEXT DEFAULT NULL,\n        network_failures TEXT DEFAULT NULL,\n        expires_in INTEGER DEFAULT 0,\n        expire_started INTEGER DEFAULT 0,\n        notified INTEGER DEFAULT 0,\n        quote_id INTEGER DEFAULT 0,\n        quote_author INTEGER DEFAULT 0,\n        quote_body TEXT DEFAULT NULL,\n        quote_missing INTEGER DEFAULT 0,\n        quote_mentions BLOB DEFAULT NULL,\n        quote_type INTEGER DEFAULT 0,\n        shared_contacts TEXT DEFAULT NULL,\n        unidentified INTEGER DEFAULT 0,\n        link_previews TEXT DEFAULT NULL,\n        view_once INTEGER DEFAULT 0,\n        reactions_unread INTEGER DEFAULT 0,\n        reactions_last_seen INTEGER DEFAULT -1,\n        remote_deleted INTEGER DEFAULT 0,\n        mentions_self INTEGER DEFAULT 0,\n        notified_timestamp INTEGER DEFAULT 0,\n        server_guid TEXT DEFAULT NULL,\n        message_ranges BLOB DEFAULT NULL,\n        story_type INTEGER DEFAULT 0,\n        parent_story_id INTEGER DEFAULT 0,\n        export_state BLOB DEFAULT NULL,\n        exported INTEGER DEFAULT 0,\n        scheduled_date INTEGER DEFAULT -1\n      )"
      ),
      Statement(
        name = "part",
        sql = "CREATE TABLE part (_id INTEGER PRIMARY KEY, mid INTEGER, seq INTEGER DEFAULT 0, ct TEXT, name TEXT, chset INTEGER, cd TEXT, fn TEXT, cid TEXT, cl TEXT, ctt_s INTEGER, ctt_t TEXT, encrypted INTEGER, pending_push INTEGER, _data TEXT, data_size INTEGER, file_name TEXT, unique_id INTEGER NOT NULL, digest BLOB, fast_preflight_id TEXT, voice_note INTEGER DEFAULT 0, borderless INTEGER DEFAULT 0, video_gif INTEGER DEFAULT 0, data_random BLOB, quote INTEGER DEFAULT 0, width INTEGER DEFAULT 0, height INTEGER DEFAULT 0, caption TEXT DEFAULT NULL, sticker_pack_id TEXT DEFAULT NULL, sticker_pack_key DEFAULT NULL, sticker_id INTEGER DEFAULT -1, sticker_emoji STRING DEFAULT NULL, data_hash TEXT DEFAULT NULL, blur_hash TEXT DEFAULT NULL, transform_properties TEXT DEFAULT NULL, transfer_file TEXT DEFAULT NULL, display_order INTEGER DEFAULT 0, upload_timestamp INTEGER DEFAULT 0, cdn_number INTEGER DEFAULT 0)"
      ),
      Statement(
        name = "thread",
        sql = "CREATE TABLE thread (\n  _id INTEGER PRIMARY KEY AUTOINCREMENT, \n  date INTEGER DEFAULT 0, \n  meaningful_messages INTEGER DEFAULT 0,\n  recipient_id INTEGER NOT NULL UNIQUE REFERENCES recipient (_id) ON DELETE CASCADE,\n  read INTEGER DEFAULT 1, \n  type INTEGER DEFAULT 0, \n  error INTEGER DEFAULT 0, \n  snippet TEXT, \n  snippet_type INTEGER DEFAULT 0, \n  snippet_uri TEXT DEFAULT NULL, \n  snippet_content_type TEXT DEFAULT NULL, \n  snippet_extras TEXT DEFAULT NULL, \n  unread_count INTEGER DEFAULT 0, \n  archived INTEGER DEFAULT 0, \n  status INTEGER DEFAULT 0, \n  delivery_receipt_count INTEGER DEFAULT 0, \n  read_receipt_count INTEGER DEFAULT 0, \n  expires_in INTEGER DEFAULT 0, \n  last_seen INTEGER DEFAULT 0, \n  has_sent INTEGER DEFAULT 0, \n  last_scrolled INTEGER DEFAULT 0, \n  pinned INTEGER DEFAULT 0, \n  unread_self_mention_count INTEGER DEFAULT 0\n)"
      ),
      Statement(
        name = "identities",
        sql = "CREATE TABLE identities (\n        _id INTEGER PRIMARY KEY AUTOINCREMENT, \n        address INTEGER UNIQUE, \n        identity_key TEXT, \n        first_use INTEGER DEFAULT 0, \n        timestamp INTEGER DEFAULT 0, \n        verified INTEGER DEFAULT 0, \n        nonblocking_approval INTEGER DEFAULT 0\n      )"
      ),
      Statement(
        name = "drafts",
        sql = "CREATE TABLE drafts (\n        _id INTEGER PRIMARY KEY, \n        thread_id INTEGER, \n        type TEXT, \n        value TEXT\n      )"
      ),
      Statement(
        name = "push",
        sql = "CREATE TABLE push (_id INTEGER PRIMARY KEY, type INTEGER, source TEXT, source_uuid TEXT, device_id INTEGER, body TEXT, content TEXT, timestamp INTEGER, server_timestamp INTEGER DEFAULT 0, server_delivered_timestamp INTEGER DEFAULT 0, server_guid TEXT DEFAULT NULL)"
      ),
      Statement(
        name = "groups",
        sql = "CREATE TABLE groups (\n        _id INTEGER PRIMARY KEY, \n        group_id TEXT, \n        recipient_id INTEGER,\n        title TEXT,\n        avatar_id INTEGER, \n        avatar_key BLOB,\n        avatar_content_type TEXT, \n        avatar_relay TEXT,\n        timestamp INTEGER,\n        active INTEGER DEFAULT 1,\n        avatar_digest BLOB, \n        mms INTEGER DEFAULT 0, \n        master_key BLOB, \n        revision BLOB, \n        decrypted_group BLOB, \n        expected_v2_id TEXT DEFAULT NULL, \n        former_v1_members TEXT DEFAULT NULL, \n        distribution_id TEXT DEFAULT NULL, \n        display_as_story INTEGER DEFAULT 0, \n        auth_service_id TEXT DEFAULT NULL, \n        last_force_update_timestamp INTEGER DEFAULT 0\n      )"
      ),
      Statement(
        name = "group_membership",
        sql = "CREATE TABLE group_membership (     _id INTEGER PRIMARY KEY,     group_id TEXT NOT NULL,     recipient_id INTEGER NOT NULL,     UNIQUE(group_id, recipient_id) )"
      ),
      Statement(
        name = "recipient",
        sql = "CREATE TABLE recipient (\n  _id INTEGER PRIMARY KEY AUTOINCREMENT,\n  uuid TEXT UNIQUE DEFAULT NULL,\n  username TEXT UNIQUE DEFAULT NULL,\n  phone TEXT UNIQUE DEFAULT NULL,\n  email TEXT UNIQUE DEFAULT NULL,\n  group_id TEXT UNIQUE DEFAULT NULL,\n  group_type INTEGER DEFAULT 0,\n  blocked INTEGER DEFAULT 0,\n  message_ringtone TEXT DEFAULT NULL, \n  message_vibrate INTEGER DEFAULT 0, \n  call_ringtone TEXT DEFAULT NULL, \n  call_vibrate INTEGER DEFAULT 0, \n  notification_channel TEXT DEFAULT NULL, \n  mute_until INTEGER DEFAULT 0, \n  color TEXT DEFAULT NULL, \n  seen_invite_reminder INTEGER DEFAULT 0,\n  default_subscription_id INTEGER DEFAULT -1,\n  message_expiration_time INTEGER DEFAULT 0,\n  registered INTEGER DEFAULT 0,\n  system_given_name TEXT DEFAULT NULL, \n  system_family_name TEXT DEFAULT NULL, \n  system_display_name TEXT DEFAULT NULL, \n  system_photo_uri TEXT DEFAULT NULL, \n  system_phone_label TEXT DEFAULT NULL, \n  system_phone_type INTEGER DEFAULT -1, \n  system_contact_uri TEXT DEFAULT NULL, \n  system_info_pending INTEGER DEFAULT 0, \n  profile_key TEXT DEFAULT NULL, \n  profile_key_credential TEXT DEFAULT NULL, \n  signal_profile_name TEXT DEFAULT NULL, \n  profile_family_name TEXT DEFAULT NULL, \n  profile_joined_name TEXT DEFAULT NULL, \n  signal_profile_avatar TEXT DEFAULT NULL, \n  profile_sharing INTEGER DEFAULT 0, \n  last_profile_fetch INTEGER DEFAULT 0, \n  unidentified_access_mode INTEGER DEFAULT 0, \n  force_sms_selection INTEGER DEFAULT 0, \n  storage_service_key TEXT UNIQUE DEFAULT NULL, \n  mention_setting INTEGER DEFAULT 0, \n  storage_proto TEXT DEFAULT NULL,\n  capabilities INTEGER DEFAULT 0,\n  last_session_reset BLOB DEFAULT NULL,\n  wallpaper BLOB DEFAULT NULL,\n  wallpaper_file TEXT DEFAULT NULL,\n  about TEXT DEFAULT NULL,\n  about_emoji TEXT DEFAULT NULL,\n  extras BLOB DEFAULT NULL,\n  groups_in_common INTEGER DEFAULT 0,\n  chat_colors BLOB DEFAULT NULL,\n  custom_chat_colors_id INTEGER DEFAULT 0,\n  badges BLOB DEFAULT NULL,\n  pni TEXT DEFAULT NULL,\n  distribution_list_id INTEGER DEFAULT NULL,\n  needs_pni_signature INTEGER DEFAULT 0,\n  unregistered_timestamp INTEGER DEFAULT 0,\n  hidden INTEGER DEFAULT 0,\n  reporting_token BLOB DEFAULT NULL,\n  system_nickname TEXT DEFAULT NULL\n)"
      ),
      Statement(
        name = "group_receipts",
        sql = "CREATE TABLE group_receipts (\n        _id INTEGER PRIMARY KEY, \n        mms_id INTEGER, \n        address INTEGER, \n        status INTEGER, \n        timestamp INTEGER, \n        unidentified INTEGER DEFAULT 0\n      )"
      ),
      Statement(
        name = "one_time_prekeys",
        sql = "CREATE TABLE one_time_prekeys (\n        _id INTEGER PRIMARY KEY,\n        account_id TEXT NOT NULL,\n        key_id INTEGER UNIQUE, \n        public_key TEXT NOT NULL, \n        private_key TEXT NOT NULL,\n        UNIQUE(account_id, key_id)\n      )"
      ),
      Statement(
        name = "signed_prekeys",
        sql = "CREATE TABLE signed_prekeys (\n        _id INTEGER PRIMARY KEY,\n        account_id TEXT NOT NULL,\n        key_id INTEGER UNIQUE, \n        public_key TEXT NOT NULL,\n        private_key TEXT NOT NULL,\n        signature TEXT NOT NULL, \n        timestamp INTEGER DEFAULT 0,\n        UNIQUE(account_id, key_id)\n    )"
      ),
      Statement(
        name = "sessions",
        sql = "CREATE TABLE sessions (\n        _id INTEGER PRIMARY KEY AUTOINCREMENT,\n        account_id TEXT NOT NULL,\n        address TEXT NOT NULL,\n        device INTEGER NOT NULL,\n        record BLOB NOT NULL,\n        UNIQUE(account_id, address, device)\n      )"
      ),
      Statement(
        name = "sender_keys",
        sql = "CREATE TABLE sender_keys (\n        _id INTEGER PRIMARY KEY AUTOINCREMENT, \n        address TEXT NOT NULL, \n        device INTEGER NOT NULL, \n        distribution_id TEXT NOT NULL,\n        record BLOB NOT NULL, \n        created_at INTEGER NOT NULL, \n        UNIQUE(address,device, distribution_id) ON CONFLICT REPLACE\n      )"
      ),
      Statement(
        name = "sender_key_shared",
        sql = "CREATE TABLE sender_key_shared (\n        _id INTEGER PRIMARY KEY AUTOINCREMENT, \n        distribution_id TEXT NOT NULL, \n        address TEXT NOT NULL, \n        device INTEGER NOT NULL, \n        timestamp INTEGER DEFAULT 0, \n        UNIQUE(distribution_id,address, device) ON CONFLICT REPLACE\n      )"
      ),
      Statement(
        name = "pending_retry_receipts",
        sql = "CREATE TABLE pending_retry_receipts(_id INTEGER PRIMARY KEY AUTOINCREMENT, author TEXT NOT NULL, device INTEGER NOT NULL, sent_timestamp INTEGER NOT NULL, received_timestamp TEXT NOT NULL, thread_id INTEGER NOT NULL, UNIQUE(author,sent_timestamp) ON CONFLICT REPLACE)"
      ),
      Statement(
        name = "sticker",
        sql = "CREATE TABLE sticker (_id INTEGER PRIMARY KEY AUTOINCREMENT, pack_id TEXT NOT NULL, pack_key TEXT NOT NULL, pack_title TEXT NOT NULL, pack_author TEXT NOT NULL, sticker_id INTEGER, cover INTEGER, pack_order INTEGER, emoji TEXT NOT NULL, content_type TEXT DEFAULT NULL, last_used INTEGER, installed INTEGER,file_path TEXT NOT NULL, file_length INTEGER, file_random BLOB, UNIQUE(pack_id, sticker_id, cover) ON CONFLICT IGNORE)"
      ),
      Statement(
        name = "storage_key",
        sql = "CREATE TABLE storage_key (_id INTEGER PRIMARY KEY AUTOINCREMENT, type INTEGER, key TEXT UNIQUE)"
      ),
      Statement(
        name = "mention",
        sql = "CREATE TABLE mention(_id INTEGER PRIMARY KEY AUTOINCREMENT, thread_id INTEGER, message_id INTEGER, recipient_id INTEGER, range_start INTEGER, range_length INTEGER)"
      ),
      Statement(
        name = "payments",
        sql = "CREATE TABLE payments(_id INTEGER PRIMARY KEY, uuid TEXT DEFAULT NULL, recipient INTEGER DEFAULT 0, recipient_address TEXT DEFAULT NULL, timestamp INTEGER, note TEXT DEFAULT NULL, direction INTEGER, state INTEGER, failure_reason INTEGER, amount BLOB NOT NULL, fee BLOB NOT NULL, transaction_record BLOB DEFAULT NULL, receipt BLOB DEFAULT NULL, payment_metadata BLOB DEFAULT NULL, receipt_public_key TEXT DEFAULT NULL, block_index INTEGER DEFAULT 0, block_timestamp INTEGER DEFAULT 0, seen INTEGER, UNIQUE(uuid) ON CONFLICT ABORT)"
      ),
      Statement(
        name = "chat_colors",
        sql = "CREATE TABLE chat_colors (\n  _id INTEGER PRIMARY KEY AUTOINCREMENT,\n  chat_colors BLOB\n)"
      ),
      Statement(
        name = "emoji_search",
        sql = "CREATE TABLE emoji_search (\n        _id INTEGER PRIMARY KEY,\n        label TEXT NOT NULL,\n        emoji TEXT NOT NULL,\n        rank INTEGER DEFAULT 2147483647 \n      )"
      ),
      Statement(
        name = "avatar_picker",
        sql = "CREATE TABLE avatar_picker (\n  _id INTEGER PRIMARY KEY AUTOINCREMENT,\n  last_used INTEGER DEFAULT 0,\n  group_id TEXT DEFAULT NULL,\n  avatar BLOB NOT NULL\n)"
      ),
      Statement(
        name = "group_call_ring",
        sql = "CREATE TABLE group_call_ring (\n  _id INTEGER PRIMARY KEY,\n  ring_id INTEGER UNIQUE,\n  date_received INTEGER,\n  ring_state INTEGER\n)"
      ),
      Statement(
        name = "reaction",
        sql = "CREATE TABLE reaction (\n  _id INTEGER PRIMARY KEY,\n  message_id INTEGER NOT NULL REFERENCES message (_id) ON DELETE CASCADE,\n  author_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,\n  emoji TEXT NOT NULL,\n  date_sent INTEGER NOT NULL,\n  date_received INTEGER NOT NULL,\n  UNIQUE(message_id, author_id) ON CONFLICT REPLACE\n)"
      ),
      Statement(
        name = "donation_receipt",
        sql = "CREATE TABLE donation_receipt (\n  _id INTEGER PRIMARY KEY AUTOINCREMENT,\n  receipt_type TEXT NOT NULL,\n  receipt_date INTEGER NOT NULL,\n  amount TEXT NOT NULL,\n  currency TEXT NOT NULL,\n  subscription_level INTEGER NOT NULL\n)"
      ),
      Statement(
        name = "story_sends",
        sql = "CREATE TABLE story_sends (\n  _id INTEGER PRIMARY KEY,\n  message_id INTEGER NOT NULL REFERENCES message (_id) ON DELETE CASCADE,\n  recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,\n  sent_timestamp INTEGER NOT NULL,\n  allows_replies INTEGER NOT NULL,\n  distribution_id TEXT NOT NULL REFERENCES distribution_list (distribution_id) ON DELETE CASCADE\n)"
      ),
      Statement(
        name = "cds",
        sql = "CREATE TABLE cds (\n        _id INTEGER PRIMARY KEY,\n        e164 TEXT NOT NULL UNIQUE ON CONFLICT IGNORE,\n        last_seen_at INTEGER DEFAULT 0\n      )"
      ),
      Statement(
        name = "remote_megaphone",
        sql = "CREATE TABLE remote_megaphone (\n  _id INTEGER PRIMARY KEY,\n  uuid TEXT UNIQUE NOT NULL,\n  priority INTEGER NOT NULL,\n  countries TEXT,\n  minimum_version INTEGER NOT NULL,\n  dont_show_before INTEGER NOT NULL,\n  dont_show_after INTEGER NOT NULL,\n  show_for_days INTEGER NOT NULL,\n  conditional_id TEXT,\n  primary_action_id TEXT,\n  secondary_action_id TEXT,\n  image_url TEXT,\n  image_uri TEXT DEFAULT NULL,\n  title TEXT NOT NULL,\n  body TEXT NOT NULL,\n  primary_action_text TEXT,\n  secondary_action_text TEXT,\n  shown_at INTEGER DEFAULT 0,\n  finished_at INTEGER DEFAULT 0,\n  primary_action_data TEXT DEFAULT NULL,\n  secondary_action_data TEXT DEFAULT NULL,\n  snoozed_at INTEGER DEFAULT 0,\n  seen_count INTEGER DEFAULT 0\n)"
      ),
      Statement(
        name = "pending_pni_signature_message",
        sql = "CREATE TABLE pending_pni_signature_message (\n        _id INTEGER PRIMARY KEY,\n        recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,\n        sent_timestamp INTEGER NOT NULL,\n        device_id INTEGER NOT NULL\n      )"
      ),
      Statement(
        name = "call",
        sql = "CREATE TABLE call (\n  _id INTEGER PRIMARY KEY,\n  call_id INTEGER NOT NULL UNIQUE,\n  message_id INTEGER NOT NULL REFERENCES message (_id) ON DELETE CASCADE,\n  peer INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,\n  type INTEGER NOT NULL,\n  direction INTEGER NOT NULL,\n  event INTEGER NOT NULL\n)"
      ),
      Statement(
        name = "message_fts",
        sql = "CREATE VIRTUAL TABLE message_fts USING fts5(body, thread_id UNINDEXED, content=message, content_rowid=_id)"
      ),
      Statement(
        name = "remapped_recipients",
        sql = "CREATE TABLE remapped_recipients (\n        _id INTEGER PRIMARY KEY AUTOINCREMENT, \n        old_id INTEGER UNIQUE, \n        new_id INTEGER\n      )"
      ),
      Statement(
        name = "remapped_threads",
        sql = "CREATE TABLE remapped_threads (\n        _id INTEGER PRIMARY KEY AUTOINCREMENT, \n        old_id INTEGER UNIQUE, \n        new_id INTEGER\n      )"
      ),
      Statement(
        name = "msl_payload",
        sql = "CREATE TABLE msl_payload (\n        _id INTEGER PRIMARY KEY,\n        date_sent INTEGER NOT NULL,\n        content BLOB NOT NULL,\n        content_hint INTEGER NOT NULL,\n        urgent INTEGER NOT NULL DEFAULT 1\n      )"
      ),
      Statement(
        name = "msl_recipient",
        sql = "CREATE TABLE msl_recipient (\n        _id INTEGER PRIMARY KEY,\n        payload_id INTEGER NOT NULL REFERENCES msl_payload (_id) ON DELETE CASCADE,\n        recipient_id INTEGER NOT NULL, \n        device INTEGER NOT NULL\n      )"
      ),
      Statement(
        name = "msl_message",
        sql = "CREATE TABLE msl_message (\n        _id INTEGER PRIMARY KEY,\n        payload_id INTEGER NOT NULL REFERENCES msl_payload (_id) ON DELETE CASCADE,\n        message_id INTEGER NOT NULL\n      )"
      ),
      Statement(
        name = "notification_profile",
        sql = "CREATE TABLE notification_profile (\n  _id INTEGER PRIMARY KEY AUTOINCREMENT,\n  name TEXT NOT NULL UNIQUE,\n  emoji TEXT NOT NULL,\n  color TEXT NOT NULL,\n  created_at INTEGER NOT NULL,\n  allow_all_calls INTEGER NOT NULL DEFAULT 0,\n  allow_all_mentions INTEGER NOT NULL DEFAULT 0\n)"
      ),
      Statement(
        name = "notification_profile_schedule",
        sql = "CREATE TABLE notification_profile_schedule (\n  _id INTEGER PRIMARY KEY AUTOINCREMENT,\n  notification_profile_id INTEGER NOT NULL REFERENCES notification_profile (_id) ON DELETE CASCADE,\n  enabled INTEGER NOT NULL DEFAULT 0,\n  start INTEGER NOT NULL,\n  end INTEGER NOT NULL,\n  days_enabled TEXT NOT NULL\n)"
      ),
      Statement(
        name = "notification_profile_allowed_members",
        sql = "CREATE TABLE notification_profile_allowed_members (\n  _id INTEGER PRIMARY KEY AUTOINCREMENT,\n  notification_profile_id INTEGER NOT NULL REFERENCES notification_profile (_id) ON DELETE CASCADE,\n  recipient_id INTEGER NOT NULL,\n  UNIQUE(notification_profile_id, recipient_id) ON CONFLICT REPLACE\n)"
      ),
      Statement(
        name = "distribution_list",
        sql = "CREATE TABLE distribution_list (\n        _id INTEGER PRIMARY KEY AUTOINCREMENT,\n        name TEXT UNIQUE NOT NULL,\n        distribution_id TEXT UNIQUE NOT NULL,\n        recipient_id INTEGER UNIQUE REFERENCES recipient (_id),\n        allows_replies INTEGER DEFAULT 1,\n        deletion_timestamp INTEGER DEFAULT 0,\n        is_unknown INTEGER DEFAULT 0,\n        privacy_mode INTEGER DEFAULT 0\n      )"
      ),
      Statement(
        name = "distribution_list_member",
        sql = "CREATE TABLE distribution_list_member (\n        _id INTEGER PRIMARY KEY AUTOINCREMENT,\n        list_id INTEGER NOT NULL REFERENCES distribution_list (_id) ON DELETE CASCADE,\n        recipient_id INTEGER NOT NULL REFERENCES recipient (_id),\n        privacy_mode INTEGER DEFAULT 0\n      )"
      ),
      Statement(
        name = "recipient_group_type_index",
        sql = "CREATE INDEX recipient_group_type_index ON recipient (group_type)"
      ),
      Statement(
        name = "recipient_pni_index",
        sql = "CREATE UNIQUE INDEX recipient_pni_index ON recipient (pni)"
      ),
      Statement(
        name = "recipient_service_id_profile_key",
        sql = "CREATE INDEX recipient_service_id_profile_key ON recipient (uuid, profile_key) WHERE uuid NOT NULL AND profile_key NOT NULL"
      ),
      Statement(
        name = "mms_read_and_notified_and_thread_id_index",
        sql = "CREATE INDEX mms_read_and_notified_and_thread_id_index ON message (read, notified, thread_id)"
      ),
      Statement(
        name = "mms_type_index",
        sql = "CREATE INDEX mms_type_index ON message (type)"
      ),
      Statement(
        name = "mms_date_sent_index",
        sql = "CREATE INDEX mms_date_sent_index ON message (date_sent, recipient_id, thread_id)"
      ),
      Statement(
        name = "mms_date_server_index",
        sql = "CREATE INDEX mms_date_server_index ON message (date_server)"
      ),
      Statement(
        name = "mms_thread_date_index",
        sql = "CREATE INDEX mms_thread_date_index ON message (thread_id, date_received)"
      ),
      Statement(
        name = "mms_reactions_unread_index",
        sql = "CREATE INDEX mms_reactions_unread_index ON message (reactions_unread)"
      ),
      Statement(
        name = "mms_story_type_index",
        sql = "CREATE INDEX mms_story_type_index ON message (story_type)"
      ),
      Statement(
        name = "mms_parent_story_id_index",
        sql = "CREATE INDEX mms_parent_story_id_index ON message (parent_story_id)"
      ),
      Statement(
        name = "mms_thread_story_parent_story_scheduled_date_index",
        sql = "CREATE INDEX mms_thread_story_parent_story_scheduled_date_index ON message (thread_id, date_received, story_type, parent_story_id, scheduled_date)"
      ),
      Statement(
        name = "message_quote_id_quote_author_scheduled_date_index",
        sql = "CREATE INDEX message_quote_id_quote_author_scheduled_date_index ON message (quote_id, quote_author, scheduled_date)"
      ),
      Statement(
        name = "mms_exported_index",
        sql = "CREATE INDEX mms_exported_index ON message (exported)"
      ),
      Statement(
        name = "mms_id_type_payment_transactions_index",
        sql = "CREATE INDEX mms_id_type_payment_transactions_index ON message (_id,type) WHERE type & 12884901888 != 0"
      ),
      Statement(
        name = "part_mms_id_index",
        sql = "CREATE INDEX part_mms_id_index ON part (mid)"
      ),
      Statement(
        name = "pending_push_index",
        sql = "CREATE INDEX pending_push_index ON part (pending_push)"
      ),
      Statement(
        name = "part_sticker_pack_id_index",
        sql = "CREATE INDEX part_sticker_pack_id_index ON part (sticker_pack_id)"
      ),
      Statement(
        name = "part_data_hash_index",
        sql = "CREATE INDEX part_data_hash_index ON part (data_hash)"
      ),
      Statement(
        name = "part_data_index",
        sql = "CREATE INDEX part_data_index ON part (_data)"
      ),
      Statement(
        name = "thread_recipient_id_index",
        sql = "CREATE INDEX thread_recipient_id_index ON thread (recipient_id)"
      ),
      Statement(
        name = "archived_count_index",
        sql = "CREATE INDEX archived_count_index ON thread (archived, meaningful_messages)"
      ),
      Statement(
        name = "thread_pinned_index",
        sql = "CREATE INDEX thread_pinned_index ON thread (pinned)"
      ),
      Statement(
        name = "thread_read",
        sql = "CREATE INDEX thread_read ON thread (read)"
      ),
      Statement(
        name = "draft_thread_index",
        sql = "CREATE INDEX draft_thread_index ON drafts (thread_id)"
      ),
      Statement(
        name = "group_id_index",
        sql = "CREATE UNIQUE INDEX group_id_index ON groups (group_id)"
      ),
      Statement(
        name = "group_recipient_id_index",
        sql = "CREATE UNIQUE INDEX group_recipient_id_index ON groups (recipient_id)"
      ),
      Statement(
        name = "expected_v2_id_index",
        sql = "CREATE UNIQUE INDEX expected_v2_id_index ON groups (expected_v2_id)"
      ),
      Statement(
        name = "group_distribution_id_index",
        sql = "CREATE UNIQUE INDEX group_distribution_id_index ON groups(distribution_id)"
      ),
      Statement(
        name = "group_receipt_mms_id_index",
        sql = "CREATE INDEX group_receipt_mms_id_index ON group_receipts (mms_id)"
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
        name = "mention_message_id_index",
        sql = "CREATE INDEX mention_message_id_index ON mention (message_id)"
      ),
      Statement(
        name = "mention_recipient_id_thread_id_index",
        sql = "CREATE INDEX mention_recipient_id_thread_id_index ON mention (recipient_id, thread_id)"
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
        name = "receipt_public_key_index",
        sql = "CREATE UNIQUE INDEX receipt_public_key_index ON payments (receipt_public_key)"
      ),
      Statement(
        name = "msl_payload_date_sent_index",
        sql = "CREATE INDEX msl_payload_date_sent_index ON msl_payload (date_sent)"
      ),
      Statement(
        name = "msl_recipient_recipient_index",
        sql = "CREATE INDEX msl_recipient_recipient_index ON msl_recipient (recipient_id, device, payload_id)"
      ),
      Statement(
        name = "msl_recipient_payload_index",
        sql = "CREATE INDEX msl_recipient_payload_index ON msl_recipient (payload_id)"
      ),
      Statement(
        name = "msl_message_message_index",
        sql = "CREATE INDEX msl_message_message_index ON msl_message (message_id, payload_id)"
      ),
      Statement(
        name = "date_received_index",
        sql = "CREATE INDEX date_received_index on group_call_ring (date_received)"
      ),
      Statement(
        name = "notification_profile_schedule_profile_index",
        sql = "CREATE INDEX notification_profile_schedule_profile_index ON notification_profile_schedule (notification_profile_id)"
      ),
      Statement(
        name = "notification_profile_allowed_members_profile_index",
        sql = "CREATE INDEX notification_profile_allowed_members_profile_index ON notification_profile_allowed_members (notification_profile_id)"
      ),
      Statement(
        name = "donation_receipt_type_index",
        sql = "CREATE INDEX donation_receipt_type_index ON donation_receipt (receipt_type)"
      ),
      Statement(
        name = "donation_receipt_date_index",
        sql = "CREATE INDEX donation_receipt_date_index ON donation_receipt (receipt_date)"
      ),
      Statement(
        name = "story_sends_recipient_id_sent_timestamp_allows_replies_index",
        sql = "CREATE INDEX story_sends_recipient_id_sent_timestamp_allows_replies_index ON story_sends (recipient_id, sent_timestamp, allows_replies)"
      ),
      Statement(
        name = "story_sends_message_id_distribution_id_index",
        sql = "CREATE INDEX story_sends_message_id_distribution_id_index ON story_sends (message_id, distribution_id)"
      ),
      Statement(
        name = "distribution_list_member_list_id_recipient_id_privacy_mode_index",
        sql = "CREATE UNIQUE INDEX distribution_list_member_list_id_recipient_id_privacy_mode_index ON distribution_list_member (list_id, recipient_id, privacy_mode)"
      ),
      Statement(
        name = "pending_pni_recipient_sent_device_index",
        sql = "CREATE UNIQUE INDEX pending_pni_recipient_sent_device_index ON pending_pni_signature_message (recipient_id, sent_timestamp, device_id)"
      ),
      Statement(
        name = "call_call_id_index",
        sql = "CREATE INDEX call_call_id_index ON call (call_id)"
      ),
      Statement(
        name = "call_message_id_index",
        sql = "CREATE INDEX call_message_id_index ON call (message_id)"
      ),
      Statement(
        name = "message_ai",
        sql = "CREATE TRIGGER message_ai AFTER INSERT ON message BEGIN\n          INSERT INTO message_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id);\n        END"
      ),
      Statement(
        name = "message_ad",
        sql = "CREATE TRIGGER message_ad AFTER DELETE ON message BEGIN\n          INSERT INTO message_fts(message_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id);\n        END"
      ),
      Statement(
        name = "message_au",
        sql = "CREATE TRIGGER message_au AFTER UPDATE ON message BEGIN\n          INSERT INTO message_fts(message_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id);\n          INSERT INTO message_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id);\n        END"
      ),
      Statement(
        name = "msl_message_delete",
        sql = "CREATE TRIGGER msl_message_delete AFTER DELETE ON message \n        BEGIN \n        \tDELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old._id);\n        END"
      ),
      Statement(
        name = "msl_attachment_delete",
        sql = "CREATE TRIGGER msl_attachment_delete AFTER DELETE ON part\n        BEGIN\n        \tDELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old.mid);\n        END"
      )
    )
  }
}
