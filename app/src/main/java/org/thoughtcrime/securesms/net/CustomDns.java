package org.thoughtcrime.securesms.net;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.util.NetworkUtil;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

import okhttp3.Dns;

/**
 * A {@link Dns} implementation that specifies the hostname of a specific DNS.
 */
public class CustomDns implements Dns {

  private static final String TAG = Log.tag(CustomDns.class);

  private final String dnsHostname;

  public CustomDns(@NonNull String dnsHostname) {
    this.dnsHostname = dnsHostname;
  }

  @Override
  public @NonNull List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
    Resolver resolver = new SimpleResolver(dnsHostname);
    Lookup   lookup   = doLookup(hostname);

    lookup.setResolver(resolver);

    Record[] records = lookup.run();

    if (records != null) {
      List<InetAddress> ipv4Addresses = Stream.of(records)
                                              .filter(r -> r.getType() == Type.A)
                                              .map(r -> (ARecord) r)
                                              .map(ARecord::getAddress)
                                              .toList();
      if (ipv4Addresses.size() > 0) {
        return ipv4Addresses;
      }
    }

    throw new UnknownHostException(hostname);
  }

  private static @NonNull Lookup doLookup(@NonNull String hostname) throws UnknownHostException {
    try {
      return new Lookup(hostname);
    } catch (Throwable e) {
      Log.w(TAG, e);
      throw new UnknownHostException();
    }
  }
}
