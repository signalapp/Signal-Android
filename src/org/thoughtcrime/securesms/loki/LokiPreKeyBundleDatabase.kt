package org.thoughtcrime.securesms.loki

import android.content.Context
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper

class LokiPreKeyBundleDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper) {
}