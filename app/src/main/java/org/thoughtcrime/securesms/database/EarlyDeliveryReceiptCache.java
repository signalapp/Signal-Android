package org.thoughtcrime.securesms.database;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.LRUCache;

import java.util.HashMap;
import java.util.Map;

public class EarlyDeliveryReceiptCache {

  private static final String TAG = Log.tag(EarlyDeliveryReceiptCache.class);

  private final LRUCache<Long, Map<RecipientId, Receipt>> cache = new LRUCache<>(100);

  public synchronized void increment(long targetTimestamp, @NonNull RecipientId receiptAuthor, long receiptSentTimestamp) {
    Map<RecipientId, Receipt> receipts = cache.get(targetTimestamp);

    if (receipts == null) {
      receipts = new HashMap<>();
    }

    Receipt receipt = receipts.get(receiptAuthor);

    if (receipt != null) {
      receipt.count++;
      receipt.timestamp = receiptSentTimestamp;
    } else {
      receipt = new Receipt(1, receiptSentTimestamp);
    }
    receipts.put(receiptAuthor, receipt);

    cache.put(targetTimestamp, receipts);
  }

  public synchronized Map<RecipientId, Receipt> remove(long timestamp) {
    Map<RecipientId, Receipt> receipts = cache.remove(timestamp);
    return receipts != null ? receipts : new HashMap<>();
  }

  public static class Receipt {
    private long count;
    private long timestamp;

    private Receipt(long count, long timestamp) {
      this.count     = count;
      this.timestamp = timestamp;
    }

    public long getCount() {
      return count;
    }

    public long getTimestamp() {
      return timestamp;
    }
  }
}
