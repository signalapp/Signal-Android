package org.whispersystems.signalservice.internal.configuration;


public class SignalServiceConfiguration {

  private final SignalServiceUrl[]          signalServiceUrls;
  private final SignalCdnUrl[]              signalCdnUrls;
  private final SignalContactDiscoveryUrl[] signalContactDiscoveryUrls;
  private final SignalKeyBackupServiceUrl[] signalKeyBackupServiceUrls;

  public SignalServiceConfiguration(SignalServiceUrl[] signalServiceUrls,
                                    SignalCdnUrl[] signalCdnUrls,
                                    SignalContactDiscoveryUrl[] signalContactDiscoveryUrls,
                                    SignalKeyBackupServiceUrl[] signalKeyBackupServiceUrls) {
    this.signalServiceUrls          = signalServiceUrls;
    this.signalCdnUrls              = signalCdnUrls;
    this.signalContactDiscoveryUrls = signalContactDiscoveryUrls;
    this.signalKeyBackupServiceUrls = signalKeyBackupServiceUrls;
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

  public SignalKeyBackupServiceUrl[] getSignalKeyBackupServiceUrls() {
    return signalKeyBackupServiceUrls;
  }
}
