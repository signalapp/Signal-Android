package org.signal.spinnertest

import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.contentValuesOf
import org.signal.spinner.Spinner
import java.util.UUID

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    val db = SpinnerTestSqliteOpenHelper(applicationContext)

//    insertMockData(db.writableDatabase)

    Spinner.init(
      application,
      mapOf(
        "Name" to { "${Build.MODEL} (API ${Build.VERSION.SDK_INT})" },
        "Package" to { packageName }
      ),
      mapOf("main" to Spinner.DatabaseConfig(db = { db })),
      emptyMap()
    )
  }

  private fun insertMockData(db: SQLiteDatabase) {
    for (i in 1..10000) {
      db.insert("test", null, contentValuesOf("col1" to UUID.randomUUID().toString(), "col2" to UUID.randomUUID().toString()))
    }
  }
}
