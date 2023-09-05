/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;


import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.CallEvent;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.CallLinkUpdate;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.CallLogEvent;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

public class SignalServiceSyncMessage {

  private final Optional<SentTranscriptMessage>             sent;
  private final Optional<ContactsMessage>                   contacts;
  private final Optional<BlockedListMessage>                blockedList;
  private final Optional<RequestMessage>                    request;
  private final Optional<List<ReadMessage>>                 reads;
  private final Optional<ViewOnceOpenMessage>               viewOnceOpen;
  private final Optional<VerifiedMessage>                   verified;
  private final Optional<ConfigurationMessage>              configuration;
  private final Optional<List<StickerPackOperationMessage>> stickerPackOperations;
  private final Optional<FetchType>                         fetchType;
  private final Optional<KeysMessage>                       keys;
  private final Optional<MessageRequestResponseMessage>     messageRequestResponse;
  private final Optional<OutgoingPaymentMessage>            outgoingPaymentMessage;
  private final Optional<List<ViewedMessage>>               views;
  private final Optional<CallEvent>                         callEvent;
  private final Optional<CallLinkUpdate>                    callLinkUpdate;
  private final Optional<CallLogEvent>                      callLogEvent;

  private SignalServiceSyncMessage(Optional<SentTranscriptMessage> sent,
                                   Optional<ContactsMessage> contacts,
                                   Optional<BlockedListMessage> blockedList,
                                   Optional<RequestMessage> request,
                                   Optional<List<ReadMessage>> reads,
                                   Optional<ViewOnceOpenMessage> viewOnceOpen,
                                   Optional<VerifiedMessage> verified,
                                   Optional<ConfigurationMessage> configuration,
                                   Optional<List<StickerPackOperationMessage>> stickerPackOperations,
                                   Optional<FetchType> fetchType,
                                   Optional<KeysMessage> keys,
                                   Optional<MessageRequestResponseMessage> messageRequestResponse,
                                   Optional<OutgoingPaymentMessage> outgoingPaymentMessage,
                                   Optional<List<ViewedMessage>> views,
                                   Optional<CallEvent> callEvent,
                                   Optional<CallLinkUpdate> callLinkUpdate,
                                   Optional<CallLogEvent> callLogEvent)
  {
    this.sent                   = sent;
    this.contacts               = contacts;
    this.blockedList            = blockedList;
    this.request                = request;
    this.reads                  = reads;
    this.viewOnceOpen           = viewOnceOpen;
    this.verified               = verified;
    this.configuration          = configuration;
    this.stickerPackOperations  = stickerPackOperations;
    this.fetchType              = fetchType;
    this.keys                   = keys;
    this.messageRequestResponse = messageRequestResponse;
    this.outgoingPaymentMessage = outgoingPaymentMessage;
    this.views                  = views;
    this.callEvent              = callEvent;
    this.callLinkUpdate         = callLinkUpdate;
    this.callLogEvent           = callLogEvent;
  }

  public static SignalServiceSyncMessage forSentTranscript(SentTranscriptMessage sent) {
    return new SignalServiceSyncMessage(Optional.of(sent),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty());
  }

  public static SignalServiceSyncMessage forContacts(ContactsMessage contacts) {
    return new SignalServiceSyncMessage(Optional.empty(),
                                        Optional.of(contacts),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty());
  }

