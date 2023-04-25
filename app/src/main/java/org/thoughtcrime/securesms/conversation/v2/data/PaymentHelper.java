package org.thoughtcrime.securesms.conversation.v2.data;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.payments.Payment;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PaymentHelper {
  private final Map<UUID, Long>    paymentMessages    = new HashMap<>();
  private final Map<Long, Payment> messageIdToPayment = new HashMap<>();

  public void add(MessageRecord messageRecord) {
    if (messageRecord.isMms() && messageRecord.isPaymentNotification()) {
      UUID paymentUuid = UuidUtil.parseOrNull(messageRecord.getBody());
      if (paymentUuid != null) {
        paymentMessages.put(paymentUuid, messageRecord.getId());
      }
    }
  }

  public void fetchPayments() {
    List<Payment> payments = SignalDatabase.payments().getPayments(paymentMessages.keySet());
    for (Payment payment : payments) {
      if (payment != null) {
        messageIdToPayment.put(paymentMessages.get(payment.getUuid()), payment);
      }
    }
  }

  public @NonNull List<MessageRecord> buildUpdatedModels(@NonNull List<MessageRecord> records) {
    return records.stream()
                  .map(record -> {
                    if (record instanceof MediaMmsMessageRecord) {
                      Payment payment = messageIdToPayment.get(record.getId());
                      if (payment != null) {
                        return ((MediaMmsMessageRecord) record).withPayment(payment);
                      }
                    }
                    return record;
                  })
                  .collect(Collectors.toList());
  }
}
