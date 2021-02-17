package org.thoughtcrime.securesms.jobs;

import android.app.Application;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.Constraint;
import org.thoughtcrime.securesms.jobmanager.ConstraintObserver;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.CellServiceConstraint;
import org.thoughtcrime.securesms.jobmanager.impl.CellServiceConstraintObserver;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraintObserver;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkOrCellServiceConstraint;
import org.thoughtcrime.securesms.jobmanager.impl.SqlCipherMigrationConstraint;
import org.thoughtcrime.securesms.jobmanager.impl.SqlCipherMigrationConstraintObserver;
import org.thoughtcrime.securesms.loki.api.PrepareAttachmentAudioExtrasJob;
import org.thoughtcrime.securesms.loki.api.ResetThreadSessionJob;
import org.thoughtcrime.securesms.loki.protocol.ClosedGroupUpdateMessageSendJobV2;
import org.thoughtcrime.securesms.loki.protocol.NullMessageSendJob;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JobManagerFactories {

  private static Collection<String> factoryKeys = new ArrayList<>();

  public static Map<String, Job.Factory> getJobFactories(@NonNull Application application) {
    HashMap<String, Job.Factory> factoryHashMap = new HashMap<String, Job.Factory>() {{
      put(AttachmentDownloadJob.KEY,                 new AttachmentDownloadJob.Factory());
      put(AttachmentUploadJob.KEY,                   new AttachmentUploadJob.Factory());
      put(AvatarDownloadJob.KEY,                     new AvatarDownloadJob.Factory());
      put(ClosedGroupUpdateMessageSendJobV2.KEY,     new ClosedGroupUpdateMessageSendJobV2.Factory());
      put(LocalBackupJob.KEY,                        new LocalBackupJob.Factory());
      put(MmsDownloadJob.KEY,                        new MmsDownloadJob.Factory());
      put(MmsReceiveJob.KEY,                         new MmsReceiveJob.Factory());
      put(MmsSendJob.KEY,                            new MmsSendJob.Factory());
      put(NullMessageSendJob.KEY,                    new NullMessageSendJob.Factory());
      put(PushContentReceiveJob.KEY,                 new PushContentReceiveJob.Factory());
      put(PushDecryptJob.KEY,                        new PushDecryptJob.Factory());
      put(PushGroupSendJob.KEY,                      new PushGroupSendJob.Factory());
      put(PushGroupUpdateJob.KEY,                    new PushGroupUpdateJob.Factory());
      put(PushMediaSendJob.KEY,                      new PushMediaSendJob.Factory());
      put(PushNotificationReceiveJob.KEY,            new PushNotificationReceiveJob.Factory());
      put(PushTextSendJob.KEY,                       new PushTextSendJob.Factory());
      put(RequestGroupInfoJob.KEY,                   new RequestGroupInfoJob.Factory());
      put(RetrieveProfileAvatarJob.KEY,              new RetrieveProfileAvatarJob.Factory(application));
      put(SendDeliveryReceiptJob.KEY,                new SendDeliveryReceiptJob.Factory());
      put(SendReadReceiptJob.KEY,                    new SendReadReceiptJob.Factory());
      put(SmsReceiveJob.KEY,                         new SmsReceiveJob.Factory());
      put(SmsSendJob.KEY,                            new SmsSendJob.Factory());
      put(SmsSentJob.KEY,                            new SmsSentJob.Factory());
      put(StickerDownloadJob.KEY,                    new StickerDownloadJob.Factory());
      put(StickerPackDownloadJob.KEY,                new StickerPackDownloadJob.Factory());
      put(TrimThreadJob.KEY,                         new TrimThreadJob.Factory());
      put(TypingSendJob.KEY,                         new TypingSendJob.Factory());
      put(UpdateApkJob.KEY,                          new UpdateApkJob.Factory());
      put(PrepareAttachmentAudioExtrasJob.KEY,       new PrepareAttachmentAudioExtrasJob.Factory());
      put(ResetThreadSessionJob.KEY,                 new ResetThreadSessionJob.Factory());
    }};
    factoryKeys.addAll(factoryHashMap.keySet());
    return factoryHashMap;
  }

  public static Map<String, Constraint.Factory> getConstraintFactories(@NonNull Application application) {
    return new HashMap<String, Constraint.Factory>() {{
      put(CellServiceConstraint.KEY,          new CellServiceConstraint.Factory(application));
      put(NetworkConstraint.KEY,              new NetworkConstraint.Factory(application));
      put(NetworkOrCellServiceConstraint.KEY, new NetworkOrCellServiceConstraint.Factory(application));
      put(SqlCipherMigrationConstraint.KEY,   new SqlCipherMigrationConstraint.Factory(application));
    }};
  }

  public static List<ConstraintObserver> getConstraintObservers(@NonNull Application application) {
    return Arrays.asList(new CellServiceConstraintObserver(application),
                         new NetworkConstraintObserver(application),
                         new SqlCipherMigrationConstraintObserver());
  }

  public static boolean hasFactoryForKey(String factoryKey) {
    return factoryKeys.contains(factoryKey);
  }
}
