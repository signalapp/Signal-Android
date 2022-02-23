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
      Spinner.DeviceInfo(
        name = "${Build.MODEL} (API ${Build.VERSION.SDK_INT})",
        packageName = packageName,
        appVersion = "0.1"
      ),
      mapOf("main" to Spinner.DatabaseConfig(db = db))
    )
  }

  private fun insertMockData(db: SQLiteDatabase) {
    for (i in 1..10000) {
      db.insert("test", null, contentValuesOf("col1" to UUID.randomUUID().toString(), "col2" to UUID.randomUUID().toString()))
    }
  }
}
