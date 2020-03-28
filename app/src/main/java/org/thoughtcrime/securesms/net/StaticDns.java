package org.thoughtcrime.securesms.net;

import androidx.annotation.NonNull;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import okhttp3.Dns;

/**
 * A super simple {@link Dns} implementation that maps hostnames to a static IP addresses.
 */
public class StaticDns implements Dns {

  private final Map<String, String> hostnameMap;

  public StaticDns(@NonNull Map<String, String> hostnameMap) {
    this.hostnameMap = hostnameMap;
  }

  @Override
  public @NonNull List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
    String ip = hostnameMap.get(hostname);

    if (ip != null) {
      return Collections.singletonList(InetAddress.getByName(ip));
    } else {
      throw new UnknownHostException(hostname);
    }

  }
}
