/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;

import java.util.LinkedList;
import java.util.List;

public class SignalServiceSyncMessage {

  private final Optional<SentTranscriptMessage>             sent;
  private final Optional<ContactsMessage>                   contacts;
  private final Optional<SignalServiceAttachment>           groups;
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

  private SignalServiceSyncMessage(Optional<SentTranscriptMessage>             sent,
                                   Optional<ContactsMessage>                   contacts,
                                   Optional<SignalServiceAttachment>           groups,
                                   Optional<BlockedListMessage>                blockedList,
                                   Optional<RequestMessage>                    request,
                                   Optional<List<ReadMessage>>                 reads,
                                   Optional<ViewOnceOpenMessage>               viewOnceOpen,
                                   Optional<VerifiedMessage>                   verified,
                                   Optional<ConfigurationMessage>              configuration,
                                   Optional<List<StickerPackOperationMessage>> stickerPackOperations,
                                   Optional<FetchType>                         fetchType,
                                   Optional<KeysMessage>                       keys,
                                   Optional<MessageRequestResponseMessage>     messageRequestResponse,
                                   Optional<OutgoingPaymentMessage>            outgoingPaymentMessage,
                                   Optional<List<ViewedMessage>>               views)
  {
    this.sent                   = sent;
    this.contacts               = contacts;
    this.groups                 = groups;
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
  }

  public static SignalServiceSyncMessage forSentTranscript(SentTranscriptMessage sent) {
    return new SignalServiceSyncMessage(Optional.of(sent),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent());
  }

  public static SignalServiceSyncMessage forContacts(ContactsMessage contacts) {
    return new SignalServiceSyncMessage(Optional.absent(),
                                        Optional.of(contacts),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent());
  }

  public static SignalServiceSyncMessage forGroups(SignalServiceAttachment groups) {
    return new SignalServiceSyncMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(groups),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent());
  }

  public static SignalServiceSyncMessage forRequest(RequestMessage request) {
    return new SignalServiceSyncMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(request),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent());
  }

  public static SignalServiceSyncMessage forRead(List<ReadMessage> reads) {
    return new SignalServiceSyncMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(reads),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent());
  }

  public static SignalServiceSyncMessage forViewed(List<ViewedMessage> views) {
    return new SignalServiceSyncMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(views));
  }

  public static SignalServiceSyncMessage forViewOnceOpen(ViewOnceOpenMessage timerRead) {
    return new SignalServiceSyncMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(timerRead),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent());
  }

  public static SignalServiceSyncMessage forRead(ReadMessage read) {
    List<ReadMessage> reads = new LinkedList<>();
    reads.add(read);

    return new SignalServiceSyncMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(reads),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent());
  }

  public static SignalServiceSyncMessage forVerified(VerifiedMessage verifiedMessage) {
    return new SignalServiceSyncMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(verifiedMessage),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent());
  }

  public static SignalServiceSyncMessage forBlocked(BlockedListMessage blocked) {
    return new SignalServiceSyncMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(blocked),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent());
  }

  public static SignalServiceSyncMessage forConfiguration(ConfigurationMessage configuration) {
    return new SignalServiceSyncMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(configuration),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent());
  }

  public static SignalServiceSyncMessage forStickerPackOperations(List<StickerPackOperationMessage> stickerPackOperations) {
    return new SignalServiceSyncMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(stickerPackOperations),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent());
  }

  public static SignalServiceSyncMessage forFetchLatest(FetchType fetchType) {
    return new SignalServiceSyncMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(fetchType),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent());
  }

  public static SignalServiceSyncMessage forKeys(KeysMessage keys) {
    return new SignalServiceSyncMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(keys),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent());
  }

  public static SignalServiceSyncMessage forMessageRequestResponse(MessageRequestResponseMessage messageRequestResponse) {
    return new SignalServiceSyncMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(messageRequestResponse),
                                        Optional.absent(),
                                        Optional.absent());
  }

  public static SignalServiceSyncMessage forOutgoingPayment(OutgoingPaymentMessage outgoingPaymentMessage) {
    return new SignalServiceSyncMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(outgoingPaymentMessage),
                                        Optional.absent());
  }

  public static SignalServiceSyncMessage empty() {
    return new SignalServiceSyncMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent());
  }

  public Optional<SentTranscriptMessage> getSent() {
    return sent;
  }

  public Optional<SignalServiceAttachment> getGroups() {
    return groups;
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

  public enum FetchType {
    LOCAL_PROFILE,
    STORAGE_MANIFEST,
    SUBSCRIPTION_STATUS
  }
}
