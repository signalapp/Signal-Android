package org.thoughtcrime.securesms.net;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.logging.Log;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import okhttp3.Dns;

/**
 * Iterates through an ordered list of {@link Dns}, trying each one in sequence.
 */
public class SequentialDns implements Dns {

  private static final String TAG = Log.tag(SequentialDns.class);

  private List<Dns> dnsList;

  public SequentialDns(Dns... dns) {
    this.dnsList = Arrays.asList(dns);
  }

  @Override
  public @NonNull List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
    for (Dns dns : dnsList) {
      try {
        List<InetAddress> addresses = dns.lookup(hostname);
        if (addresses.size() > 0) {
          return addresses;
        } else {
          Log.w(TAG, String.format(Locale.ENGLISH, "Didn't find any addresses for %s using %s. Continuing.", hostname, dns.getClass().getSimpleName()));
        }
      } catch (UnknownHostException e) {
        Log.w(TAG, String.format(Locale.ENGLISH, "Failed to resolve %s using %s. Continuing.", hostname, dns.getClass().getSimpleName()));
      }
    }
    Log.w(TAG, "Failed to resolve using any DNS.");
    throw new UnknownHostException(hostname);
  }
}
