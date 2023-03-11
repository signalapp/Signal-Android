package org.signal.core.util

import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery

fun SupportSQLiteQuery.toAndroidQuery(): SqlUtil.Query {
  val program = CapturingSqliteProgram(this.argCount)
  this.bindTo(program)
  return SqlUtil.Query(this.sql, program.args())
}

private class CapturingSqliteProgram(count: Int) : SupportSQLiteProgram {
  private val args: Array<String?> = arrayOfNulls(count)

  fun args(): Array<String> {
    return args.filterNotNull().toTypedArray()
  }

  override fun close() {
  }

  override fun bindNull(index: Int) {
    throw UnsupportedOperationException()
  }

  override fun bindLong(index: Int, value: Long) {
    args[index - 1] = value.toString()
  }

  override fun bindDouble(index: Int, value: Double) {
    args[index - 1] = value.toString()
  }

  override fun bindString(index: Int, value: String?) {
    args[index - 1] = value
  }

  override fun bindBlob(index: Int, value: ByteArray?) {
    throw UnsupportedOperationException()
  }

  override fun clearBindings() {
    for (i in args.indices) {
      args[i] = null
    }
  }
}
