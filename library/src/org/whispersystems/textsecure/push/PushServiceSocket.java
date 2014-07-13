/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecure.push;

import android.content.Context;
import android.util.Log;

import com.google.thoughtcrimegson.Gson;
import com.google.thoughtcrimegson.JsonParseException;

import org.apache.http.conn.ssl.StrictHostnameVerifier;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.state.PreKeyBundle;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.textsecure.util.Base64;
import org.whispersystems.textsecure.util.BlacklistingTrustManager;
import org.whispersystems.textsecure.util.Util;

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
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/**
 *
 * Network interface to the TextSecure server API.
 *
 * @author Moxie Marlinspike
 */
public class PushServiceSocket {

  private static final String CREATE_ACCOUNT_SMS_PATH   = "/v1/accounts/sms/code/%s";
  private static final String CREATE_ACCOUNT_VOICE_PATH = "/v1/accounts/voice/code/%s";
  private static final String VERIFY_ACCOUNT_PATH       = "/v1/accounts/code/%s";
  private static final String REGISTER_GCM_PATH         = "/v1/accounts/gcm/";
  private static final String PREKEY_METADATA_PATH      = "/v2/keys/";
  private static final String PREKEY_PATH               = "/v2/keys/%s";
  private static final String PREKEY_DEVICE_PATH        = "/v2/keys/%s/%s";
  private static final String SIGNED_PREKEY_PATH        = "/v2/keys/signed";

  private static final String DIRECTORY_TOKENS_PATH     = "/v1/directory/tokens";
  private static final String DIRECTORY_VERIFY_PATH     = "/v1/directory/%s";
  private static final String MESSAGE_PATH              = "/v1/messages/%s";
  private static final String ATTACHMENT_PATH           = "/v1/attachments/%s";

  private static final boolean ENFORCE_SSL = true;

  private final Context        context;
  private final String         serviceUrl;
  private final String         localNumber;
  private final String         password;
  private final TrustManager[] trustManagers;

  public PushServiceSocket(Context context, String serviceUrl, TrustStore trustStore,
                           String localNumber, String password)
  {
    this.context       = context.getApplicationContext();
    this.serviceUrl    = serviceUrl;
    this.localNumber   = localNumber;
    this.password      = password;
    this.trustManagers = initializeTrustManager(trustStore);
  }

  public void createAccount(boolean voice) throws IOException {
    String path = voice ? CREATE_ACCOUNT_VOICE_PATH : CREATE_ACCOUNT_SMS_PATH;
    makeRequest(String.format(path, localNumber), "GET", null);
  }

  public void verifyAccount(String verificationCode, String signalingKey,
                            boolean supportsSms, int registrationId)
      throws IOException
  {
    AccountAttributes signalingKeyEntity = new AccountAttributes(signalingKey, supportsSms, registrationId);
    makeRequest(String.format(VERIFY_ACCOUNT_PATH, verificationCode),
                "PUT", new Gson().toJson(signalingKeyEntity));
  }

  public void registerGcmId(String gcmRegistrationId) throws IOException {
    GcmRegistrationId registration = new GcmRegistrationId(gcmRegistrationId);
    makeRequest(REGISTER_GCM_PATH, "PUT", new Gson().toJson(registration));
  }

  public void unregisterGcmId() throws IOException {
    makeRequest(REGISTER_GCM_PATH, "DELETE", null);
  }

  public void sendMessage(OutgoingPushMessageList bundle)
      throws IOException
  {
    try {
      makeRequest(String.format(MESSAGE_PATH, bundle.getDestination()), "PUT", new Gson().toJson(bundle));
    } catch (NotFoundException nfe) {
      throw new UnregisteredUserException(bundle.getDestination(), nfe);
    }
  }

