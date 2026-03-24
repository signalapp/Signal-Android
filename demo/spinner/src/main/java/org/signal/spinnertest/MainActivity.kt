package org.signal.spinnertest

import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.contentValuesOf
import org.signal.core.util.ThreadUtil
import org.signal.core.util.logging.AndroidLogger
import org.signal.core.util.logging.Log
import org.signal.spinner.Spinner
import org.signal.spinner.SpinnerLogger
import java.util.UUID
import kotlin.random.Random

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

    Log.initialize(AndroidLogger, SpinnerLogger)

    object : Thread() {
      override fun run() {
        while (true) {
          when (Random.nextInt(0, 5)) {
            0 -> Log.v("MyTag", "Message: ${System.currentTimeMillis()}")
            1 -> Log.d("MyTag", "Message: ${System.currentTimeMillis()}")
            2 -> Log.i("MyTag", "Message: ${System.currentTimeMillis()}")
            3 -> Log.w("MyTag", "Message: ${System.currentTimeMillis()}")
            4 -> Log.e("MyTag", "Message: ${System.currentTimeMillis()}")
          }
          ThreadUtil.sleep(Random.nextLong(0, 200))
        }
      }
    }.start()

    findViewById<Button>(R.id.log_throwable_button).setOnClickListener { Log.e("MyTag", "Message: ${System.currentTimeMillis()}", Throwable()) }
  }

  private fun insertMockData(db: SQLiteDatabase) {
    for (i in 1..10000) {
      db.insert("test", null, contentValuesOf("col1" to UUID.randomUUID().toString(), "col2" to UUID.randomUUID().toString()))
    }
  }
}
