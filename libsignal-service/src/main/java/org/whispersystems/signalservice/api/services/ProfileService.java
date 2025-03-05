package org.whispersystems.signalservice.api.services;

import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.protocol.util.Pair;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredentialRequest;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredentialRequestContext;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyVersion;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.MalformedResponseException;
import org.whispersystems.signalservice.api.websocket.SignalWebSocket;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.ServiceResponseProcessor;
import org.whispersystems.signalservice.internal.push.IdentityCheckRequest;
import org.whispersystems.signalservice.internal.push.IdentityCheckRequest.ServiceIdFingerprintPair;
import org.whispersystems.signalservice.internal.push.IdentityCheckResponse;
import org.whispersystems.signalservice.internal.push.http.AcceptLanguagesUtil;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.signalservice.internal.websocket.DefaultResponseMapper;
import org.whispersystems.signalservice.internal.websocket.ResponseMapper;
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Single;

/**
 * Provide Profile-related API services, encapsulating the logic to make the request, parse the response,
 * and fallback to appropriate WebSocket or RESTful alternatives.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class ProfileService {

  private static final String TAG = ProfileService.class.getSimpleName();

  private final ClientZkProfileOperations                clientZkProfileOperations;
  private final SignalServiceMessageReceiver             receiver;
  private final SignalWebSocket.AuthenticatedWebSocket   authWebSocket;
  private final SignalWebSocket.UnauthenticatedWebSocket unauthWebSocket;

  public ProfileService(ClientZkProfileOperations clientZkProfileOperations,
                        SignalServiceMessageReceiver receiver,
                        SignalWebSocket.AuthenticatedWebSocket authWebSocket,
                        SignalWebSocket.UnauthenticatedWebSocket unauthWebSocket)
  {
    this.clientZkProfileOperations = clientZkProfileOperations;
    this.receiver                  = receiver;
    this.authWebSocket             = authWebSocket;
    this.unauthWebSocket           = unauthWebSocket;
  }

  public Single<ServiceResponse<ProfileAndCredential>> getProfile(@Nonnull SignalServiceAddress address,
                                                                  @Nonnull Optional<ProfileKey> profileKey,
                                                                  @Nullable SealedSenderAccess sealedSenderAccess,
                                                                  @Nonnull SignalServiceProfile.RequestType requestType,
                                                                  @Nonnull Locale locale)
  {
    ServiceId                          serviceId      = address.getServiceId();
    SecureRandom                       random         = new SecureRandom();
    ProfileKeyCredentialRequestContext requestContext = null;

    WebSocketRequestMessage.Builder builder = new WebSocketRequestMessage.Builder()
                                                                         .id(random.nextLong())
                                                                         .verb("GET");

    if (profileKey.isPresent()) {
      if (!(serviceId instanceof ACI)) {
        Log.w(TAG, "ServiceId  must be an ACI if a profile key is available!");
        return Single.just(ServiceResponse.forUnknownError(new IllegalArgumentException("ServiceId  must be an ACI if a profile key is available!")));
      }

      ACI               aci                  = (ACI) serviceId;
      ProfileKeyVersion profileKeyIdentifier = profileKey.get().getProfileKeyVersion(aci.getLibSignalAci());
      String            version              = profileKeyIdentifier.serialize();

      if (requestType == SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL) {
        requestContext = clientZkProfileOperations.createProfileKeyCredentialRequestContext(random, aci.getLibSignalAci(), profileKey.get());

        ProfileKeyCredentialRequest request           = requestContext.getRequest();
        String                      credentialRequest = Hex.toStringCondensed(request.serialize());

        builder.path(String.format("/v1/profile/%s/%s/%s?credentialType=expiringProfileKey", serviceId, version, credentialRequest));
      } else {
        builder.path(String.format("/v1/profile/%s/%s", serviceId, version));
      }
    } else {
      builder.path(String.format("/v1/profile/%s", address.getIdentifier()));
    }

    builder.headers(Collections.singletonList(AcceptLanguagesUtil.getAcceptLanguageHeader(locale)));

    WebSocketRequestMessage requestMessage = builder.build();

    ResponseMapper<ProfileAndCredential> responseMapper = DefaultResponseMapper.extend(ProfileAndCredential.class)
                                                                               .withResponseMapper(new ProfileResponseMapper(requestType, requestContext))
                                                                               .build();

    if (sealedSenderAccess == null) {
      return authWebSocket.request(requestMessage)
                          .map(responseMapper::map)
                          .onErrorResumeNext(t -> getProfileRestFallback(address, profileKey, null, requestType, locale))
                          .onErrorReturn(ServiceResponse::forUnknownError);
    } else {
      return unauthWebSocket.request(requestMessage, sealedSenderAccess)
                            .flatMap(response -> {
                              if (response.getStatus() == 401) {
                                return authWebSocket.request(requestMessage);
                              } else {
                                return Single.just(response);
                              }
                            })
                            .map(responseMapper::map)
                            .onErrorResumeNext(t -> getProfileRestFallback(address, profileKey, sealedSenderAccess, requestType, locale))
                            .onErrorReturn(ServiceResponse::forUnknownError);
    }
  }

  public @NonNull Single<ServiceResponse<IdentityCheckResponse>> performIdentityCheck(@Nonnull Map<ServiceId, IdentityKey> serviceIdIdentityKeyMap) {
    List<ServiceIdFingerprintPair> serviceIdKeyPairs = serviceIdIdentityKeyMap.entrySet()
                                                                              .stream()
                                                                              .map(e -> new ServiceIdFingerprintPair(e.getKey(), e.getValue()))
                                                                              .collect(Collectors.toList());

    IdentityCheckRequest request = new IdentityCheckRequest(serviceIdKeyPairs);

    WebSocketRequestMessage.Builder builder = new WebSocketRequestMessage.Builder()
                                                                         .id(new SecureRandom().nextLong())
                                                                         .verb("POST")
                                                                         .path("/v1/profile/identity_check/batch")
                                                                         .headers(Collections.singletonList("content-type:application/json"))
                                                                         .body(JsonUtil.toJsonByteString(request));

    ResponseMapper<IdentityCheckResponse> responseMapper = DefaultResponseMapper.getDefault(IdentityCheckResponse.class);

    return unauthWebSocket.request(builder.build())
                          .map(responseMapper::map)
                          .onErrorResumeNext(t -> performIdentityCheckRestFallback(request, responseMapper))
                          .onErrorReturn(ServiceResponse::forUnknownError);
  }

  private Single<ServiceResponse<ProfileAndCredential>> getProfileRestFallback(@Nonnull SignalServiceAddress address,
                                                                               @Nonnull Optional<ProfileKey> profileKey,
                                                                               @Nullable SealedSenderAccess sealedSenderAccess,
                                                                               @Nonnull SignalServiceProfile.RequestType requestType,
                                                                               @Nonnull Locale locale)
  {
    return Single.fromFuture(receiver.retrieveProfile(address, profileKey, sealedSenderAccess, requestType, locale), 10, TimeUnit.SECONDS)
                 .onErrorResumeNext(t -> {
                   Throwable error;
                   if (t instanceof ExecutionException && t.getCause() != null) {
                     error = t.getCause();
                   } else {
                     error = t;
                   }

                   if (error instanceof AuthorizationFailedException) {
                     return Single.fromFuture(receiver.retrieveProfile(address, profileKey, null, requestType, locale), 10, TimeUnit.SECONDS);
                   } else {
                     return Single.error(t);
                   }
                 })
                 .map(p -> ServiceResponse.forResult(p, 0, null));
  }

  private @NonNull Single<ServiceResponse<IdentityCheckResponse>> performIdentityCheckRestFallback(@Nonnull IdentityCheckRequest request,
                                                                                                   @Nonnull ResponseMapper<IdentityCheckResponse> responseMapper) {
    return receiver.performIdentityCheck(request, responseMapper)
                   .onErrorResumeNext(t -> {
                     Throwable error;
                     if (t instanceof ExecutionException && t.getCause() != null) {
                       error = t.getCause();
                     } else {
                       error = t;
                     }

                     if (error instanceof AuthorizationFailedException) {
                       return receiver.performIdentityCheck(request, responseMapper);
                     } else {
                       return Single.error(t);
                     }
                   });
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
        SignalServiceProfile         signalServiceProfile         = JsonUtil.fromJsonResponse(body, SignalServiceProfile.class);
        ExpiringProfileKeyCredential expiringProfileKeyCredential = null;
        if (requestContext != null && signalServiceProfile.getExpiringProfileKeyCredentialResponse() != null) {
          expiringProfileKeyCredential = clientZkProfileOperations.receiveExpiringProfileKeyCredential(requestContext, signalServiceProfile.getExpiringProfileKeyCredentialResponse());
        }

        return ServiceResponse.forResult(new ProfileAndCredential(signalServiceProfile, requestType, Optional.ofNullable(expiringProfileKeyCredential)), status, body);
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

    @Override
    public Throwable getError() {
      return super.getError();
    }
  }
}
