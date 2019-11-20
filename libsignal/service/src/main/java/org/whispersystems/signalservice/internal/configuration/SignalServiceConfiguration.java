package org.whispersystems.signalservice.internal.configuration;


public class SignalServiceConfiguration {

  private final SignalServiceUrl[]          signalServiceUrls;
  private final SignalCdnUrl[]              signalCdnUrls;
  private final SignalContactDiscoveryUrl[] signalContactDiscoveryUrls;

  public SignalServiceConfiguration(SignalServiceUrl[] signalServiceUrls, SignalCdnUrl[] signalCdnUrls, SignalContactDiscoveryUrl[] signalContactDiscoveryUrls) {
    this.signalServiceUrls          = signalServiceUrls;
    this.signalCdnUrls              = signalCdnUrls;
    this.signalContactDiscoveryUrls = signalContactDiscoveryUrls;
  }

  public SignalServiceUrl[] getSignalServiceUrls() {
    return signalServiceUrls;
  }

  public SignalCdnUrl[] getSignalCdnUrls() {
    return signalCdnUrls;
  }

  public SignalContactDiscoveryUrl[] getSignalContactDiscoveryUrls() {
    return signalContactDiscoveryUrls;
  }
}
