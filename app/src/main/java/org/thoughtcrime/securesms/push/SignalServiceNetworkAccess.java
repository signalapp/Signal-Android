package org.thoughtcrime.securesms.push;


import android.content.Context;

import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.net.CustomDns;
import org.thoughtcrime.securesms.net.SequentialDns;
import org.thoughtcrime.securesms.net.UserAgentInterceptor;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl;
import org.whispersystems.signalservice.internal.configuration.SignalContactDiscoveryUrl;
import org.whispersystems.signalservice.internal.configuration.SignalKeyBackupServiceUrl;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.configuration.SignalServiceUrl;
import org.whispersystems.signalservice.internal.configuration.SignalStorageUrl;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.Dns;
import okhttp3.Interceptor;
import okhttp3.TlsVersion;

public class SignalServiceNetworkAccess {

  @SuppressWarnings("unused")
  private static final String TAG = SignalServiceNetworkAccess.class.getSimpleName();

  public static final Dns DNS = new SequentialDns(Dns.SYSTEM, new CustomDns("1.1.1.1"));

  private static final String COUNTRY_CODE_EGYPT = "+20";
  private static final String COUNTRY_CODE_UAE   = "+971";
  private static final String COUNTRY_CODE_OMAN  = "+968";
  private static final String COUNTRY_CODE_QATAR = "+974";

  private static final String SERVICE_REFLECTOR_HOST = "europe-west1-signal-cdn-reflector.cloudfunctions.net";