  public static SignalServiceSyncMessage forRequest(RequestMessage request) {
    return new SignalServiceSyncMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(request),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty());
  }

  public static SignalServiceSyncMessage forRead(List<ReadMessage> reads) {
    return new SignalServiceSyncMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(reads),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty());
  }

  public static SignalServiceSyncMessage forViewed(List<ViewedMessage> views) {
    return new SignalServiceSyncMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(views),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty());
  }

  public static SignalServiceSyncMessage forViewOnceOpen(ViewOnceOpenMessage timerRead) {
    return new SignalServiceSyncMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(timerRead),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty());
  }

  public static SignalServiceSyncMessage forRead(ReadMessage read) {
    List<ReadMessage> reads = new LinkedList<>();
    reads.add(read);

    return new SignalServiceSyncMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(reads),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty());
  }

  public static SignalServiceSyncMessage forVerified(VerifiedMessage verifiedMessage) {
    return new SignalServiceSyncMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(verifiedMessage),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty());
  }

  public static SignalServiceSyncMessage forBlocked(BlockedListMessage blocked) {
    return new SignalServiceSyncMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(blocked),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty());
  }

  public static SignalServiceSyncMessage forConfiguration(ConfigurationMessage configuration) {
    return new SignalServiceSyncMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(configuration),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty());
  }

  public static SignalServiceSyncMessage forStickerPackOperations(List<StickerPackOperationMessage> stickerPackOperations) {
    return new SignalServiceSyncMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(stickerPackOperations),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty());
  }

  public static SignalServiceSyncMessage forFetchLatest(FetchType fetchType) {
    return new SignalServiceSyncMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(fetchType),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty());
  }

  public static SignalServiceSyncMessage forKeys(KeysMessage keys) {
    return new SignalServiceSyncMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(keys),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty());
  }

  public static SignalServiceSyncMessage forMessageRequestResponse(MessageRequestResponseMessage messageRequestResponse) {
    return new SignalServiceSyncMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(messageRequestResponse),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty());
  }

  public static SignalServiceSyncMessage forOutgoingPayment(OutgoingPaymentMessage outgoingPaymentMessage) {
    return new SignalServiceSyncMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(outgoingPaymentMessage),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty());
  }

  public static SignalServiceSyncMessage forCallEvent(@Nonnull CallEvent callEvent) {
    return new SignalServiceSyncMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(callEvent),
                                        Optional.empty(),
                                        Optional.empty());
  }

  public static SignalServiceSyncMessage forCallLinkUpdate(@Nonnull CallLinkUpdate callLinkUpdate) {
    return new SignalServiceSyncMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(callLinkUpdate),
                                        Optional.empty());
  }

  public static SignalServiceSyncMessage forCallLogEvent(@Nonnull CallLogEvent callLogEvent) {
    return new SignalServiceSyncMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(callLogEvent));
  }

  public static SignalServiceSyncMessage empty() {
    return new SignalServiceSyncMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty());
  }

  public Optional<SentTranscriptMessage> getSent() {
    return sent;
  }

  public Optional<ContactsMessage> getContacts() {
    return contacts;
  }

  public Optional<RequestMessage> getRequest() {
    return request;
  }

  public Optional<List<ReadMessage>> getRead() {
    return reads;
  }

  public Optional<ViewOnceOpenMessage> getViewOnceOpen() {
    return viewOnceOpen;
  }

  public Optional<BlockedListMessage> getBlockedList() {
    return blockedList;
  }

  public Optional<VerifiedMessage> getVerified() {
    return verified;
  }

  public Optional<ConfigurationMessage> getConfiguration() {
    return configuration;
  }

  public Optional<List<StickerPackOperationMessage>> getStickerPackOperations() {
    return stickerPackOperations;
  }

  public Optional<FetchType> getFetchType() {
    return fetchType;
  }

  public Optional<KeysMessage> getKeys() {
    return keys;
  }

  public Optional<MessageRequestResponseMessage> getMessageRequestResponse() {
    return messageRequestResponse;
  }

  public Optional<OutgoingPaymentMessage> getOutgoingPaymentMessage() {
    return outgoingPaymentMessage;
  }

  public Optional<List<ViewedMessage>> getViewed() {
    return views;
  }

  public Optional<CallEvent> getCallEvent() {
    return callEvent;
  }

  public Optional<CallLinkUpdate> getCallLinkUpdate() {
    return callLinkUpdate;
  }

  public Optional<CallLogEvent> getCallLogEvent() {
    return callLogEvent;
  }

  public enum FetchType {
    LOCAL_PROFILE,
    STORAGE_MANIFEST,
    SUBSCRIPTION_STATUS
  }
}
