package org.thoughtcrime.securesms.database.helpers

import android.app.Application
import android.content.Context
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.helpers.migration.V149_LegacyMigrations
import org.thoughtcrime.securesms.database.helpers.migration.V150_UrgentMslFlagMigration
import org.thoughtcrime.securesms.database.helpers.migration.V151_MyStoryMigration
import org.thoughtcrime.securesms.database.helpers.migration.V152_StoryGroupTypesMigration
import org.thoughtcrime.securesms.database.helpers.migration.V153_MyStoryMigration
import org.thoughtcrime.securesms.database.helpers.migration.V154_PniSignaturesMigration
import org.thoughtcrime.securesms.database.helpers.migration.V155_SmsExporterMigration
import org.thoughtcrime.securesms.database.helpers.migration.V156_RecipientUnregisteredTimestampMigration
import org.thoughtcrime.securesms.database.helpers.migration.V157_RecipeintHiddenMigration
import org.thoughtcrime.securesms.database.helpers.migration.V158_GroupsLastForceUpdateTimestampMigration
import org.thoughtcrime.securesms.database.helpers.migration.V159_ThreadUnreadSelfMentionCount
import org.thoughtcrime.securesms.database.helpers.migration.V160_SmsMmsExportedIndexMigration
import org.thoughtcrime.securesms.database.helpers.migration.V161_StorySendMessageIdIndex
import org.thoughtcrime.securesms.database.helpers.migration.V162_ThreadUnreadSelfMentionCountFixup
import org.thoughtcrime.securesms.database.helpers.migration.V163_RemoteMegaphoneSnoozeSupportMigration
import org.thoughtcrime.securesms.database.helpers.migration.V164_ThreadDatabaseReadIndexMigration
import org.thoughtcrime.securesms.database.helpers.migration.V165_MmsMessageBoxPaymentTransactionIndexMigration
import org.thoughtcrime.securesms.database.helpers.migration.V166_ThreadAndMessageForeignKeys
import org.thoughtcrime.securesms.database.helpers.migration.V167_RecreateReactionTriggers
import org.thoughtcrime.securesms.database.helpers.migration.V168_SingleMessageTableMigration
import org.thoughtcrime.securesms.database.helpers.migration.V169_EmojiSearchIndexRank
import org.thoughtcrime.securesms.database.helpers.migration.V170_CallTableMigration
import org.thoughtcrime.securesms.database.helpers.migration.V171_ThreadForeignKeyFix
import org.thoughtcrime.securesms.database.helpers.migration.V172_GroupMembershipMigration
import org.thoughtcrime.securesms.database.helpers.migration.V173_ScheduledMessagesMigration
import org.thoughtcrime.securesms.database.helpers.migration.V174_ReactionForeignKeyMigration
import org.thoughtcrime.securesms.database.helpers.migration.V175_FixFullTextSearchLink
import org.thoughtcrime.securesms.database.helpers.migration.V176_AddScheduledDateToQuoteIndex
import org.thoughtcrime.securesms.database.helpers.migration.V177_MessageSendLogTableCleanupMigration
import org.thoughtcrime.securesms.database.helpers.migration.V178_ReportingTokenColumnMigration
import org.thoughtcrime.securesms.database.helpers.migration.V179_CleanupDanglingMessageSendLogMigration
import org.thoughtcrime.securesms.database.helpers.migration.V180_RecipientNicknameMigration
import org.thoughtcrime.securesms.database.helpers.migration.V181_ThreadTableForeignKeyCleanup
import org.thoughtcrime.securesms.database.helpers.migration.V182_CallTableMigration
import org.thoughtcrime.securesms.database.helpers.migration.V183_CallLinkTableMigration
import org.thoughtcrime.securesms.database.helpers.migration.V184_CallLinkReplaceIndexMigration

/**
 * Contains all of the database migrations for [SignalDatabase]. Broken into a separate file for cleanliness.
 */
object SignalDatabaseMigrations {

  val TAG: String = Log.tag(SignalDatabaseMigrations.javaClass)

  const val DATABASE_VERSION = 184

  @JvmStatic
  fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 149) {
      V149_LegacyMigrations.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 150) {
      V150_UrgentMslFlagMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 151) {
      V151_MyStoryMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 152) {
      V152_StoryGroupTypesMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 153) {
      V153_MyStoryMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 154) {
      V154_PniSignaturesMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 155) {
      V155_SmsExporterMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 156) {
      V156_RecipientUnregisteredTimestampMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 157) {
      V157_RecipeintHiddenMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 158) {
      V158_GroupsLastForceUpdateTimestampMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 159) {
      V159_ThreadUnreadSelfMentionCount.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 160) {
      V160_SmsMmsExportedIndexMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 161) {
      V161_StorySendMessageIdIndex.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 162) {
      V162_ThreadUnreadSelfMentionCountFixup.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 163) {
      V163_RemoteMegaphoneSnoozeSupportMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 164) {
      V164_ThreadDatabaseReadIndexMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 165) {
      V165_MmsMessageBoxPaymentTransactionIndexMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 166) {
      V166_ThreadAndMessageForeignKeys.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 167) {
      V167_RecreateReactionTriggers.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 168) {
      V168_SingleMessageTableMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 169) {
      V169_EmojiSearchIndexRank.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 170) {
      V170_CallTableMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 171) {
      V171_ThreadForeignKeyFix.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 172) {
      V172_GroupMembershipMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 173) {
      V173_ScheduledMessagesMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 174) {
      V174_ReactionForeignKeyMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 175) {
      V175_FixFullTextSearchLink.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 176) {
      V176_AddScheduledDateToQuoteIndex.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 177) {
      V177_MessageSendLogTableCleanupMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 178) {
      V178_ReportingTokenColumnMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 179) {
      V179_CleanupDanglingMessageSendLogMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 180) {
      V180_RecipientNicknameMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 181) {
      V181_ThreadTableForeignKeyCleanup.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 182) {
      V182_CallTableMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 183) {
      V183_CallLinkTableMigration.migrate(context, db, oldVersion, newVersion)
    }

    if (oldVersion < 184) {
      V184_CallLinkReplaceIndexMigration.migrate(context, db, oldVersion, newVersion)
    }
  }

  @JvmStatic
  fun migratePostTransaction(context: Context, oldVersion: Int) {
    if (oldVersion < V149_LegacyMigrations.MIGRATE_PREKEYS_VERSION) {
      PreKeyMigrationHelper.cleanUpPreKeys(context)
    }
  }
}
