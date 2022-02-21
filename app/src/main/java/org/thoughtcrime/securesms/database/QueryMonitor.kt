package org.thoughtcrime.securesms.database

import android.content.ContentValues

interface QueryMonitor {
  fun onSql(sql: String, args: Array<Any>?)
  fun onQuery(distinct: Boolean, table: String, projection: Array<String>?, selection: String?, args: Array<Any>?, groupBy: String?, having: String?, orderBy: String?, limit: String?)
  fun onDelete(table: String, selection: String?, args: Array<Any>?)
  fun onUpdate(table: String, values: ContentValues, selection: String?, args: Array<Any>?)
}
