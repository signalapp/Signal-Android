package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewKt;
import androidx.core.widget.ImageViewCompat;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.ThreadUtil;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatarDrawable;
import org.thoughtcrime.securesms.badges.BadgeImageView;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto;
import org.thoughtcrime.securesms.conversation.colors.ChatColors;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.AvatarUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.webrtc.RendererCommon;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Encapsulates views needed to show a call participant including their
 * avatar in full screen or pip mode, and their video feed.
 */
public class CallParticipantView extends ConstraintLayout {

  private static final long DELAY_SHOWING_MISSING_MEDIA_KEYS = TimeUnit.SECONDS.toMillis(5);
  private static final int  SMALL_AVATAR                     = ViewUtil.dpToPx(96);
  private static final int  LARGE_AVATAR                     = ViewUtil.dpToPx(112);

  private RecipientId recipientId;
  private boolean     infoMode;
  private boolean     raiseHandAllowed;
  private Runnable    missingMediaKeysUpdater;
  private boolean     shouldRenderInPip;

  private SelfPipMode selfPipMode = SelfPipMode.NOT_SELF_PIP;

  private AppCompatImageView  backgroundAvatar;
  private AvatarImageView     avatar;
  private BadgeImageView      badge;
  private View                rendererFrame;
  private TextureViewRenderer renderer;
  private ImageView           pipAvatar;
  private BadgeImageView      pipBadge;
  private ContactPhoto        contactPhoto;
  private AudioIndicatorView  audioIndicator;
  private View                infoOverlay;
  private EmojiTextView       infoMessage;
  private Button              infoMoreInfo;
  private AppCompatImageView  infoIcon;
  private View                switchCameraIconFrame;
  private View                switchCameraIcon;
  private ImageView           raiseHandIcon;
  private TextView            nameLabel;

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

    backgroundAvatar      = findViewById(R.id.call_participant_background_avatar);
    avatar                = findViewById(R.id.call_participant_item_avatar);
    pipAvatar             = findViewById(R.id.call_participant_item_pip_avatar);
    rendererFrame         = findViewById(R.id.call_participant_renderer_frame);
    renderer              = findViewById(R.id.call_participant_renderer);
    audioIndicator        = findViewById(R.id.call_participant_audio_indicator);
    infoOverlay           = findViewById(R.id.call_participant_info_overlay);
    infoIcon              = findViewById(R.id.call_participant_info_icon);
    infoMessage           = findViewById(R.id.call_participant_info_message);
    infoMoreInfo          = findViewById(R.id.call_participant_info_more_info);
    badge                 = findViewById(R.id.call_participant_item_badge);
    pipBadge              = findViewById(R.id.call_participant_item_pip_badge);
    switchCameraIconFrame = findViewById(R.id.call_participant_switch_camera);
    switchCameraIcon      = findViewById(R.id.call_participant_switch_camera_icon);
    raiseHandIcon         = findViewById(R.id.call_participant_raise_hand_icon);
    nameLabel             = findViewById(R.id.call_participant_name_label);

