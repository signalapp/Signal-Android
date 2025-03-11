package org.thoughtcrime.securesms.payments;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.mobilecoin.lib.ClientConfig;
import com.mobilecoin.lib.exceptions.AttestationException;

import org.signal.core.util.Base64;
import org.thoughtcrime.securesms.R;
import org.whispersystems.signalservice.api.NetworkResultUtil;
import org.whispersystems.signalservice.api.payments.PaymentsApi;
import org.whispersystems.signalservice.internal.push.AuthCredentials;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

final class MobileCoinTestNetConfig extends MobileCoinConfig {
  private final PaymentsApi paymentsApi;

  public MobileCoinTestNetConfig(@NonNull PaymentsApi paymentsApi) {
    this.paymentsApi = paymentsApi;
  }

  @Override
  @NonNull List<Uri> getConsensusUris() {
    return Arrays.asList(
     Uri.parse("mc://node1.consensus.mob.staging.namda.net"),
     Uri.parse("mc://node2.consensus.mob.staging.namda.net")
    );
  }

  @Override
  @NonNull Uri getFogUri() {
    return Uri.parse("fog://fog.test.mobilecoin.com");
  }

  @Override
  @NonNull Uri getFogReportUri() {
    return Uri.parse("fog://fog-rpt-stg.namda.net");
  }

  @Override
  @NonNull byte[] getFogAuthoritySpki() {
    return Base64.decodeOrThrow("MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAoCMq8nnjTq5EEQ4EI7yrABL9P4y4h1P/h0DepWgXx+w/fywcfRSZINxbaMpvcV3uSJayExrpV1KmaS2wfASeYhSj+rEzAm0XUOw3Q94NOx5A/dOQag/d1SS6/QpF3PQYZTULnRFetmM4yzEnXsXcWtzEu0hh02wYJbLeAq4CCcPTPe2qckrbUP9sD18/KOzzNeypF4p5dQ2m/ezfxtgaLvdUMVDVIAs2v9a5iu6ce4bIcwTIUXgX0w3+UKRx8zqowc3HIqo9yeaGn4ZOwQHvAJZecPmb2pH1nK+BtDUvHpvf+Y3/NJxwh+IPp6Ef8aoUxs2g5oIBZ3Q31fjS2Bh2gmwoVooyytEysPAHvRPVBxXxLi36WpKfk1Vq8K7cgYh3IraOkH2/l2Pyi8EYYFkWsLYofYogaiPzVoq2ZdcizfoJWIYei5mgq+8m0ZKZYLebK1i2GdseBJNIbSt3wCNXZxyN6uqFHOCB29gmA5cbKvs/j9mDz64PJe9LCanqcDQV1U5l9dt9UdmUt7Ab1PjBtoIFaP+u473Z0hmZdCgAivuiBMMYMqt2V2EIw4IXLASE3roLOYp0p7h0IQHb+lVIuEl0ZmwAI30ZmzgcWc7RBeWD1/zNt55zzhfPRLx/DfDY5Kdp6oFHWMvI2r1/oZkdhjFp7pV6qrl7vOyR5QqmuRkCAwEAAQ==");
  }

  @Override
  @NonNull AuthCredentials getAuth() throws IOException {
    return NetworkResultUtil.toBasicLegacy(paymentsApi.getAuthorization());
  }

  @Override
  @NonNull ClientConfig getConfig() {
    try {
      Set<X509Certificate> trustRoots = getTrustRoots(R.raw.signal_mobilecoin_authority);
      ClientConfig         config     = new ClientConfig();
      VerifierFactory verifierFactory = new VerifierFactory(// ~May 30, 2023
                                                            new ServiceConfig(
                                                                "5341c6702a3312243c0f049f87259352ff32aa80f0f6426351c3dd063d817d7a",
                                                                "248356aa0d3431abc45da1773cfd6191a4f2989a4a99da31f450bd7c461e312b",
                                                                "b61188a6c946557f32e612eff5615908abd1b72ec11d8b7070595a92d4abbbf1",
                                                                "ac292a1ad27c0338a5159d5fab2bed3917ea144536cb13b5c1226d09a2fbc648",
                                                                new String[] { "INTEL-SA-00334", "INTEL-SA-00615", "INTEL-SA-00657" }
                                                            ),
                                                            // ~May 9, 2024
                                                            new ServiceConfig(
                                                                "ae7930646f37e026806087d2a3725d3f6d75a8e989fb320e6ecb258eb829057a",
                                                                "4a5daa23db5efa4b18071291cfa24a808f58fb0cedce7da5de804b011e87cfde",
                                                                "065b1e17e95f2c356d4d071d434cea7eb6b95bc797f94954146736efd47057a7",
                                                                "44de03c2ba34c303e6417480644f9796161eacbe5af4f2092e413b4ebf5ccf6a",
                                                                new String[] { "INTEL-SA-00334", "INTEL-SA-00615", "INTEL-SA-00657" }
                                                            )
                                                            );

      config.logAdapter = new MobileCoinLogAdapter();
      config.fogView    = new ClientConfig.Service().withTrustRoots(trustRoots)
                                                    .withVerifier(verifierFactory.createViewVerifier());
      config.fogLedger  = new ClientConfig.Service().withTrustRoots(trustRoots)
                                                    .withVerifier(verifierFactory.createLedgerVerifier());
      config.consensus  = new ClientConfig.Service().withTrustRoots(trustRoots)
                                                    .withVerifier(verifierFactory.createConsensusVerifier());
      config.report     = new ClientConfig.Service().withVerifier(verifierFactory.createReportVerifier());

      return config;
    } catch (AttestationException ex) {
      throw new IllegalStateException();
    }
  }
}
