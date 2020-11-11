package org.whispersystems.signalservice.api.messages.calls;

import org.whispersystems.libsignal.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;

public class SignalServiceCallMessage {

  private final Optional<OfferMessage>           offerMessage;
  private final Optional<AnswerMessage>          answerMessage;
  private final Optional<HangupMessage>          hangupMessage;
  private final Optional<BusyMessage>            busyMessage;
  private final Optional<List<IceUpdateMessage>> iceUpdateMessages;
  private final Optional<OpaqueMessage>          opaqueMessage;
  private final Optional<Integer>                destinationDeviceId;
  private final boolean                          isMultiRing;

  private SignalServiceCallMessage(Optional<OfferMessage> offerMessage,
                                   Optional<AnswerMessage> answerMessage,
                                   Optional<List<IceUpdateMessage>> iceUpdateMessages,
                                   Optional<HangupMessage> hangupMessage,
                                   Optional<BusyMessage> busyMessage,
                                   Optional<OpaqueMessage> opaqueMessage,
                                   boolean isMultiRing,
                                   Optional<Integer> destinationDeviceId)
  {
    this.offerMessage        = offerMessage;
    this.answerMessage       = answerMessage;
    this.iceUpdateMessages   = iceUpdateMessages;
    this.hangupMessage       = hangupMessage;
    this.busyMessage         = busyMessage;
    this.opaqueMessage       = opaqueMessage;
    this.isMultiRing         = isMultiRing;
    this.destinationDeviceId = destinationDeviceId;
  }

  public static SignalServiceCallMessage forOffer(OfferMessage offerMessage, boolean isMultiRing, Integer destinationDeviceId) {
    return new SignalServiceCallMessage(Optional.of(offerMessage),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        isMultiRing,
                                        Optional.fromNullable(destinationDeviceId));
  }

  public static SignalServiceCallMessage forAnswer(AnswerMessage answerMessage, boolean isMultiRing, Integer destinationDeviceId) {
    return new SignalServiceCallMessage(Optional.absent(),
                                        Optional.of(answerMessage),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        isMultiRing,
                                        Optional.fromNullable(destinationDeviceId));
  }

  public static SignalServiceCallMessage forIceUpdates(List<IceUpdateMessage> iceUpdateMessages, boolean isMultiRing, Integer destinationDeviceId) {
    return new SignalServiceCallMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(iceUpdateMessages),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        isMultiRing,
                                        Optional.fromNullable(destinationDeviceId));
  }

  public static SignalServiceCallMessage forIceUpdate(final IceUpdateMessage iceUpdateMessage, boolean isMultiRing, Integer destinationDeviceId) {
    List<IceUpdateMessage> iceUpdateMessages = new LinkedList<>();
    iceUpdateMessages.add(iceUpdateMessage);

    return new SignalServiceCallMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(iceUpdateMessages),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        isMultiRing,
                                        Optional.fromNullable(destinationDeviceId));
  }

  public static SignalServiceCallMessage forHangup(HangupMessage hangupMessage, boolean isMultiRing, Integer destinationDeviceId) {
    return new SignalServiceCallMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(hangupMessage),
                                        Optional.absent(),
                                        Optional.absent(),
                                        isMultiRing,
                                        Optional.fromNullable(destinationDeviceId));
  }

  public static SignalServiceCallMessage forBusy(BusyMessage busyMessage, boolean isMultiRing, Integer destinationDeviceId) {
    return new SignalServiceCallMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(busyMessage),
                                        Optional.absent(),
                                        isMultiRing,
                                        Optional.fromNullable(destinationDeviceId));
  }

  public static SignalServiceCallMessage forOpaque(OpaqueMessage opaqueMessage, boolean isMultiRing, Integer destinationDeviceId) {
    return new SignalServiceCallMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.of(opaqueMessage),
                                        isMultiRing,
                                        Optional.fromNullable(destinationDeviceId));
  }


  public static SignalServiceCallMessage empty() {
    return new SignalServiceCallMessage(Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(),
                                        Optional.absent(), false,
                                        Optional.absent());
  }

  public Optional<List<IceUpdateMessage>> getIceUpdateMessages() {
    return iceUpdateMessages;
  }

  public Optional<AnswerMessage> getAnswerMessage() {
    return answerMessage;
  }

  public Optional<OfferMessage> getOfferMessage() {
    return offerMessage;
  }

  public Optional<HangupMessage> getHangupMessage() {
    return hangupMessage;
  }

  public Optional<BusyMessage> getBusyMessage() {
    return busyMessage;
  }

  public Optional<OpaqueMessage> getOpaqueMessage() {
    return opaqueMessage;
  }

  public boolean isMultiRing() {
    return isMultiRing;
  }

  public Optional<Integer> getDestinationDeviceId() {
    return destinationDeviceId;
  }
}
