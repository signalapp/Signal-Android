package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.constraintlayout.widget.Group;
import androidx.core.util.Consumer;
import androidx.transition.AutoTransition;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AccessibleToggleButton;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.FallbackContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ResourceContactPhoto;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.thoughtcrime.securesms.util.AvatarUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.webrtc.RendererCommon;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;

public class WebRtcCallView extends FrameLayout {

  private static final long                  TRANSITION_DURATION_MILLIS = 250;
  private static final FallbackPhotoProvider FALLBACK_PHOTO_PROVIDER    = new FallbackPhotoProvider();

  public static final int FADE_OUT_DELAY = 5000;

  private TextureViewRenderer           localRenderer;
  private Group                         ongoingCallButtons;
  private Group                         incomingCallButtons;
  private Group                         answerWithVoiceGroup;
  private Group                         topViews;
  private View                          topGradient;
  private WebRtcAudioOutputToggleButton speakerToggle;
  private AccessibleToggleButton        videoToggle;
  private AccessibleToggleButton        micToggle;
  private ViewGroup                     largeLocalRenderContainer;
  private ViewGroup                     localRenderPipFrame;
  private ViewGroup                     smallLocalRenderContainer;
  private ViewGroup                     remoteRenderContainer;
  private TextView                      recipientName;
  private TextView                      status;
  private ConstraintLayout              parent;
  private AvatarImageView               avatar;
  private ImageView                     avatarCard;
  private ControlsListener              controlsListener;
  private RecipientId                   recipientId;
  private CameraState.Direction         cameraDirection;
  private boolean                       shouldFadeControls;
  private ImageView                     accept;
  private View                          cameraDirectionToggle;
  private PictureInPictureGestureHelper pictureInPictureGestureHelper;

  private final Runnable fadeOutRunnable = () -> { if (isAttachedToWindow()) fadeOutControls(); };

  public WebRtcCallView(@NonNull Context context) {
    this(context, null);
  }

