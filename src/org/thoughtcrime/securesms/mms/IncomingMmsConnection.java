package org.thoughtcrime.securesms.mms;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.mms.pdu_alt.RetrieveConf;

import java.io.IOException;

public interface IncomingMmsConnection {
  @Nullable
  RetrieveConf retrieve(@NonNull String contentLocation, byte[] transactionId, int subscriptionId) throws MmsException, MmsRadioException, ApnUnavailableException, IOException;
}
