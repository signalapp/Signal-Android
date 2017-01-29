package org.thoughtcrime.redphone.signaling;

import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.TrustStore;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RedPhoneAccountManager {

  private final OkHttpClient client;
  private final String       baseUrl;
  private final String       login;
  private final String       password;

  public RedPhoneAccountManager(String baseUrl, TrustStore trustStore, String login, String password) {
    try {
      TrustManager[] trustManagers = getTrustManager(trustStore);
      SSLContext     context       = SSLContext.getInstance("TLS");
      context.init(null, trustManagers, null);

      this.baseUrl  = baseUrl;
      this.login    = login;
      this.password = password;
      this.client   = new OkHttpClient.Builder()
                                      .sslSocketFactory(context.getSocketFactory(), (X509TrustManager)trustManagers[0])
                                      .build();
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new AssertionError(e);
    }
  }

  public void setGcmId(Optional<String> gcmId) throws IOException {
    Request.Builder builder = new Request.Builder();
    builder.url(baseUrl + "/api/v1/accounts/gcm/");
    builder.header("Authorization", "Basic " + Base64.encodeBytes((login + ":" + password).getBytes()));

    if (gcmId.isPresent()) {
      String body = JsonUtils.toJson(new RedPhoneGcmId(gcmId.get()));
      builder.put(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body));
    } else {
      builder.delete();
    }

    Response response = client.newCall(builder.build()).execute();

    if (response.code() == 401 || response.code() == 403) {
      throw new UnauthorizedException("Failed to perform GCM operation: " + response.code());
    }

    if (!response.isSuccessful()) {
      throw new IOException("Failed to perform GCM operation: " + response.code());
    }
  }

  public void createAccount(String verificationToken, RedPhoneAccountAttributes attributes) throws IOException {
    String body = JsonUtils.toJson(attributes);

    Request request = new Request.Builder()
        .url(baseUrl + "/api/v1/accounts/token/" + verificationToken)
        .put(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body))
        .header("Authorization", "Basic " + Base64.encodeBytes((login + ":" + password).getBytes()))
        .build();

    Response response = client.newCall(request).execute();

    if (!response.isSuccessful()) {
      throw new IOException("Failed to create account: " + response.code());
    }
  }

  public TrustManager[] getTrustManager(TrustStore trustStore) {
    try {
      InputStream keyStoreInputStream = trustStore.getKeyStoreInputStream();
      KeyStore keyStore            = KeyStore.getInstance("BKS");

      keyStore.load(keyStoreInputStream, trustStore.getKeyStorePassword().toCharArray());

      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
      trustManagerFactory.init(keyStore);

      return trustManagerFactory.getTrustManagers();
    } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }
}
