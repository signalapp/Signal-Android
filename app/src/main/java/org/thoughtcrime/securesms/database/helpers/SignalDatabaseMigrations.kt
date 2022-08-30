package org.thoughtcrime.securesms.database.helpers

import android.app.Application
import android.content.Context
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.thoughtcrime.securesms.database.helpers.migration.V149_LegacyMigrations
import org.thoughtcrime.securesms.database.helpers.migration.V150_UrgentMslFlagMigration
import org.thoughtcrime.securesms.database.helpers.migration.V151_MyStoryMigration
import org.thoughtcrime.securesms.database.helpers.migration.V152_StoryGroupTypesMigration
import org.thoughtcrime.securesms.database.helpers.migration.V153_MyStoryMigration
import org.thoughtcrime.securesms.database.helpers.migration.V154_PniSignaturesMigration
import org.thoughtcrime.securesms.database.helpers.migration.V155_SmsExporterMigration

/**
 * Contains all of the database migrations for [SignalDatabase]. Broken into a separate file for cleanliness.
 */
object SignalDatabaseMigrations {

  const val DATABASE_VERSION = 155

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
  }

  @JvmStatic
  fun migratePostTransaction(context: Context, oldVersion: Int) {
    if (oldVersion < V149_LegacyMigrations.MIGRATE_PREKEYS_VERSION) {
      PreKeyMigrationHelper.cleanUpPreKeys(context)
    }
  }
}
