package org.thoughtcrime.securesms.payments;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;

import com.mobilecoin.lib.ClientConfig;

import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.internal.push.AuthCredentials;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class MobileCoinConfig {

  abstract @NonNull List<Uri> getConsensusUris();

  abstract @NonNull Uri getFogUri();

  abstract @NonNull Uri getFogReportUri();

  abstract @NonNull byte[] getFogAuthoritySpki();

  abstract @NonNull AuthCredentials getAuth() throws IOException;

  abstract @NonNull ClientConfig getConfig();

  public static MobileCoinConfig getTestNet(SignalServiceAccountManager signalServiceAccountManager) {
    return new MobileCoinTestNetConfig(signalServiceAccountManager);
  }

  public static MobileCoinConfig getMainNet(SignalServiceAccountManager signalServiceAccountManager) {
    return new MobileCoinMainNetConfig(signalServiceAccountManager);
  }

  protected static Set<X509Certificate> getTrustRoots(@RawRes int pemResource) {
    try (InputStream inputStream = AppDependencies.getApplication().getResources().openRawResource(pemResource)) {
      Collection<? extends Certificate> certificates = CertificateFactory.getInstance("X.509")
                                                                         .generateCertificates(inputStream);

      HashSet<X509Certificate> x509Certificates = new HashSet<>(certificates.size());
      for (Certificate c : certificates) {
        x509Certificates.add((X509Certificate) c);
      }

      return x509Certificates;
    } catch (IOException | CertificateException e) {
      throw new AssertionError(e);
    }
  }
}
