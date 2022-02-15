package org.thoughtcrime.securesms.payments;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.mobilecoin.lib.ClientConfig;
import com.mobilecoin.lib.Verifier;
import com.mobilecoin.lib.exceptions.AttestationException;
import com.mobilecoin.lib.util.Hex;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.internal.push.AuthCredentials;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Set;

final class MobileCoinMainNetConfig extends MobileCoinConfig {

  private final SignalServiceAccountManager signalServiceAccountManager;

  public MobileCoinMainNetConfig(@NonNull SignalServiceAccountManager signalServiceAccountManager) {
    this.signalServiceAccountManager = signalServiceAccountManager;
  }

  @Override
  @NonNull Uri getConsensusUri() {
    return Uri.parse("mc://node1.prod.mobilecoinww.com");
  }

  @Override
  @NonNull Uri getFogUri() {
    return Uri.parse("fog://service.fog.mob.production.namda.net");
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
      byte[] mrEnclaveConsensus    = Hex.toByteArray("e66db38b8a43a33f6c1610d335a361963bb2b31e056af0dc0a895ac6c857cab9");
      byte[] mrEnclaveConsensusNew = Hex.toByteArray("653228afd2b02a6c28f1dc3b108b1dfa457d170b32ae8ec2978f941bd1655c83");
      byte[] mrEnclaveReport       = Hex.toByteArray("709ab90621e3a8d9eb26ed9e2830e091beceebd55fb01c5d7c31d27e83b9b0d1");
      byte[] mrEnclaveReportNew    = Hex.toByteArray("f3f7e9a674c55fb2af543513527b6a7872de305bac171783f6716a0bf6919499");
      byte[] mrEnclaveLedger       = Hex.toByteArray("511eab36de691ded50eb08b173304194da8b9d86bfdd7102001fe6bb279c3666");
      byte[] mrEnclaveLedgerNew    = Hex.toByteArray("89db0d1684fcc98258295c39f4ab68f7de5917ef30f0004d9a86f29930cebbbd");
      byte[] mrEnclaveView         = Hex.toByteArray("ddd59da874fdf3239d5edb1ef251df07a8728c9ef63057dd0b50ade5a9ddb041");
      byte[] mrEnclaveViewNew      = Hex.toByteArray("dd84abda7f05116e21fcd1ee6361b0ec29445fff0472131eaf37bf06255b567a");

      Set<X509Certificate> trustRoots          = getTrustRoots(R.raw.signal_mobilecoin_authority);
      ClientConfig         config              = new ClientConfig();
      String[]             hardeningAdvisories = { "INTEL-SA-00334" };

      config.logAdapter = new MobileCoinLogAdapter();
      config.fogView    = new ClientConfig.Service().withTrustRoots(trustRoots)
                                                    .withVerifier(new Verifier().withMrEnclave(mrEnclaveView, null, hardeningAdvisories)
                                                                                .withMrEnclave(mrEnclaveViewNew, null, hardeningAdvisories));
      config.fogLedger  = new ClientConfig.Service().withTrustRoots(trustRoots)
                                                    .withVerifier(new Verifier().withMrEnclave(mrEnclaveLedger, null, hardeningAdvisories)
                                                                                .withMrEnclave(mrEnclaveLedgerNew, null, hardeningAdvisories));
      config.consensus  = new ClientConfig.Service().withTrustRoots(trustRoots)
                                                    .withVerifier(new Verifier().withMrEnclave(mrEnclaveConsensus, null, hardeningAdvisories)
                                                                                .withMrEnclave(mrEnclaveConsensusNew, null, hardeningAdvisories));
      config.report     = new ClientConfig.Service().withVerifier(new Verifier().withMrEnclave(mrEnclaveReport, null, hardeningAdvisories)
                                                                                .withMrEnclave(mrEnclaveReportNew, null, hardeningAdvisories));
      return config;
    } catch (AttestationException ex) {
      throw new IllegalStateException();
    }
  }
}
