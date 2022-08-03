package org.thoughtcrime.securesms.testing

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import org.thoughtcrime.securesms.SignalInstrumentationApplicationContext

/**
 * Custom runner that replaces application with [SignalInstrumentationApplicationContext].
 */
@Suppress("unused")
class SignalTestRunner : AndroidJUnitRunner() {
  override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
    return super.newApplication(cl, SignalInstrumentationApplicationContext::class.java.name, context)
  }
}
