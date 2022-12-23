package org.thoughtcrime.securesms.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import androidx.annotation.RequiresApi
import io.reactivex.rxjava3.core.Observable
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint

object InternetConnectionObserver {
  /**
   * Observe network availability changes.
   */
  fun observe(): Observable<Boolean> {
    return if (Build.VERSION.SDK_INT >= 24) {
      observeApi24()
    } else {
      observeApi19()
    }
  }

  @RequiresApi(24)
  private fun observeApi24(): Observable<Boolean> {
    return Observable.create {
      val application = ApplicationDependencies.getApplication()
      val connectivityManager = ServiceUtil.getConnectivityManager(application)

      val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
          it.onNext(true)
        }

        override fun onLost(network: Network) {
          it.onNext(false)
        }
      }

      connectivityManager.registerDefaultNetworkCallback(callback)

      it.setCancellable {
        connectivityManager.unregisterNetworkCallback(callback)
      }
    }
  }

  @Suppress("DEPRECATION")
  private fun observeApi19(): Observable<Boolean> {
    return Observable.create {
      val application = ApplicationDependencies.getApplication()

      val observer = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
          if (!it.isDisposed) {
            it.onNext(NetworkConstraint.isMet(application))
          }
        }
      }

      it.setCancellable { application.unregisterReceiver(observer) }
      application.registerReceiver(observer, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }
  }
}
