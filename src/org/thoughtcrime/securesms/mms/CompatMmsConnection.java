package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.thoughtcrime.securesms.transport.UndeliverableMessageException;

import java.io.IOException;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.RetrieveConf;
import ws.com.google.android.mms.pdu.SendConf;

public class CompatMmsConnection implements OutgoingMmsConnection, IncomingMmsConnection {
  private static final String TAG = CompatMmsConnection.class.getSimpleName();

  private Context context;

  public CompatMmsConnection(Context context) {
    this.context = context;
  }

  @Nullable
  @Override
  public SendConf send(@NonNull byte[] pduBytes, int subscriptionId)
      throws UndeliverableMessageException
  {
    if (subscriptionId == -1 || VERSION.SDK_INT < 22) {
      Log.w(TAG, "Sending via legacy connection");
      try {
        SendConf result = new OutgoingLegacyMmsConnection(context).send(pduBytes, subscriptionId);

        if (result != null && result.getResponseStatus() == PduHeaders.RESPONSE_STATUS_OK) {
          return result;
        } else {
          Log.w(TAG, "Got bad legacy response: " + (result != null ? result.getResponseStatus() : null));
        }
      } catch (UndeliverableMessageException | ApnUnavailableException e) {
        Log.w(TAG, e);
      }
    }

    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      return new OutgoingLollipopMmsConnection(context).send(pduBytes, subscriptionId);
    } else {
      throw new UndeliverableMessageException("Lollipop API not available to try...");
    }
  }

  @Nullable
  @Override
  public RetrieveConf retrieve(@NonNull String contentLocation,
                               byte[] transactionId,
                               int subscriptionId)
      throws MmsException, MmsRadioException, ApnUnavailableException, IOException
  {
    if (VERSION.SDK_INT < 22 || subscriptionId == -1) {
      Log.w(TAG, "Receiving via legacy connection");
      try {
        return new IncomingLegacyMmsConnection(context).retrieve(contentLocation, transactionId, subscriptionId);
      } catch (MmsRadioException | ApnUnavailableException | IOException e) {
        Log.w(TAG, e);
      }
    }

    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      Log.w(TAG, "Falling back to try receiving via Lollipop API");
      return new IncomingLollipopMmsConnection(context).retrieve(contentLocation, transactionId, subscriptionId);
    } else {
      throw new IOException("Not able to use Lollipop APIs, giving up...");
    }
  }
}