  public void registerPreKeys(IdentityKey identityKey,
                              PreKeyRecord lastResortKey,
                              SignedPreKeyRecord signedPreKey,
                              List<PreKeyRecord> records)
      throws IOException
  {
    List<PreKeyEntity> entities = new LinkedList<>();

    for (PreKeyRecord record : records) {
      PreKeyEntity entity = new PreKeyEntity(record.getId(),
                                             record.getKeyPair().getPublicKey());

      entities.add(entity);
    }

    PreKeyEntity lastResortEntity = new PreKeyEntity(lastResortKey.getId(),
                                                     lastResortKey.getKeyPair().getPublicKey());

    SignedPreKeyEntity signedPreKeyEntity = new SignedPreKeyEntity(signedPreKey.getId(),
                                                                   signedPreKey.getKeyPair().getPublicKey(),
                                                                   signedPreKey.getSignature());

    makeRequest(String.format(PREKEY_PATH, ""), "PUT",
                PreKeyState.toJson(new PreKeyState(entities, lastResortEntity,
                                                   signedPreKeyEntity, identityKey)));
  }

  public int getAvailablePreKeys() throws IOException {
    String       responseText = makeRequest(PREKEY_METADATA_PATH, "GET", null);
    PreKeyStatus preKeyStatus = new Gson().fromJson(responseText, PreKeyStatus.class);

    return preKeyStatus.getCount();
  }

  public List<PreKeyBundle> getPreKeys(PushAddress destination) throws IOException {
    try {
      String deviceId = String.valueOf(destination.getDeviceId());

      if (deviceId.equals("1"))
        deviceId = "*";

      String path = String.format(PREKEY_DEVICE_PATH, destination.getNumber(), deviceId);

      if (!Util.isEmpty(destination.getRelay())) {
        path = path + "?relay=" + destination.getRelay();
      }

      String             responseText = makeRequest(path, "GET", null);
      PreKeyResponse     response     = PreKeyResponse.fromJson(responseText);
      List<PreKeyBundle> bundles      = new LinkedList<>();

      for (PreKeyResponseItem device : response.getDevices()) {
        ECPublicKey preKey                = null;
        ECPublicKey signedPreKey          = null;
        byte[]      signedPreKeySignature = null;
        int         preKeyId              = -1;
        int         signedPreKeyId        = -1;

        if (device.getSignedPreKey() != null) {
          signedPreKey          = device.getSignedPreKey().getPublicKey();
          signedPreKeyId        = device.getSignedPreKey().getKeyId();
          signedPreKeySignature = device.getSignedPreKey().getSignature();
        }

        if (device.getPreKey() != null) {
          preKeyId = device.getPreKey().getKeyId();
          preKey   = device.getPreKey().getPublicKey();
        }

        bundles.add(new PreKeyBundle(device.getRegistrationId(), device.getDeviceId(), preKeyId,
                                     preKey, signedPreKeyId, signedPreKey, signedPreKeySignature,
                                     response.getIdentityKey()));
      }

      return bundles;
    } catch (JsonParseException e) {
      throw new IOException(e);
    } catch (NotFoundException nfe) {
      throw new UnregisteredUserException(destination.getNumber(), nfe);
    }
  }

  public PreKeyBundle getPreKey(PushAddress destination) throws IOException {
    try {
      String path = String.format(PREKEY_DEVICE_PATH, destination.getNumber(),
                                  String.valueOf(destination.getDeviceId()));

      if (!Util.isEmpty(destination.getRelay())) {
        path = path + "?relay=" + destination.getRelay();
      }

      String         responseText = makeRequest(path, "GET", null);
      PreKeyResponse response     = PreKeyResponse.fromJson(responseText);

      if (response.getDevices() == null || response.getDevices().size() < 1)
        throw new IOException("Empty prekey list");

      PreKeyResponseItem device                = response.getDevices().get(0);
      ECPublicKey        preKey                = null;
      ECPublicKey        signedPreKey          = null;
      byte[]             signedPreKeySignature = null;
      int                preKeyId              = -1;
      int                signedPreKeyId        = -1;

      if (device.getPreKey() != null) {
        preKeyId = device.getPreKey().getKeyId();
        preKey   = device.getPreKey().getPublicKey();
      }

      if (device.getSignedPreKey() != null) {
        signedPreKeyId        = device.getSignedPreKey().getKeyId();
        signedPreKey          = device.getSignedPreKey().getPublicKey();
        signedPreKeySignature = device.getSignedPreKey().getSignature();
      }

      return new PreKeyBundle(device.getRegistrationId(), device.getDeviceId(), preKeyId, preKey,
                              signedPreKeyId, signedPreKey, signedPreKeySignature, response.getIdentityKey());
    } catch (JsonParseException e) {
      throw new IOException(e);
    } catch (NotFoundException nfe) {
      throw new UnregisteredUserException(destination.getNumber(), nfe);
    }
  }

