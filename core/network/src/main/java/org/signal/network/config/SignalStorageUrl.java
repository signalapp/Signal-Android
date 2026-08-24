package org.signal.network.config;



import okhttp3.ConnectionSpec;

public class SignalStorageUrl extends SignalUrl {

  public SignalStorageUrl(String url, TrustStore trustStore) {
    super(url, trustStore);
  }

  public SignalStorageUrl(String url, String hostHeader, TrustStore trustStore, ConnectionSpec connectionSpec) {
    super(url, hostHeader, trustStore, connectionSpec);
  }
}
