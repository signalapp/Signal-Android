package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.FallbackContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ResourceContactPhoto;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.AvatarUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.webrtc.RendererCommon;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Encapsulates views needed to show a call participant including their
 * avatar in full screen or pip mode, and their video feed.
 */
public class CallParticipantView extends ConstraintLayout {

  private static final FallbackPhotoProvider FALLBACK_PHOTO_PROVIDER = new FallbackPhotoProvider();

  private static final long DELAY_SHOWING_MISSING_MEDIA_KEYS = TimeUnit.SECONDS.toMillis(5);
  private static final int  SMALL_AVATAR                     = ViewUtil.dpToPx(96);
  private static final int  LARGE_AVATAR                     = ViewUtil.dpToPx(112);

  private RecipientId recipientId;
  private boolean     infoMode;
  private Runnable    missingMediaKeysUpdater;

  private AppCompatImageView  backgroundAvatar;
  private AvatarImageView     avatar;
  private TextureViewRenderer renderer;
  private ImageView           pipAvatar;
  private ContactPhoto        contactPhoto;
  private View                audioMuted;
  private View                infoOverlay;
  private EmojiTextView       infoMessage;
  private Button              infoMoreInfo;
  private AppCompatImageView  infoIcon;

  public CallParticipantView(@NonNull Context context) {
    super(context);
    onFinishInflate();
  }

  public CallParticipantView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public CallParticipantView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    backgroundAvatar = findViewById(R.id.call_participant_background_avatar);
    avatar           = findViewById(R.id.call_participant_item_avatar);
    pipAvatar        = findViewById(R.id.call_participant_item_pip_avatar);
    renderer         = findViewById(R.id.call_participant_renderer);
    audioMuted       = findViewById(R.id.call_participant_mic_muted);
    infoOverlay      = findViewById(R.id.call_participant_info_overlay);
    infoIcon         = findViewById(R.id.call_participant_info_icon);
    infoMessage      = findViewById(R.id.call_participant_info_message);
    infoMoreInfo     = findViewById(R.id.call_participant_info_more_info);

