package org.whispersystems.signalservice.api.services;

import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.signal.zkgroup.profiles.ProfileKeyCredentialRequest;
import org.signal.zkgroup.profiles.ProfileKeyCredentialRequestContext;
import org.signal.zkgroup.profiles.ProfileKeyVersion;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Function;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalWebSocket;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.MalformedResponseException;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.ServiceResponseProcessor;
import org.whispersystems.signalservice.internal.push.http.AcceptLanguagesUtil;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.signalservice.internal.websocket.DefaultResponseMapper;
import org.whispersystems.signalservice.internal.websocket.ResponseMapper;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;

/**
 * Provide Profile-related API services, encapsulating the logic to make the request, parse the response,
 * and fallback to appropriate WebSocket or RESTful alternatives.
 */
public final class ProfileService {

  private static final String TAG = ProfileService.class.getSimpleName();

  private final ClientZkProfileOperations    clientZkProfileOperations;
  private final SignalServiceMessageReceiver receiver;
  private final SignalWebSocket              signalWebSocket;

  public ProfileService(ClientZkProfileOperations clientZkProfileOperations,
                        SignalServiceMessageReceiver receiver,
                        SignalWebSocket signalWebSocket)
  {
    this.clientZkProfileOperations = clientZkProfileOperations;
    this.receiver                  = receiver;
    this.signalWebSocket           = signalWebSocket;
  }

  public Single<ServiceResponse<ProfileAndCredential>> getProfile(SignalServiceAddress address,
                                                                  Optional<ProfileKey> profileKey,
                                                                  Optional<UnidentifiedAccess> unidentifiedAccess,
                                                                  SignalServiceProfile.RequestType requestType,
                                                                  Locale locale)
  {
    ACI                                aci   = address.getAci();
    SecureRandom                       random = new SecureRandom();
    ProfileKeyCredentialRequestContext requestContext = null;

    WebSocketProtos.WebSocketRequestMessage.Builder builder = WebSocketProtos.WebSocketRequestMessage.newBuilder()
                                                                                                     .setId(random.nextLong())
                                                                                                     .setVerb("GET");

    if (profileKey.isPresent()) {
      ProfileKeyVersion profileKeyIdentifier = profileKey.get().getProfileKeyVersion(aci.uuid());
      String            version              = profileKeyIdentifier.serialize();

      if (requestType == SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL) {
        requestContext = clientZkProfileOperations.createProfileKeyCredentialRequestContext(random, aci.uuid(), profileKey.get());

        ProfileKeyCredentialRequest request           = requestContext.getRequest();
        String                      credentialRequest = Hex.toStringCondensed(request.serialize());

        builder.setPath(String.format("/v1/profile/%s/%s/%s", aci, version, credentialRequest));
      } else {
        builder.setPath(String.format("/v1/profile/%s/%s", aci, version));
      }
    } else {
      builder.setPath(String.format("/v1/profile/%s", address.getIdentifier()));
    }

    builder.addHeaders(AcceptLanguagesUtil.getAcceptLanguageHeader(locale));

    WebSocketProtos.WebSocketRequestMessage requestMessage = builder.build();

    ResponseMapper<ProfileAndCredential> responseMapper = DefaultResponseMapper.extend(ProfileAndCredential.class)
                                                                               .withResponseMapper(new ProfileResponseMapper(requestType, requestContext))
                                                                               .build();

    return signalWebSocket.request(requestMessage, unidentifiedAccess)
                          .map(responseMapper::map)
                          .onErrorResumeNext(t -> restFallback(address, profileKey, unidentifiedAccess, requestType, locale))
                          .onErrorReturn(ServiceResponse::forUnknownError);
  }

  private Single<ServiceResponse<ProfileAndCredential>> restFallback(SignalServiceAddress address,
                                                                     Optional<ProfileKey> profileKey,
                                                                     Optional<UnidentifiedAccess> unidentifiedAccess,
                                                                     SignalServiceProfile.RequestType requestType,
                                                                     Locale locale)
  {
    return Single.fromFuture(receiver.retrieveProfile(address, profileKey, unidentifiedAccess, requestType, locale), 10, TimeUnit.SECONDS)
                 .onErrorResumeNext(t -> Single.fromFuture(receiver.retrieveProfile(address, profileKey, Optional.absent(), requestType, locale), 10, TimeUnit.SECONDS))
                 .map(p -> ServiceResponse.forResult(p, 0, null));
  }

  /**
   * Maps the API {@link SignalServiceProfile} model into the desired {@link ProfileAndCredential} domain model.
   */
  private class ProfileResponseMapper implements DefaultResponseMapper.CustomResponseMapper<ProfileAndCredential> {
    private final SignalServiceProfile.RequestType   requestType;
    private final ProfileKeyCredentialRequestContext requestContext;

    public ProfileResponseMapper(SignalServiceProfile.RequestType requestType, ProfileKeyCredentialRequestContext requestContext) {
      this.requestType    = requestType;
      this.requestContext = requestContext;
    }

    @Override
    public ServiceResponse<ProfileAndCredential> map(int status, String body, Function<String, String> getHeader, boolean unidentified)
        throws MalformedResponseException
    {
      try {
        SignalServiceProfile signalServiceProfile = JsonUtil.fromJsonResponse(body, SignalServiceProfile.class);
        ProfileKeyCredential profileKeyCredential = null;
        if (requestContext != null && signalServiceProfile.getProfileKeyCredentialResponse() != null) {
          profileKeyCredential = clientZkProfileOperations.receiveProfileKeyCredential(requestContext, signalServiceProfile.getProfileKeyCredentialResponse());
        }

        return ServiceResponse.forResult(new ProfileAndCredential(signalServiceProfile, requestType, Optional.fromNullable(profileKeyCredential)), status, body);
      } catch (VerificationFailedException e) {
        return ServiceResponse.forApplicationError(e, status, body);
      }
    }
  }

  /**
   * Response processor for {@link ProfileAndCredential} service response.
   */
  public static final class ProfileResponseProcessor extends ServiceResponseProcessor<ProfileAndCredential> {
    public ProfileResponseProcessor(ServiceResponse<ProfileAndCredential> response) {
      super(response);
    }

    public <T> Pair<T, ProfileAndCredential> getResult(T with) {
      return new Pair<>(with, getResult());
    }

    @Override
    public boolean notFound() {
      return super.notFound();
    }

    @Override
    public boolean genericIoError() {
      return super.genericIoError();
    }
  }
}
