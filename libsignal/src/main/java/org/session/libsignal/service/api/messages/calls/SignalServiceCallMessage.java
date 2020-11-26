package org.session.libsignal.service.api.messages.calls;

import org.session.libsignal.libsignal.util.guava.Optional;
import org.session.libsignal.service.loki.protocol.meta.TTLUtilities;

import java.util.LinkedList;
import java.util.List;

public class SignalServiceCallMessage {

  private final Optional<OfferMessage>           offerMessage;
  private final Optional<AnswerMessage>          answerMessage;
  private final Optional<HangupMessage>          hangupMessage;
  private final Optional<BusyMessage>            busyMessage;
  private final Optional<List<IceUpdateMessage>> iceUpdateMessages;

  private SignalServiceCallMessage(Optional<OfferMessage> offerMessage,
                                   Optional<AnswerMessage> answerMessage,
                                   Optional<List<IceUpdateMessage>> iceUpdateMessages,
                                   Optional<HangupMessage> hangupMessage,
                                   Optional<BusyMessage> busyMessage)
  {
    this.offerMessage      = offerMessage;
    this.answerMessage     = answerMessage;
    this.iceUpdateMessages = iceUpdateMessages;
    this.hangupMessage     = hangupMessage;
    this.busyMessage       = busyMessage;
  }

  public static SignalServiceCallMessage forOffer(OfferMessage offerMessage) {
    return new SignalServiceCallMessage(Optional.of(offerMessage),
                                        Optional.<AnswerMessage>absent(),
                                        Optional.<List<IceUpdateMessage>>absent(),
                                        Optional.<HangupMessage>absent(),
                                        Optional.<BusyMessage>absent());
  }

  public static SignalServiceCallMessage forAnswer(AnswerMessage answerMessage) {
    return new SignalServiceCallMessage(Optional.<OfferMessage>absent(),
                                        Optional.of(answerMessage),
                                        Optional.<List<IceUpdateMessage>>absent(),
                                        Optional.<HangupMessage>absent(),
                                        Optional.<BusyMessage>absent());
  }

  public static SignalServiceCallMessage forIceUpdates(List<IceUpdateMessage> iceUpdateMessages) {
    return new SignalServiceCallMessage(Optional.<OfferMessage>absent(),
                                        Optional.<AnswerMessage>absent(),
                                        Optional.of(iceUpdateMessages),
                                        Optional.<HangupMessage>absent(),
                                        Optional.<BusyMessage>absent());
  }

  public static SignalServiceCallMessage forIceUpdate(final IceUpdateMessage iceUpdateMessage) {
    List<IceUpdateMessage> iceUpdateMessages = new LinkedList<IceUpdateMessage>();
    iceUpdateMessages.add(iceUpdateMessage);

    return new SignalServiceCallMessage(Optional.<OfferMessage>absent(),
                                        Optional.<AnswerMessage>absent(),
                                        Optional.of(iceUpdateMessages),
                                        Optional.<HangupMessage>absent(),
                                        Optional.<BusyMessage>absent());
  }

  public static SignalServiceCallMessage forHangup(HangupMessage hangupMessage) {
    return new SignalServiceCallMessage(Optional.<OfferMessage>absent(),
                                        Optional.<AnswerMessage>absent(),
                                        Optional.<List<IceUpdateMessage>>absent(),
                                        Optional.of(hangupMessage),
                                        Optional.<BusyMessage>absent());
  }

  public static SignalServiceCallMessage forBusy(BusyMessage busyMessage) {
    return new SignalServiceCallMessage(Optional.<OfferMessage>absent(),
                                        Optional.<AnswerMessage>absent(),
                                        Optional.<List<IceUpdateMessage>>absent(),
                                        Optional.<HangupMessage>absent(),
                                        Optional.of(busyMessage));
  }


  public static SignalServiceCallMessage empty() {
    return new SignalServiceCallMessage(Optional.<OfferMessage>absent(),
                                        Optional.<AnswerMessage>absent(),
                                        Optional.<List<IceUpdateMessage>>absent(),
                                        Optional.<HangupMessage>absent(),
                                        Optional.<BusyMessage>absent());
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

  public int getTTL() { return TTLUtilities.getTTL(TTLUtilities.MessageType.Call); }
}
