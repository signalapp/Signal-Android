package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.telephony.TelephonyManager;

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

  public static boolean isConnected(@NonNull Context context) {
    final NetworkInfo info = getNetworkInfo(context);
    return info != null && info.isConnected();
  }

  public static @NonNull CallManager.DataMode getCallingDataMode(@NonNull Context context) {
    return getCallingDataMode(context, PeerConnection.AdapterType.UNKNOWN);
  }

  public static @NonNull CallManager.DataMode getCallingDataMode(@NonNull Context context, @NonNull PeerConnection.AdapterType networkAdapter) {
    if (SignalStore.internal().callingDataMode() != CallManager.DataMode.NORMAL) {
      return SignalStore.internal().callingDataMode();
    }

    return useLowDataCalling(context, networkAdapter) ? CallManager.DataMode.LOW : CallManager.DataMode.NORMAL;
  }

  public static String getNetworkTypeDescriptor(@NonNull Context context) {
    NetworkInfo info = getNetworkInfo(context);
    if (info == null || !info.isConnected()) {
      return "NOT CONNECTED";
    } else if (info.getType() == ConnectivityManager.TYPE_WIFI) {
      return "WIFI";
    } else if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
      int networkType = info.getSubtype();
      switch (networkType) {
        case TelephonyManager.NETWORK_TYPE_GPRS:     return "MOBILE - GPRS";
        case TelephonyManager.NETWORK_TYPE_EDGE:     return "MOBILE - EDGE";
        case TelephonyManager.NETWORK_TYPE_CDMA:     return "MOBILE - CDMA";
        case TelephonyManager.NETWORK_TYPE_1xRTT:    return "MOBILE - 1xRTT";
        case TelephonyManager.NETWORK_TYPE_IDEN:     return "MOBILE - IDEN";
        case TelephonyManager.NETWORK_TYPE_GSM:      return "MOBILE - GSM";
        case TelephonyManager.NETWORK_TYPE_UMTS:     return "MOBILE - UMTS";
        case TelephonyManager.NETWORK_TYPE_EVDO_0:   return "MOBILE - EVDO_0";
        case TelephonyManager.NETWORK_TYPE_EVDO_A:   return "MOBILE - EVDO_A";
        case TelephonyManager.NETWORK_TYPE_HSDPA:    return "MOBILE - HSDPA";
        case TelephonyManager.NETWORK_TYPE_HSUPA:    return "MOBILE - HSUPA";
        case TelephonyManager.NETWORK_TYPE_HSPA:     return "MOBILE - HSPA";
        case TelephonyManager.NETWORK_TYPE_EVDO_B:   return "MOBILE - EVDO_B";
        case TelephonyManager.NETWORK_TYPE_EHRPD:    return "MOBILE - EHRDP";
        case TelephonyManager.NETWORK_TYPE_HSPAP:    return "MOBILE - HSPAP";
        case TelephonyManager.NETWORK_TYPE_TD_SCDMA: return "MOBILE - TD_SCDMA";
        case TelephonyManager.NETWORK_TYPE_LTE:      return "MOBILE - LTE";
        case TelephonyManager.NETWORK_TYPE_IWLAN:    return "MOBILE - IWLAN";
        case 19:                                     return "MOBILE - LTE_CA";
        case TelephonyManager.NETWORK_TYPE_NR:       return "MOBILE - NR";
        default:                                     return "MOBILE - OTHER";
      }
    } else {
      return "UNKNOWN";
    }
  }

  public static @NonNull NetworkStatus getNetworkStatus(@NonNull Context context) {
    ConnectivityManager connectivityManager = ServiceUtil.getConnectivityManager(context);

    if (Build.VERSION.SDK_INT >= 23) {
      Network             network      = connectivityManager.getActiveNetwork();
      NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);

      boolean onVpn        = capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
      boolean isNotMetered = capabilities == null || capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);

      return new NetworkStatus(onVpn, !isNotMetered);
    } else {
      return new NetworkStatus(false, false);
    }
  }

  private static boolean useLowDataCalling(@NonNull Context context, @NonNull PeerConnection.AdapterType networkAdapter) {
    switch (SignalStore.settings().getCallDataMode()) {
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
