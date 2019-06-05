package org.thoughtcrime.securesms.push;


import android.content.Context;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl;
import org.whispersystems.signalservice.internal.configuration.SignalContactDiscoveryUrl;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.configuration.SignalServiceUrl;

import java.util.HashMap;
import java.util.Map;

import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.TlsVersion;

public class SignalServiceNetworkAccess {

  @SuppressWarnings("unused")
  private static final String TAG = SignalServiceNetworkAccess.class.getSimpleName();

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

    final SignalContactDiscoveryUrl baseGoogleDiscovery     = new SignalContactDiscoveryUrl("https://www.google.com/directory", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalContactDiscoveryUrl baseAndroidDiscovery    = new SignalContactDiscoveryUrl("https://android.clients.google.com/directory", SERVICE_REFLECTOR_HOST, trustStore, PLAY_CONNECTION_SPEC);
    final SignalContactDiscoveryUrl mapsOneAndroidDiscovery = new SignalContactDiscoveryUrl("https://clients3.google.com/directory", SERVICE_REFLECTOR_HOST, trustStore, GMAPS_CONNECTION_SPEC);
    final SignalContactDiscoveryUrl mapsTwoAndroidDiscovery = new SignalContactDiscoveryUrl("https://clients4.google.com/directory", SERVICE_REFLECTOR_HOST, trustStore, GMAPS_CONNECTION_SPEC);
    final SignalContactDiscoveryUrl mailAndroidDiscovery    = new SignalContactDiscoveryUrl("https://inbox.google.com/directory", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalContactDiscoveryUrl egyptGoogleDiscovery    = new SignalContactDiscoveryUrl("https://www.google.com.eg/directory", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalContactDiscoveryUrl uaeGoogleDiscovery      = new SignalContactDiscoveryUrl("https://www.google.ae/directory", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalContactDiscoveryUrl omanGoogleDiscovery     = new SignalContactDiscoveryUrl("https://www.google.com.om/directory", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);
    final SignalContactDiscoveryUrl qatarGoogleDiscovery    = new SignalContactDiscoveryUrl("https://www.google.com.qa/directory", SERVICE_REFLECTOR_HOST, trustStore, GMAIL_CONNECTION_SPEC);


    this.censorshipConfiguration = new HashMap<String, SignalServiceConfiguration>() {{
      put(COUNTRY_CODE_EGYPT, new SignalServiceConfiguration(new SignalServiceUrl[] {egyptGoogleService, baseGoogleService, baseAndroidService, mapsOneAndroidService, mapsTwoAndroidService, mailAndroidService},
                                                             new SignalCdnUrl[] {egyptGoogleCdn, baseAndroidCdn, baseGoogleCdn, mapsOneAndroidCdn, mapsTwoAndroidCdn, mailAndroidCdn, mailAndroidCdn},
                                                             new SignalContactDiscoveryUrl[] {egyptGoogleDiscovery, baseGoogleDiscovery, baseAndroidDiscovery, mapsOneAndroidDiscovery, mapsTwoAndroidDiscovery, mailAndroidDiscovery}));

      put(COUNTRY_CODE_UAE, new SignalServiceConfiguration(new SignalServiceUrl[] {uaeGoogleService, baseAndroidService, baseGoogleService, mapsOneAndroidService, mapsTwoAndroidService, mailAndroidService},
                                                           new SignalCdnUrl[] {uaeGoogleCdn, baseAndroidCdn, baseGoogleCdn, mapsOneAndroidCdn, mapsTwoAndroidCdn, mailAndroidCdn},
                                                           new SignalContactDiscoveryUrl[] {uaeGoogleDiscovery, baseGoogleDiscovery, baseAndroidDiscovery, mapsOneAndroidDiscovery, mapsTwoAndroidDiscovery, mailAndroidDiscovery}));

      put(COUNTRY_CODE_OMAN, new SignalServiceConfiguration(new SignalServiceUrl[] {omanGoogleService, baseAndroidService, baseGoogleService, mapsOneAndroidService, mapsTwoAndroidService, mailAndroidService},
                                                            new SignalCdnUrl[] {omanGoogleCdn, baseAndroidCdn, baseGoogleCdn, mapsOneAndroidCdn, mapsTwoAndroidCdn, mailAndroidCdn},
                                                            new SignalContactDiscoveryUrl[] {omanGoogleDiscovery, baseGoogleDiscovery, baseAndroidDiscovery, mapsOneAndroidDiscovery, mapsTwoAndroidDiscovery, mailAndroidDiscovery}));


      put(COUNTRY_CODE_QATAR, new SignalServiceConfiguration(new SignalServiceUrl[] {qatarGoogleService, baseAndroidService, baseGoogleService, mapsOneAndroidService, mapsTwoAndroidService, mailAndroidService},
                                                             new SignalCdnUrl[] {qatarGoogleCdn, baseAndroidCdn, baseGoogleCdn, mapsOneAndroidCdn, mapsTwoAndroidCdn, mailAndroidCdn},
                                                             new SignalContactDiscoveryUrl[] {qatarGoogleDiscovery, baseGoogleDiscovery, baseAndroidDiscovery, mapsOneAndroidDiscovery, mapsTwoAndroidDiscovery, mailAndroidDiscovery}));
    }};

    this.uncensoredConfiguration = new SignalServiceConfiguration(new SignalServiceUrl[] {new SignalServiceUrl(BuildConfig.SIGNAL_URL, new SignalServiceTrustStore(context))},
                                                                  new SignalCdnUrl[] {new SignalCdnUrl(BuildConfig.SIGNAL_CDN_URL, new SignalServiceTrustStore(context))},
                                                                  new SignalContactDiscoveryUrl[] {new SignalContactDiscoveryUrl(BuildConfig.SIGNAL_CONTACT_DISCOVERY_URL, new SignalServiceTrustStore(context))});

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

}
