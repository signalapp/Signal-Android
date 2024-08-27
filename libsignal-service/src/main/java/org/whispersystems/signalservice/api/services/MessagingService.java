package org.whispersystems.signalservice.api.services;

import org.whispersystems.signalservice.api.SignalWebSocket;
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.ServiceResponseProcessor;
import org.whispersystems.signalservice.internal.push.GroupMismatchedDevices;
import org.whispersystems.signalservice.internal.push.GroupStaleDevices;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessageList;
import org.whispersystems.signalservice.internal.push.SendGroupMessageResponse;
import org.whispersystems.signalservice.internal.push.SendMessageResponse;
import org.whispersystems.signalservice.internal.push.exceptions.GroupMismatchedDevicesException;
import org.whispersystems.signalservice.internal.push.exceptions.GroupStaleDevicesException;
import org.whispersystems.signalservice.internal.push.exceptions.InvalidUnidentifiedAccessHeaderException;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.internal.websocket.DefaultResponseMapper;
import org.whispersystems.signalservice.internal.websocket.ResponseMapper;
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage;

import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.reactivex.rxjava3.core.Single;
import okio.ByteString;

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

  public Single<ServiceResponse<SendMessageResponse>> send(OutgoingPushMessageList list,
                                                           @Nullable SealedSenderAccess sealedSenderAccess,
                                                           boolean story) {
    List<String> headers = new LinkedList<String>() {{
      add("content-type:application/json");
    }};

    WebSocketRequestMessage requestMessage = new WebSocketRequestMessage.Builder()
                                                                        .id(new SecureRandom().nextLong())
                                                                        .verb("PUT")
                                                                        .path(String.format("/v1/messages/%s?story=%s", list.getDestination(), story ? "true" : "false"))
                                                                        .headers(headers)
                                                                        .body(ByteString.of(JsonUtil.toJson(list).getBytes()))
                                                                        .build();

    ResponseMapper<SendMessageResponse> responseMapper = DefaultResponseMapper.extend(SendMessageResponse.class)
                                                                              .withResponseMapper((status, body, getHeader, unidentified) -> {
                                                                                SendMessageResponse sendMessageResponse = Util.isEmpty(body) ? new SendMessageResponse(false, unidentified)
                                                                                                                                             : JsonUtil.fromJsonResponse(body, SendMessageResponse.class);
                                                                                sendMessageResponse.setSentUnidentfied(unidentified);

                                                                                return ServiceResponse.forResult(sendMessageResponse, status, body);
                                                                              })
                                                                              .withCustomError(404, (status, body, getHeader) -> new UnregisteredUserException(list.getDestination(), new NotFoundException("not found")))
                                                                              .build();

    return signalWebSocket.request(requestMessage, sealedSenderAccess)
                          .map(responseMapper::map)
                          .onErrorReturn(ServiceResponse::forUnknownError);
  }

  public Single<ServiceResponse<SendGroupMessageResponse>> sendToGroup(byte[] body, @Nonnull SealedSenderAccess sealedSenderAccess, long timestamp, boolean online, boolean urgent, boolean story) {
    List<String> headers = new LinkedList<String>() {{
      add("content-type:application/vnd.signal-messenger.mrm");
      add(sealedSenderAccess.getHeader());
    }};

    String path = String.format(Locale.US, "/v1/messages/multi_recipient?ts=%s&online=%s&urgent=%s&story=%s", timestamp, online, urgent, story);

    WebSocketRequestMessage requestMessage = new WebSocketRequestMessage.Builder()
                                                                        .id(new SecureRandom().nextLong())
                                                                        .verb("PUT")
                                                                        .path(path)
                                                                        .headers(headers)
                                                                        .body(ByteString.of(body))
                                                                        .build();

    return signalWebSocket.request(requestMessage)
                          .map(DefaultResponseMapper.extend(SendGroupMessageResponse.class)
                                                    .withCustomError(401, (status, errorBody, getHeader) -> new InvalidUnidentifiedAccessHeaderException())
                                                    .withCustomError(404, (status, errorBody, getHeader) -> new NotFoundException("At least one unregistered user in message send."))
                                                    .withCustomError(409, (status, errorBody, getHeader) -> {
                                                      GroupMismatchedDevices[] mismatchedDevices = JsonUtil.fromJsonResponse(errorBody, GroupMismatchedDevices[].class);
                                                      return new GroupMismatchedDevicesException(mismatchedDevices);
                                                    })
                                                    .withCustomError(410, (status, errorBody, getHeader) -> {
                                                      GroupStaleDevices[] staleDevices = JsonUtil.fromJsonResponse(errorBody, GroupStaleDevices[].class);
                                                      return new GroupStaleDevicesException(staleDevices);
                                                    })
                                                    .build()::map)
                          .onErrorReturn(ServiceResponse::forUnknownError);
  }

  public static class SendResponseProcessor<T> extends ServiceResponseProcessor<T> {
    public SendResponseProcessor(ServiceResponse<T> response) {
      super(response);
    }
  }
}