  public WebRtcCallView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);

    LayoutInflater.from(context).inflate(R.layout.webrtc_call_view, this, true);
  }

  @SuppressWarnings("CodeBlock2Expr")
  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    ongoingCallButtons        = findViewById(R.id.call_screen_in_call_buttons);
    incomingCallButtons       = findViewById(R.id.call_screen_incoming_call_buttons);
    answerWithVoiceGroup      = findViewById(R.id.call_screen_answer_with_audio_button);
    topViews                  = findViewById(R.id.call_screen_top_views);
    topGradient               = findViewById(R.id.call_screen_header_gradient);
    speakerToggle             = findViewById(R.id.call_screen_speaker_toggle);
    videoToggle               = findViewById(R.id.call_screen_video_toggle);
    micToggle                 = findViewById(R.id.call_screen_mic_toggle);
    localRenderPipFrame       = findViewById(R.id.call_screen_pip);
    largeLocalRenderContainer = findViewById(R.id.call_screen_large_local_renderer_holder);
    smallLocalRenderContainer = findViewById(R.id.call_screen_small_local_renderer_holder);
    remoteRenderContainer     = findViewById(R.id.call_screen_remote_renderer_holder);
    recipientName             = findViewById(R.id.call_screen_recipient_name);
    status                    = findViewById(R.id.call_screen_status);
    parent                    = findViewById(R.id.call_screen);
    avatar                    = findViewById(R.id.call_screen_recipient_avatar);
    avatarCard                = findViewById(R.id.call_screen_recipient_avatar_call_card);
    accept                    = findViewById(R.id.call_screen_answer_call);
    cameraDirectionToggle     = findViewById(R.id.call_screen_camera_direction_toggle);

    View      hangup                = findViewById(R.id.call_screen_end_call);
    View      downCaret             = findViewById(R.id.call_screen_down_arrow);
    View      decline               = findViewById(R.id.call_screen_decline_call);
    View      answerWithAudio       = findViewById(R.id.call_screen_answer_with_audio);

    speakerToggle.setOnAudioOutputChangedListener(outputMode -> {
      runIfNonNull(controlsListener, listener -> listener.onAudioOutputChanged(outputMode));
    });

    videoToggle.setOnCheckedChangeListener((v, isOn) -> {
      runIfNonNull(controlsListener, listener -> listener.onVideoChanged(isOn));
    });

    micToggle.setOnCheckedChangeListener((v, isOn) -> {
      runIfNonNull(controlsListener, listener -> listener.onMicChanged(isOn));
    });

    cameraDirectionToggle.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onCameraDirectionChanged));

    hangup.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onEndCallPressed));
    decline.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onDenyCallPressed));

    downCaret.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onDownCaretPressed));

    accept.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onAcceptCallPressed));
    answerWithAudio.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onAcceptCallWithVoiceOnlyPressed));

    setOnClickListener(v -> toggleControls());
    avatar.setOnClickListener(v -> toggleControls());

    pictureInPictureGestureHelper = PictureInPictureGestureHelper.applyTo(localRenderPipFrame);

    int                statusBarHeight = ViewUtil.getStatusBarHeight(this);
    MarginLayoutParams params          = (MarginLayoutParams) parent.getLayoutParams();

    params.topMargin = statusBarHeight;
    parent.setLayoutParams(params);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();

    if (shouldFadeControls) {
      scheduleFadeOut();
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    cancelFadeOut();
  }

  public void showCameraToggleButton(boolean shouldShowCameraToggleButton) {
    cameraDirectionToggle.setVisibility(shouldShowCameraToggleButton ? VISIBLE : GONE);
  }

  public void setControlsListener(@Nullable ControlsListener controlsListener) {
    this.controlsListener = controlsListener;
  }

  public void setMicEnabled(boolean isMicEnabled) {
    micToggle.setChecked(isMicEnabled, false);
  }

  public void setBluetoothEnabled(boolean isBluetoothEnabled) {
    speakerToggle.setIsHeadsetAvailable(isBluetoothEnabled);
  }

  public void setAudioOutput(WebRtcAudioOutput output) {
    speakerToggle.setAudioOutput(output);
  }

  public void setRemoteVideoEnabled(boolean isRemoteVideoEnabled) {
    boolean wasRemoteVideoEnabled = remoteRenderContainer.getVisibility() == View.VISIBLE;

    shouldFadeControls = isRemoteVideoEnabled;

    if (isRemoteVideoEnabled) {
      remoteRenderContainer.setVisibility(View.VISIBLE);
    } else {
      remoteRenderContainer.setVisibility(View.GONE);
    }

    if (isRemoteVideoEnabled && !wasRemoteVideoEnabled) {
      fadeInControls();
    } else if (!isRemoteVideoEnabled && wasRemoteVideoEnabled) {
      fadeInControls();
      cancelFadeOut();
    }
  }

  public void setLocalRenderer(@Nullable TextureViewRenderer surfaceViewRenderer) {
    if (localRenderer == surfaceViewRenderer) {
      return;
    }

    localRenderer = surfaceViewRenderer;

    if (surfaceViewRenderer == null) {
      setRenderer(largeLocalRenderContainer, null);
      setRenderer(smallLocalRenderContainer, null);
    } else {
      localRenderer.setMirror(cameraDirection == CameraState.Direction.FRONT);
      localRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
    }
  }

  public void setRemoteRenderer(@Nullable TextureViewRenderer remoteRenderer) {
    setRenderer(remoteRenderContainer, remoteRenderer);
  }

  public void setLocalRenderState(WebRtcLocalRenderState localRenderState) {

    videoToggle.setChecked(localRenderState != WebRtcLocalRenderState.GONE, false);

    switch (localRenderState) {
      case GONE:
        localRenderPipFrame.setVisibility(View.GONE);
        largeLocalRenderContainer.setVisibility(View.GONE);
        cameraDirectionToggle.animate().setDuration(0).alpha(0f);
        setRenderer(largeLocalRenderContainer, null);
        setRenderer(smallLocalRenderContainer, null);
        break;
      case LARGE:
        localRenderPipFrame.setVisibility(View.GONE);
        largeLocalRenderContainer.setVisibility(View.VISIBLE);
        cameraDirectionToggle.animate().setDuration(0).alpha(0f);
        if (largeLocalRenderContainer.getChildCount() == 0) {
          setRenderer(largeLocalRenderContainer, localRenderer);
        }
        break;
      case SMALL:
        localRenderPipFrame.setVisibility(View.VISIBLE);
        largeLocalRenderContainer.setVisibility(View.GONE);
        cameraDirectionToggle.animate()
                             .setDuration(450)
                             .alpha(1f);

        if (smallLocalRenderContainer.getChildCount() == 0) {
          setRenderer(smallLocalRenderContainer, localRenderer);
        }
    }
  }

  public void setCameraDirection(@NonNull CameraState.Direction cameraDirection) {
    this.cameraDirection = cameraDirection;

    if (localRenderer != null) {
      localRenderer.setMirror(cameraDirection == CameraState.Direction.FRONT);
    }
  }

  public void setRecipient(@NonNull Recipient recipient) {
    if (recipient.getId() == recipientId) {
      return;
    }

    recipientId = recipient.getId();
    recipientName.setText(recipient.getDisplayName(getContext()));
    avatar.setFallbackPhotoProvider(FALLBACK_PHOTO_PROVIDER);
    avatar.setAvatar(GlideApp.with(this), recipient, false);
    AvatarUtil.loadBlurredIconIntoViewBackground(recipient, this);

    setRecipientCallCard(recipient);
  }

  public void showCallCard(boolean showCallCard) {
    avatarCard.setVisibility(showCallCard ? VISIBLE : GONE);
    avatar.setVisibility(showCallCard ? GONE : VISIBLE);
  }

  public void setStatus(@NonNull String status) {
    this.status.setText(status);
  }

  public void setStatusFromHangupType(@NonNull HangupMessage.Type hangupType) {
    switch (hangupType) {
      case NORMAL:
        status.setText(R.string.RedPhone_ending_call);
        break;
      case ACCEPTED:
        status.setText(R.string.WebRtcCallActivity__answered_on_a_linked_device);
        break;
      case DECLINED:
        status.setText(R.string.WebRtcCallActivity__declined_on_a_linked_device);
        break;
      case BUSY:
        status.setText(R.string.WebRtcCallActivity__busy_on_a_linked_device);
        break;
      default:
        throw new IllegalStateException("Unknown hangup type: " + hangupType);
    }
  }

  public void setWebRtcControls(WebRtcControls webRtcControls) {
    answerWithVoiceGroup.setVisibility(View.GONE);

    switch (webRtcControls) {
      case NONE:
        ongoingCallButtons.setVisibility(View.GONE);
        incomingCallButtons.setVisibility(View.GONE);
        setTopViewsVisibility(View.GONE);
        break;
      case INCOMING_VIDEO:
        answerWithVoiceGroup.setVisibility(View.VISIBLE);
        setTopViewsVisibility(View.VISIBLE);
        ongoingCallButtons.setVisibility(View.GONE);
        incomingCallButtons.setVisibility(View.VISIBLE);
        status.setText(R.string.WebRtcCallView__signal_video_call);
        accept.setImageDrawable(AppCompatResources.getDrawable(getContext(), R.drawable.webrtc_call_screen_answer_with_video));
        break;
      case INCOMING_AUDIO:
        setTopViewsVisibility(View.VISIBLE);
        ongoingCallButtons.setVisibility(View.GONE);
        incomingCallButtons.setVisibility(View.VISIBLE);
        status.setText(R.string.WebRtcCallView__signal_voice_call);
        accept.setImageDrawable(AppCompatResources.getDrawable(getContext(), R.drawable.webrtc_call_screen_answer));
        break;
      case RINGING:
        setTopViewsVisibility(View.VISIBLE);
        incomingCallButtons.setVisibility(View.GONE);
        ongoingCallButtons.setVisibility(View.VISIBLE);
        break;
      case CONNECTED:
        setTopViewsVisibility(View.VISIBLE);
        incomingCallButtons.setVisibility(View.GONE);
        ongoingCallButtons.setVisibility(View.VISIBLE);

        post(() -> {
          pictureInPictureGestureHelper.setVerticalBoundaries(status.getBottom(), speakerToggle.getTop());
        });
    }
  }

  private void setTopViewsVisibility(int visibility) {
    topViews.setVisibility(visibility);
    topGradient.setVisibility(visibility);
  }

  public @NonNull View getVideoTooltipTarget() {
    return videoToggle;
  }

  private void toggleControls() {
    if (shouldFadeControls) {
      if (status.getVisibility() == VISIBLE) {
        fadeOutControls();
      } else {
        fadeInControls();
      }
    }
  }

  private void fadeOutControls() {
    fadeControls(ConstraintSet.GONE);
    controlsListener.onControlsFadeOut();
    pictureInPictureGestureHelper.clearVerticalBoundaries();
  }

  private void fadeInControls() {
    fadeControls(ConstraintSet.VISIBLE);
    pictureInPictureGestureHelper.setVerticalBoundaries(status.getBottom(), speakerToggle.getTop());

    scheduleFadeOut();
  }

  private void fadeControls(int visibility) {
    Transition transition = new AutoTransition().setDuration(TRANSITION_DURATION_MILLIS);

    TransitionManager.beginDelayedTransition(parent, transition);

    ConstraintSet constraintSet = new ConstraintSet();
    constraintSet.clone(parent);

    constraintSet.setVisibility(R.id.call_screen_in_call_buttons, visibility);
    constraintSet.setVisibility(R.id.call_screen_top_views, visibility);

    constraintSet.applyTo(parent);

    topGradient.animate()
               .alpha(visibility == ConstraintSet.VISIBLE ? 1f : 0f)
               .setDuration(TRANSITION_DURATION_MILLIS)
               .start();
  }

  private void scheduleFadeOut() {
    cancelFadeOut();
    shouldFadeControls = true;

    if (getHandler() == null) return;
    getHandler().postDelayed(fadeOutRunnable, FADE_OUT_DELAY);
  }

  private void cancelFadeOut() {
    shouldFadeControls = false;

    if (getHandler() == null) return;
    getHandler().removeCallbacks(fadeOutRunnable);
  }

  private static void runIfNonNull(@Nullable ControlsListener controlsListener, @NonNull Consumer<ControlsListener> controlsListenerConsumer) {
    if (controlsListener != null) {
      controlsListenerConsumer.accept(controlsListener);
    }
  }

  private static void setRenderer(@NonNull ViewGroup container, @Nullable View renderer) {
    if (renderer == null) {
      container.removeAllViews();
      return;
    }

    ViewParent parent = renderer.getParent();
    if (parent != null && parent != container) {
      ((ViewGroup) parent).removeAllViews();
    }

    if (parent == container) {
      return;
    }

    container.addView(renderer);
  }

  private void setRecipientCallCard(@NonNull Recipient recipient) {
    ContactPhoto         contactPhoto  = recipient.getContactPhoto();
    FallbackContactPhoto fallbackPhoto = recipient.getFallbackContactPhoto(FALLBACK_PHOTO_PROVIDER);

    GlideApp.with(this).load(contactPhoto)
                       .fallback(fallbackPhoto.asCallCard(getContext()))
                       .error(fallbackPhoto.asCallCard(getContext()))
                       .diskCacheStrategy(DiskCacheStrategy.ALL)
                       .into(this.avatarCard);

    if (contactPhoto == null) this.avatarCard.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    else                      this.avatarCard.setScaleType(ImageView.ScaleType.CENTER_CROP);

    this.avatarCard.setBackgroundColor(recipient.getColor().toActionBarColor(getContext()));
  }

  private static final class FallbackPhotoProvider extends Recipient.FallbackPhotoProvider {
    @Override
    public @NonNull FallbackContactPhoto getPhotoForRecipientWithoutName() {
      return new ResourceContactPhoto(R.drawable.ic_profile_outline_120);
    }
  }

  public interface ControlsListener {
    void onControlsFadeOut();
    void onAudioOutputChanged(@NonNull WebRtcAudioOutput audioOutput);
    void onVideoChanged(boolean isVideoEnabled);
    void onMicChanged(boolean isMicEnabled);
    void onCameraDirectionChanged();
    void onEndCallPressed();
    void onDenyCallPressed();
    void onAcceptCallWithVoiceOnlyPressed();
    void onAcceptCallPressed();
    void onDownCaretPressed();
  }
}
