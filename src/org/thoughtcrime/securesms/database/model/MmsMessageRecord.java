package org.thoughtcrime.securesms.database.model;


import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.LinkedList;
import java.util.List;

public abstract class MmsMessageRecord extends MessageRecord {

  private final @NonNull  SlideDeck     slideDeck;
  private final @Nullable Quote         quote;
  private final @NonNull  List<Contact> contacts = new LinkedList<>();

  MmsMessageRecord(Context context, long id, String body, Recipient conversationRecipient,
                   Recipient individualRecipient, int recipientDeviceId, long dateSent,
                   long dateReceived, long threadId, int deliveryStatus, int deliveryReceiptCount,
                   long type, List<IdentityKeyMismatch> mismatches,
                   List<NetworkFailure> networkFailures, int subscriptionId, long expiresIn,
                   long expireStarted, @NonNull SlideDeck slideDeck, int readReceiptCount,
                   @Nullable Quote quote, @NonNull List<Contact> contacts)
  {
    super(context, id, body, conversationRecipient, individualRecipient, recipientDeviceId, dateSent, dateReceived, threadId, deliveryStatus, deliveryReceiptCount, type, mismatches, networkFailures, subscriptionId, expiresIn, expireStarted, readReceiptCount);

    this.slideDeck = slideDeck;
    this.quote     = quote;

    this.contacts.addAll(contacts);
  }

  @Override
  public boolean isMms() {
    return true;
  }

  @NonNull
  public SlideDeck getSlideDeck() {
    return slideDeck;
  }

  @Override
  public boolean isMediaPending() {
    for (Slide slide : getSlideDeck().getSlides()) {
      if (slide.isInProgress() || slide.isPendingDownload()) {
        return true;
      }
    }

    return false;
  }

  public boolean containsMediaSlide() {
    return slideDeck.containsMediaSlide();
  }

  public @Nullable Quote getQuote() {
    return quote;
  }

  public @NonNull List<Contact> getSharedContacts() {
    return contacts;
  }
}
