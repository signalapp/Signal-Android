package org.thoughtcrime.securesms.jobmanager.migration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob;
import org.thoughtcrime.securesms.jobs.AttachmentUploadJob;
import org.thoughtcrime.securesms.jobs.AvatarDownloadJob;
import org.thoughtcrime.securesms.jobs.LocalBackupJob;
import org.thoughtcrime.securesms.jobs.MmsDownloadJob;
import org.thoughtcrime.securesms.jobs.MmsReceiveJob;
import org.thoughtcrime.securesms.jobs.MmsSendJob;
import org.thoughtcrime.securesms.jobs.PushContentReceiveJob;
import org.thoughtcrime.securesms.jobs.PushDecryptJob;
import org.thoughtcrime.securesms.jobs.PushGroupSendJob;
import org.thoughtcrime.securesms.jobs.PushGroupUpdateJob;
import org.thoughtcrime.securesms.jobs.PushMediaSendJob;
import org.thoughtcrime.securesms.jobs.PushNotificationReceiveJob;
import org.thoughtcrime.securesms.jobs.PushTextSendJob;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.jobs.RefreshUnidentifiedDeliveryAbilityJob;
import org.thoughtcrime.securesms.jobs.RequestGroupInfoJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob;
import org.thoughtcrime.securesms.jobs.RotateCertificateJob;
import org.thoughtcrime.securesms.jobs.RotateProfileKeyJob;
import org.thoughtcrime.securesms.jobs.SendDeliveryReceiptJob;
import org.thoughtcrime.securesms.jobs.SendReadReceiptJob;
import org.thoughtcrime.securesms.jobs.SmsReceiveJob;
import org.thoughtcrime.securesms.jobs.SmsSendJob;
import org.thoughtcrime.securesms.jobs.SmsSentJob;
import org.thoughtcrime.securesms.jobs.TrimThreadJob;
import org.thoughtcrime.securesms.jobs.TypingSendJob;
import org.thoughtcrime.securesms.jobs.UpdateApkJob;
import org.thoughtcrime.securesms.loki.api.PrepareAttachmentAudioExtrasJob;
import org.thoughtcrime.securesms.loki.api.ResetThreadSessionJob;
import org.thoughtcrime.securesms.loki.protocol.ClosedGroupUpdateMessageSendJob;
import org.thoughtcrime.securesms.loki.protocol.NullMessageSendJob;

import java.util.HashMap;
import java.util.Map;

//TODO AC: Looks like we don't use it anymore. Make sure it's abandoned and delete.
public class WorkManagerFactoryMappings {

  private static final Map<String, String> FACTORY_MAP = new HashMap<String, String>() {{
    put(AttachmentDownloadJob.class.getName(), AttachmentDownloadJob.KEY);
    put(AttachmentUploadJob.class.getName(), AttachmentUploadJob.KEY);
    put(AvatarDownloadJob.class.getName(), AvatarDownloadJob.KEY);
    put(ClosedGroupUpdateMessageSendJob.class.getName(), ClosedGroupUpdateMessageSendJob.KEY);
    put(LocalBackupJob.class.getName(), LocalBackupJob.KEY);
    put(MmsDownloadJob.class.getName(), MmsDownloadJob.KEY);
    put(MmsReceiveJob.class.getName(), MmsReceiveJob.KEY);
    put(MmsSendJob.class.getName(), MmsSendJob.KEY);
    put(NullMessageSendJob.class.getName(), NullMessageSendJob.KEY);
    put(PushContentReceiveJob.class.getName(), PushContentReceiveJob.KEY);
    put(PushDecryptJob.class.getName(), PushDecryptJob.KEY);
    put(PushGroupSendJob.class.getName(), PushGroupSendJob.KEY);
    put(PushGroupUpdateJob.class.getName(), PushGroupUpdateJob.KEY);
    put(PushMediaSendJob.class.getName(), PushMediaSendJob.KEY);
    put(PushNotificationReceiveJob.class.getName(), PushNotificationReceiveJob.KEY);
    put(PushTextSendJob.class.getName(), PushTextSendJob.KEY);
    put(RefreshAttributesJob.class.getName(), RefreshAttributesJob.KEY);
    put(RefreshUnidentifiedDeliveryAbilityJob.class.getName(), RefreshUnidentifiedDeliveryAbilityJob.KEY);
    put(RequestGroupInfoJob.class.getName(), RequestGroupInfoJob.KEY);
    put(RetrieveProfileAvatarJob.class.getName(), RetrieveProfileAvatarJob.KEY);
    put(RotateCertificateJob.class.getName(), RotateCertificateJob.KEY);
    put(RotateProfileKeyJob.class.getName(), RotateProfileKeyJob.KEY);
    put(SendDeliveryReceiptJob.class.getName(), SendDeliveryReceiptJob.KEY);
    put(SendReadReceiptJob.class.getName(), SendReadReceiptJob.KEY);
    put(SmsReceiveJob.class.getName(), SmsReceiveJob.KEY);
    put(SmsSendJob.class.getName(), SmsSendJob.KEY);
    put(SmsSentJob.class.getName(), SmsSentJob.KEY);
    put(TrimThreadJob.class.getName(), TrimThreadJob.KEY);
    put(TypingSendJob.class.getName(), TypingSendJob.KEY);
    put(UpdateApkJob.class.getName(), UpdateApkJob.KEY);
    put(PrepareAttachmentAudioExtrasJob.class.getName(), PrepareAttachmentAudioExtrasJob.KEY);
    put(ResetThreadSessionJob.class.getName(), ResetThreadSessionJob.KEY);
  }};

  public static @Nullable String getFactoryKey(@NonNull String workManagerClass) {
    return FACTORY_MAP.get(workManagerClass);
  }
}
