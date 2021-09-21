package org.whispersystems.signalservice.api.services;

import com.google.protobuf.ByteString;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalWebSocket;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.ServiceResponseProcessor;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessageList;
import org.whispersystems.signalservice.internal.push.SendGroupMessageResponse;
import org.whispersystems.signalservice.internal.push.SendMessageResponse;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.internal.websocket.DefaultResponseMapper;
import org.whispersystems.signalservice.internal.websocket.ResponseMapper;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketRequestMessage;
import org.whispersystems.util.Base64;

import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import io.reactivex.rxjava3.core.Single;

/**
 * Provide WebSocket based interface to message sending endpoints.
 * <p>
 * Note: To be expanded to have REST fallback and other messaging related operations.
 */
public class MessagingService {
  private final SignalWebSocket signalWebSocket;

  public MessagingService(SignalWebSocket signalWebSocket) {
    this.signalWebSocket = signalWebSocket;
  }

  public Single<ServiceResponse<SendMessageResponse>> send(OutgoingPushMessageList list, Optional<UnidentifiedAccess> unidentifiedAccess) {
    List<String> headers = new LinkedList<String>() {{
      add("content-type:application/json");
    }};

    WebSocketRequestMessage requestMessage = WebSocketRequestMessage.newBuilder()
                                                                    .setId(new SecureRandom().nextLong())
                                                                    .setVerb("PUT")
                                                                    .setPath(String.format("/v1/messages/%s", list.getDestination()))
                                                                    .addAllHeaders(headers)
                                                                    .setBody(ByteString.copyFrom(JsonUtil.toJson(list).getBytes()))
                                                                    .build();

    ResponseMapper<SendMessageResponse> responseMapper = DefaultResponseMapper.extend(SendMessageResponse.class)
                                                                              .withResponseMapper((status, body, getHeader) -> {
                                                                                SendMessageResponse sendMessageResponse = Util.isEmpty(body) ? new SendMessageResponse(false)
                                                                                                                                             : JsonUtil.fromJsonResponse(body, SendMessageResponse.class);
                                                                                return ServiceResponse.forResult(sendMessageResponse, status, body);
                                                                              })
                                                                              .withCustomError(404, (status, body, getHeader) -> new UnregisteredUserException(list.getDestination(), new NotFoundException("not found")))
                                                                              .build();

    return signalWebSocket.request(requestMessage, unidentifiedAccess)
                          .map(responseMapper::map)
                          .onErrorReturn(ServiceResponse::forUnknownError);
  }

  public Single<ServiceResponse<SendGroupMessageResponse>> sendToGroup(byte[] body, byte[] joinedUnidentifiedAccess, long timestamp, boolean online) {
    List<String> headers = new LinkedList<String>() {{
      add("content-type:application/vnd.signal-messenger.mrm");
      add("Unidentified-Access-Key:" + Base64.encodeBytes(joinedUnidentifiedAccess));
    }};

    String path = String.format(Locale.US, "/v1/messages/multi_recipient?ts=%s&online=%s", timestamp, online);

    WebSocketRequestMessage requestMessage = WebSocketRequestMessage.newBuilder()
                                                                    .setId(new SecureRandom().nextLong())
                                                                    .setVerb("PUT")
                                                                    .setPath(path)
                                                                    .addAllHeaders(headers)
                                                                    .setBody(ByteString.copyFrom(body))
                                                                    .build();

    return signalWebSocket.request(requestMessage)
                          .map(DefaultResponseMapper.getDefault(SendGroupMessageResponse.class)::map)
                          .onErrorReturn(ServiceResponse::forUnknownError);
  }

  public static class SendResponseProcessor<T> extends ServiceResponseProcessor<T> {
    public SendResponseProcessor(ServiceResponse<T> response) {
      super(response);
    }
  }
}
