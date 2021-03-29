package org.thoughtcrime.securesms.service;


import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.chooser.ChooserTarget;
import android.service.chooser.ChooserTargetService;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.view.ContextThemeWrapper;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sharing.ShareActivity;
import org.thoughtcrime.securesms.util.AvatarUtil;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.ConversationUtil;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.ServiceUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RequiresApi(api = Build.VERSION_CODES.M)
public class DirectShareService extends ChooserTargetService {


  private static final String TAG         = Log.tag(DirectShareService.class);
  private static final int    MAX_TARGETS = 10;

  @Override
  public List<ChooserTarget> onGetChooserTargets(ComponentName targetActivityName,
                                                 IntentFilter matchedFilter)
  {
    Map<RecipientId, ChooserTarget> results = new LinkedHashMap<>();

    if (Build.VERSION.SDK_INT >= ConversationUtil.CONVERSATION_SUPPORT_VERSION) {
      ShortcutManager shortcutManager = ServiceUtil.getShortcutManager(this);
      if (shortcutManager != null && !shortcutManager.getDynamicShortcuts().isEmpty()) {
        addChooserTargetsFromDynamicShortcuts(results, shortcutManager.getDynamicShortcuts());
      }

      if (results.size() >= MAX_TARGETS) {
        return new ArrayList<>(results.values());
      }
    }

    ComponentName  componentName  = new ComponentName(this, ShareActivity.class);
    ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(this);

    try (ThreadDatabase.Reader reader = threadDatabase.readerFor(threadDatabase.getRecentConversationList(MAX_TARGETS, false, FeatureFlags.groupsV1ForcedMigration()))) {
      ThreadRecord record;

      while ((record = reader.getNext()) != null) {
          if (results.containsKey(record.getRecipient().getId())) {
            continue;
          }

          Recipient recipient = Recipient.resolved(record.getRecipient().getId());
          String    name      = recipient.getDisplayName(this);

          Bitmap avatar;

          if (recipient.getContactPhoto() != null) {
            try {
              avatar = GlideApp.with(this)
                               .asBitmap()
                               .load(recipient.getContactPhoto())
                               .circleCrop()
                               .submit(getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width),
                                       getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width))
                               .get();
            } catch (InterruptedException | ExecutionException e) {
              Log.w(TAG, e);
              avatar = getFallbackDrawable(recipient);
            }
          } else {
            avatar = getFallbackDrawable(recipient);
          }

          Bundle bundle = buildExtras(record);

          results.put(recipient.getId(), new ChooserTarget(name, Icon.createWithBitmap(avatar), 1.0f, componentName, bundle));
      }

      return new ArrayList<>(results.values());
    }
  }

  private @NonNull Bundle buildExtras(@NonNull ThreadRecord threadRecord) {
    Bundle bundle = new Bundle();

    bundle.putLong(ShareActivity.EXTRA_THREAD_ID, threadRecord.getThreadId());
    bundle.putString(ShareActivity.EXTRA_RECIPIENT_ID, threadRecord.getRecipient().getId().serialize());
    bundle.putInt(ShareActivity.EXTRA_DISTRIBUTION_TYPE, threadRecord.getDistributionType());

    return bundle;
  }

  private Bitmap getFallbackDrawable(@NonNull Recipient recipient) {
    Context themedContext = new ContextThemeWrapper(this, R.style.TextSecure_LightTheme);
    return BitmapUtil.createFromDrawable(recipient.getFallbackContactPhotoDrawable(themedContext, false),
                                         getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width),
                                         getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_height));
  }

  @RequiresApi(ConversationUtil.CONVERSATION_SUPPORT_VERSION)
  private void addChooserTargetsFromDynamicShortcuts(@NonNull Map<RecipientId, ChooserTarget> targetMap, @NonNull List<ShortcutInfo> shortcutInfos) {
    Stream.of(shortcutInfos)
          .sorted((lhs, rhs) -> Integer.compare(lhs.getRank(), rhs.getRank()))
          .takeWhileIndexed((idx, info) -> idx < MAX_TARGETS)
          .forEach(info -> {
            Recipient     recipient = Recipient.resolved(RecipientId.from(info.getId()));
            ChooserTarget target    = buildChooserTargetFromShortcutInfo(info, recipient);

            targetMap.put(RecipientId.from(info.getId()), target);
          });
  }

  @RequiresApi(ConversationUtil.CONVERSATION_SUPPORT_VERSION)
  private @NonNull ChooserTarget buildChooserTargetFromShortcutInfo(@NonNull ShortcutInfo info, @NonNull Recipient recipient) {
    ThreadRecord threadRecord = DatabaseFactory.getThreadDatabase(this).getThreadRecordFor(recipient);

    return new ChooserTarget(info.getShortLabel(),
                             AvatarUtil.getIconForShortcut(this, recipient),
                             info.getRank() / ((float) MAX_TARGETS),
                             new ComponentName(this, ShareActivity.class),
                             buildExtras(threadRecord));
  }
}
