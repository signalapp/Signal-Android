package org.thoughtcrime.securesms.jobmanager.impl

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.util.FeatureFlags

@Suppress("DEPRECATION")
object NewNetworkConnectivity {

  private val TAG: String = Log.tag(NewNetworkConnectivity::class.java)
  private val context: Application = ApplicationDependencies.getApplication()

  private lateinit var connectivityManager: ConnectivityManager
  private var retryCount = 0

  private var callback: NetworkCallback? = null
  private var handler: Handler? = null
  private var hasInternet: Boolean? = null
  private var previousHasInternet: Boolean? = null

  @JvmStatic
  fun start() {
    if (FeatureFlags.internalUser() && Build.VERSION.SDK_INT >= 26) {
      connectivityManager = ContextCompat.getSystemService(context, ConnectivityManager::class.java)!!

      val thread = SignalExecutors.getAndStartHandlerThread("NewNetwork")
      handler = Handler(thread.looper)
      handler!!.post(this::requestNetwork)
    }
  }

  @RequiresApi(26)
  private fun requestNetwork() {
    if (retryCount > 5) {
      Log.internal().w(TAG, "Unable to request network after $retryCount attempts")
    } else {
      callback?.let { connectivityManager.unregisterNetworkCallback(it) }

      Log.internal().i(TAG, "Requesting network attempt: $retryCount")
      val callback = NetworkCallback()
      connectivityManager.requestNetwork(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback, handler!!)
      this.callback = callback
    }
  }

  @JvmStatic
  fun consistencyCheck() {
    if (FeatureFlags.internalUser() && Build.VERSION.SDK_INT >= 26) {
      handler?.post {
        val oldActiveNetwork: NetworkInfo? = connectivityManager.activeNetworkInfo
        val oldIsMet: Boolean = isMet(oldActiveNetwork)

        if (hasInternet == null) {
          Log.internal().w(TAG, "New has not been initialized yet, defaulting to false")
          hasInternet = false
        }

        when {
          oldIsMet == hasInternet && previousHasInternet != hasInternet -> {
            Log.internal().i(TAG, "Old and new are consistent: $hasInternet")
            previousHasInternet = hasInternet
          }
          oldIsMet != hasInternet && previousHasInternet != hasInternet -> {
            Log.internal().w(TAG, "Network inconsistent old: $oldIsMet new: $hasInternet", Throwable())
            Log.internal().w(TAG, "Old active network: $oldActiveNetwork")

            previousHasInternet = hasInternet
            postInternalErrorNotification()
          }
        }
      } ?: Log.internal().w(TAG, "New handler has not been initialized yet")
    }
  }

  private fun isMet(oldActiveNetwork: NetworkInfo?): Boolean {
    return oldActiveNetwork != null && oldActiveNetwork.isConnected
  }

  private fun postInternalErrorNotification() {
    if (!FeatureFlags.internalUser()) return

    NotificationManagerCompat.from(context).notify(
      NotificationIds.INTERNAL_NET_ERROR,
      NotificationCompat.Builder(context, NotificationChannels.FAILURES)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Network check inconsistency")
        .setContentText(context.getString(R.string.MessageDecryptionUtil_tap_to_send_a_debug_log))
        .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, SubmitDebugLogActivity::class.java), 0))
        .build()
    )
  }

  @RequiresApi(26)
  private class NetworkCallback : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
      Log.internal().i(TAG, "onAvailable $network ${connectivityManager.getNetworkCapabilities(network)}")
      retryCount = 0
      hasInternet = true
      consistencyCheck()
    }

    override fun onLost(network: Network) {
      Log.internal().i(TAG, "onLost $network")
      retryCount = 0
      hasInternet = false
      consistencyCheck()
    }

    override fun onUnavailable() {
      retryCount++
      requestNetwork()
    }
  }
}