    avatar.setFallbackPhotoProvider(FALLBACK_PHOTO_PROVIDER);
    useLargeAvatar();
  }

  void setMirror(boolean mirror) {
    renderer.setMirror(mirror);
  }

  void setScalingType(@NonNull RendererCommon.ScalingType scalingType) {
    renderer.setScalingType(scalingType);
  }

  void setCallParticipant(@NonNull CallParticipant participant) {
    boolean participantChanged = recipientId == null || !recipientId.equals(participant.getRecipient().getId());
    recipientId = participant.getRecipient().getId();
    infoMode    = participant.getRecipient().isBlocked() || isMissingMediaKeys(participant);

    if (infoMode) {
      renderer.setVisibility(View.GONE);
      renderer.attachBroadcastVideoSink(null);
      audioMuted.setVisibility(View.GONE);
      avatar.setVisibility(View.GONE);
      pipAvatar.setVisibility(View.GONE);

      infoOverlay.setVisibility(View.VISIBLE);

      ImageViewCompat.setImageTintList(infoIcon, ContextCompat.getColorStateList(getContext(), R.color.core_white));

      if (participant.getRecipient().isBlocked()) {
        infoIcon.setImageResource(R.drawable.ic_block_tinted_24);
        infoMessage.setText(getContext().getString(R.string.CallParticipantView__s_is_blocked, participant.getRecipient().getShortDisplayName(getContext())));
        infoMoreInfo.setOnClickListener(v -> showBlockedDialog(participant.getRecipient()));
      } else {
        infoIcon.setImageResource(R.drawable.ic_error_solid_24);
        infoMessage.setText(getContext().getString(R.string.CallParticipantView__cant_receive_audio_video_from_s, participant.getRecipient().getShortDisplayName(getContext())));
        infoMoreInfo.setOnClickListener(v -> showNoMediaKeysDialog(participant.getRecipient()));
      }
    } else {
      infoOverlay.setVisibility(View.GONE);

      renderer.setVisibility(participant.isVideoEnabled() ? View.VISIBLE : View.GONE);

      if (participant.isVideoEnabled()) {
        if (participant.getVideoSink().getEglBase() != null) {
          renderer.init(participant.getVideoSink().getEglBase());
        }
        renderer.attachBroadcastVideoSink(participant.getVideoSink());
      } else {
        renderer.attachBroadcastVideoSink(null);
      }

      audioMuted.setVisibility(participant.isMicrophoneEnabled() ? View.GONE : View.VISIBLE);
    }

    if (participantChanged || !Objects.equals(contactPhoto, participant.getRecipient().getContactPhoto())) {
      avatar.setAvatarUsingProfile(participant.getRecipient());
      AvatarUtil.loadBlurredIconIntoImageView(participant.getRecipient(), backgroundAvatar);
      setPipAvatar(participant.getRecipient());
      contactPhoto = participant.getRecipient().getContactPhoto();
    }
  }

  private boolean isMissingMediaKeys(@NonNull CallParticipant participant) {
    if (missingMediaKeysUpdater != null) {
      Util.cancelRunnableOnMain(missingMediaKeysUpdater);
      missingMediaKeysUpdater = null;
    }

    if (!participant.isMediaKeysReceived()) {
      long time = System.currentTimeMillis() - participant.getAddedToCallTime();
      if (time > DELAY_SHOWING_MISSING_MEDIA_KEYS) {
        return true;
      } else {
        missingMediaKeysUpdater = () -> {
          if (recipientId.equals(participant.getRecipient().getId())) {
            setCallParticipant(participant);
          }
        };
        Util.runOnMainDelayed(missingMediaKeysUpdater, DELAY_SHOWING_MISSING_MEDIA_KEYS - time);
      }
    }
    return false;
  }

  void setRenderInPip(boolean shouldRenderInPip) {
    if (infoMode) {
      infoMessage.setVisibility(shouldRenderInPip ? View.GONE : View.VISIBLE);
      infoMoreInfo.setVisibility(shouldRenderInPip ? View.GONE : View.VISIBLE);
      return;
    }

    avatar.setVisibility(shouldRenderInPip ? View.GONE : View.VISIBLE);
    pipAvatar.setVisibility(shouldRenderInPip ? View.VISIBLE : View.GONE);
  }

  void useLargeAvatar() {
    changeAvatarParams(LARGE_AVATAR);
  }

  void useSmallAvatar() {
    changeAvatarParams(SMALL_AVATAR);
  }

  void releaseRenderer() {
    renderer.release();
  }

  private void changeAvatarParams(int dimension) {
    ViewGroup.LayoutParams params = avatar.getLayoutParams();
    if (params.height != dimension) {
      params.height = dimension;
      params.width  = dimension;
      avatar.setLayoutParams(params);
    }
  }

  private void setPipAvatar(@NonNull Recipient recipient) {
    ContactPhoto         contactPhoto  = recipient.isSelf() ? new ProfileContactPhoto(Recipient.self(), Recipient.self().getProfileAvatar())
                                                            : recipient.getContactPhoto();
    FallbackContactPhoto fallbackPhoto = recipient.getFallbackContactPhoto(FALLBACK_PHOTO_PROVIDER);

    GlideApp.with(this)
            .load(contactPhoto)
            .fallback(fallbackPhoto.asCallCard(getContext()))
            .error(fallbackPhoto.asCallCard(getContext()))
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(pipAvatar);

    pipAvatar.setScaleType(contactPhoto == null ? ImageView.ScaleType.CENTER_INSIDE : ImageView.ScaleType.CENTER_CROP);
    pipAvatar.setBackgroundColor(recipient.getColor().toActionBarColor(getContext()));
  }

  private void showBlockedDialog(@NonNull Recipient recipient) {
    new AlertDialog.Builder(getContext())
                   .setTitle(getContext().getString(R.string.CallParticipantView__s_is_blocked, recipient.getShortDisplayName(getContext())))
                   .setMessage(R.string.CallParticipantView__you_wont_receive_their_audio_or_video)
                   .setPositiveButton(android.R.string.ok, null)
                   .show();
  }

  private void showNoMediaKeysDialog(@NonNull Recipient recipient) {
    new AlertDialog.Builder(getContext())
                   .setTitle(getContext().getString(R.string.CallParticipantView__cant_receive_audio_and_video_from_s, recipient.getShortDisplayName(getContext())))
                   .setMessage(R.string.CallParticipantView__this_may_be_Because_they_have_not_verified_your_safety_number_change)
                   .setPositiveButton(android.R.string.ok, null)
                   .show();
  }

  private static final class FallbackPhotoProvider extends Recipient.FallbackPhotoProvider {
    @Override
    public @NonNull FallbackContactPhoto getPhotoForLocalNumber() {
      return super.getPhotoForRecipientWithoutName();
    }

    @Override
    public @NonNull FallbackContactPhoto getPhotoForRecipientWithoutName() {
      ResourceContactPhoto photo = new ResourceContactPhoto(R.drawable.ic_profile_outline_120);
      photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
      return photo;
    }
  }
}
