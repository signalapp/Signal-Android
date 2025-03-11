package org.thoughtcrime.securesms.payments;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.mobilecoin.lib.ClientConfig;
import com.mobilecoin.lib.Verifier;
import com.mobilecoin.lib.exceptions.AttestationException;

import org.thoughtcrime.securesms.R;
import org.signal.core.util.Base64;
import org.whispersystems.signalservice.api.NetworkResultUtil;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.payments.PaymentsApi;
import org.whispersystems.signalservice.internal.push.AuthCredentials;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

final class MobileCoinMainNetConfig extends MobileCoinConfig {
  private final PaymentsApi paymentsApi;

  public MobileCoinMainNetConfig(@NonNull PaymentsApi paymentsApi) {
    this.paymentsApi = paymentsApi;
  }

  @Override
  @NonNull List<Uri> getConsensusUris() {
    return Arrays.asList(
        Uri.parse("mc://node1.consensus.mob.production.namda.net"),
        Uri.parse("mc://node2.consensus.mob.production.namda.net")
    );
  }

  @Override
  @NonNull Uri getFogUri() {
    return Uri.parse("fog://fog.prod.mobilecoinww.com");
  }

  @Override
  @NonNull Uri getFogReportUri() {
    return Uri.parse("fog://fog-rpt-prd.namda.net");
  }

  @Override
  @NonNull byte[] getFogAuthoritySpki() {
    return Base64.decodeOrThrow("MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAxaNIOgcoQtq0S64dFVha\n"
                                + "6rn0hDv/ec+W0cKRdFKygiyp5xuWdW3YKVAkK1PPgSDD2dwmMN/1xcGWrPMqezx1\n"
                                + "h1xCzbr7HL7XvLyFyoiMB2JYd7aoIuGIbHpCOlpm8ulVnkOX7BNuo0Hi2F0AAHyT\n"
                                + "PwmtVMt6RZmae1Z/Pl2I06+GgWN6vufV7jcjiLT3yQPsn1kVSj+DYCf3zq+1sCkn\n"
                                + "KIvoRPMdQh9Vi3I/fqNXz00DSB7lt3v5/FQ6sPbjljqdGD/qUl4xKRW+EoDLlAUf\n"
                                + "zahomQOLXVAlxcws3Ua5cZUhaJi6U5jVfw5Ng2N7FwX/D5oX82r9o3xcFqhWpGnf\n"
                                + "SxSrAudv1X7WskXomKhUzMl/0exWpcJbdrQWB/qshzi9Et7HEDNY+xEDiwGiikj5\n"
                                + "f0Lb+QA4mBMlAhY/cmWec8NKi1gf3Dmubh6c3sNteb9OpZ/irA3AfE8jI37K1rve\n"
                                + "zDI8kbNtmYgvyhfz0lZzRT2WAfffiTe565rJglvKa8rh8eszKk2HC9DyxUb/TcyL\n"
                                + "/OjGhe2fDYO2t6brAXCqjPZAEkVJq3I30NmnPdE19SQeP7wuaUIb3U7MGxoZC/Nu\n"
                                + "JoxZh8svvZ8cyqVjG+dOQ6/UfrFY0jiswT8AsrfqBis/ZV5EFukZr+zbPtg2MH0H\n"
                                + "3tSJ14BCLduvc7FY6lAZmOcCAwEAAQ==");
  }

  @Override
  @NonNull AuthCredentials getAuth() throws IOException {
    return NetworkResultUtil.toBasicLegacy(paymentsApi.getAuthorization());
  }

  @Override
  @NonNull ClientConfig getConfig() {
    try {
      Set<X509Certificate> trustRoots      = getTrustRoots(R.raw.signal_mobilecoin_authority);
      ClientConfig         config          = new ClientConfig();
      VerifierFactory      verifierFactory = new VerifierFactory(// ~May 30, 2023
                                                                 new ServiceConfig(
                                                                     "cd86d300c78f74ec23558cdaf734f90dd3e1bcdf8ae43fc827c6b4734ccb8862",
                                                                     "7d10f5e72cacc87a6027b2be42ed4a74a6370a03c3476be754933eb18c404b0b",
                                                                     "1dee8e2e98b7dc684506991d62856b2e572a0c23f5a7d698086e62f08fb997cc",
                                                                     "e94f6e6557b3fb85b27d804e2d005ee14a564cc50fc477797f2e5f9984b0bd79",
                                                                     new String[] { "INTEL-SA-00334", "INTEL-SA-00615", "INTEL-SA-00657" }
                                                                 ),
                                                                 // ~May 9, 2024
                                                                 new ServiceConfig(
                                                                     "82c14d06951a2168763c8ddb9c34174f7d2059564146650661da26ab62224b8a",
                                                                     "34881106254a626842fa8557e27d07cdf863083e9e6f888d5a492a456720916f",
                                                                     "2494f1542f30a6962707d0bf2aa6c8c08d7bed35668c9db1e5c61d863a0176d1",
                                                                     "2f542dcd8f682b72e8921d87e06637c16f4aa4da27dce55b561335326731fa73",
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
