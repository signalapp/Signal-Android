package org.session.libsignal.service.internal.configuration;


import org.session.libsignal.libsignal.util.guava.Optional;
import org.session.libsignal.service.api.push.TrustStore;
import org.session.libsignal.service.internal.util.BlacklistingTrustManager;

import java.util.Collections;
import java.util.List;

import javax.net.ssl.TrustManager;

import okhttp3.ConnectionSpec;

public class SignalUrl {

  private final String                   url;
  private final Optional<String>         hostHeader;
  private final Optional<ConnectionSpec> connectionSpec;
  private       TrustStore               trustStore;

  public SignalUrl(String url, TrustStore trustStore) {
    this(url, null, trustStore, null);
  }

  public SignalUrl(String url, String hostHeader,
                   TrustStore trustStore,
                   ConnectionSpec connectionSpec)
  {
    this.url            = url;
    this.hostHeader     = Optional.fromNullable(hostHeader);
    this.trustStore     = trustStore;
    this.connectionSpec = Optional.fromNullable(connectionSpec);
  }


  public Optional<String> getHostHeader() {
    return hostHeader;
  }

  public String getUrl() {
    return url;
  }

  public TrustStore getTrustStore() {
    return trustStore;
  }

  public Optional<List<ConnectionSpec>> getConnectionSpecs() {
    return connectionSpec.isPresent() ? Optional.of(Collections.singletonList(connectionSpec.get())) : Optional.<List<ConnectionSpec>>absent();
  }

}
