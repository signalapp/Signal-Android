package org.signal.spinner

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import org.signal.core.util.logging.Log
import java.io.IOException

/**
 * A class to help initialize Spinner, our database debugging interface.
 */
object Spinner {
  val TAG: String = Log.tag(Spinner::class.java)

  fun init(context: Context, deviceInfo: DeviceInfo, databases: Map<String, SupportSQLiteDatabase>) {
    try {
      SpinnerServer(context, deviceInfo, databases).start()
    } catch (e: IOException) {
      Log.w(TAG, "Spinner server hit IO exception! Restarting.", e)
    }
  }

  data class DeviceInfo(
    val name: String,
    val packageName: String,
    val appVersion: String
  )
}
