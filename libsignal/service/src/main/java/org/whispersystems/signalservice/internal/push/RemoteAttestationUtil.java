package org.whispersystems.signalservice.internal.push;

import org.whispersystems.curve25519.Curve25519;
import org.whispersystems.curve25519.Curve25519KeyPair;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.internal.contacts.crypto.Quote;
import org.whispersystems.signalservice.internal.contacts.crypto.RemoteAttestation;
import org.whispersystems.signalservice.internal.contacts.crypto.RemoteAttestationCipher;
import org.whispersystems.signalservice.internal.contacts.crypto.RemoteAttestationKeys;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedQuoteException;
import org.whispersystems.signalservice.internal.contacts.entities.RemoteAttestationRequest;
import org.whispersystems.signalservice.internal.contacts.entities.RemoteAttestationResponse;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.io.IOException;
import java.security.KeyStore;
import java.security.SignatureException;
import java.util.LinkedList;
import java.util.List;

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
    throws IOException, Quote.InvalidQuoteFormatException, InvalidCiphertextException, UnauthenticatedQuoteException, SignatureException
  {
    Curve25519                                    curve                   = Curve25519.getInstance(Curve25519.BEST);
    Curve25519KeyPair                             keyPair                 = curve.generateKeyPair();
    RemoteAttestationRequest                      attestationRequest      = new RemoteAttestationRequest(keyPair.getPublicKey());
    Pair<RemoteAttestationResponse, List<String>> attestationResponsePair = getRemoteAttestation(socket, clientSet, authorization, attestationRequest, enclaveName);
    RemoteAttestationResponse                     attestationResponse     = attestationResponsePair.first();
    List<String>                                  attestationCookies      = attestationResponsePair.second();

    RemoteAttestationKeys keys      = new RemoteAttestationKeys(keyPair, attestationResponse.getServerEphemeralPublic(), attestationResponse.getServerStaticPublic());
    Quote                 quote     = new Quote(attestationResponse.getQuote());
    byte[]                requestId = RemoteAttestationCipher.getRequestId(keys, attestationResponse);

    RemoteAttestationCipher.verifyServerQuote(quote, attestationResponse.getServerStaticPublic(), mrenclave);

    RemoteAttestationCipher.verifyIasSignature(iasKeyStore, attestationResponse.getCertificates(), attestationResponse.getSignatureBody(), attestationResponse.getSignature(), quote);

    return new RemoteAttestation(requestId, keys, attestationCookies);
  }

  private static Pair<RemoteAttestationResponse, List<String>> getRemoteAttestation(PushServiceSocket socket,
                                                                                    PushServiceSocket.ClientSet clientSet,
                                                                                    String authorization,
                                                                                    RemoteAttestationRequest request,
                                                                                    String enclaveName)
    throws IOException
  {
    Response response       = socket.makeRequest(clientSet, authorization, new LinkedList<String>(), "/v1/attestation/" + enclaveName, "PUT", JsonUtil.toJson(request));
    ResponseBody body       = response.body();
    List<String> rawCookies = response.headers("Set-Cookie");
    List<String> cookies    = new LinkedList<>();

    for (String cookie : rawCookies) {
      cookies.add(cookie.split(";")[0]);
    }

    if (body != null) {
      return new Pair<>(JsonUtil.fromJson(body.string(), RemoteAttestationResponse.class), cookies);
    } else {
      throw new NonSuccessfulResponseCodeException("Empty response!");
    }
  }
}
