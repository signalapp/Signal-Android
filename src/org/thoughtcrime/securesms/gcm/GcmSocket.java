package org.thoughtcrime.securesms.gcm;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Base64;
import android.util.Log;

import com.google.thoughtcrimegson.Gson;
import org.thoughtcrime.securesms.directory.DirectoryDescriptor;
import org.thoughtcrime.securesms.directory.NumberFilter;
import org.thoughtcrime.securesms.util.Util;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileOutputStream;
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
import java.util.zip.GZIPInputStream;

public class GcmSocket {

  private static final String CREATE_ACCOUNT_PATH = "/v1/accounts/%s";
  private static final String VERIFY_ACCOUNT_PATH = "/v1/accounts/%s";
  private static final String REGISTER_GCM_PATH   = "/v1/accounts/gcm/%s";
  private static final String DIRECTORY_PATH      = "/v1/directory/";
  private static final String MESSAGE_PATH        = "/v1/messages/";

  private final String localNumber;
  private final String password;
  private final TrustManagerFactory trustManagerFactory;

  public GcmSocket(Context context, String localNumber, String password) {
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

  public void unregisterGcmId(String gcmRegistrationId) throws IOException {
    GcmRegistrationId registration = new GcmRegistrationId(gcmRegistrationId);
    makeRequest(String.format(REGISTER_GCM_PATH, localNumber), "DELETE", new Gson().toJson(registration));
  }

  public void sendMessage(String recipient, String messageText) throws IOException {
    OutgoingGcmMessage message  = new OutgoingGcmMessage(recipient, messageText);
    String responseText         = makeRequest(MESSAGE_PATH, "POST", new Gson().toJson(message));
    GcmMessageResponse response = new Gson().fromJson(responseText, GcmMessageResponse.class);

    if (response.getFailure().size() != 0)
      throw new IOException("Got send failure: " + response.getFailure().get(0));
  }

  public void retrieveDirectory(Context context ) {
    try {
      DirectoryDescriptor directoryDescriptor = new Gson().fromJson(makeRequest(DIRECTORY_PATH, "GET", null),
                                                                    DirectoryDescriptor.class);

      File directoryData = downloadExternalFile(context, directoryDescriptor.getUrl());

      NumberFilter.getInstance(context).update(directoryData,
                                               directoryDescriptor.getCapacity(),
                                               directoryDescriptor.getHashCount(),
                                               directoryDescriptor.getVersion());

    } catch (IOException ioe) {
      Log.w("GcmSocket", ioe);
    }
  }

  private File downloadExternalFile(Context context, String url) throws IOException {
    File download                 = File.createTempFile("directory", ".dat", context.getFilesDir());
    URL downloadUrl               = new URL(url);
    HttpsURLConnection connection = (HttpsURLConnection)downloadUrl.openConnection();
    connection.setDoInput(true);

    if (connection.getResponseCode() != 200) {
      throw new IOException("Bad response: " + connection.getResponseCode());
    }

    OutputStream output = new FileOutputStream(download);
    InputStream input   = new GZIPInputStream(connection.getInputStream());
    int read            = 0;
    byte[] buffer       = new byte[4096];

    while ((read = input.read(buffer)) != -1) {
      output.write(buffer, 0, read);
    }

    output.close();

    return download;
  }

  private String makeRequest(String urlFragment, String method, String body) throws IOException {
    HttpsURLConnection connection = getConnection(urlFragment, method);

    if (body != null) {
      connection.setDoOutput(true);
    }

    connection.connect();

    if (body != null) {
      Log.w("GcmSocket", method +  "  --  " + body);
      OutputStream out = connection.getOutputStream();
      out.write(body.getBytes());
      out.close();
    }

    if (connection.getResponseCode() != 200) {
      throw new IOException("Bad response: " + connection.getResponseCode() + " " + connection.getResponseMessage());
    }

    return Util.readFully(connection.getInputStream());
  }

  private HttpsURLConnection getConnection(String urlFragment, String method) throws IOException {
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustManagerFactory.getTrustManagers(), null);

      URL url = new URL(String.format("https://gcm.textsecure.whispersystems.org%s", urlFragment));
      HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
      connection.setSSLSocketFactory(context.getSocketFactory());
      connection.setRequestMethod(method);
      connection.setRequestProperty("Content-Type", "application/json");

      if (password != null) {
        System.out.println("Adding authorization header: " + getAuthorizationHeader());
        connection.setRequestProperty("Authorization", getAuthorizationHeader());
      }

      return connection;
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
