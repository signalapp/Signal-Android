package org.thoughtcrime.securesms.sharing.interstitial;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.List;
import java.util.Set;

class ShareInterstitialRepository {

  void loadRecipients(@NonNull Set<ContactSearchKey.RecipientSearchKey> recipientSearchKeys, Consumer<List<Recipient>> consumer) {
    SignalExecutors.BOUNDED.execute(() -> consumer.accept(resolveRecipients(recipientSearchKeys)));
  }

  @WorkerThread
  private List<Recipient> resolveRecipients(@NonNull Set<ContactSearchKey.RecipientSearchKey> recipientSearchKeys) {
    return Stream.of(recipientSearchKeys)
                 .map(ContactSearchKey.RecipientSearchKey::getRecipientId)
                 .map(Recipient::resolved)
                 .toList();
  }
}
