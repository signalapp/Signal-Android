package org.thoughtcrime.securesms.database

import android.content.ContentValues

object DatabaseMonitor {
  private var queryMonitor: QueryMonitor? = null

  fun initialize(queryMonitor: QueryMonitor?) {
    DatabaseMonitor.queryMonitor = queryMonitor
  }

  @JvmStatic
  fun onSql(sql: String, args: Array<Any>?) {
    queryMonitor?.onSql(sql, args)
  }

  @JvmStatic
  fun onQuery(distinct: Boolean, table: String, projection: Array<String>?, selection: String?, args: Array<Any>?, groupBy: String?, having: String?, orderBy: String?, limit: String?) {
    queryMonitor?.onQuery(distinct, table, projection, selection, args, groupBy, having, orderBy, limit)
  }

  @JvmStatic
  fun onDelete(table: String, selection: String?, args: Array<Any>?) {
    queryMonitor?.onDelete(table, selection, args)
  }

  @JvmStatic
  fun onUpdate(table: String, values: ContentValues, selection: String?, args: Array<Any>?) {
    queryMonitor?.onUpdate(table, values, selection, args)
  }
}
