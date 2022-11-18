package org.whispersystems.signalservice.internal.configuration;

public class SignalProxy {
  private final String host;
  private final int    port;

  public SignalProxy(String host, int port) {
    this.host = host;
    this.port = port;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }
}
