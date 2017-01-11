package org.thoughtcrime.securesms.push;


import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.internal.push.SignalServiceUrl;

import java.util.HashMap;
import java.util.Map;

import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.TlsVersion;

public class SignalServiceNetworkAccess {

  private static final String TAG = SignalServiceNetworkAccess.class.getName();

  private static final String APPSPOT_REFLECTOR_HOST = "signal-reflector-meek.appspot.com";

  private static final ConnectionSpec GMAPS_CONNECTION_SPEC = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_2)
      .cipherSuites(CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_DHE_RSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA)
      .supportsTlsExtensions(true)
      .build();

  private static final ConnectionSpec GMAIL_CONNECTION_SPEC = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_2)
      .cipherSuites(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_DHE_RSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_RC4_128_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_RC4_128_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_RC4_128_SHA,
                    CipherSuite.TLS_EMPTY_RENEGOTIATION_INFO_SCSV)
      .supportsTlsExtensions(true)
      .build();

  private static final ConnectionSpec PLAY_CONNECTION_SPEC = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_2)
      .cipherSuites(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_RC4_128_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_RC4_128_SHA,
                    CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_RC4_128_SHA,
                    CipherSuite.TLS_EMPTY_RENEGOTIATION_INFO_SCSV)
      .supportsTlsExtensions(true)
      .build();


  private final Map<String, SignalServiceUrl[]> censorshipConfiguration;
  private final String[]                        censoredCountries;
  private final SignalServiceUrl[]              uncensoredConfiguration;

  public SignalServiceNetworkAccess(Context context) {
    final TrustStore       googleTrustStore = new GoogleFrontingTrustStore(context);
    final SignalServiceUrl baseGoogle       = new SignalServiceUrl("https://www.google.com", APPSPOT_REFLECTOR_HOST, googleTrustStore, GMAIL_CONNECTION_SPEC);
    final SignalServiceUrl baseAndroid      = new SignalServiceUrl("https://android.clients.google.com", APPSPOT_REFLECTOR_HOST, googleTrustStore, PLAY_CONNECTION_SPEC);
    final SignalServiceUrl mapsOneAndroid   = new SignalServiceUrl("https://clients3.google.com", APPSPOT_REFLECTOR_HOST, googleTrustStore, GMAPS_CONNECTION_SPEC);
    final SignalServiceUrl mapsTwoAndroid   = new SignalServiceUrl("https://clients4.google.com", APPSPOT_REFLECTOR_HOST, googleTrustStore, GMAPS_CONNECTION_SPEC);
    final SignalServiceUrl mailAndroid      = new SignalServiceUrl("https://mail.google.com", APPSPOT_REFLECTOR_HOST, googleTrustStore, GMAIL_CONNECTION_SPEC);

    this.censorshipConfiguration = new HashMap<String, SignalServiceUrl[]>() {{
      put("+20", new SignalServiceUrl[] {new SignalServiceUrl("https://www.google.com.eg",
                                                              APPSPOT_REFLECTOR_HOST,
                                                              googleTrustStore, GMAIL_CONNECTION_SPEC),
                                         baseAndroid, mapsOneAndroid, mapsTwoAndroid, mailAndroid});

      put("+971", new SignalServiceUrl[] {new SignalServiceUrl("https://www.google.ae",
                                                               APPSPOT_REFLECTOR_HOST,
                                                               googleTrustStore, GMAIL_CONNECTION_SPEC),
                                          baseAndroid, baseGoogle, mapsOneAndroid, mapsTwoAndroid, mailAndroid});

      put("+53", new SignalServiceUrl[] {new SignalServiceUrl("https://www.google.com.cu",
                                                              APPSPOT_REFLECTOR_HOST,
                                                              googleTrustStore, GMAIL_CONNECTION_SPEC),
                                         baseAndroid, baseGoogle, mapsOneAndroid, mapsTwoAndroid, mailAndroid});

      put("+968", new SignalServiceUrl[] {new SignalServiceUrl("https://www.google.com.om",
                                                               APPSPOT_REFLECTOR_HOST,
                                                               googleTrustStore, GMAIL_CONNECTION_SPEC),
                                          baseAndroid, baseGoogle, mapsOneAndroid, mapsTwoAndroid, mailAndroid});
    }};

    this.uncensoredConfiguration = new SignalServiceUrl[] {
        new SignalServiceUrl(BuildConfig.SIGNAL_URL, new SignalServiceTrustStore(context))
    };

    this.censoredCountries = this.censorshipConfiguration.keySet().toArray(new String[0]);
  }

  public SignalServiceUrl[] getConfiguration(Context context) {
    String localNumber = TextSecurePreferences.getLocalNumber(context);
    return getConfiguration(localNumber);
  }

  public SignalServiceUrl[] getConfiguration(@Nullable String localNumber) {
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
