package org.thoughtcrime.securesms.mms;

import java.io.IOException;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.RetrieveConf;

public interface IncomingMmsConnection {
  RetrieveConf retrieve(String contentLocation, byte[] transactionId) throws MmsException, MmsRadioException, ApnUnavailableException, IOException;
}
