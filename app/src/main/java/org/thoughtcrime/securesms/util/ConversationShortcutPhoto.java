package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import org.signal.libsignal.protocol.util.ByteUtil;
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatarDrawable;
import org.thoughtcrime.securesms.contacts.avatars.SystemContactPhoto;
import org.thoughtcrime.securesms.database.model.ProfileAvatarFileDetails;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.security.MessageDigest;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public final class ConversationShortcutPhoto implements Key {

  /**
   * Version integer to update whenever business logic changes in this class (such as
   * design tweaks or bug fixes). This way, the old photos will be considered invalid
   * in the cache.
   */
  private static final long VERSION = 1L;

  private final Recipient                recipient;
  private final String                   avatarObject;
  private final ProfileAvatarFileDetails profileAvatarFileDetails;

  @WorkerThread
  public ConversationShortcutPhoto(@NonNull Recipient recipient) {
    this.recipient                = recipient.resolve();
    this.avatarObject             = Util.firstNonNull(recipient.getProfileAvatar(), "");
    this.profileAvatarFileDetails = recipient.getProfileAvatarFileDetails();
  }

  @Override
  public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
    messageDigest.update(recipient.getDisplayName(AppDependencies.getApplication()).getBytes());
    messageDigest.update(avatarObject.getBytes());
    messageDigest.update(isSystemContactPhoto() ? (byte) 1 : (byte) 0);
    messageDigest.update(profileAvatarFileDetails.getDiskCacheKeyBytes());
    messageDigest.update(ByteUtil.longToByteArray(VERSION));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConversationShortcutPhoto that = (ConversationShortcutPhoto) o;
    return Objects.equals(recipient, that.recipient)             &&
           Objects.equals(avatarObject, that.avatarObject)       &&
           isSystemContactPhoto() == that.isSystemContactPhoto() &&
           Objects.equals(profileAvatarFileDetails, that.profileAvatarFileDetails);
  }

  @Override
  public int hashCode() {
    return Objects.hash(recipient, avatarObject, isSystemContactPhoto(), profileAvatarFileDetails);
  }

  private boolean isSystemContactPhoto() {
    return recipient.getContactPhoto() instanceof SystemContactPhoto;
  }

  public static final class Loader implements ModelLoader<ConversationShortcutPhoto, Bitmap> {

    private final Context context;

    private Loader(@NonNull Context context) {
      this.context = context;
    }

    @Override
    public @NonNull LoadData<Bitmap> buildLoadData(@NonNull ConversationShortcutPhoto conversationShortcutPhoto, int width, int height, @NonNull Options options) {
      return new LoadData<>(conversationShortcutPhoto, new Fetcher(context, conversationShortcutPhoto));
    }

    @Override
    public boolean handles(@NonNull ConversationShortcutPhoto conversationShortcutPhoto) {
      return true;
    }

    public static class Factory implements ModelLoaderFactory<ConversationShortcutPhoto, Bitmap> {

      private final Context context;

      public Factory(@NonNull Context context) {
        this.context = context;
      }

      @Override
      public @NonNull ModelLoader<ConversationShortcutPhoto, Bitmap> build(@NonNull MultiModelLoaderFactory multiFactory) {
        return new Loader(context);
      }

      @Override
      public void teardown() {
      }
    }
  }

  static final class Fetcher implements DataFetcher<Bitmap> {

    private final Context                   context;
    private final ConversationShortcutPhoto photo;

    private Fetcher(@NonNull Context context, @NonNull ConversationShortcutPhoto photo) {
      this.context = context;
      this.photo   = photo;
    }

    @Override
    public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super Bitmap> callback) {
      Bitmap bitmap;

      try {
        bitmap = getShortcutInfoBitmap(context);
      } catch (ExecutionException | InterruptedException e) {
        bitmap = getFallbackForShortcut(context);
      }

      callback.onDataReady(bitmap);
    }

    @Override
    public void cleanup() {
    }

    @Override
    public void cancel() {
    }

    @Override
    public @NonNull Class<Bitmap> getDataClass() {
      return Bitmap.class;
    }

    @Override
    public @NonNull DataSource getDataSource() {
      return DataSource.LOCAL;
    }

    private @NonNull Bitmap getShortcutInfoBitmap(@NonNull Context context) throws ExecutionException, InterruptedException {
      return DrawableUtil.wrapBitmapForShortcutInfo(AvatarUtil.loadIconBitmapSquareNoCache(context,
                                                                                           photo.recipient,
                                                                                           AdaptiveBitmapMetrics.getInnerWidth(),
                                                                                           AdaptiveBitmapMetrics.getInnerWidth()));
    }

    private @NonNull Bitmap getFallbackForShortcut(@NonNull Context context) {
      Recipient recipient = photo.recipient;

      Bitmap toWrap  = DrawableUtil.toBitmap(new FallbackAvatarDrawable(context, recipient.getFallbackAvatar()), ViewUtil.dpToPx(80), ViewUtil.dpToPx(80));
      Bitmap wrapped = DrawableUtil.wrapBitmapForShortcutInfo(toWrap);

      toWrap.recycle();

      return wrapped;
    }
  }
}
