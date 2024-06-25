package org.thoughtcrime.securesms.notifications

import android.os.Build
import androidx.annotation.VisibleForTesting
import com.fasterxml.jackson.annotation.JsonProperty
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.JsonUtils
import org.thoughtcrime.securesms.util.RemoteConfig
import java.io.IOException

/**
 * Remote configs for a device to show a support screen in an effort to prevent delayed notifications
 */
object DelayedNotificationConfig {

  private val TAG = Log.tag(DelayedNotificationConfig::class.java)
  private const val GENERAL_SUPPORT_URL = "https://support.signal.org/hc/articles/360007318711#android_notifications_troubleshooting"

  val currentConfig: Config by lazy { computeConfig() }

  /**
   * Maps a device model to specific modifications set in order to support better notification
   * @param model either exact device model name or model name that ends with a wildcard
   * @param showPreemptively shows support sheet immediately if true or after a vitals failure if not, still dependent on localePercent
   * @param link represents the Signal support url that corresponds to this device model
   * @param localePercent represents the percent of people who will get this change per country
   */
  data class Config(
    @JsonProperty val model: String = "",
    @JsonProperty val showPreemptively: Boolean = false,
    @JsonProperty val link: String = GENERAL_SUPPORT_URL,
    @JsonProperty val localePercent: String = RemoteConfig.promptBatterySaver
  )

  @VisibleForTesting
  fun computeConfig(): Config {
    val default = Config()
    val serialized = RemoteConfig.promptDelayedNotificationConfig
    if (serialized.isNullOrBlank()) {
      return default
    }

    val list: List<Config> = try {
      JsonUtils.fromJsonArray(serialized, Config::class.java)
    } catch (e: IOException) {
      Log.w(TAG, "Failed to parse json!", e)
      emptyList()
    }

    val config: Config? = list
      .filter { it.model.isNotEmpty() }
      .find {
        if (it.model.last() == '*') {
          Build.MODEL.startsWith(it.model.substring(0, it.model.length - 1))
        } else {
          it.model.contains(Build.MODEL)
        }
      }

    return config ?: default
  }
}
