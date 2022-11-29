package org.thoughtcrime.securesms.database.model;

import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.SmsTable;

final class StatusUtil {
  private StatusUtil() {}

  static boolean isDelivered(long deliveryStatus, int deliveryReceiptCount) {
    return (deliveryStatus >= SmsTable.Status.STATUS_COMPLETE &&
            deliveryStatus < SmsTable.Status.STATUS_PENDING) || deliveryReceiptCount > 0;
  }

  static boolean isPending(long type) {
    return MmsSmsColumns.Types.isPendingMessageType(type) &&
           !MmsSmsColumns.Types.isIdentityVerified(type)  &&
           !MmsSmsColumns.Types.isIdentityDefault(type);
  }

  static boolean isFailed(long type, long deliveryStatus) {
    return MmsSmsColumns.Types.isFailedMessageType(type)            ||
           MmsSmsColumns.Types.isPendingSecureSmsFallbackType(type) ||
           deliveryStatus >= SmsTable.Status.STATUS_FAILED;
  }

  static boolean isVerificationStatusChange(long type) {
    return SmsTable.Types.isIdentityDefault(type) || SmsTable.Types.isIdentityVerified(type);
  }
}
