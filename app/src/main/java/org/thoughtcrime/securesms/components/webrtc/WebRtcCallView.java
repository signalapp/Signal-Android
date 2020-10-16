package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.constraintlayout.widget.Guideline;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.animation.ResizeAnimation;
import org.thoughtcrime.securesms.components.AccessibleToggleButton;
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.mediasend.SimpleAnimationListener;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.thoughtcrime.securesms.util.BlurTransformation;
import org.thoughtcrime.securesms.util.SetUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.webrtc.RendererCommon;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WebRtcCallView extends FrameLayout {

  private static final long TRANSITION_DURATION_MILLIS          = 250;
  private static final int  SMALL_ONGOING_CALL_BUTTON_MARGIN_DP = 8;
  private static final int  LARGE_ONGOING_CALL_BUTTON_MARGIN_DP = 16;

  public static final int FADE_OUT_DELAY      = 5000;
  public static final int PIP_RESIZE_DURATION = 300;
  public static final int CONTROLS_HEIGHT     = 98;

  private WebRtcAudioOutputToggleButton audioToggle;
  private AccessibleToggleButton        videoToggle;
  private AccessibleToggleButton        micToggle;
  private ViewGroup                     smallLocalRenderFrame;
  private TextureViewRenderer           smallLocalRender;
  private View                          largeLocalRenderFrame;
  private TextureViewRenderer           largeLocalRender;
  private View                          largeLocalRenderNoVideo;
  private ImageView                     largeLocalRenderNoVideoAvatar;
  private TextView                      recipientName;
  private TextView                      status;
  private ConstraintLayout              parent;
  private ConstraintLayout              participantsParent;
  private ControlsListener              controlsListener;
  private RecipientId                   recipientId;
  private ImageView                     answer;
  private ImageView                     cameraDirectionToggle;
  private PictureInPictureGestureHelper pictureInPictureGestureHelper;
  private ImageView                     hangup;
  private View                          answerWithAudio;
  private View                          answerWithAudioLabel;
  private View                          footerGradient;
  private View                          startCallControls;
  private ViewPager2                    callParticipantsPager;
  private RecyclerView                  callParticipantsRecycler;
  private Toolbar                       toolbar;
  private int                           pagerBottomMarginDp;
  private boolean                       controlsVisible = true;

  private WebRtcCallParticipantsPagerAdapter    pagerAdapter;
  private WebRtcCallParticipantsRecyclerAdapter recyclerAdapter;

  private final Set<View> incomingCallViews    = new HashSet<>();
  private final Set<View> topViews             = new HashSet<>();
  private final Set<View> visibleViewSet       = new HashSet<>();
  private final Set<View> adjustableMarginsSet = new HashSet<>();

  private       WebRtcControls controls        = WebRtcControls.NONE;
  private final Runnable       fadeOutRunnable = () -> {
    if (isAttachedToWindow() && controls.isFadeOutEnabled()) fadeOutControls();
  };

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

    audioToggle                   = findViewById(R.id.call_screen_speaker_toggle);
    videoToggle                   = findViewById(R.id.call_screen_video_toggle);
    micToggle                     = findViewById(R.id.call_screen_audio_mic_toggle);
    smallLocalRenderFrame         = findViewById(R.id.call_screen_pip);
    smallLocalRender              = findViewById(R.id.call_screen_small_local_renderer);
    largeLocalRenderFrame         = findViewById(R.id.call_screen_large_local_renderer_frame);
    largeLocalRender              = findViewById(R.id.call_screen_large_local_renderer);
    largeLocalRenderNoVideo       = findViewById(R.id.call_screen_large_local_video_off);
    largeLocalRenderNoVideoAvatar = findViewById(R.id.call_screen_large_local_video_off_avatar);
    recipientName                 = findViewById(R.id.call_screen_recipient_name);
    status                        = findViewById(R.id.call_screen_status);
    parent                        = findViewById(R.id.call_screen);
    participantsParent            = findViewById(R.id.call_screen_participants_parent);
    answer                        = findViewById(R.id.call_screen_answer_call);
    cameraDirectionToggle         = findViewById(R.id.call_screen_camera_direction_toggle);
    hangup                        = findViewById(R.id.call_screen_end_call);
    answerWithAudio               = findViewById(R.id.call_screen_answer_with_audio);
    answerWithAudioLabel          = findViewById(R.id.call_screen_answer_with_audio_label);
    footerGradient                = findViewById(R.id.call_screen_footer_gradient);
    startCallControls             = findViewById(R.id.call_screen_start_call_controls);
    callParticipantsPager         = findViewById(R.id.call_screen_participants_pager);
    callParticipantsRecycler      = findViewById(R.id.call_screen_participants_recycler);
    toolbar                       = findViewById(R.id.call_screen_toolbar);

    View      topGradient            = findViewById(R.id.call_screen_header_gradient);
    View      decline                = findViewById(R.id.call_screen_decline_call);
    View      answerLabel            = findViewById(R.id.call_screen_answer_call_label);
    View      declineLabel           = findViewById(R.id.call_screen_decline_call_label);
    Guideline statusBarGuideline     = findViewById(R.id.call_screen_status_bar_guideline);
    View      startCall              = findViewById(R.id.call_screen_start_call_start_call);
    View      cancelStartCall        = findViewById(R.id.call_screen_start_call_cancel);

    callParticipantsPager.setPageTransformer(new MarginPageTransformer(ViewUtil.dpToPx(4)));

    pagerAdapter    = new WebRtcCallParticipantsPagerAdapter(this::toggleControls);
    recyclerAdapter = new WebRtcCallParticipantsRecyclerAdapter();

    callParticipantsPager.setAdapter(pagerAdapter);
    callParticipantsRecycler.setAdapter(recyclerAdapter);

    callParticipantsPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
      @Override
      public void onPageSelected(int position) {
        runIfNonNull(controlsListener, listener -> listener.onPageChanged(position == 0 ? CallParticipantsState.SelectedPage.GRID : CallParticipantsState.SelectedPage.FOCUSED));
      }
    });

    topViews.add(toolbar);
    topViews.add(topGradient);

    incomingCallViews.add(answer);
    incomingCallViews.add(answerLabel);
    incomingCallViews.add(decline);
    incomingCallViews.add(declineLabel);
    incomingCallViews.add(footerGradient);

    adjustableMarginsSet.add(micToggle);
    adjustableMarginsSet.add(cameraDirectionToggle);
    adjustableMarginsSet.add(videoToggle);
    adjustableMarginsSet.add(audioToggle);

    audioToggle.setOnAudioOutputChangedListener(outputMode -> {
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

    answer.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onAcceptCallPressed));
    answerWithAudio.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onAcceptCallWithVoiceOnlyPressed));

    pictureInPictureGestureHelper = PictureInPictureGestureHelper.applyTo(smallLocalRenderFrame);

    startCall.setOnClickListener(v -> runIfNonNull(controlsListener, listener -> listener.onStartCall(videoToggle.isChecked())));
    cancelStartCall.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onCancelStartCall));

    ColorMatrix greyScaleMatrix = new ColorMatrix();
    greyScaleMatrix.setSaturation(0);
    largeLocalRenderNoVideoAvatar.setAlpha(0.6f);
    largeLocalRenderNoVideoAvatar.setColorFilter(new ColorMatrixColorFilter(greyScaleMatrix));

    int statusBarHeight = ViewUtil.getStatusBarHeight(this);
    statusBarGuideline.setGuidelineBegin(statusBarHeight);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();

    if (controls.isFadeOutEnabled()) {
      scheduleFadeOut();
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    cancelFadeOut();
  }

  public void setControlsListener(@Nullable ControlsListener controlsListener) {
    this.controlsListener = controlsListener;
  }

  public void setMicEnabled(boolean isMicEnabled) {
    micToggle.setChecked(isMicEnabled, false);
  }

  public void updateCallParticipants(@NonNull CallParticipantsState state) {
    List<WebRtcCallParticipantsPage> pages = new ArrayList<>(2);

    if (!state.getGridParticipants().isEmpty()) {
      pages.add(WebRtcCallParticipantsPage.forMultipleParticipants(state.getGridParticipants(), state.isInPipMode()));
    }

    if (state.getFocusedParticipant() != null && state.getAllRemoteParticipants().size() > 1) {
      pages.add(WebRtcCallParticipantsPage.forSingleParticipant(state.getFocusedParticipant(), state.isInPipMode()));
    }

    pagerAdapter.submitList(pages);
    recyclerAdapter.submitList(state.getListParticipants());
    updateLocalCallParticipant(state.getLocalRenderState(), state.getLocalParticipant());

    if (state.isLargeVideoGroup()) {
      layoutParticipantsForLargeCount();
    } else {
      layoutParticipantsForSmallCount();
    }
  }

  public void updateLocalCallParticipant(@NonNull WebRtcLocalRenderState state, @NonNull CallParticipant localCallParticipant) {
    smallLocalRender.setMirror(localCallParticipant.getCameraDirection() == CameraState.Direction.FRONT);
    largeLocalRender.setMirror(localCallParticipant.getCameraDirection() == CameraState.Direction.FRONT);

    smallLocalRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
    largeLocalRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);

    if (localCallParticipant.getVideoSink().getEglBase() != null) {
      smallLocalRender.init(localCallParticipant.getVideoSink().getEglBase());
      largeLocalRender.init(localCallParticipant.getVideoSink().getEglBase());
    }

    switch (state) {
      case GONE:
        largeLocalRender.attachBroadcastVideoSink(null);
        largeLocalRenderFrame.setVisibility(View.GONE);
        smallLocalRender.attachBroadcastVideoSink(null);
        smallLocalRenderFrame.setVisibility(View.GONE);

        videoToggle.setChecked(false, false);
        break;
      case SMALL_RECTANGLE:
        smallLocalRenderFrame.setVisibility(View.VISIBLE);
        smallLocalRender.attachBroadcastVideoSink(localCallParticipant.getVideoSink());
        animatePipToRectangle();

        largeLocalRender.attachBroadcastVideoSink(null);
        largeLocalRenderFrame.setVisibility(View.GONE);

        videoToggle.setChecked(true, false);
        break;
      case SMALL_SQUARE:
        smallLocalRenderFrame.setVisibility(View.VISIBLE);
        smallLocalRender.attachBroadcastVideoSink(localCallParticipant.getVideoSink());
        animatePipToSquare();

        largeLocalRender.attachBroadcastVideoSink(null);
        largeLocalRenderFrame.setVisibility(View.GONE);

        videoToggle.setChecked(true, false);
        break;
      case LARGE:
        largeLocalRender.attachBroadcastVideoSink(localCallParticipant.getVideoSink());
        largeLocalRenderFrame.setVisibility(View.VISIBLE);

        largeLocalRenderNoVideo.setVisibility(View.GONE);
        largeLocalRenderNoVideoAvatar.setVisibility(View.GONE);

        smallLocalRender.attachBroadcastVideoSink(null);
        smallLocalRenderFrame.setVisibility(View.GONE);

        videoToggle.setChecked(true, false);
        break;
      case LARGE_NO_VIDEO:
        largeLocalRender.attachBroadcastVideoSink(null);
        largeLocalRenderFrame.setVisibility(View.VISIBLE);

        largeLocalRenderNoVideo.setVisibility(View.VISIBLE);
        largeLocalRenderNoVideoAvatar.setVisibility(View.VISIBLE);

        GlideApp.with(getContext().getApplicationContext())
                .load(new ProfileContactPhoto(localCallParticipant.getRecipient(), localCallParticipant.getRecipient().getProfileAvatar()))
                .transform(new CenterCrop(), new BlurTransformation(getContext(), 0.25f, BlurTransformation.MAX_RADIUS))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(largeLocalRenderNoVideoAvatar);

        smallLocalRender.attachBroadcastVideoSink(null);
        smallLocalRenderFrame.setVisibility(View.GONE);

        videoToggle.setChecked(false, false);
        break;
    }
  }

  public void setRecipient(@NonNull Recipient recipient) {
    if (recipient.getId() == recipientId) {
      return;
    }

    recipientId = recipient.getId();

    if (recipient.isGroup()) {
      recipientName.setText(R.string.WebRtcCallView__group_call);
      if (toolbar.getMenu().findItem(R.id.menu_group_call_participants_list) == null) {
        toolbar.inflateMenu(R.menu.group_call);
        toolbar.setOnMenuItemClickListener(unused -> showParticipantsList());
      }
    } else {
      recipientName.setText(recipient.getDisplayName(getContext()));
    }
  }

  public void setStatus(@NonNull String status) {
    this.status.setText(status);
  }

  public void setStatusFromHangupType(@NonNull HangupMessage.Type hangupType) {
    switch (hangupType) {
      case NORMAL:
      case NEED_PERMISSION:
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

  public void setWebRtcControls(@NonNull WebRtcControls webRtcControls) {
    Set<View> lastVisibleSet = new HashSet<>(visibleViewSet);

    visibleViewSet.clear();

    if (webRtcControls.displayStartCallControls()) {
      visibleViewSet.add(footerGradient);
      visibleViewSet.add(startCallControls);
    }

    if (webRtcControls.displayTopViews()) {
      visibleViewSet.addAll(topViews);
    }

    if (webRtcControls.displayIncomingCallButtons()) {
      visibleViewSet.addAll(incomingCallViews);

      status.setText(R.string.WebRtcCallView__signal_voice_call);
      answer.setImageDrawable(AppCompatResources.getDrawable(getContext(), R.drawable.webrtc_call_screen_answer));
    }

    if (webRtcControls.displayAnswerWithAudio()) {
      visibleViewSet.add(answerWithAudio);
      visibleViewSet.add(answerWithAudioLabel);

      status.setText(R.string.WebRtcCallView__signal_video_call);
      answer.setImageDrawable(AppCompatResources.getDrawable(getContext(), R.drawable.webrtc_call_screen_answer_with_video));
    }

    if (webRtcControls.displayAudioToggle()) {
      visibleViewSet.add(audioToggle);

      audioToggle.setControlAvailability(webRtcControls.enableHandsetInAudioToggle(),
                                         webRtcControls.enableHeadsetInAudioToggle());

      audioToggle.setAudioOutput(webRtcControls.getAudioOutput(), false);
    }

    if (webRtcControls.displayCameraToggle()) {
      visibleViewSet.add(cameraDirectionToggle);
    }

    if (webRtcControls.displayEndCall()) {
      visibleViewSet.add(hangup);
      visibleViewSet.add(footerGradient);
    }

    if (webRtcControls.displayMuteAudio()) {
      visibleViewSet.add(micToggle);
    }

    if (webRtcControls.displayVideoToggle()) {
      visibleViewSet.add(videoToggle);
    }

    if (webRtcControls.displaySmallOngoingCallButtons()) {
      updateButtonStateForSmallButtons();
    } else if (webRtcControls.displayLargeOngoingCallButtons()) {
      updateButtonStateForLargeButtons();
    }

    if (webRtcControls.displayRemoteVideoRecycler()) {
      callParticipantsRecycler.setVisibility(View.VISIBLE);
    } else {
      callParticipantsRecycler.setVisibility(View.GONE);
    }

    if (webRtcControls.isFadeOutEnabled()) {
      if (!controls.isFadeOutEnabled()) {
        scheduleFadeOut();
      }
    } else {
      cancelFadeOut();
    }

    controls = webRtcControls;

    if (!visibleViewSet.equals(lastVisibleSet) || !controls.isFadeOutEnabled()) {
      fadeInNewUiState(lastVisibleSet, webRtcControls.displaySmallOngoingCallButtons());
      post(() -> pictureInPictureGestureHelper.setVerticalBoundaries(toolbar.getBottom(), videoToggle.getTop()));
    }
  }

  public @NonNull View getVideoTooltipTarget() {
    return videoToggle;
  }

  private void animatePipToRectangle() {
    ResizeAnimation animation = new ResizeAnimation(smallLocalRenderFrame, ViewUtil.dpToPx(90), ViewUtil.dpToPx(160));
    animation.setDuration(PIP_RESIZE_DURATION);
    animation.setAnimationListener(new SimpleAnimationListener() {
      @Override
      public void onAnimationEnd(Animation animation) {
        pictureInPictureGestureHelper.enableCorners();
        pictureInPictureGestureHelper.adjustPip();
      }
    });

    smallLocalRenderFrame.startAnimation(animation);
  }

  private void animatePipToSquare() {
    pictureInPictureGestureHelper.lockToBottomEnd();

    pictureInPictureGestureHelper.performAfterFling(() -> {
      ResizeAnimation animation = new ResizeAnimation(smallLocalRenderFrame, ViewUtil.dpToPx(72), ViewUtil.dpToPx(72));
      animation.setDuration(PIP_RESIZE_DURATION);
      animation.setAnimationListener(new SimpleAnimationListener() {
        @Override
        public void onAnimationEnd(Animation animation) {
          pictureInPictureGestureHelper.adjustPip();
        }
      });

      smallLocalRenderFrame.startAnimation(animation);
    });
  }

  private void toggleControls() {
    if (controls.isFadeOutEnabled() && toolbar.getVisibility() == VISIBLE) {
      fadeOutControls();
    } else {
      fadeInControls();
    }
  }

  private void fadeOutControls() {
    fadeControls(ConstraintSet.GONE);
    controlsListener.onControlsFadeOut();
    pictureInPictureGestureHelper.clearVerticalBoundaries();
  }

  private void fadeInControls() {
    fadeControls(ConstraintSet.VISIBLE);
    pictureInPictureGestureHelper.setVerticalBoundaries(toolbar.getBottom(), videoToggle.getTop());

    scheduleFadeOut();
  }

  private void layoutParticipantsForSmallCount() {
    pagerBottomMarginDp = 0;

    layoutParticipants();
  }

  private void layoutParticipantsForLargeCount() {
    pagerBottomMarginDp = 104;

    layoutParticipants();
  }

  private int withControlsHeight(int margin) {
    if (margin == 0) {
      return 0;
    }

    return controlsVisible ? margin + CONTROLS_HEIGHT : margin;
  }

  private void layoutParticipants() {
    Transition transition = new AutoTransition().setDuration(TRANSITION_DURATION_MILLIS);

    TransitionManager.beginDelayedTransition(participantsParent, transition);

    ConstraintSet constraintSet = new ConstraintSet();
    constraintSet.clone(participantsParent);

    constraintSet.setMargin(R.id.call_screen_participants_pager, ConstraintSet.BOTTOM, ViewUtil.dpToPx(withControlsHeight(pagerBottomMarginDp)));
    constraintSet.applyTo(participantsParent);
  }

  private void fadeControls(int visibility) {
    controlsVisible = visibility == VISIBLE;

    Transition transition = new AutoTransition().setOrdering(TransitionSet.ORDERING_TOGETHER)
                                                .setDuration(TRANSITION_DURATION_MILLIS);

    TransitionManager.endTransitions(parent);
    TransitionManager.beginDelayedTransition(parent, transition);

    ConstraintSet constraintSet = new ConstraintSet();
    constraintSet.clone(parent);

    for (View view : visibleViewSet) {
      constraintSet.setVisibility(view.getId(), visibility);
    }

    constraintSet.applyTo(parent);

    layoutParticipants();
  }

  private void fadeInNewUiState(@NonNull Set<View> previouslyVisibleViewSet, boolean useSmallMargins) {
    Transition transition = new AutoTransition().setDuration(TRANSITION_DURATION_MILLIS);

    TransitionManager.endTransitions(parent);
    TransitionManager.beginDelayedTransition(parent, transition);

    ConstraintSet constraintSet = new ConstraintSet();
    constraintSet.clone(parent);

    for (View view : SetUtil.difference(previouslyVisibleViewSet, visibleViewSet)) {
      constraintSet.setVisibility(view.getId(), ConstraintSet.GONE);
    }

    for (View view : visibleViewSet) {
      constraintSet.setVisibility(view.getId(), ConstraintSet.VISIBLE);

      if (adjustableMarginsSet.contains(view)) {
        constraintSet.setMargin(view.getId(),
                                ConstraintSet.END,
                                ViewUtil.dpToPx(useSmallMargins ? SMALL_ONGOING_CALL_BUTTON_MARGIN_DP
                                                                : LARGE_ONGOING_CALL_BUTTON_MARGIN_DP));
      }
    }

    constraintSet.applyTo(parent);
  }

  private void scheduleFadeOut() {
    cancelFadeOut();

    if (getHandler() == null) return;
    getHandler().postDelayed(fadeOutRunnable, FADE_OUT_DELAY);
  }

  private void cancelFadeOut() {
    if (getHandler() == null) return;
    getHandler().removeCallbacks(fadeOutRunnable);
  }

  private static void runIfNonNull(@Nullable ControlsListener controlsListener, @NonNull Consumer<ControlsListener> controlsListenerConsumer) {
    if (controlsListener != null) {
      controlsListenerConsumer.accept(controlsListener);
    }
  }

  private void updateButtonStateForLargeButtons() {
    cameraDirectionToggle.setImageResource(R.drawable.webrtc_call_screen_camera_toggle);
    hangup.setImageResource(R.drawable.webrtc_call_screen_hangup);
    micToggle.setBackgroundResource(R.drawable.webrtc_call_screen_mic_toggle);
    videoToggle.setBackgroundResource(R.drawable.webrtc_call_screen_video_toggle);
    audioToggle.setImageResource(R.drawable.webrtc_call_screen_speaker_toggle);
  }

  private void updateButtonStateForSmallButtons() {
    cameraDirectionToggle.setImageResource(R.drawable.webrtc_call_screen_camera_toggle_small);
    hangup.setImageResource(R.drawable.webrtc_call_screen_hangup_small);
    micToggle.setBackgroundResource(R.drawable.webrtc_call_screen_mic_toggle_small);
    videoToggle.setBackgroundResource(R.drawable.webrtc_call_screen_video_toggle_small);
    audioToggle.setImageResource(R.drawable.webrtc_call_screen_speaker_toggle_small);
  }

  private boolean showParticipantsList() {
    controlsListener.onShowParticipantsList();
    return true;
  }

  public interface ControlsListener {
    void onStartCall(boolean isVideoCall);
    void onCancelStartCall();
    void onControlsFadeOut();
    void onAudioOutputChanged(@NonNull WebRtcAudioOutput audioOutput);
    void onVideoChanged(boolean isVideoEnabled);
    void onMicChanged(boolean isMicEnabled);
    void onCameraDirectionChanged();
    void onEndCallPressed();
    void onDenyCallPressed();
    void onAcceptCallWithVoiceOnlyPressed();
    void onAcceptCallPressed();
    void onShowParticipantsList();
    void onPageChanged(@NonNull CallParticipantsState.SelectedPage page);
  }
}
