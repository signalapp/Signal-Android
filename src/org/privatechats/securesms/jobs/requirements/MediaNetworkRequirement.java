package org.privatechats.securesms.jobs.requirements;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;
import android.util.Log;

import org.privatechats.securesms.attachments.Attachment;
import org.privatechats.securesms.attachments.AttachmentId;
import org.privatechats.securesms.database.DatabaseFactory;
import org.privatechats.securesms.database.AttachmentDatabase;
import org.privatechats.securesms.util.MediaUtil;
import org.privatechats.securesms.util.ServiceUtil;
import org.privatechats.securesms.util.TextSecurePreferences;
import org.whispersystems.jobqueue.dependencies.ContextDependent;
import org.whispersystems.jobqueue.requirements.Requirement;

import java.util.Collections;
import java.util.Set;

public class MediaNetworkRequirement implements Requirement, ContextDependent {
  private static final long   serialVersionUID = 0L;
  private static final String TAG              = MediaNetworkRequirement.class.getSimpleName();

  private transient Context context;

  private final long messageId;
  private final long partRowId;
  private final long partUniqueId;

  public MediaNetworkRequirement(Context context, long messageId, AttachmentId attachmentId) {
    this.context      = context;
    this.messageId    = messageId;
    this.partRowId    = attachmentId.getRowId();
    this.partUniqueId = attachmentId.getUniqueId();
  }

  @Override
  public void setContext(Context context) {
    this.context = context;
  }

  private NetworkInfo getNetworkInfo() {
    return ServiceUtil.getConnectivityManager(context).getActiveNetworkInfo();
  }

  public boolean isConnectedWifi() {
    final NetworkInfo info = getNetworkInfo();
    return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI;
  }

  public boolean isConnectedMobile() {
    final NetworkInfo info = getNetworkInfo();
    return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_MOBILE;
  }

  public boolean isConnectedRoaming() {
    final NetworkInfo info = getNetworkInfo();
    return info != null && info.isConnected() && info.isRoaming() && info.getType() == ConnectivityManager.TYPE_MOBILE;
  }

  private @NonNull Set<String> getAllowedAutoDownloadTypes() {
    if (isConnectedWifi()) {
      return TextSecurePreferences.getWifiMediaDownloadAllowed(context);
    } else if (isConnectedRoaming()) {
      return TextSecurePreferences.getRoamingMediaDownloadAllowed(context);
    } else if (isConnectedMobile()) {
      return TextSecurePreferences.getMobileMediaDownloadAllowed(context);
    } else {
      return Collections.emptySet();
    }
  }

  @Override
  public boolean isPresent() {
    final AttachmentId       attachmentId = new AttachmentId(partRowId, partUniqueId);
    final AttachmentDatabase db           = DatabaseFactory.getAttachmentDatabase(context);
    final Attachment         attachment   = db.getAttachment(attachmentId);

    if (attachment == null) {
      Log.w(TAG, "attachment was null, returning vacuous true");
      return true;
    }

    Log.w(TAG, "part transfer progress is " + attachment.getTransferState());
    switch (attachment.getTransferState()) {
    case AttachmentDatabase.TRANSFER_PROGRESS_STARTED:
      return true;
    case AttachmentDatabase.TRANSFER_PROGRESS_AUTO_PENDING:
      final Set<String> allowedTypes = getAllowedAutoDownloadTypes();
      final boolean     isAllowed    = allowedTypes.contains(MediaUtil.getDiscreteMimeType(attachment.getContentType()));

      /// XXX WTF -- This is *hella* gross. A requirement shouldn't have the side effect of
      // *modifying the database* just by calling isPresent().
      if (isAllowed) db.setTransferState(messageId, attachmentId, AttachmentDatabase.TRANSFER_PROGRESS_STARTED);
      return isAllowed;
    default:
      return false;
    }
  }
}
