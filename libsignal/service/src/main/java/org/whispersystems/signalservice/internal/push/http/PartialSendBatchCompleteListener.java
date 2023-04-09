package org.whispersystems.signalservice.internal.push.http;

import org.whispersystems.signalservice.api.messages.SendMessageResult;

import java.util.List;

/**
 * Used to let a listener know when a batch of sends in a collection of sends has been completed.
 */
public interface PartialSendBatchCompleteListener {
  void onPartialSendComplete(List<SendMessageResult> results);
}