  private static final ConnectionSpec GMAPS_CONNECTION_SPEC = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_2)
      .cipherSuites(CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA)
      .supportsTlsExtensions(true)
      .build();

  private static final ConnectionSpec GMAIL_CONNECTION_SPEC = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_2)
      .cipherSuites(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA)
      .supportsTlsExtensions(true)
      .build();

  private static final ConnectionSpec PLAY_CONNECTION_SPEC = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_2)
      .cipherSuites(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA)
      .supportsTlsExtensions(true)
      .build();


  private final Map<String, SignalServiceConfiguration> censorshipConfiguration;
  private final String[]                                censoredCountries;
  private final SignalServiceConfiguration              uncensoredConfiguration;

  public SignalServiceNetworkAccess(Context context) {

    final TrustStore                trustStore              = new DomainFrontingTrustStore(context);
    final SignalServiceUrl          baseGoogleService       = new SignalServiceUrl("https://www.google.com/service", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalServiceUrl          baseAndroidService      = new SignalServiceUrl("https://android.clients.google.com/service", SERVICE_REFLECTOR_HOST, trustStore, PLAY_CONNECTION_SPEC);
    final SignalServiceUrl          mapsOneAndroidService   = new SignalServiceUrl("https://clients3.google.com/service", SERVICE_REFLECTOR_HOST, trustStore, GMAPS_CONNECTION_SPEC);
    final SignalServiceUrl          mapsTwoAndroidService   = new SignalServiceUrl("https://clients4.google.com/service", SERVICE_REFLECTOR_HOST, trustStore, GMAPS_CONNECTION_SPEC);
    final SignalServiceUrl          mailAndroidService      = new SignalServiceUrl("https://inbox.google.com/service", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalServiceUrl          egyptGoogleService      = new SignalServiceUrl("https://www.google.com.eg/service", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalServiceUrl          uaeGoogleService        = new SignalServiceUrl("https://www.google.ae/service", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalServiceUrl          omanGoogleService       = new SignalServiceUrl("https://www.google.com.om/service", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalServiceUrl          qatarGoogleService      = new SignalServiceUrl("https://www.google.com.qa/service", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);

    final SignalCdnUrl              baseGoogleCdn           = new SignalCdnUrl("https://www.google.com/cdn", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalCdnUrl              baseAndroidCdn          = new SignalCdnUrl("https://android.clients.google.com/cdn", SERVICE_REFLECTOR_HOST, trustStore, PLAY_CONNECTION_SPEC);
    final SignalCdnUrl              mapsOneAndroidCdn       = new SignalCdnUrl("https://clients3.google.com/cdn", SERVICE_REFLECTOR_HOST, trustStore, GMAPS_CONNECTION_SPEC);
    final SignalCdnUrl              mapsTwoAndroidCdn       = new SignalCdnUrl("https://clients4.google.com/cdn", SERVICE_REFLECTOR_HOST, trustStore, GMAPS_CONNECTION_SPEC);
    final SignalCdnUrl              mailAndroidCdn          = new SignalCdnUrl("https://inbox.google.com/cdn", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalCdnUrl              egyptGoogleCdn          = new SignalCdnUrl("https://www.google.com.eg/cdn", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalCdnUrl              uaeGoogleCdn            = new SignalCdnUrl("https://www.google.ae/cdn", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalCdnUrl              omanGoogleCdn           = new SignalCdnUrl("https://www.google.com.om/cdn", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalCdnUrl              qatarGoogleCdn          = new SignalCdnUrl("https://www.google.com.qa/cdn", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);

    final SignalCdnUrl              baseGoogleCdn2          = new SignalCdnUrl("https://www.google.com/cdn2", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalCdnUrl              baseAndroidCdn2         = new SignalCdnUrl("https://android.clients.google.com/cdn2", SERVICE_REFLECTOR_HOST, trustStore, PLAY_CONNECTION_SPEC);
    final SignalCdnUrl              mapsOneAndroidCdn2      = new SignalCdnUrl("https://clients3.google.com/cdn2", SERVICE_REFLECTOR_HOST, trustStore, GMAPS_CONNECTION_SPEC);
    final SignalCdnUrl              mapsTwoAndroidCdn2      = new SignalCdnUrl("https://clients4.google.com/cdn2", SERVICE_REFLECTOR_HOST, trustStore, GMAPS_CONNECTION_SPEC);
    final SignalCdnUrl              mailAndroidCdn2         = new SignalCdnUrl("https://inbox.google.com/cdn2", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalCdnUrl              egyptGoogleCdn2         = new SignalCdnUrl("https://www.google.com.eg/cdn2", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalCdnUrl              uaeGoogleCdn2           = new SignalCdnUrl("https://www.google.ae/cdn2", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalCdnUrl              omanGoogleCdn2          = new SignalCdnUrl("https://www.google.com.om/cdn2", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalCdnUrl              qatarGoogleCdn2         = new SignalCdnUrl("https://www.google.com.qa/cdn2", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);

    final SignalContactDiscoveryUrl baseGoogleDiscovery     = new SignalContactDiscoveryUrl("https://www.google.com/directory", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalContactDiscoveryUrl baseAndroidDiscovery    = new SignalContactDiscoveryUrl("https://android.clients.google.com/directory", SERVICE_REFLECTOR_HOST, trustStore, PLAY_CONNECTION_SPEC);
    final SignalContactDiscoveryUrl mapsOneAndroidDiscovery = new SignalContactDiscoveryUrl("https://clients3.google.com/directory", SERVICE_REFLECTOR_HOST, trustStore, GMAPS_CONNECTION_SPEC);
    final SignalContactDiscoveryUrl mapsTwoAndroidDiscovery = new SignalContactDiscoveryUrl("https://clients4.google.com/directory", SERVICE_REFLECTOR_HOST, trustStore, GMAPS_CONNECTION_SPEC);
    final SignalContactDiscoveryUrl mailAndroidDiscovery    = new SignalContactDiscoveryUrl("https://inbox.google.com/directory", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalContactDiscoveryUrl egyptGoogleDiscovery    = new SignalContactDiscoveryUrl("https://www.google.com.eg/directory", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalContactDiscoveryUrl uaeGoogleDiscovery      = new SignalContactDiscoveryUrl("https://www.google.ae/directory", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalContactDiscoveryUrl omanGoogleDiscovery     = new SignalContactDiscoveryUrl("https://www.google.com.om/directory", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalContactDiscoveryUrl qatarGoogleDiscovery    = new SignalContactDiscoveryUrl("https://www.google.com.qa/directory", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);

    final SignalKeyBackupServiceUrl baseGoogleKbs     = new SignalKeyBackupServiceUrl("https://www.google.com/backup", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalKeyBackupServiceUrl baseAndroidKbs    = new SignalKeyBackupServiceUrl("https://android.clients.google.com/backup", SERVICE_REFLECTOR_HOST, trustStore, PLAY_CONNECTION_SPEC);
    final SignalKeyBackupServiceUrl mapsOneAndroidKbs = new SignalKeyBackupServiceUrl("https://clients3.google.com/backup", SERVICE_REFLECTOR_HOST, trustStore, GMAPS_CONNECTION_SPEC);
    final SignalKeyBackupServiceUrl mapsTwoAndroidKbs = new SignalKeyBackupServiceUrl("https://clients4.google.com/backup", SERVICE_REFLECTOR_HOST, trustStore, GMAPS_CONNECTION_SPEC);
    final SignalKeyBackupServiceUrl mailAndroidKbs    = new SignalKeyBackupServiceUrl("https://inbox.google.com/backup", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalKeyBackupServiceUrl egyptGoogleKbs    = new SignalKeyBackupServiceUrl("https://www.google.com.eg/backup", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalKeyBackupServiceUrl uaeGoogleKbs      = new SignalKeyBackupServiceUrl("https://www.google.ae/backup", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalKeyBackupServiceUrl omanGoogleKbs     = new SignalKeyBackupServiceUrl("https://www.google.com.om/backup", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalKeyBackupServiceUrl qatarGoogleKbs    = new SignalKeyBackupServiceUrl("https://www.google.com.qa/backup", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);

    final SignalStorageUrl baseGoogleStorage     = new SignalStorageUrl("https://www.google.com/storage", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalStorageUrl baseAndroidStorage    = new SignalStorageUrl("https://android.clients.google.com/storage", SERVICE_REFLECTOR_HOST, trustStore, PLAY_CONNECTION_SPEC);
    final SignalStorageUrl mapsOneAndroidStorage = new SignalStorageUrl("https://clients3.google.com/storage", SERVICE_REFLECTOR_HOST, trustStore, GMAPS_CONNECTION_SPEC);
    final SignalStorageUrl mapsTwoAndroidStorage = new SignalStorageUrl("https://clients4.google.com/storage", SERVICE_REFLECTOR_HOST, trustStore, GMAPS_CONNECTION_SPEC);
    final SignalStorageUrl mailAndroidStorage    = new SignalStorageUrl("https://inbox.google.com/storage", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalStorageUrl egyptGoogleStorage    = new SignalStorageUrl("https://www.google.com.eg/storage", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalStorageUrl uaeGoogleStorage      = new SignalStorageUrl("https://www.google.ae/storage", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalStorageUrl omanGoogleStorage     = new SignalStorageUrl("https://www.google.com.om/storage", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalStorageUrl qatarGoogleStorage    = new SignalStorageUrl("https://www.google.com.qa/storage", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);

    final List<Interceptor> interceptors = Collections.singletonList(new UserAgentInterceptor());
    final Optional<Dns>     dns          = Optional.of(DNS);

    final byte[] zkGroupServerPublicParams;

    try {
      zkGroupServerPublicParams = Base64.decode(BuildConfig.ZKGROUP_SERVER_PUBLIC_PARAMS);
    } catch (IOException e) {
      throw new AssertionError(e);
    }

    this.censorshipConfiguration = new HashMap<String, SignalServiceConfiguration>() {{
      put(COUNTRY_CODE_EGYPT, new SignalServiceConfiguration(new SignalServiceUrl[] {egyptGoogleService, baseGoogleService, baseAndroidService, mapsOneAndroidService, mapsTwoAndroidService, mailAndroidService},
                                                             makeSignalCdnUrlMapFor(new SignalCdnUrl[] {egyptGoogleCdn, baseAndroidCdn, baseGoogleCdn, mapsOneAndroidCdn, mapsTwoAndroidCdn, mailAndroidCdn, mailAndroidCdn},
                                                                                    new SignalCdnUrl[] {egyptGoogleCdn2, baseAndroidCdn2, baseGoogleCdn2, mapsOneAndroidCdn2, mapsTwoAndroidCdn2, mailAndroidCdn2, mailAndroidCdn2}),
                                                             new SignalContactDiscoveryUrl[] {egyptGoogleDiscovery, baseGoogleDiscovery, baseAndroidDiscovery, mapsOneAndroidDiscovery, mapsTwoAndroidDiscovery, mailAndroidDiscovery},
                                                             new SignalKeyBackupServiceUrl[] {egyptGoogleKbs, baseGoogleKbs, baseAndroidKbs, mapsOneAndroidKbs, mapsTwoAndroidKbs, mailAndroidKbs},
                                                             new SignalStorageUrl[] {egyptGoogleStorage, baseGoogleStorage, baseAndroidStorage, mapsOneAndroidStorage, mapsTwoAndroidStorage, mailAndroidStorage},
                                                             interceptors,
                                                             dns,
                                                             zkGroupServerPublicParams));

      put(COUNTRY_CODE_UAE, new SignalServiceConfiguration(new SignalServiceUrl[] {uaeGoogleService, baseAndroidService, baseGoogleService, mapsOneAndroidService, mapsTwoAndroidService, mailAndroidService},
                                                           makeSignalCdnUrlMapFor(new SignalCdnUrl[] {uaeGoogleCdn, baseAndroidCdn, baseGoogleCdn, mapsOneAndroidCdn, mapsTwoAndroidCdn, mailAndroidCdn},
                                                                                  new SignalCdnUrl[] {uaeGoogleCdn2, baseAndroidCdn2, baseGoogleCdn2, mapsOneAndroidCdn2, mapsTwoAndroidCdn2, mailAndroidCdn2}),
                                                           new SignalContactDiscoveryUrl[] {uaeGoogleDiscovery, baseGoogleDiscovery, baseAndroidDiscovery, mapsOneAndroidDiscovery, mapsTwoAndroidDiscovery, mailAndroidDiscovery},
                                                           new SignalKeyBackupServiceUrl[] {uaeGoogleKbs, baseGoogleKbs, baseAndroidKbs, mapsOneAndroidKbs, mapsTwoAndroidKbs, mailAndroidKbs},
                                                           new SignalStorageUrl[] {uaeGoogleStorage, baseGoogleStorage, baseAndroidStorage, mapsOneAndroidStorage, mapsTwoAndroidStorage, mailAndroidStorage},
                                                           interceptors,
                                                           dns,
                                                           zkGroupServerPublicParams));

      put(COUNTRY_CODE_OMAN, new SignalServiceConfiguration(new SignalServiceUrl[] {omanGoogleService, baseAndroidService, baseGoogleService, mapsOneAndroidService, mapsTwoAndroidService, mailAndroidService},
                                                            makeSignalCdnUrlMapFor(new SignalCdnUrl[] {omanGoogleCdn, baseAndroidCdn, baseGoogleCdn, mapsOneAndroidCdn, mapsTwoAndroidCdn, mailAndroidCdn},
                                                                                   new SignalCdnUrl[] {omanGoogleCdn2, baseAndroidCdn2, baseGoogleCdn2, mapsOneAndroidCdn2, mapsTwoAndroidCdn2, mailAndroidCdn2}),
                                                            new SignalContactDiscoveryUrl[] {omanGoogleDiscovery, baseGoogleDiscovery, baseAndroidDiscovery, mapsOneAndroidDiscovery, mapsTwoAndroidDiscovery, mailAndroidDiscovery},
                                                            new SignalKeyBackupServiceUrl[] {omanGoogleKbs, baseGoogleKbs, baseAndroidKbs, mapsOneAndroidKbs, mapsTwoAndroidKbs, mailAndroidKbs},
                                                            new SignalStorageUrl[] {omanGoogleStorage, baseGoogleStorage, baseAndroidStorage, mapsOneAndroidStorage, mapsTwoAndroidStorage, mailAndroidStorage},
                                                            interceptors,
                                                            dns,
                                                            zkGroupServerPublicParams));


      put(COUNTRY_CODE_QATAR, new SignalServiceConfiguration(new SignalServiceUrl[] {qatarGoogleService, baseAndroidService, baseGoogleService, mapsOneAndroidService, mapsTwoAndroidService, mailAndroidService},
                                                             makeSignalCdnUrlMapFor(new SignalCdnUrl[] {qatarGoogleCdn, baseAndroidCdn, baseGoogleCdn, mapsOneAndroidCdn, mapsTwoAndroidCdn, mailAndroidCdn},
                                                                                    new SignalCdnUrl[] {qatarGoogleCdn2, baseAndroidCdn2, baseGoogleCdn2, mapsOneAndroidCdn2, mapsTwoAndroidCdn2, mailAndroidCdn2}),
                                                             new SignalContactDiscoveryUrl[] {qatarGoogleDiscovery, baseGoogleDiscovery, baseAndroidDiscovery, mapsOneAndroidDiscovery, mapsTwoAndroidDiscovery, mailAndroidDiscovery},
                                                             new SignalKeyBackupServiceUrl[] {qatarGoogleKbs, baseGoogleKbs, baseAndroidKbs, mapsOneAndroidKbs, mapsTwoAndroidKbs, mailAndroidKbs},
                                                             new SignalStorageUrl[] {qatarGoogleStorage, baseGoogleStorage, baseAndroidStorage, mapsOneAndroidStorage, mapsTwoAndroidStorage, mailAndroidStorage},
                                                             interceptors,
                                                             dns,
                                                             zkGroupServerPublicParams));
    }};

    this.uncensoredConfiguration = new SignalServiceConfiguration(new SignalServiceUrl[] {new SignalServiceUrl(BuildConfig.SIGNAL_URL, new SignalServiceTrustStore(context))},
                                                                  makeSignalCdnUrlMapFor(new SignalCdnUrl[] {new SignalCdnUrl(BuildConfig.SIGNAL_CDN_URL, new SignalServiceTrustStore(context))},
                                                                                         new SignalCdnUrl[] {new SignalCdnUrl(BuildConfig.SIGNAL_CDN2_URL, new SignalServiceTrustStore(context))}),
                                                                  new SignalContactDiscoveryUrl[] {new SignalContactDiscoveryUrl(BuildConfig.SIGNAL_CONTACT_DISCOVERY_URL, new SignalServiceTrustStore(context))},
                                                                  new SignalKeyBackupServiceUrl[] { new SignalKeyBackupServiceUrl(BuildConfig.SIGNAL_KEY_BACKUP_URL, new SignalServiceTrustStore(context)) },
                                                                  new SignalStorageUrl[] {new SignalStorageUrl(BuildConfig.STORAGE_URL, new SignalServiceTrustStore(context))},
                                                                  interceptors,
                                                                  dns,
                                                                  zkGroupServerPublicParams);

    this.censoredCountries = this.censorshipConfiguration.keySet().toArray(new String[0]);
  }

  public SignalServiceConfiguration getConfiguration(Context context) {
    String localNumber = TextSecurePreferences.getLocalNumber(context);
    return getConfiguration(localNumber);
  }

  public SignalServiceConfiguration getConfiguration(@Nullable String localNumber) {
    if (localNumber == null) return this.uncensoredConfiguration;

    for (String censoredRegion : this.censoredCountries) {
      if (localNumber.startsWith(censoredRegion)) {
        return this.censorshipConfiguration.get(censoredRegion);
      }
    }

    return this.uncensoredConfiguration;
  }

  public boolean isCensored(Context context) {
    return getConfiguration(context) != this.uncensoredConfiguration;
  }

  public boolean isCensored(String number) {
    return getConfiguration(number) != this.uncensoredConfiguration;
  }

  private static Map<Integer, SignalCdnUrl[]> makeSignalCdnUrlMapFor(SignalCdnUrl[] cdn0Urls, SignalCdnUrl[] cdn2Urls) {
    Map<Integer, SignalCdnUrl[]> result = new HashMap<>();
    result.put(0, cdn0Urls);
    result.put(2, cdn2Urls);
    return Collections.unmodifiableMap(result);
  }
}
