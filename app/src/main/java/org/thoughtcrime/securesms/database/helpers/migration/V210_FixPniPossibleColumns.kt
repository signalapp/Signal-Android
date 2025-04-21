/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.requireString
import org.thoughtcrime.securesms.database.KeyValueDatabase
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.whispersystems.signalservice.api.push.ServiceId

/**
 * We moved to a strongly typed PNI but did not update all places where a PNI may pre-exist in
 * our database. This updates the other tables where our PNI may already be in use without the 'PNI:'
 * prefix.
 *
 * This is an issue for installs that setup PNI related data prior to the strongly typed PNI system and
 * then become PNP enabled.
 */
@Suppress("ClassName")
object V210_FixPniPossibleColumns : SignalDatabaseMigration {

  private val TAG = Log.tag(V210_FixPniPossibleColumns::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val pni = getLocalPni(context)?.toStringWithoutPrefix()

    if (pni == null) {
      Log.i(TAG, "No local PNI, nothing to migrate")
      return
    }

    db.execSQL("UPDATE OR IGNORE identities SET address = 'PNI:' || address WHERE address = '$pni'")
    db.execSQL("UPDATE OR IGNORE one_time_prekeys SET account_id = 'PNI:' || account_id WHERE account_id = '$pni'")
    db.execSQL("UPDATE OR IGNORE kyber_prekey SET account_id = 'PNI:' || account_id WHERE account_id = '$pni'")
    db.execSQL("UPDATE OR IGNORE sender_key_shared SET address = 'PNI:' || address WHERE address = '$pni'")
    db.execSQL("UPDATE OR IGNORE sender_keys SET address = 'PNI:' || address WHERE address = '$pni'")
    db.execSQL("UPDATE OR IGNORE sessions SET address = 'PNI:' || address WHERE address = '$pni'")
    db.execSQL("UPDATE OR IGNORE signed_prekeys SET account_id = 'PNI:' || account_id WHERE account_id = '$pni'")
  }

  private fun getLocalPni(context: Application): ServiceId.PNI? {
    if (KeyValueDatabase.exists(context)) {
      val keyValueDatabase = KeyValueDatabase.getInstance(context).readableDatabase
      keyValueDatabase.query("key_value", arrayOf("value"), "key = ?", SqlUtil.buildArgs("account.pni"), null, null, null).use { cursor ->
        return if (cursor.moveToFirst()) {
          ServiceId.PNI.parseOrNull(cursor.requireString("value"))
        } else {
          null
        }
      }
    } else {
      return null
    }
  }
}
