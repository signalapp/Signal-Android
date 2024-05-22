package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.identity.IdentityRecordList;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.Collections;
import java.util.List;

class WebRtcCallRepository {

  private final Context      context;

  WebRtcCallRepository(@NonNull Context context) {
    this.context      = context;
  }

  @WorkerThread
  void getIdentityRecords(@NonNull Recipient recipient, @NonNull Consumer<IdentityRecordList> consumer) {
    SignalExecutors.BOUNDED.execute(() -> {
      List<Recipient> recipients;

      if (recipient.isGroup()) {
        recipients = SignalDatabase.groups().getGroupMembers(recipient.requireGroupId(), GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);
      } else {
        recipients = Collections.singletonList(recipient);
      }

      consumer.accept(AppDependencies.getProtocolStore().aci().identities().getIdentityRecords(recipients));
    });
  }
}
