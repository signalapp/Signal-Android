package org.thoughtcrime.securesms.payments;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.mobilecoin.lib.ClientConfig;
import com.mobilecoin.lib.Verifier;
import com.mobilecoin.lib.exceptions.AttestationException;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.internal.push.AuthCredentials;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

final class MobileCoinMainNetConfig extends MobileCoinConfig {

  private final SignalServiceAccountManager signalServiceAccountManager;

  public MobileCoinMainNetConfig(@NonNull SignalServiceAccountManager signalServiceAccountManager) {
    this.signalServiceAccountManager = signalServiceAccountManager;
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
    return Uri.parse("fog://service.fog.mob.production.namda.net");
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
    return signalServiceAccountManager.getPaymentsAuthorization();
  }

  @Override
  @NonNull ClientConfig getConfig() {
    try {
      Set<X509Certificate> trustRoots      = getTrustRoots(R.raw.signal_mobilecoin_authority);
      ClientConfig         config          = new ClientConfig();
      VerifierFactory      verifierFactory = new VerifierFactory(// ~July 8th, 2022
                                                                 new ServiceConfig(
                                                                     "733080d6ece4504f66ba606fa8163dae0a5220f3dbf6ca55fbafbac12c6f1897",
                                                                     "660103d766cde0fd1e1cfb443b99e52da2ce0617d0dee42f8b875f7104942c6b",
                                                                     "ed8ed6e1b4b6827e5543b25c1c13b9c06b478d819f8df912eb11fa140780fc51",
                                                                     "c64a3b04348b10596442868758875f312dc3a755b450805149774a091d2822d3",
                                                                     new String[] { "INTEL-SA-00334" }
                                                                 ),
                                                                 // ~August 10th, 2022
                                                                 new ServiceConfig(
                                                                     "d6e54e43c368f0fa2c5f13361afd303ee8f890424e99bd6c367f6164b5fff1b5",
                                                                     "3e9bf61f3191add7b054f0e591b62f832854606f6594fd63faef1e2aedec4021",
                                                                     "92fb35d0f603ceb5eaf2988b24a41d4a4a83f8fb9cd72e67c3bc37960d864ad6",
                                                                     "3d6e528ee0574ae3299915ea608b71ddd17cbe855d4f5e1c46df9b0d22b04cdb",
                                                                     new String[] { "INTEL-SA-00334", "INTEL-SA-00615" }
                                                                 ),
                                                                 // ~November 1, 2022
                                                                 new ServiceConfig(
                                                                     "207c9705bf640fdb960034595433ee1ff914f9154fbe4bc7fc8a97e912961e5c",
                                                                     "3370f131b41e5a49ed97c4188f7a976461ac6127f8d222a37929ac46b46d560e",
                                                                     "dca7521ce4564cc2e54e1637e533ea9d1901c2adcbab0e7a41055e719fb0ff9d",
                                                                     "fd4c1c82cca13fa007be15a4c90e2b506c093b21c2e7021a055cbb34aa232f3f",
                                                                     new String[] { "INTEL-SA-00334", "INTEL-SA-00615", "INTEL-SA-00657" }
                                                                 ),
                                                                 // ~December 15, 2022
                                                                 new ServiceConfig(
                                                                     "e35bc15ee92775029a60a715dca05d310ad40993f56ad43bca7e649ccc9021b5",
                                                                     "a8af815564569aae3558d8e4e4be14d1bcec896623166a10494b4eaea3e1c48c",
                                                                     "8c80a2b95a549fa8d928dd0f0771be4f3d774408c0f98bf670b1a2c390706bf3",
                                                                     "da209f4b24e8f4471bd6440c4e9f1b3100f1da09e2836d236e285b274901ed3b",
                                                                     new String[] { "INTEL-SA-00334", "INTEL-SA-00615", "INTEL-SA-00657" }
                                                                 ));


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