  public SignedPreKeyEntity getCurrentSignedPreKey() throws IOException {
    try {
      String responseText = makeRequest(SIGNED_PREKEY_PATH, "GET", null);
      return SignedPreKeyEntity.fromJson(responseText);
    } catch (NotFoundException e) {
      Log.w("PushServiceSocket", e);
      return null;
    }
  }

  public void setCurrentSignedPreKey(SignedPreKeyRecord signedPreKey) throws IOException {
    SignedPreKeyEntity signedPreKeyEntity = new SignedPreKeyEntity(signedPreKey.getId(),
                                                                   signedPreKey.getKeyPair().getPublicKey(),
                                                                   signedPreKey.getSignature());
    makeRequest(SIGNED_PREKEY_PATH, "PUT", SignedPreKeyEntity.toJson(signedPreKeyEntity));
  }

  public long sendAttachment(PushAttachmentData attachment) throws IOException {
    String               response      = makeRequest(String.format(ATTACHMENT_PATH, ""), "GET", null);
    AttachmentDescriptor attachmentKey = new Gson().fromJson(response, AttachmentDescriptor.class);

    if (attachmentKey == null || attachmentKey.getLocation() == null) {
      throw new IOException("Server failed to allocate an attachment key!");
    }

    Log.w("PushServiceSocket", "Got attachment content location: " + attachmentKey.getLocation());

    uploadExternalFile("PUT", attachmentKey.getLocation(), attachment.getData());

    return attachmentKey.getId();
  }

  public File retrieveAttachment(String relay, long attachmentId) throws IOException {
    String path = String.format(ATTACHMENT_PATH, String.valueOf(attachmentId));

    if (!Util.isEmpty(relay)) {
      path = path + "?relay=" + relay;
    }

    String               response   = makeRequest(path, "GET", null);
    AttachmentDescriptor descriptor = new Gson().fromJson(response, AttachmentDescriptor.class);

    Log.w("PushServiceSocket", "Attachment: " + attachmentId + " is at: " + descriptor.getLocation());

    File attachment = File.createTempFile("attachment", ".tmp", context.getFilesDir());
    attachment.deleteOnExit();

    downloadExternalFile(descriptor.getLocation(), attachment);

    return attachment;
  }

  public List<ContactTokenDetails> retrieveDirectory(Set<String> contactTokens) {
    try {
      ContactTokenList        contactTokenList = new ContactTokenList(new LinkedList<String>(contactTokens));
      String                  response         = makeRequest(DIRECTORY_TOKENS_PATH, "PUT", new Gson().toJson(contactTokenList));
      ContactTokenDetailsList activeTokens     = new Gson().fromJson(response, ContactTokenDetailsList.class);

      return activeTokens.getContacts();
    } catch (IOException ioe) {
      Log.w("PushServiceSocket", ioe);
      return null;
    }
  }

  public ContactTokenDetails getContactTokenDetails(String contactToken) throws IOException {
    try {
      String response = makeRequest(String.format(DIRECTORY_VERIFY_PATH, contactToken), "GET", null);
      return new Gson().fromJson(response, ContactTokenDetails.class);
    } catch (NotFoundException nfe) {
      return null;
    }
  }

