package org.thoughtcrime.securesms.gcm;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Base64;
import android.util.Log;

import com.google.thoughtcrimegson.Gson;
import org.thoughtcrime.securesms.Release;
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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.zip.GZIPInputStream;

public class PushServiceSocket {

  private static final String CREATE_ACCOUNT_SMS_PATH   = "/v1/accounts/sms/%s";
  private static final String CREATE_ACCOUNT_VOICE_PATH = "/v1/accounts/voice/%s";
  private static final String VERIFY_ACCOUNT_PATH       = "/v1/accounts/code/%s";
  private static final String REGISTER_GCM_PATH         = "/v1/accounts/gcm/";

  private static final String DIRECTORY_PATH            = "/v1/directory/";
  private static final String MESSAGE_PATH              = "/v1/messages/";

  private final String localNumber;
  private final String password;
  private final TrustManagerFactory trustManagerFactory;

  public PushServiceSocket(Context context, String localNumber, String password) {
    this.localNumber         = localNumber;
    this.password            = password;
    this.trustManagerFactory = initializeTrustManagerFactory(context);
  }

  public void createAccount(boolean voice) throws IOException, RateLimitException {
    String path = voice ? CREATE_ACCOUNT_VOICE_PATH : CREATE_ACCOUNT_SMS_PATH;
    makeRequest(String.format(path, localNumber), "POST", null);
  }

  public void verifyAccount(String verificationCode) throws IOException, RateLimitException {
    makeRequest(String.format(VERIFY_ACCOUNT_PATH, verificationCode), "PUT", null);
  }

  public void registerGcmId(String gcmRegistrationId) throws IOException, RateLimitException {
    GcmRegistrationId registration = new GcmRegistrationId(gcmRegistrationId);
    makeRequest(REGISTER_GCM_PATH, "PUT", new Gson().toJson(registration));
  }

  public void unregisterGcmId(String gcmRegistrationId) throws IOException, RateLimitException {
    GcmRegistrationId registration = new GcmRegistrationId(gcmRegistrationId);
    makeRequest(REGISTER_GCM_PATH, "DELETE", new Gson().toJson(registration));
  }

  public void sendMessage(String recipient, String messageText)
      throws IOException, RateLimitException
  {
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
      Log.w("PushServiceSocket", ioe);
    } catch (RateLimitException e) {
      Log.w("PushServiceSocket", e);
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
    byte[] buffer       = new byte[4096];
    int read;

    while ((read = input.read(buffer)) != -1) {
      output.write(buffer, 0, read);
    }

    output.close();

    return download;
  }

  private String makeRequest(String urlFragment, String method, String body)
      throws IOException, RateLimitException
  {
    HttpURLConnection connection = getConnection(urlFragment, method);

    if (body != null) {
      connection.setDoOutput(true);
    }

    connection.connect();

    if (body != null) {
      Log.w("PushServiceSocket", method +  "  --  " + body);
      OutputStream out = connection.getOutputStream();
      out.write(body.getBytes());
      out.close();
    }

    if (connection.getResponseCode() == 413) {
      throw new RateLimitException("Rate limit exceeded: " + connection.getResponseCode());
    }

    if (connection.getResponseCode() != 200) {
      throw new IOException("Bad response: " + connection.getResponseCode() + " " + connection.getResponseMessage());
    }

    return Util.readFully(connection.getInputStream());
  }

  private HttpURLConnection getConnection(String urlFragment, String method) throws IOException {
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustManagerFactory.getTrustManagers(), null);

      URL url = new URL(String.format("%s%s", Release.PUSH_SERVICE_URL, urlFragment));
      HttpURLConnection connection = (HttpURLConnection)url.openConnection();

      if (Release.ENFORCE_SSL) {
        ((HttpsURLConnection)connection).setSSLSocketFactory(context.getSocketFactory());
      }

      connection.setRequestMethod(method);
      connection.setRequestProperty("Content-Type", "application/json");

      if (password != null) {
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


//  private class Verification {
//
//    private String verificationCode;
//
//    public Verification() {}
//
//    public Verification(String verificationCode) {
//      this.verificationCode    = verificationCode;
//    }
//  }

  private class GcmRegistrationId {
    private String gcmRegistrationId;

    public GcmRegistrationId() {}

    public GcmRegistrationId(String gcmRegistrationId) {
      this.gcmRegistrationId = gcmRegistrationId;
    }
  }

}
