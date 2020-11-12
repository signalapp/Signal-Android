package org.whispersystems.signalservice.internal.push;

import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.internal.contacts.crypto.Quote;
import org.whispersystems.signalservice.internal.contacts.crypto.RemoteAttestation;
import org.whispersystems.signalservice.internal.contacts.crypto.RemoteAttestationCipher;
import org.whispersystems.signalservice.internal.contacts.crypto.RemoteAttestationKeys;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedQuoteException;
import org.whispersystems.signalservice.internal.contacts.entities.MultiRemoteAttestationResponse;
import org.whispersystems.signalservice.internal.contacts.entities.RemoteAttestationRequest;
import org.whispersystems.signalservice.internal.contacts.entities.RemoteAttestationResponse;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.io.IOException;
import java.security.KeyStore;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import okhttp3.Response;
import okhttp3.ResponseBody;

public final class RemoteAttestationUtil {

  private RemoteAttestationUtil() {
  }

  public static RemoteAttestation getAndVerifyRemoteAttestation(PushServiceSocket socket,
                                                                PushServiceSocket.ClientSet clientSet,
                                                                KeyStore iasKeyStore,
                                                                String enclaveName,
                                                                String mrenclave,
                                                                String authorization)
    throws IOException, Quote.InvalidQuoteFormatException, InvalidCiphertextException, UnauthenticatedQuoteException, SignatureException, InvalidKeyException
  {
    ECKeyPair                 keyPair  = buildKeyPair();
    ResponsePair              result   = makeAttestationRequest(socket, clientSet, authorization, enclaveName, keyPair);
    RemoteAttestationResponse response = JsonUtil.fromJson(result.body, RemoteAttestationResponse.class);

    return validateAndBuildRemoteAttestation(response, result.cookies, iasKeyStore, keyPair, mrenclave);
  }

  public static Map<String, RemoteAttestation> getAndVerifyMultiRemoteAttestation(PushServiceSocket socket,
                                                                                  PushServiceSocket.ClientSet clientSet,
                                                                                  KeyStore iasKeyStore,
                                                                                  String enclaveName,
                                                                                  String mrenclave,
                                                                                  String authorization)
      throws IOException, Quote.InvalidQuoteFormatException, InvalidCiphertextException, UnauthenticatedQuoteException, SignatureException, InvalidKeyException
  {
    ECKeyPair                      keyPair      = buildKeyPair();
    ResponsePair                   result       = makeAttestationRequest(socket, clientSet, authorization, enclaveName, keyPair);
    MultiRemoteAttestationResponse response     = JsonUtil.fromJson(result.body, MultiRemoteAttestationResponse.class);
    Map<String, RemoteAttestation> attestations = new HashMap<>();

    if (response.getAttestations().isEmpty() || response.getAttestations().size() > 3) {
      throw new NonSuccessfulResponseCodeException("Incorrect number of attestations: " + response.getAttestations().size());
    }

    for (Map.Entry<String, RemoteAttestationResponse> entry : response.getAttestations().entrySet()) {
      attestations.put(entry.getKey(),
                       validateAndBuildRemoteAttestation(entry.getValue(),
                                                         result.cookies,
                                                         iasKeyStore,
                                                         keyPair,
                                                         mrenclave));
    }

    return attestations;
  }

  private static ECKeyPair buildKeyPair() {
    return Curve.generateKeyPair();
  }

  private static ResponsePair makeAttestationRequest(PushServiceSocket socket,
                                                     PushServiceSocket.ClientSet clientSet,
                                                     String authorization,
                                                     String enclaveName,
                                                     ECKeyPair keyPair)
      throws IOException
  {
    RemoteAttestationRequest attestationRequest = new RemoteAttestationRequest(keyPair.getPublicKey().getPublicKeyBytes());
    Response                 response           = socket.makeRequest(clientSet, authorization, new LinkedList<String>(), "/v1/attestation/" + enclaveName, "PUT", JsonUtil.toJson(attestationRequest));
    ResponseBody             body               = response.body();

    if (body == null) {
      throw new NonSuccessfulResponseCodeException("Empty response!");
    }

    return new ResponsePair(body.string(), parseCookies(response));
  }

  private static List<String> parseCookies(Response response) {
    List<String> rawCookies = response.headers("Set-Cookie");
    List<String> cookies    = new LinkedList<>();

    for (String cookie : rawCookies) {
      cookies.add(cookie.split(";")[0]);
    }

    return cookies;
  }

  private static RemoteAttestation validateAndBuildRemoteAttestation(RemoteAttestationResponse response,
                                                                     List<String> cookies,
                                                                     KeyStore iasKeyStore,
                                                                     ECKeyPair keyPair,
                                                                     String mrenclave)
      throws Quote.InvalidQuoteFormatException, InvalidCiphertextException, UnauthenticatedQuoteException, SignatureException, InvalidKeyException
  {
    RemoteAttestationKeys keys      = new RemoteAttestationKeys(keyPair, response.getServerEphemeralPublic(), response.getServerStaticPublic());
    Quote                 quote     = new Quote(response.getQuote());
    byte[]                requestId = RemoteAttestationCipher.getRequestId(keys, response);

    RemoteAttestationCipher.verifyServerQuote(quote, response.getServerStaticPublic(), mrenclave);

    RemoteAttestationCipher.verifyIasSignature(iasKeyStore, response.getCertificates(), response.getSignatureBody(), response.getSignature(), quote);

    return new RemoteAttestation(requestId, keys, cookies);
  }

  private static class ResponsePair {
    final String       body;
    final List<String> cookies;

    private ResponsePair(String body, List<String> cookies) {
      this.body    = body;
      this.cookies = cookies;
    }
  }
}