  private void downloadExternalFile(String url, File localDestination)
      throws IOException
  {
    URL               downloadUrl = new URL(url);
    HttpURLConnection connection  = (HttpURLConnection) downloadUrl.openConnection();
    connection.setRequestProperty("Content-Type", "application/octet-stream");
    connection.setRequestMethod("GET");
    connection.setDoInput(true);

    try {
      if (connection.getResponseCode() != 200) {
        throw new IOException("Bad response: " + connection.getResponseCode());
      }

      OutputStream output = new FileOutputStream(localDestination);
      InputStream input   = connection.getInputStream();
      byte[] buffer       = new byte[4096];
      int read;

      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }

      output.close();
      Log.w("PushServiceSocket", "Downloaded: " + url + " to: " + localDestination.getAbsolutePath());
    } finally {
      connection.disconnect();
    }
  }

  private void uploadExternalFile(String method, String url, byte[] data)
    throws IOException
  {
    URL                uploadUrl  = new URL(url);
    HttpsURLConnection connection = (HttpsURLConnection) uploadUrl.openConnection();
    connection.setDoOutput(true);
    connection.setRequestMethod(method);
    connection.setRequestProperty("Content-Type", "application/octet-stream");
    connection.connect();

    try {
      OutputStream out = connection.getOutputStream();
      out.write(data);
      out.close();

      if (connection.getResponseCode() != 200) {
        throw new IOException("Bad response: " + connection.getResponseCode() + " " + connection.getResponseMessage());
      }
    } finally {
      connection.disconnect();
    }
  }

  private String makeRequest(String urlFragment, String method, String body)
      throws IOException
  {
    HttpURLConnection connection = makeBaseRequest(urlFragment, method, body);
    String            response   = Util.readFully(connection.getInputStream());

    connection.disconnect();

    return response;
  }

  private HttpURLConnection makeBaseRequest(String urlFragment, String method, String body)
      throws IOException
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
      connection.disconnect();
      throw new RateLimitException("Rate limit exceeded: " + connection.getResponseCode());
    }

    if (connection.getResponseCode() == 401 || connection.getResponseCode() == 403) {
      connection.disconnect();
      throw new AuthorizationFailedException("Authorization failed!");
    }

    if (connection.getResponseCode() == 404) {
      connection.disconnect();
      throw new NotFoundException("Not found");
    }

    if (connection.getResponseCode() == 409) {
      String response = Util.readFully(connection.getErrorStream());
      throw new MismatchedDevicesException(new Gson().fromJson(response, MismatchedDevices.class));
    }

    if (connection.getResponseCode() == 410) {
      String response = Util.readFully(connection.getErrorStream());
      throw new StaleDevicesException(new Gson().fromJson(response, StaleDevices.class));
    }

    if (connection.getResponseCode() == 417) {
      throw new ExpectationFailedException();
    }

    if (connection.getResponseCode() != 200 && connection.getResponseCode() != 204) {
      throw new IOException("Bad response: " + connection.getResponseCode() + " " + connection.getResponseMessage());
    }

    return connection;
  }

  private HttpURLConnection getConnection(String urlFragment, String method) throws IOException {
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustManagers, null);

      URL url = new URL(String.format("%s%s", serviceUrl, urlFragment));
      Log.w("PushServiceSocket", "Push service URL: " + serviceUrl);
      Log.w("PushServiceSocket", "Opening URL: " + url);

      HttpURLConnection connection = (HttpURLConnection)url.openConnection();

      if (ENFORCE_SSL) {
        ((HttpsURLConnection)connection).setSSLSocketFactory(context.getSocketFactory());
        ((HttpsURLConnection)connection).setHostnameVerifier(new StrictHostnameVerifier());
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
      return "Basic " + Base64.encodeBytes((localNumber + ":" + password).getBytes("UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  private TrustManager[] initializeTrustManager(TrustStore trustStore) {
    try {
      InputStream keyStoreInputStream = trustStore.getKeyStoreInputStream();
      KeyStore    keyStore            = KeyStore.getInstance("BKS");

      keyStore.load(keyStoreInputStream, trustStore.getKeyStorePassword().toCharArray());

      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
      trustManagerFactory.init(keyStore);

      return BlacklistingTrustManager.createFor(trustManagerFactory.getTrustManagers());
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

  private static class GcmRegistrationId {
    private String gcmRegistrationId;

    public GcmRegistrationId() {}

    public GcmRegistrationId(String gcmRegistrationId) {
      this.gcmRegistrationId = gcmRegistrationId;
    }
  }

  private static class AttachmentDescriptor {
    private long id;
    private String location;

    public long getId() {
      return id;
    }

    public String getLocation() {
      return location;
    }
  }

  public interface TrustStore {
    public InputStream getKeyStoreInputStream();
    public String getKeyStorePassword();
  }
}
