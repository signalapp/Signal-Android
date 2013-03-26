package org.thoughtcrime.securesms.gcm;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Base64;

import com.google.thoughtcrimegson.Gson;
import org.thoughtcrime.securesms.util.Util;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class RegistrationSocket {

  private static final String CREATE_ACCOUNT_PATH = "/v1/accounts/%s";
  private static final String VERIFY_ACCOUNT_PATH = "/v1/accounts/%s";
  private static final String REGISTER_GCM_PATH   = "/v1/accounts/gcm/%s";

  private final String localNumber;
  private final String password;
  private final TrustManagerFactory trustManagerFactory;

  public RegistrationSocket(Context context, String localNumber, String password) {
    this.localNumber         = localNumber;
    this.password            = password;
    this.trustManagerFactory = initializeTrustManagerFactory(context);
  }

  public void createAccount() throws IOException {
    makeRequest(String.format(CREATE_ACCOUNT_PATH, localNumber), "POST", null);
  }

  public void verifyAccount(String verificationCode, String password)
      throws IOException
  {
    Verification verification = new Verification(verificationCode, password);
    makeRequest(String.format(VERIFY_ACCOUNT_PATH, localNumber), "PUT", new Gson().toJson(verification));
  }

  public void registerGcmId(String gcmRegistrationId) throws IOException {
    GcmRegistrationId registration = new GcmRegistrationId(gcmRegistrationId);
    makeRequest(String.format(REGISTER_GCM_PATH, localNumber), "PUT", new Gson().toJson(registration));
  }

  private TrustManagerFactory initializeTrustManagerFactory(Context context) {
    try {
      AssetManager assetManager       = context.getAssets();
      InputStream keyStoreInputStream = assetManager.open("whisper.store");
      KeyStore trustStore             = KeyStore.getInstance("BKS");

      trustStore.load(keyStoreInputStream, "whisper".toCharArray());

      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
      trustManagerFactory.init(trustStore);

      return trustManagerFactory;
    } catch (KeyStoreException kse) {
      throw new AssertionError(kse);
    } catch (CertificateException e) {
      throw new AssertionError(e);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (IOException ioe) {
      throw new AssertionError(ioe);
    }
  }

  private String makeRequest(String urlFragment, String method, String body) throws IOException {
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustManagerFactory.getTrustManagers(), null);

      URL url = new URL(String.format("https://gcm.textsecure.whispersystems.org/%s", urlFragment));
      HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
      connection.setSSLSocketFactory(context.getSocketFactory());
      connection.setRequestMethod(method);
      connection.setRequestProperty("Content-Type", "application/json");

      if (password != null) {
        connection.setRequestProperty("Authorization", getAuthorizationHeader());
      }

      if (body != null) {
        connection.setDoOutput(true);
      }

      connection.connect();

      if (body != null) {
        OutputStream out = connection.getOutputStream();
        out.write(body.getBytes());
        out.close();
      }

      if (connection.getResponseCode() != 200) {
        throw new IOException("Bad response: " + connection.getResponseCode());
      }

      return Util.readFully(connection.getInputStream());
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (KeyManagementException e) {
      throw new AssertionError(e);
    } catch (MalformedURLException e) {
      throw new AssertionError(e);
    }
  }

  private String getAuthorizationHeader() {
    try {
      return "Basic " + new String(Base64.encode((localNumber + ":" + password).getBytes("UTF-8"), Base64.NO_WRAP));
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  private class Verification {

    private String verificationCode;
    private String authenticationToken;

    public Verification() {}

    public Verification(String verificationCode,
                        String authenticationToken)
    {
      this.verificationCode    = verificationCode;
      this.authenticationToken = authenticationToken;
    }
  }

  private class GcmRegistrationId {
    private String gcmRegistrationId;

    public GcmRegistrationId() {}

    public GcmRegistrationId(String gcmRegistrationId) {
      this.gcmRegistrationId = gcmRegistrationId;
    }
  }

}
