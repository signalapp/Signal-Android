package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MenuItem;
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
import com.google.android.material.button.MaterialButton;
import com.google.common.collect.Sets;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.animation.ResizeAnimation;
import org.thoughtcrime.securesms.components.AccessibleToggleButton;
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.mediasend.SimpleAnimationListener;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.thoughtcrime.securesms.util.BlurTransformation;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.SetUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.Stub;
import org.thoughtcrime.securesms.webrtc.CallParticipantsViewState;
import org.webrtc.RendererCommon;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WebRtcCallView extends ConstraintLayout {

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
  private CallParticipantView           smallLocalRender;
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
  private MaterialButton                startCall;
  private TextView                      participantCount;
  private Stub<FrameLayout>             groupCallSpeakerHint;
  private Stub<View>                    groupCallFullStub;
  private View                          errorButton;
  private int                           pagerBottomMarginDp;
  private boolean                       controlsVisible = true;
  private Guideline                     topFoldGuideline;
  private Guideline                     callScreenTopFoldGuideline;
  private View                          foldParticipantCountWrapper;
  private TextView                      foldParticipantCount;

  private WebRtcCallParticipantsPagerAdapter    pagerAdapter;
  private WebRtcCallParticipantsRecyclerAdapter recyclerAdapter;
  private PictureInPictureExpansionHelper       pictureInPictureExpansionHelper;

  private final Set<View> incomingCallViews    = new HashSet<>();
  private final Set<View> topViews             = new HashSet<>();
  private final Set<View> visibleViewSet       = new HashSet<>();
  private final Set<View> adjustableMarginsSet = new HashSet<>();
  private final Set<View> rotatableControls    = new HashSet<>();

  private       WebRtcControls controls        = WebRtcControls.NONE;
  private final Runnable       fadeOutRunnable = () -> {
    if (isAttachedToWindow() && controls.isFadeOutEnabled()) fadeOutControls();
  };

  public WebRtcCallView(@NonNull Context context) {
    this(context, null);
  }

  public WebRtcCallView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);

    inflate(context, R.layout.webrtc_call_view, this);
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
    startCall                     = findViewById(R.id.call_screen_start_call_start_call);
    errorButton                   = findViewById(R.id.call_screen_error_cancel);
    groupCallSpeakerHint          = new Stub<>(findViewById(R.id.call_screen_group_call_speaker_hint));
    groupCallFullStub             = new Stub<>(findViewById(R.id.group_call_call_full_view));
    topFoldGuideline              = findViewById(R.id.fold_top_guideline);
    callScreenTopFoldGuideline    = findViewById(R.id.fold_top_call_screen_guideline);
    foldParticipantCountWrapper   = findViewById(R.id.fold_show_participants_menu_counter_wrapper);
    foldParticipantCount          = findViewById(R.id.fold_show_participants_menu_counter);

    View      topGradient            = findViewById(R.id.call_screen_header_gradient);
    View      decline                = findViewById(R.id.call_screen_decline_call);
    View      answerLabel            = findViewById(R.id.call_screen_answer_call_label);
    View      declineLabel           = findViewById(R.id.call_screen_decline_call_label);
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

    pictureInPictureGestureHelper   = PictureInPictureGestureHelper.applyTo(smallLocalRenderFrame);
    pictureInPictureExpansionHelper = new PictureInPictureExpansionHelper();

    smallLocalRenderFrame.setOnClickListener(v -> {
      if (controlsListener != null) {
        controlsListener.onLocalPictureInPictureClicked();
      }
    });

    startCall.setOnClickListener(v -> {
      if (controlsListener != null) {
        startCall.setEnabled(false);
        controlsListener.onStartCall(videoToggle.isChecked());
      }
    });
    cancelStartCall.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onCancelStartCall));

    ColorMatrix greyScaleMatrix = new ColorMatrix();
    greyScaleMatrix.setSaturation(0);
    largeLocalRenderNoVideoAvatar.setAlpha(0.6f);
    largeLocalRenderNoVideoAvatar.setColorFilter(new ColorMatrixColorFilter(greyScaleMatrix));

    errorButton.setOnClickListener(v -> {
      if (controlsListener != null) {
        controlsListener.onCancelStartCall();
      }
    });

    rotatableControls.add(hangup);
    rotatableControls.add(answer);
    rotatableControls.add(answerWithAudio);
    rotatableControls.add(audioToggle);
    rotatableControls.add(micToggle);
    rotatableControls.add(videoToggle);
    rotatableControls.add(cameraDirectionToggle);
    rotatableControls.add(decline);
    rotatableControls.add(smallLocalRender.findViewById(R.id.call_participant_mic_muted));
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();

    if (controls.isFadeOutEnabled()) {
      scheduleFadeOut();
    }
  }

  @Override
  protected boolean fitSystemWindows(Rect insets) {
    Guideline statusBarGuideline     = findViewById(R.id.call_screen_status_bar_guideline);
    Guideline navigationBarGuideline = findViewById(R.id.call_screen_navigation_bar_guideline);

    statusBarGuideline.setGuidelineBegin(insets.top);
    navigationBarGuideline.setGuidelineEnd(insets.bottom);

    return true;
  }

  @Override
  public void onWindowSystemUiVisibilityChanged(int visible) {
    if ((visible & SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
      if (controls.adjustForFold()) {
        pictureInPictureGestureHelper.clearVerticalBoundaries();
        pictureInPictureGestureHelper.setTopVerticalBoundary(toolbar.getTop());
      } else {
        pictureInPictureGestureHelper.setTopVerticalBoundary(toolbar.getBottom());
        pictureInPictureGestureHelper.setBottomVerticalBoundary(videoToggle.getTop());
      }
    } else {
      pictureInPictureGestureHelper.clearVerticalBoundaries();
    }

    pictureInPictureGestureHelper.adjustPip();
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    cancelFadeOut();
  }

  public void rotateControls(int degrees) {
    for (View view : rotatableControls) {
      view.animate().rotation(degrees);
    }
  }

  public void setControlsListener(@Nullable ControlsListener controlsListener) {
    this.controlsListener = controlsListener;
  }

  public void setMicEnabled(boolean isMicEnabled) {
    micToggle.setChecked(isMicEnabled, false);
  }

  public void updateCallParticipants(@NonNull CallParticipantsViewState callParticipantsViewState) {
    CallParticipantsState            state              = callParticipantsViewState.getCallParticipantsState();
    boolean                          isPortrait         = callParticipantsViewState.isPortrait();
    boolean                          isLandscapeEnabled = callParticipantsViewState.isLandscapeEnabled();
    List<WebRtcCallParticipantsPage> pages              = new ArrayList<>(2);

    if (!state.getGridParticipants().isEmpty()) {
      pages.add(WebRtcCallParticipantsPage.forMultipleParticipants(state.getGridParticipants(), state.getFocusedParticipant(), state.isInPipMode(), isPortrait, isLandscapeEnabled));
    }

    if (state.getFocusedParticipant() != CallParticipant.EMPTY && state.getAllRemoteParticipants().size() > 1) {
      pages.add(WebRtcCallParticipantsPage.forSingleParticipant(state.getFocusedParticipant(), state.isInPipMode(), isPortrait, isLandscapeEnabled));
    }

    if ((state.getGroupCallState().isNotIdle() && state.getRemoteDevicesCount().orElse(0) > 0) || state.getGroupCallState().isConnected()) {
      recipientName.setText(state.getRemoteParticipantsDescription(getContext()));
    } else if (state.getGroupCallState().isNotIdle()) {
      recipientName.setText(getContext().getString(R.string.WebRtcCallView__s_group_call, Recipient.resolved(recipientId).getDisplayName(getContext())));
    }

    if (state.getGroupCallState().isNotIdle() && participantCount != null) {
      String  text    = state.getParticipantCount()
                             .mapToObj(String::valueOf).orElse("\u2014");
      boolean enabled = state.getParticipantCount().isPresent();

      participantCount.setText(text);
      participantCount.setEnabled(enabled);

      foldParticipantCount.setText(text);
      foldParticipantCount.setEnabled(enabled);
    }

    pagerAdapter.submitList(pages);
    recyclerAdapter.submitList(state.getListParticipants());

    boolean displaySmallSelfPipInLandscape = !isPortrait && isLandscapeEnabled;

    updateLocalCallParticipant(state.getLocalRenderState(), state.getLocalParticipant(), state.getFocusedParticipant(), displaySmallSelfPipInLandscape);

    if (state.isLargeVideoGroup() && !state.isInPipMode() && !state.isFolded()) {
      layoutParticipantsForLargeCount();
    } else {
      layoutParticipantsForSmallCount();
    }
  }

  public void updateLocalCallParticipant(@NonNull WebRtcLocalRenderState state,
                                         @NonNull CallParticipant localCallParticipant,
                                         @NonNull CallParticipant focusedParticipant,
                                         boolean displaySmallSelfPipInLandscape)
  {
    largeLocalRender.setMirror(localCallParticipant.getCameraDirection() == CameraState.Direction.FRONT);

    smallLocalRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
    largeLocalRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);

    if (localCallParticipant.getVideoSink().getEglBase() != null) {
      largeLocalRender.init(localCallParticipant.getVideoSink().getEglBase());
    }

    videoToggle.setChecked(localCallParticipant.isVideoEnabled(), false);
    smallLocalRender.setRenderInPip(true);

    if (state == WebRtcLocalRenderState.EXPANDED) {
      expandPip(localCallParticipant, focusedParticipant);
      return;
    } else if ((state == WebRtcLocalRenderState.SMALL_RECTANGLE || state == WebRtcLocalRenderState.GONE) && pictureInPictureExpansionHelper.isExpandedOrExpanding()) {
      shrinkPip(localCallParticipant);
      return;
    } else {
      smallLocalRender.setCallParticipant(localCallParticipant);
      smallLocalRender.setMirror(localCallParticipant.getCameraDirection() == CameraState.Direction.FRONT);
    }

    switch (state) {
      case GONE:
        largeLocalRender.attachBroadcastVideoSink(null);
        largeLocalRenderFrame.setVisibility(View.GONE);
        smallLocalRenderFrame.setVisibility(View.GONE);

        break;
      case SMALL_RECTANGLE:
        smallLocalRenderFrame.setVisibility(View.VISIBLE);
        animatePipToLargeRectangle(displaySmallSelfPipInLandscape);

        largeLocalRender.attachBroadcastVideoSink(null);
        largeLocalRenderFrame.setVisibility(View.GONE);
        break;
      case SMALLER_RECTANGLE:
        smallLocalRenderFrame.setVisibility(View.VISIBLE);
        animatePipToSmallRectangle();

        largeLocalRender.attachBroadcastVideoSink(null);
        largeLocalRenderFrame.setVisibility(View.GONE);
        break;
      case LARGE:
        largeLocalRender.attachBroadcastVideoSink(localCallParticipant.getVideoSink());
        largeLocalRenderFrame.setVisibility(View.VISIBLE);

        largeLocalRenderNoVideo.setVisibility(View.GONE);
        largeLocalRenderNoVideoAvatar.setVisibility(View.GONE);

        smallLocalRenderFrame.setVisibility(View.GONE);
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

        smallLocalRenderFrame.setVisibility(View.GONE);
        break;
    }
  }

  public void setRecipient(@NonNull Recipient recipient) {
    if (recipient.getId() == recipientId) {
      return;
    }

    recipientId = recipient.getId();

    if (recipient.isGroup()) {
      if (toolbar.getMenu().findItem(R.id.menu_group_call_participants_list) == null) {
        toolbar.inflateMenu(R.menu.group_call);

        View showParticipants = toolbar.getMenu().findItem(R.id.menu_group_call_participants_list).getActionView();
        showParticipants.setAlpha(0);
        showParticipants.setOnClickListener(unused -> showParticipantsList());
        foldParticipantCountWrapper.setOnClickListener(unused -> showParticipantsList());

        participantCount = showParticipants.findViewById(R.id.show_participants_menu_counter);
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

  public void setStatusFromGroupCallState(@NonNull WebRtcViewModel.GroupCallState groupCallState) {
    switch (groupCallState) {
      case DISCONNECTED:
        status.setText(R.string.WebRtcCallView__disconnected);
        break;
      case RECONNECTING:
        status.setText(R.string.WebRtcCallView__reconnecting);
        break;
      case CONNECTED_AND_JOINING:
        status.setText(R.string.WebRtcCallView__joining);
        break;
      case CONNECTING:
      case CONNECTED_AND_JOINED:
      case CONNECTED:
        status.setText("");
        break;
    }
  }

  public void setWebRtcControls(@NonNull WebRtcControls webRtcControls) {
    Set<View> lastVisibleSet = new HashSet<>(visibleViewSet);

    visibleViewSet.clear();

    if (webRtcControls.adjustForFold()) {
      topFoldGuideline.setGuidelineEnd(webRtcControls.getFold());
      callScreenTopFoldGuideline.setGuidelineEnd(webRtcControls.getFold());

      if (webRtcControls.displayGroupMembersButton()) {
        visibleViewSet.add(foldParticipantCountWrapper);
      }
    } else {
      topFoldGuideline.setGuidelineEnd(0);
      callScreenTopFoldGuideline.setGuidelineEnd(0);
    }

    setShowParticipantsState(webRtcControls.adjustForFold());

    if (webRtcControls.displayStartCallControls()) {
      visibleViewSet.add(footerGradient);
      visibleViewSet.add(startCallControls);

      startCall.setText(webRtcControls.getStartCallButtonText());
      startCall.setEnabled(webRtcControls.isStartCallEnabled());
    }

    if (webRtcControls.displayErrorControls()) {
      visibleViewSet.add(footerGradient);
      visibleViewSet.add(errorButton);
    }

    if (webRtcControls.displayGroupCallFull()) {
      groupCallFullStub.get().setVisibility(View.VISIBLE);
      ((TextView) groupCallFullStub.get().findViewById(R.id.group_call_call_full_message)).setText(webRtcControls.getGroupCallFullMessage(getContext()));
    } else if (groupCallFullStub.resolved()) {
      groupCallFullStub.get().setVisibility(View.GONE);
    }

    MenuItem item = toolbar.getMenu().findItem(R.id.menu_group_call_participants_list);
    if (item != null) {
      item.setVisible(webRtcControls.displayGroupMembersButton());
      item.setEnabled(webRtcControls.displayGroupMembersButton());
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

      if (controlsListener != null) {
        controlsListener.showSystemUI();
      }
    }

    if (webRtcControls.adjustForFold() && webRtcControls.isFadeOutEnabled() && !controls.adjustForFold()) {
      scheduleFadeOut();
    }

    controls = webRtcControls;

    if (!controls.isFadeOutEnabled()) {
      controlsVisible = true;
    }

    if (!visibleViewSet.equals(lastVisibleSet) ||
        !controls.isFadeOutEnabled()) {

      if (controlsListener != null) {
        controlsListener.showSystemUI();
      }

      fadeInNewUiState(lastVisibleSet, webRtcControls.displaySmallOngoingCallButtons());
    }

    onWindowSystemUiVisibilityChanged(getWindowSystemUiVisibility());
  }

  public @NonNull View getVideoTooltipTarget() {
    return videoToggle;
  }

  public void showSpeakerViewHint() {
    groupCallSpeakerHint.get().setVisibility(View.VISIBLE);
  }

  public void hideSpeakerViewHint() {
    if (groupCallSpeakerHint.resolved()) {
      groupCallSpeakerHint.get().setVisibility(View.GONE);
    }
  }

  private void setShowParticipantsState(boolean isFolded) {
    MenuItem item = toolbar.getMenu().findItem(R.id.menu_group_call_participants_list);

    if (item != null) {
      View showParticipants = item.getActionView();
      showParticipants.animate().alpha(isFolded ? 0f : 1f);
      showParticipants.setClickable(!isFolded);
      foldParticipantCountWrapper.animate().alpha(isFolded ? 1f : 0f);
      foldParticipantCount.setClickable(isFolded);
    }
  }

  private void expandPip(@NonNull CallParticipant localCallParticipant, @NonNull CallParticipant focusedParticipant) {
    pictureInPictureExpansionHelper.expand(smallLocalRenderFrame, new PictureInPictureExpansionHelper.Callback() {
      @Override
      public void onAnimationWillStart() {
        largeLocalRender.attachBroadcastVideoSink(localCallParticipant.getVideoSink());
      }

      @Override
      public void onPictureInPictureExpanded() {
        largeLocalRenderFrame.setVisibility(View.VISIBLE);
        largeLocalRenderNoVideo.setVisibility(View.GONE);
        largeLocalRenderNoVideoAvatar.setVisibility(View.GONE);
      }

      @Override
      public void onPictureInPictureNotVisible() {
        smallLocalRender.setCallParticipant(focusedParticipant);
        smallLocalRender.setMirror(false);
      }

      @Override
      public void onAnimationHasFinished() {
        pictureInPictureGestureHelper.adjustPip();
      }
    });
  }

  private void shrinkPip(@NonNull CallParticipant localCallParticipant) {
    pictureInPictureExpansionHelper.shrink(smallLocalRenderFrame, new PictureInPictureExpansionHelper.Callback() {
      @Override
      public void onAnimationWillStart() {
      }

      @Override
      public void onPictureInPictureExpanded() {
        largeLocalRenderFrame.setVisibility(View.GONE);
        largeLocalRender.attachBroadcastVideoSink(null);
      }

      @Override
      public void onPictureInPictureNotVisible() {
        smallLocalRender.setCallParticipant(localCallParticipant);
        smallLocalRender.setMirror(localCallParticipant.getCameraDirection() == CameraState.Direction.FRONT);

        if (!localCallParticipant.isVideoEnabled()) {
          smallLocalRenderFrame.setVisibility(View.GONE);
        }
      }

      @Override
      public void onAnimationHasFinished() {
        pictureInPictureGestureHelper.adjustPip();
      }
    });
  }

  private void animatePipToLargeRectangle(boolean isLandscape) {
    final Point dimens;
    if (isLandscape) {
      dimens = new Point(ViewUtil.dpToPx(160), ViewUtil.dpToPx(90));
    } else {
      dimens = new Point(ViewUtil.dpToPx(90), ViewUtil.dpToPx(160));
    }

    ResizeAnimation animation = new ResizeAnimation(smallLocalRenderFrame, dimens.x, dimens.y);
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

  private void animatePipToSmallRectangle() {
    pictureInPictureGestureHelper.lockToBottomEnd();

    pictureInPictureGestureHelper.performAfterFling(() -> {
      ResizeAnimation animation = new ResizeAnimation(smallLocalRenderFrame, ViewUtil.dpToPx(54), ViewUtil.dpToPx(72));
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
  }

  private void fadeInControls() {
    fadeControls(ConstraintSet.VISIBLE);

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

    return (controlsVisible || controls.adjustForFold()) ? margin + CONTROLS_HEIGHT : margin;
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

    if (controlsListener != null) {
      if (controlsVisible) {
        controlsListener.showSystemUI();
      } else {
        controlsListener.hideSystemUI();
      }
    }

    TransitionManager.beginDelayedTransition(parent, transition);

    ConstraintSet constraintSet = new ConstraintSet();
    constraintSet.clone(parent);

    for (View view : controlsToFade()) {
      constraintSet.setVisibility(view.getId(), visibility);
    }

    adjustParticipantsRecycler(constraintSet);

    constraintSet.applyTo(parent);

    layoutParticipants();
  }

  private Set<View> controlsToFade() {
    if (controls.adjustForFold()) {
      return Sets.intersection(topViews, visibleViewSet);
    } else {
      return visibleViewSet;
    }
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

    adjustParticipantsRecycler(constraintSet);

    constraintSet.applyTo(parent);
  }

  private void adjustParticipantsRecycler(@NonNull ConstraintSet constraintSet) {
    if (controlsVisible || controls.adjustForFold()) {
      constraintSet.connect(R.id.call_screen_participants_recycler, ConstraintSet.BOTTOM, R.id.call_screen_video_toggle, ConstraintSet.TOP);
    } else {
      constraintSet.connect(R.id.call_screen_participants_recycler, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
    }

    constraintSet.setHorizontalBias(R.id.call_screen_participants_recycler, controls.adjustForFold() ? 0.5f : 1f);
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

  private static <T> void runIfNonNull(@Nullable T listener, @NonNull Consumer<T> listenerConsumer) {
    if (listener != null) {
      listenerConsumer.accept(listener);
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

  public void switchToSpeakerView() {
    if (pagerAdapter.getItemCount() > 0) {
      callParticipantsPager.setCurrentItem(pagerAdapter.getItemCount() - 1, false);
    }
  }

  public interface ControlsListener {
    void onStartCall(boolean isVideoCall);
    void onCancelStartCall();
    void onControlsFadeOut();
    void showSystemUI();
    void hideSystemUI();
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
    void onLocalPictureInPictureClicked();
  }
}
