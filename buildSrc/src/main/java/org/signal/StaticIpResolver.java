package org.signal;

import org.gradle.internal.impldep.org.eclipse.jgit.annotations.NonNull;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public final class StaticIpResolver {

  private StaticIpResolver() {}

  public static String resolveToBuildConfig(String hostName) {
    String[] ips = resolve(hostName);
    StringBuilder builder = new StringBuilder();
    builder.append("new String[]{");
    for (int i = 0; i < ips.length; i++) {
      builder.append("\"").append(ips[i]).append("\"");
      if (i < ips.length - 1) {
        builder.append(",");
      }
    }
    return builder.append("}").toString();
  }

  private static String[] resolve(String hostName) {
    try {
      Resolver resolver = new SimpleResolver("1.1.1.1");
      Lookup   lookup   = doLookup(hostName);

      lookup.setResolver(resolver);

      Record[] records = lookup.run();

      if (records != null) {
        return Arrays.stream(records)
                     .filter(r -> r.getType() == Type.A)
                     .map(r -> (ARecord) r)
                     .map(ARecord::getAddress)
                     .map(InetAddress::getHostAddress)
                     .toArray(String[]::new);
      } else {
        throw new IllegalStateException("Failed to resolve host! " + hostName);
      }
    } catch (UnknownHostException e) {
      throw new IllegalStateException("Failed to resolve host! " + hostName);
    }
  }

  private static @NonNull Lookup doLookup(@NonNull String hostname) throws UnknownHostException {
    try {
      return new Lookup(hostname);
    } catch (Throwable e) {
      throw new UnknownHostException();
    }
  }
}
