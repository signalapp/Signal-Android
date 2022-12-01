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

/**
 * Contains all of the database migrations for [SignalDatabase]. Broken into a separate file for cleanliness.
 */
object SignalDatabaseMigrations {

  val TAG: String = Log.tag(SignalDatabaseMigrations.javaClass)

  const val DATABASE_VERSION = 165

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
  }

  @JvmStatic
  fun migratePostTransaction(context: Context, oldVersion: Int) {
    if (oldVersion < V149_LegacyMigrations.MIGRATE_PREKEYS_VERSION) {
      PreKeyMigrationHelper.cleanUpPreKeys(context)
    }
  }
}
