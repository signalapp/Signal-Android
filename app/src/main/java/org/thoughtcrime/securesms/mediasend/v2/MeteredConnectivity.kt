package org.thoughtcrime.securesms.mediasend.v2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import androidx.core.net.ConnectivityManagerCompat
import io.reactivex.rxjava3.core.Observable
import org.thoughtcrime.securesms.util.ServiceUtil

@Suppress("DEPRECATION")
object MeteredConnectivity {
  fun isMetered(context: Context): Observable<Boolean> = Observable.create { emitter ->
    val connectivityManager = ServiceUtil.getConnectivityManager(context)

    emitter.onNext(ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager))

    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        emitter.onNext(ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager))
      }
    }

    context.registerReceiver(receiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

    emitter.setCancellable {
      context.unregisterReceiver(receiver)
    }
  }
}
