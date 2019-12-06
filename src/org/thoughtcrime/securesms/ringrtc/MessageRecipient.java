package org.thoughtcrime.securesms.ringrtc;

import android.content.Context;
import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.List;

import org.signal.ringrtc.SignalMessageRecipient;

import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public final class MessageRecipient implements SignalMessageRecipient {

  private static final String TAG = Log.tag(MessageRecipient.class);

  @NonNull private final Recipient recipient;
  @NonNull private final SignalServiceMessageSender messageSender;

  public MessageRecipient(@NonNull SignalServiceMessageSender messageSender,
                          @NonNull Recipient                  recipient)
  {
    this.recipient     = recipient;
    this.messageSender = messageSender;
  }

  public @NonNull RecipientId getId() {
    return recipient.getId();
  }

  @Override
  public boolean isEqual(@NonNull SignalMessageRecipient inRecipient) {
    if (!(inRecipient instanceof MessageRecipient)) {
      return false;
    }

    if (getClass() != inRecipient.getClass()) {
      Log.e(TAG, "CLASSES NOT EQUAL: " + getClass().toString() + ", " + recipient.getClass().toString());
      return false;
    }

    MessageRecipient that = (MessageRecipient) inRecipient;

    return recipient.equals(that.recipient);
  }

  private void sendMessage(Context context, SignalServiceCallMessage callMessage)
    throws UntrustedIdentityException, IOException
  {
    messageSender.sendCallMessage(RecipientUtil.toSignalServiceAddress(context, recipient),
                                  UnidentifiedAccessUtil.getAccessFor(context, recipient),
                                  callMessage);
  }

  @Override
  public void sendOfferMessage(Context context, long callId, String description)
    throws UntrustedIdentityException, IOException
  {
    Log.i(TAG, "MessageRecipient::sendOfferMessage(): callId: 0x" + Long.toHexString(callId));

    OfferMessage offerMessage = new OfferMessage(callId, description);
    sendMessage(context, SignalServiceCallMessage.forOffer(offerMessage));
  }

  @Override
  public void sendAnswerMessage(Context context, long callId, String description)
    throws UntrustedIdentityException, IOException
  {
    Log.i(TAG, "MessageRecipient::sendAnswerMessage(): callId: 0x" + Long.toHexString(callId));

    AnswerMessage answerMessage = new AnswerMessage(callId, description);
    sendMessage(context, SignalServiceCallMessage.forAnswer(answerMessage));
  }

  @Override
  public void sendIceUpdates(Context context, List<IceUpdateMessage> iceUpdateMessages)
    throws UntrustedIdentityException, IOException
  {
    Log.i(TAG, "MessageRecipient::sendIceUpdates(): iceUpdates: " + iceUpdateMessages.size());

    sendMessage(context, SignalServiceCallMessage.forIceUpdates(iceUpdateMessages));
  }

  @Override
  public void sendHangupMessage(Context context, long callId)
    throws UntrustedIdentityException, IOException
  {
    Log.i(TAG, "MessageRecipient::sendHangupMessage(): callId: 0x" + Long.toHexString(callId));

    HangupMessage hangupMessage = new HangupMessage(callId);
    sendMessage(context, SignalServiceCallMessage.forHangup(hangupMessage));
  }

}
