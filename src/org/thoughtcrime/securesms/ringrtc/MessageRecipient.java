package org.thoughtcrime.securesms.ringrtc;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.List;

import org.signal.ringrtc.SignalMessageRecipient;

import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Util;

import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public final class MessageRecipient implements SignalMessageRecipient {

  private static final String TAG = Log.tag(MessageRecipient.class);

  @NonNull private final Recipient recipient;
  @NonNull private final SignalServiceMessageSender messageSender;

  public MessageRecipient(SignalServiceMessageSender messageSender,
                          Recipient                  recipient)
  {
    this.recipient     = recipient;
    this.messageSender = messageSender;
  }

  public @NonNull Address getAddress() {
    return recipient.getAddress();
  }

  @Override
  public boolean isEqual(@NonNull SignalMessageRecipient o) {
    if (!(o instanceof MessageRecipient)) {
      return false;
    }

    MessageRecipient that = (MessageRecipient) o;

    return recipient.equals(that.recipient);
  }

  void sendMessage(Context context, SignalServiceCallMessage callMessage)
    throws IOException, UntrustedIdentityException, IOException
  {
    messageSender.sendCallMessage(new SignalServiceAddress(recipient.getAddress().toPhoneString()),
                                  UnidentifiedAccessUtil.getAccessFor(context, recipient),
                                  callMessage);
  }

  @Override
  public void sendOfferMessage(Context context, long callId, String description)
    throws IOException, UntrustedIdentityException, IOException
  {
    Log.i(TAG, "MessageRecipient::sendOfferMessage(): callId: " + callId);

    OfferMessage offerMessage = new OfferMessage(callId, description);
    sendMessage(context, SignalServiceCallMessage.forOffer(offerMessage));
  }

  @Override
  public void sendAnswerMessage(Context context, long callId, String description)
    throws IOException, UntrustedIdentityException, IOException
  {
    Log.i(TAG, "MessageRecipient::sendAnswerMessage(): callId: " + callId);

    AnswerMessage answerMessage = new AnswerMessage(callId, description);
    sendMessage(context, SignalServiceCallMessage.forAnswer(answerMessage));
  }

  @Override
  public void sendIceUpdates(Context context, List<IceUpdateMessage> iceUpdateMessages)
    throws IOException, UntrustedIdentityException, IOException
  {
    Log.i(TAG, "MessageRecipient::sendIceUpdates(): iceUpdates: " + iceUpdateMessages.size());

    sendMessage(context, SignalServiceCallMessage.forIceUpdates(iceUpdateMessages));
  }

  @Override
  public void sendHangupMessage(Context context, long callId)
    throws IOException, UntrustedIdentityException, IOException
  {
    Log.i(TAG, "MessageRecipient::sendHangupMessage(): callId: " + callId);

    HangupMessage hangupMessage = new HangupMessage(callId);
    sendMessage(context, SignalServiceCallMessage.forHangup(hangupMessage));
  }

  @Override
  public void sendBusyMessage(Context context, long callId)
    throws IOException, UntrustedIdentityException, IOException
  {
    Log.i(TAG, "MessageRecipient::sendBusyMessage(): callId: " + callId);

    BusyMessage busyMessage = new BusyMessage(callId);
    sendMessage(context, SignalServiceCallMessage.forBusy(busyMessage));
  }

}
