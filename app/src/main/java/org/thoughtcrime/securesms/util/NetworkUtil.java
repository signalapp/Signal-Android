package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import androidx.annotation.NonNull;

import org.signal.ringrtc.CallManager;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.webrtc.PeerConnection;

public final class NetworkUtil {

  private NetworkUtil() {}

  public static boolean isConnectedWifi(@NonNull Context context) {
    final NetworkInfo info = getNetworkInfo(context);
    return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI;
  }

  public static boolean isConnectedMobile(@NonNull Context context) {
    final NetworkInfo info = getNetworkInfo(context);
    return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_MOBILE;
  }

  public static boolean isConnectedRoaming(@NonNull Context context) {
    final NetworkInfo info = getNetworkInfo(context);
    return info != null && info.isConnected() && info.isRoaming() && info.getType() == ConnectivityManager.TYPE_MOBILE;
  }

  public static @NonNull CallManager.BandwidthMode getCallingBandwidthMode(@NonNull Context context) {
    return getCallingBandwidthMode(context, PeerConnection.AdapterType.UNKNOWN);
  }

  public static @NonNull CallManager.BandwidthMode getCallingBandwidthMode(@NonNull Context context, @NonNull PeerConnection.AdapterType networkAdapter) {
    return useLowBandwidthCalling(context, networkAdapter) ? CallManager.BandwidthMode.LOW : CallManager.BandwidthMode.NORMAL;
  }

  private static boolean useLowBandwidthCalling(@NonNull Context context, @NonNull PeerConnection.AdapterType networkAdapter) {
    switch (SignalStore.settings().getCallBandwidthMode()) {
      case HIGH_ON_WIFI:
        switch (networkAdapter) {
          case UNKNOWN:
          case VPN:
          case ADAPTER_TYPE_ANY:
            return !NetworkUtil.isConnectedWifi(context);
          case ETHERNET:
          case WIFI:
          case LOOPBACK:
            return false;
          case CELLULAR:
          case CELLULAR_2G:
          case CELLULAR_3G:
          case CELLULAR_4G:
          case CELLULAR_5G:
            return true;
        }
      case HIGH_ALWAYS:
        return false;
      default:
        return true;
    }
  }

  private static NetworkInfo getNetworkInfo(@NonNull Context context) {
    return ServiceUtil.getConnectivityManager(context).getActiveNetworkInfo();
  }
}
