package org.thoughtcrime.securesms.database;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.LRUCache;

import java.util.HashMap;
import java.util.Map;

public class EarlyReceiptCache {

  private static final String TAG = Log.tag(EarlyReceiptCache.class);

  private final LRUCache<Long, Map<RecipientId, Receipt>> cache = new LRUCache<>(100);
  private final String name;

  public EarlyReceiptCache(@NonNull String name) {
    this.name = name;
  }

  public synchronized void increment(long timestamp, @NonNull RecipientId origin, long receiptTimestamp) {
    Map<RecipientId, Receipt> receipts = cache.get(timestamp);

    if (receipts == null) {
      receipts = new HashMap<>();
    }

    Receipt receipt = receipts.get(origin);

    if (receipt != null) {
      receipt.count++;
      receipt.timestamp = receiptTimestamp;
    } else {
      receipt = new Receipt(1, receiptTimestamp);
    }
    receipts.put(origin, receipt);

    cache.put(timestamp, receipts);
  }

  public synchronized Map<RecipientId, Receipt> remove(long timestamp) {
    Map<RecipientId, Receipt> receipts = cache.remove(timestamp);
    return receipts != null ? receipts : new HashMap<>();
  }

  public class Receipt {
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
