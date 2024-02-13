package org.whispersystems.signalservice.api.messages.calls;



import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class SignalServiceCallMessage {

  private final Optional<OfferMessage>           offerMessage;
  private final Optional<AnswerMessage>          answerMessage;
  private final Optional<HangupMessage>          hangupMessage;
  private final Optional<BusyMessage>            busyMessage;
  private final Optional<List<IceUpdateMessage>> iceUpdateMessages;
  private final Optional<OpaqueMessage>          opaqueMessage;
  private final Optional<Integer>                destinationDeviceId;
  private final Optional<byte[]>                 groupId;
  private final Optional<Long>                   timestamp;

  private SignalServiceCallMessage(Optional<OfferMessage> offerMessage,
                                   Optional<AnswerMessage> answerMessage,
                                   Optional<List<IceUpdateMessage>> iceUpdateMessages,
                                   Optional<HangupMessage> hangupMessage,
                                   Optional<BusyMessage> busyMessage,
                                   Optional<OpaqueMessage> opaqueMessage,
                                   Optional<Integer> destinationDeviceId)
  {
    this(offerMessage, answerMessage, iceUpdateMessages, hangupMessage, busyMessage, opaqueMessage, destinationDeviceId, Optional.empty(), Optional.empty());
  }

  private SignalServiceCallMessage(Optional<OfferMessage> offerMessage,
                                   Optional<AnswerMessage> answerMessage,
                                   Optional<List<IceUpdateMessage>> iceUpdateMessages,
                                   Optional<HangupMessage> hangupMessage,
                                   Optional<BusyMessage> busyMessage,
                                   Optional<OpaqueMessage> opaqueMessage,
                                   Optional<Integer> destinationDeviceId,
                                   Optional<byte[]> groupId,
                                   Optional<Long> timestamp)
  {
    this.offerMessage        = offerMessage;
    this.answerMessage       = answerMessage;
    this.iceUpdateMessages   = iceUpdateMessages;
    this.hangupMessage       = hangupMessage;
    this.busyMessage         = busyMessage;
    this.opaqueMessage       = opaqueMessage;
    this.destinationDeviceId = destinationDeviceId;
    this.groupId             = groupId;
    this.timestamp           = timestamp;
  }

  public static SignalServiceCallMessage forOffer(OfferMessage offerMessage, Integer destinationDeviceId) {
    return new SignalServiceCallMessage(Optional.of(offerMessage),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.ofNullable(destinationDeviceId));
  }

  public static SignalServiceCallMessage forAnswer(AnswerMessage answerMessage, Integer destinationDeviceId) {
    return new SignalServiceCallMessage(Optional.empty(),
                                        Optional.of(answerMessage),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.ofNullable(destinationDeviceId));
  }

  public static SignalServiceCallMessage forIceUpdates(List<IceUpdateMessage> iceUpdateMessages, Integer destinationDeviceId) {
    return new SignalServiceCallMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(iceUpdateMessages),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.ofNullable(destinationDeviceId));
  }

  public static SignalServiceCallMessage forIceUpdate(final IceUpdateMessage iceUpdateMessage, Integer destinationDeviceId) {
    List<IceUpdateMessage> iceUpdateMessages = new LinkedList<>();
    iceUpdateMessages.add(iceUpdateMessage);

    return new SignalServiceCallMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(iceUpdateMessages),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.ofNullable(destinationDeviceId));
  }

  public static SignalServiceCallMessage forHangup(HangupMessage hangupMessage, Integer destinationDeviceId) {
    return new SignalServiceCallMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(hangupMessage),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.ofNullable(destinationDeviceId));
  }

  public static SignalServiceCallMessage forBusy(BusyMessage busyMessage, Integer destinationDeviceId) {
    return new SignalServiceCallMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(busyMessage),
                                        Optional.empty(),
                                        Optional.ofNullable(destinationDeviceId));
  }

  public static SignalServiceCallMessage forOpaque(OpaqueMessage opaqueMessage, Integer destinationDeviceId) {
    return new SignalServiceCallMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(opaqueMessage),
                                        Optional.ofNullable(destinationDeviceId));
  }

  public static SignalServiceCallMessage forOutgoingGroupOpaque(byte[] groupId, long timestamp, OpaqueMessage opaqueMessage, Integer destinationDeviceId) {
    return new SignalServiceCallMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(opaqueMessage),
                                        Optional.ofNullable(destinationDeviceId),
                                        Optional.of(groupId),
                                        Optional.of(timestamp));
  }


  public static SignalServiceCallMessage empty() {
    return new SignalServiceCallMessage(Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty());
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

  public Optional<Integer> getDestinationDeviceId() {
    return destinationDeviceId;
  }

  public Optional<byte[]> getGroupId() {
    return groupId;
  }

  public Optional<Long> getTimestamp() {
    return timestamp;
  }

  public boolean isUrgent() {
    return offerMessage.isPresent() ||
           hangupMessage.isPresent() ||
           opaqueMessage.map(m -> m.getUrgency() == OpaqueMessage.Urgency.HANDLE_IMMEDIATELY).orElse(false);
  }
}
