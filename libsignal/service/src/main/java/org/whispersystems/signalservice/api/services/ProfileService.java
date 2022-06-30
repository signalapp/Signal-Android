package org.whispersystems.signalservice.api.services;

import org.signal.libsignal.protocol.util.Pair;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredentialRequest;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredentialRequestContext;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyVersion;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalWebSocket;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.MalformedResponseException;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.ServiceResponseProcessor;
import org.whispersystems.signalservice.internal.push.http.AcceptLanguagesUtil;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.signalservice.internal.websocket.DefaultResponseMapper;
import org.whispersystems.signalservice.internal.websocket.ResponseMapper;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketRequestMessage;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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
    ServiceId                          serviceId      = address.getServiceId();
    SecureRandom                       random         = new SecureRandom();
    ProfileKeyCredentialRequestContext requestContext = null;

    WebSocketRequestMessage.Builder builder = WebSocketRequestMessage.newBuilder()
                                                                     .setId(random.nextLong())
                                                                     .setVerb("GET");

    if (profileKey.isPresent()) {
      ProfileKeyVersion profileKeyIdentifier = profileKey.get().getProfileKeyVersion(serviceId.uuid());
      String            version              = profileKeyIdentifier.serialize();

      if (requestType == SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL) {
        requestContext = clientZkProfileOperations.createProfileKeyCredentialRequestContext(random, serviceId.uuid(), profileKey.get());

        ProfileKeyCredentialRequest request           = requestContext.getRequest();
        String                      credentialRequest = Hex.toStringCondensed(request.serialize());

        builder.setPath(String.format("/v1/profile/%s/%s/%s", serviceId, version, credentialRequest));
      } else {
        builder.setPath(String.format("/v1/profile/%s/%s", serviceId, version));
      }
    } else {
      builder.setPath(String.format("/v1/profile/%s", address.getIdentifier()));
    }

    builder.addHeaders(AcceptLanguagesUtil.getAcceptLanguageHeader(locale));

    WebSocketRequestMessage requestMessage = builder.build();

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
                 .onErrorResumeNext(t -> Single.fromFuture(receiver.retrieveProfile(address, profileKey, Optional.empty(), requestType, locale), 10, TimeUnit.SECONDS))
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

        return ServiceResponse.forResult(new ProfileAndCredential(signalServiceProfile, requestType, Optional.ofNullable(profileKeyCredential)), status, body);
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
