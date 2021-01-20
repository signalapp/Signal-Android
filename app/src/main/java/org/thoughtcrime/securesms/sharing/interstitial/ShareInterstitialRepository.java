package org.thoughtcrime.securesms.sharing.interstitial;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sharing.ShareContactAndThread;

import java.util.List;
import java.util.Set;

class ShareInterstitialRepository {

  void loadRecipients(@NonNull Set<ShareContactAndThread> shareContactAndThreads, Consumer<List<Recipient>> consumer) {
    SignalExecutors.BOUNDED.execute(() -> consumer.accept(resolveRecipients(shareContactAndThreads)));
  }

  @WorkerThread
  private List<Recipient> resolveRecipients(@NonNull Set<ShareContactAndThread> shareContactAndThreads) {
    return Stream.of(shareContactAndThreads)
                 .map(ShareContactAndThread::getRecipientId)
                 .map(Recipient::resolved)
                 .toList();
  }
}