    useLargeAvatar();
  }

  void setMirror(boolean mirror) {
    renderer.setMirror(mirror);
  }

  void setScalingType(@NonNull RendererCommon.ScalingType scalingType) {
    renderer.setScalingType(scalingType);
  }

  void setScalingType(@NonNull RendererCommon.ScalingType scalingTypeMatchOrientation, @NonNull RendererCommon.ScalingType scalingTypeMismatchOrientation) {
    renderer.setScalingType(scalingTypeMatchOrientation, scalingTypeMismatchOrientation);
  }

  void setCallParticipant(@NonNull CallParticipant participant) {
    boolean participantChanged = recipientId == null || !recipientId.equals(participant.getRecipient().getId());
    recipientId = participant.getRecipient().getId();
    infoMode    = participant.getRecipient().isBlocked() || isMissingMediaKeys(participant);

    if (infoMode) {
      rendererFrame.setVisibility(View.GONE);
      renderer.setVisibility(View.GONE);
      renderer.attachBroadcastVideoSink(null);
      audioIndicator.setVisibility(View.GONE);
      avatar.setVisibility(View.GONE);
      badge.setVisibility(View.GONE);
      pipAvatar.setVisibility(View.GONE);
      pipBadge.setVisibility(View.GONE);

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

      //TODO: [calling] SFU instability causes the forwarding video flag to alternate quickly, should restore after calling server update
      boolean hasContentToRender = (participant.isVideoEnabled() || participant.isScreenSharing()); // && participant.isForwardingVideo();

      rendererFrame.setVisibility(hasContentToRender ? View.VISIBLE : View.INVISIBLE);
      renderer.setVisibility(hasContentToRender ? View.VISIBLE : View.INVISIBLE);

      if (participant.isVideoEnabled()) {
        participant.getVideoSink().getLockableEglBase().performWithValidEglBase(eglBase -> {
          renderer.init(eglBase);
        });
        renderer.attachBroadcastVideoSink(participant.getVideoSink());
      } else {
        renderer.attachBroadcastVideoSink(null);
      }

      audioIndicator.setVisibility(View.VISIBLE);
      audioIndicator.bind(participant.isMicrophoneEnabled(), participant.getAudioLevel());
      final String shortRecipientDisplayName = participant.getShortRecipientDisplayName(getContext());
      if (raiseHandAllowed && participant.isHandRaised()) {
        raiseHandIcon.setVisibility(View.VISIBLE);
        nameLabel.setVisibility(View.VISIBLE);
        nameLabel.setText(shortRecipientDisplayName);
      } else {
        raiseHandIcon.setVisibility(View.GONE);
        nameLabel.setVisibility(View.GONE);
      }
    }

    if (participantChanged || !Objects.equals(contactPhoto, participant.getRecipient().getContactPhoto())) {
      avatar.setAvatarUsingProfile(participant.getRecipient());
      badge.setBadgeFromRecipient(participant.getRecipient());
      AvatarUtil.loadBlurredIconIntoImageView(participant.getRecipient(), backgroundAvatar);
      setPipAvatar(participant.getRecipient());
      pipBadge.setBadgeFromRecipient(participant.getRecipient());
      contactPhoto = participant.getRecipient().getContactPhoto();
    }

    setRenderInPip(shouldRenderInPip);
  }

  private boolean isMissingMediaKeys(@NonNull CallParticipant participant) {
    if (missingMediaKeysUpdater != null) {
      ThreadUtil.cancelRunnableOnMain(missingMediaKeysUpdater);
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
        ThreadUtil.runOnMainDelayed(missingMediaKeysUpdater, DELAY_SHOWING_MISSING_MEDIA_KEYS - time);
      }
    }
    return false;
  }

  void setRenderInPip(boolean shouldRenderInPip) {
    this.shouldRenderInPip = shouldRenderInPip;

    if (infoMode) {
      infoMessage.setVisibility(shouldRenderInPip ? View.GONE : View.VISIBLE);
      infoMoreInfo.setVisibility(shouldRenderInPip ? View.GONE : View.VISIBLE);
      infoOverlay.setOnClickListener(shouldRenderInPip ? v -> infoMoreInfo.performClick() : null);
      return;
    } else {
      infoOverlay.setOnClickListener(null);
    }

    avatar.setVisibility(shouldRenderInPip ? View.GONE : View.VISIBLE);
    badge.setVisibility(shouldRenderInPip ? View.GONE : View.VISIBLE);
    pipAvatar.setVisibility(shouldRenderInPip ? View.VISIBLE : View.GONE);
    pipBadge.setVisibility(shouldRenderInPip ? View.VISIBLE : View.GONE);
  }

  public void setRaiseHandAllowed(boolean raiseHandAllowed) {
    this.raiseHandAllowed = raiseHandAllowed;
  }

  /**
   * Adjust UI elements for the various self PIP positions. If called after a {@link TransitionManager#beginDelayedTransition(ViewGroup, Transition)},
   * the changes to the UI elements will animate.
   */
  void setSelfPipMode(@NonNull SelfPipMode selfPipMode, boolean isMoreThanOneCameraAvailable) {
    Preconditions.checkArgument(selfPipMode != SelfPipMode.NOT_SELF_PIP);

    if (this.selfPipMode == selfPipMode) {
      return;
    }

    this.selfPipMode = selfPipMode;

    ConstraintSet constraints = new ConstraintSet();
    constraints.clone(this);

    switch (selfPipMode) {
      case NORMAL_SELF_PIP -> {
        constraints.connect(
            R.id.call_participant_audio_indicator,
            ConstraintSet.START,
            ConstraintSet.PARENT_ID,
            ConstraintSet.START,
            ViewUtil.dpToPx(6)
        );
        constraints.clear(
            R.id.call_participant_audio_indicator,
            ConstraintSet.END
        );
        constraints.setMargin(
            R.id.call_participant_audio_indicator,
            ConstraintSet.BOTTOM,
            ViewUtil.dpToPx(6)
        );

        if (isMoreThanOneCameraAvailable) {
          constraints.setVisibility(R.id.call_participant_switch_camera, View.VISIBLE);
          constraints.setMargin(
              R.id.call_participant_switch_camera,
              ConstraintSet.END,
              ViewUtil.dpToPx(6)
          );
          constraints.setMargin(
              R.id.call_participant_switch_camera,
              ConstraintSet.BOTTOM,
              ViewUtil.dpToPx(6)
          );
          constraints.constrainWidth(R.id.call_participant_switch_camera, ViewUtil.dpToPx(28));
          constraints.constrainHeight(R.id.call_participant_switch_camera, ViewUtil.dpToPx(28));

          ViewGroup.LayoutParams params = switchCameraIcon.getLayoutParams();
          params.width = params.height = ViewUtil.dpToPx(16);
          switchCameraIcon.setLayoutParams(params);

          switchCameraIconFrame.setClickable(false);
          switchCameraIconFrame.setEnabled(false);
        } else {
          constraints.setVisibility(R.id.call_participant_switch_camera, View.GONE);
        }
      }
      case EXPANDED_SELF_PIP -> {
        constraints.connect(
            R.id.call_participant_audio_indicator,
            ConstraintSet.START,
            ConstraintSet.PARENT_ID,
            ConstraintSet.START,
            ViewUtil.dpToPx(8)
        );
        constraints.clear(
            R.id.call_participant_audio_indicator,
            ConstraintSet.END
        );
        constraints.setMargin(
            R.id.call_participant_audio_indicator,
            ConstraintSet.BOTTOM,
            ViewUtil.dpToPx(8)
        );

        if (isMoreThanOneCameraAvailable) {
          constraints.setVisibility(R.id.call_participant_switch_camera, View.VISIBLE);
          constraints.setMargin(
              R.id.call_participant_switch_camera,
              ConstraintSet.END,
              ViewUtil.dpToPx(8)
          );
          constraints.setMargin(
              R.id.call_participant_switch_camera,
              ConstraintSet.BOTTOM,
              ViewUtil.dpToPx(8)
          );
          constraints.constrainWidth(R.id.call_participant_switch_camera, ViewUtil.dpToPx(48));
          constraints.constrainHeight(R.id.call_participant_switch_camera, ViewUtil.dpToPx(48));

          ViewGroup.LayoutParams params = switchCameraIcon.getLayoutParams();
          params.width = params.height = ViewUtil.dpToPx(24);
          switchCameraIcon.setLayoutParams(params);

          switchCameraIconFrame.setClickable(true);
          switchCameraIconFrame.setEnabled(true);
        } else {
          constraints.setVisibility(R.id.call_participant_switch_camera, View.GONE);
        }
      }
      case MINI_SELF_PIP -> {
        constraints.connect(
            R.id.call_participant_audio_indicator,
            ConstraintSet.START,
            ConstraintSet.PARENT_ID,
            ConstraintSet.START,
            0
        );
        constraints.connect(
            R.id.call_participant_audio_indicator,
            ConstraintSet.END,
            ConstraintSet.PARENT_ID,
            ConstraintSet.END,
            0
        );
        constraints.setMargin(
            R.id.call_participant_audio_indicator,
            ConstraintSet.BOTTOM,
            ViewUtil.dpToPx(6)
        );
        constraints.setVisibility(R.id.call_participant_switch_camera, View.GONE);
      }
    }

    constraints.applyTo(this);
  }

  void hideAvatar() {
    avatar.setAlpha(0f);
    badge.setAlpha(0f);
  }

  void showAvatar() {
    avatar.setAlpha(1f);
    badge.setAlpha(1f);
  }

  void useLargeAvatar() {
    changeAvatarParams(LARGE_AVATAR);
  }

  void useSmallAvatar() {
    changeAvatarParams(SMALL_AVATAR);
  }

  void setBottomInset(int bottomInset) {
    int desiredMargin = getResources().getDimensionPixelSize(R.dimen.webrtc_audio_indicator_margin) + bottomInset;
    if (ViewKt.getMarginBottom(audioIndicator) == desiredMargin) {
      return;
    }

    TransitionManager.beginDelayedTransition(this);

    ViewUtil.setBottomMargin(audioIndicator, desiredMargin);
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
    ContactPhoto         contactPhoto  = recipient.isSelf() ? new ProfileContactPhoto(Recipient.self())
                                                            : recipient.getContactPhoto();

    FallbackAvatarDrawable fallbackAvatarDrawable = new FallbackAvatarDrawable(getContext(), recipient.getFallbackAvatar());

    Glide.with(this)
            .load(contactPhoto)
            .fallback(fallbackAvatarDrawable)
            .error(fallbackAvatarDrawable)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .fitCenter()
            .into(pipAvatar);

    pipAvatar.setScaleType(contactPhoto == null ? ImageView.ScaleType.CENTER_INSIDE : ImageView.ScaleType.CENTER_CROP);

    ChatColors chatColors = recipient.getChatColors();

    pipAvatar.setBackground(chatColors.getChatBubbleMask());
  }

  private void showBlockedDialog(@NonNull Recipient recipient) {
    new MaterialAlertDialogBuilder(getContext())
                   .setTitle(getContext().getString(R.string.CallParticipantView__s_is_blocked, recipient.getShortDisplayName(getContext())))
                   .setMessage(R.string.CallParticipantView__you_wont_receive_their_audio_or_video)
                   .setPositiveButton(android.R.string.ok, null)
                   .show();
  }

  private void showNoMediaKeysDialog(@NonNull Recipient recipient) {
    new MaterialAlertDialogBuilder(getContext())
                   .setTitle(getContext().getString(R.string.CallParticipantView__cant_receive_audio_and_video_from_s, recipient.getShortDisplayName(getContext())))
                   .setMessage(R.string.CallParticipantView__this_may_be_Because_they_have_not_verified_your_safety_number_change)
                   .setPositiveButton(android.R.string.ok, null)
                   .show();
  }

  public enum SelfPipMode {
    NOT_SELF_PIP,
    NORMAL_SELF_PIP,
    EXPANDED_SELF_PIP,
    MINI_SELF_PIP
  }
}
