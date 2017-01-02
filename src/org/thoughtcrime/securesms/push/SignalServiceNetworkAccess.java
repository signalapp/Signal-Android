package org.thoughtcrime.securesms.push;


import android.content.Context;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.internal.push.SignalServiceUrl;

import java.util.HashMap;
import java.util.Map;

public class SignalServiceNetworkAccess {

  private static final String TAG = SignalServiceNetworkAccess.class.getName();

  private static final String APPSPOT_REFLECTOR_HOST = "signal-reflector-meek.appspot.com";

  private final Map<String, SignalServiceUrl[]> censorshipConfiguration;
  private final String[]                        censoredCountries;
  private final SignalServiceUrl[]              uncensoredConfiguration;

  public SignalServiceNetworkAccess(Context context) {
    final TrustStore       googleTrustStore = new GoogleFrontingTrustStore(context);
    final SignalServiceUrl baseGoogle       = new SignalServiceUrl("https://www.google.com", APPSPOT_REFLECTOR_HOST, googleTrustStore);
    final SignalServiceUrl baseAndroid      = new SignalServiceUrl("https://android.clients.google.com", APPSPOT_REFLECTOR_HOST, googleTrustStore);

    this.censorshipConfiguration = new HashMap<String, SignalServiceUrl[]>() {{
      put("+20", new SignalServiceUrl[] {new SignalServiceUrl("https://www.google.com.eg",
                                                              APPSPOT_REFLECTOR_HOST,
                                                              googleTrustStore),
                                         baseAndroid});

      put("+971", new SignalServiceUrl[] {new SignalServiceUrl("https://www.google.ae",
                                                               APPSPOT_REFLECTOR_HOST,
                                                               googleTrustStore),
                                          baseAndroid, baseGoogle});

      put("+53", new SignalServiceUrl[] {new SignalServiceUrl("https://www.google.com.cu",
                                                              APPSPOT_REFLECTOR_HOST,
                                                              googleTrustStore),
                                         baseAndroid, baseGoogle});

      put("+968", new SignalServiceUrl[] {new SignalServiceUrl("https://www.google.com.om",
                                                               APPSPOT_REFLECTOR_HOST,
                                                               googleTrustStore),
                                          baseAndroid, baseGoogle});
      put("+98", new SignalServiceUrl[] {baseAndroid, baseGoogle});
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

  public SignalServiceUrl[] getConfiguration(String localNumber) {
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

}
