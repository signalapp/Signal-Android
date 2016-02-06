package org.thoughtcrime.securesms.mms;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.RetrieveConf;

public interface IncomingMmsConnection {
  @Nullable RetrieveConf retrieve(@NonNull String contentLocation, byte[] transactionId, int subscriptionId) throws MmsException, MmsRadioException, ApnUnavailableException, IOException;
}
