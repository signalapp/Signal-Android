package org.whispersystems.signalservice.internal.configuration;


import org.whispersystems.signalservice.api.push.TrustStore;

import okhttp3.ConnectionSpec;

public class SignalCdsiUrl extends SignalUrl {

  public SignalCdsiUrl(String url, TrustStore trustStore) {
    super(url, trustStore);
  }

  public SignalCdsiUrl(String url, String hostHeader, TrustStore trustStore, ConnectionSpec connectionSpec) {
    super(url, hostHeader, trustStore, connectionSpec);
  }
}
