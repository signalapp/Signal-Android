package org.thoughtcrime.securesms.messagedetails;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;

final class RecipientHeader {

  private final int          headerOrder;
  private final int          headerText;
  private final HeaderStatus status;

  private RecipientHeader(int headerOrder, @StringRes int headerText, @NonNull HeaderStatus headerStatus) {
    this.headerOrder = headerOrder;
    this.headerText = headerText;
    this.status = headerStatus;
  }

  int getHeaderOrder() {
    return headerOrder;
  }

  @StringRes int getHeader() {
    return headerText;
  }

  @NonNull HeaderStatus getHeaderStatus() {
    return status;
  }

  static RecipientHeader pending(int idx) {
    return new RecipientHeader(idx, R.string.message_details_recipient_header__pending_send, HeaderStatus.PENDING);
  }

  static RecipientHeader sentTo(int idx) {
    return new RecipientHeader(idx, R.string.message_details_recipient_header__sent_to, HeaderStatus.SENT_TO);
  }

  static RecipientHeader sentFrom(int idx) {
    return new RecipientHeader(idx, R.string.message_details_recipient_header__sent_from, HeaderStatus.SENT_FROM);
  }

  static RecipientHeader delivered(int idx) {
    return new RecipientHeader(idx, R.string.message_details_recipient_header__delivered_to, HeaderStatus.DELIVERED);
  }

  static RecipientHeader read(int idx) {
    return new RecipientHeader(idx, R.string.message_details_recipient_header__read_by, HeaderStatus.READ);
  }

  static RecipientHeader notSent(int idx) {
    return new RecipientHeader(idx, R.string.message_details_recipient_header__not_sent, HeaderStatus.NOT_SENT);
  }

  enum HeaderStatus {
    PENDING,
    SENT_TO,
    SENT_FROM,
    DELIVERED,
    READ,
    NOT_SENT
  }
}
