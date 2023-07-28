package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.constraintlayout.widget.Guideline;
import androidx.core.util.Consumer;
import androidx.core.view.ViewKt;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
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

import org.signal.core.util.DimensionUnit;
import org.signal.core.util.SetUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.animation.ResizeAnimation;
import org.thoughtcrime.securesms.components.AccessibleToggleButton;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.mediasend.SimpleAnimationListener;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.thoughtcrime.securesms.util.BlurTransformation;
import org.thoughtcrime.securesms.util.ThrottledDebouncer;
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

  private static final String TAG = Log.tag(WebRtcCallView.class);

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
  private TextView                      incomingRingStatus;
  private ConstraintLayout              parent;
  private ConstraintLayout              participantsParent;
  private ControlsListener              controlsListener;
  private RecipientId                   recipientId;
  private ImageView                     answer;
  private TextView                      answerWithoutVideoLabel;
  private ImageView                     cameraDirectionToggle;
  private AccessibleToggleButton        ringToggle;
  private PictureInPictureGestureHelper pictureInPictureGestureHelper;
  private ImageView                     hangup;
  private View                          answerWithoutVideo;
  private View                          topGradient;
  private View                          footerGradient;
  private View                          startCallControls;
  private ViewPager2                    callParticipantsPager;
  private RecyclerView                  callParticipantsRecycler;
  private ConstraintLayout              largeHeader;
  private MaterialButton                startCall;
  private Stub<FrameLayout>             groupCallSpeakerHint;
  private Stub<View>                    groupCallFullStub;
  private View                          errorButton;
  private int                           pagerBottomMarginDp;
  private boolean                       controlsVisible = true;
  private Guideline                     showParticipantsGuideline;
  private Guideline                     topFoldGuideline;
  private Guideline                     callScreenTopFoldGuideline;
  private AvatarImageView               largeHeaderAvatar;
  private Guideline                     statusBarGuideline;
  private Guideline                     navigationBarGuideline;
  private int                           navBarBottomInset;
  private View                          fullScreenShade;
  private Toolbar                       collapsedToolbar;
  private Toolbar                       headerToolbar;

  private WebRtcCallParticipantsPagerAdapter    pagerAdapter;
  private WebRtcCallParticipantsRecyclerAdapter recyclerAdapter;
  private PictureInPictureExpansionHelper       pictureInPictureExpansionHelper;

  private final Set<View> incomingCallViews    = new HashSet<>();
  private final Set<View> topViews             = new HashSet<>();
  private final Set<View> visibleViewSet       = new HashSet<>();
  private final Set<View> allTimeVisibleViews  = new HashSet<>();
  private final Set<View> adjustableMarginsSet = new HashSet<>();
  private final Set<View> rotatableControls    = new HashSet<>();


  private final ThrottledDebouncer throttledDebouncer = new ThrottledDebouncer(TRANSITION_DURATION_MILLIS);
  private       WebRtcControls     controls           = WebRtcControls.NONE;
  private final Runnable           fadeOutRunnable    = () -> {
    if (isAttachedToWindow() && controls.isFadeOutEnabled()) fadeOutControls();
  };

  private CallParticipantsViewState lastState;
  private ContactPhoto              previousLocalAvatar;

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
    incomingRingStatus            = findViewById(R.id.call_screen_incoming_ring_status);
    parent                        = findViewById(R.id.call_screen);
    participantsParent            = findViewById(R.id.call_screen_participants_parent);
    answer                        = findViewById(R.id.call_screen_answer_call);
    answerWithoutVideoLabel       = findViewById(R.id.call_screen_answer_without_video_label);
    cameraDirectionToggle         = findViewById(R.id.call_screen_camera_direction_toggle);
    ringToggle                    = findViewById(R.id.call_screen_audio_ring_toggle);
    hangup                        = findViewById(R.id.call_screen_end_call);
    answerWithoutVideo            = findViewById(R.id.call_screen_answer_without_video);
    topGradient                   = findViewById(R.id.call_screen_header_gradient);
    footerGradient                = findViewById(R.id.call_screen_footer_gradient);
    startCallControls             = findViewById(R.id.call_screen_start_call_controls);
    callParticipantsPager         = findViewById(R.id.call_screen_participants_pager);
    callParticipantsRecycler      = findViewById(R.id.call_screen_participants_recycler);
    largeHeader                   = findViewById(R.id.call_screen_header);
    startCall                     = findViewById(R.id.call_screen_start_call_start_call);
    errorButton                   = findViewById(R.id.call_screen_error_cancel);
    groupCallSpeakerHint          = new Stub<>(findViewById(R.id.call_screen_group_call_speaker_hint));
    groupCallFullStub             = new Stub<>(findViewById(R.id.group_call_call_full_view));
    showParticipantsGuideline     = findViewById(R.id.call_screen_show_participants_guideline);
    topFoldGuideline              = findViewById(R.id.fold_top_guideline);
    callScreenTopFoldGuideline    = findViewById(R.id.fold_top_call_screen_guideline);
    largeHeaderAvatar             = findViewById(R.id.call_screen_header_avatar);
    statusBarGuideline            = findViewById(R.id.call_screen_status_bar_guideline);
    navigationBarGuideline        = findViewById(R.id.call_screen_navigation_bar_guideline);
    fullScreenShade               = findViewById(R.id.call_screen_full_shade);
    collapsedToolbar              = findViewById(R.id.webrtc_call_view_toolbar_text);
    headerToolbar                 = findViewById(R.id.webrtc_call_view_toolbar_no_text);

    View      decline                = findViewById(R.id.call_screen_decline_call);
    View      answerLabel            = findViewById(R.id.call_screen_answer_call_label);
    View      declineLabel           = findViewById(R.id.call_screen_decline_call_label);

    callParticipantsPager.setPageTransformer(new MarginPageTransformer(ViewUtil.dpToPx(4)));

    pagerAdapter    = new WebRtcCallParticipantsPagerAdapter(this::toggleControls);
    recyclerAdapter = new WebRtcCallParticipantsRecyclerAdapter();

    callParticipantsPager.setAdapter(pagerAdapter);
    callParticipantsRecycler.setAdapter(recyclerAdapter);

    DefaultItemAnimator animator = new DefaultItemAnimator();
    animator.setSupportsChangeAnimations(false);
    callParticipantsRecycler.setItemAnimator(animator);

    callParticipantsPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
      @Override
      public void onPageSelected(int position) {
        runIfNonNull(controlsListener, listener -> listener.onPageChanged(position == 0 ? CallParticipantsState.SelectedPage.GRID : CallParticipantsState.SelectedPage.FOCUSED));
      }
    });

    topViews.add(collapsedToolbar);
    topViews.add(headerToolbar);
    topViews.add(largeHeader);
    topViews.add(topGradient);

    incomingCallViews.add(answer);
    incomingCallViews.add(answerLabel);
    incomingCallViews.add(decline);
    incomingCallViews.add(declineLabel);
    incomingCallViews.add(footerGradient);
    incomingCallViews.add(incomingRingStatus);

    adjustableMarginsSet.add(micToggle);
    adjustableMarginsSet.add(cameraDirectionToggle);
    adjustableMarginsSet.add(videoToggle);
    adjustableMarginsSet.add(audioToggle);

    audioToggle.setOnAudioOutputChangedListener(webRtcAudioDevice -> {
      runIfNonNull(controlsListener, listener ->
      {
        if (Build.VERSION.SDK_INT >= 31) {
          if (webRtcAudioDevice.getDeviceId() != null) {
            listener.onAudioOutputChanged31(webRtcAudioDevice);
          } else {
            Log.e(TAG, "Attempted to change audio output to null device ID.");
          }
        } else {
          listener.onAudioOutputChanged(webRtcAudioDevice.getWebRtcAudioOutput());
        }
      });
    });

    videoToggle.setOnCheckedChangeListener((v, isOn) -> {
      runIfNonNull(controlsListener, listener -> listener.onVideoChanged(isOn));
    });

    micToggle.setOnCheckedChangeListener((v, isOn) -> {
      runIfNonNull(controlsListener, listener -> listener.onMicChanged(isOn));
    });

    ringToggle.setOnCheckedChangeListener((v, isOn) -> {
      runIfNonNull(controlsListener, listener -> listener.onRingGroupChanged(isOn, ringToggle.isActivated()));
    });

    cameraDirectionToggle.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onCameraDirectionChanged));

    hangup.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onEndCallPressed));
    decline.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onDenyCallPressed));

    answer.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onAcceptCallPressed));
    answerWithoutVideo.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onAcceptCallWithVoiceOnlyPressed));

    pictureInPictureGestureHelper   = PictureInPictureGestureHelper.applyTo(smallLocalRenderFrame);
    pictureInPictureExpansionHelper = new PictureInPictureExpansionHelper();

    smallLocalRenderFrame.setOnClickListener(v -> {
      if (controlsListener != null) {
        controlsListener.onLocalPictureInPictureClicked();
      }
    });

    View smallLocalAudioIndicator = smallLocalRender.findViewById(R.id.call_participant_audio_indicator);
    int  audioIndicatorMargin     = (int) DimensionUnit.DP.toPixels(8f);
    ViewUtil.setLeftMargin(smallLocalAudioIndicator, audioIndicatorMargin);
    ViewUtil.setBottomMargin(smallLocalAudioIndicator, audioIndicatorMargin);

    startCall.setOnClickListener(v -> {
      if (controlsListener != null) {
        startCall.setEnabled(false);
        controlsListener.onStartCall(videoToggle.isChecked());
      }
    });

    ColorMatrix greyScaleMatrix = new ColorMatrix();
    greyScaleMatrix.setSaturation(0);
    largeLocalRenderNoVideoAvatar.setAlpha(0.6f);
    largeLocalRenderNoVideoAvatar.setColorFilter(new ColorMatrixColorFilter(greyScaleMatrix));

    errorButton.setOnClickListener(v -> {
      if (controlsListener != null) {
        controlsListener.onCancelStartCall();
      }
    });

    collapsedToolbar.setNavigationOnClickListener(unused -> {
      if (controlsListener != null) {
        controlsListener.onNavigateUpClicked();
      }
    });

    collapsedToolbar.setOnMenuItemClickListener(item -> {
      if (item.getItemId() == R.id.action_info && controlsListener != null) {
        controlsListener.onCallInfoClicked();
        return true;
      }

      return false;
    });

    headerToolbar.setNavigationOnClickListener(unused -> {
      if (controlsListener != null) {
        controlsListener.onNavigateUpClicked();
      }
    });

    headerToolbar.setOnMenuItemClickListener(item -> {
      if (item.getItemId() == R.id.action_info && controlsListener != null) {
        controlsListener.onCallInfoClicked();
        return true;
      }

      return false;
    });

    rotatableControls.add(hangup);
    rotatableControls.add(answer);
    rotatableControls.add(answerWithoutVideo);
    rotatableControls.add(audioToggle);
    rotatableControls.add(micToggle);
    rotatableControls.add(videoToggle);
    rotatableControls.add(cameraDirectionToggle);
    rotatableControls.add(decline);
    rotatableControls.add(smallLocalAudioIndicator);
    rotatableControls.add(ringToggle);
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
    if (insets.top != 0) {
      statusBarGuideline.setGuidelineBegin(insets.top);
    }
    navigationBarGuideline.setGuidelineEnd(insets.bottom);

    return true;
  }

  @Override
  public WindowInsets onApplyWindowInsets(WindowInsets insets) {
    navBarBottomInset = WindowInsetsCompat.toWindowInsetsCompat(insets).getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;

    if (lastState != null) {
      updateCallParticipants(lastState);
    }

    return super.onApplyWindowInsets(insets);
  }

  @Override
  public void onWindowSystemUiVisibilityChanged(int visible) {
    if ((visible & SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
      if (controls.adjustForFold()) {
        pictureInPictureGestureHelper.clearVerticalBoundaries();
        pictureInPictureGestureHelper.setTopVerticalBoundary(getPipBarrier().getTop());
      } else {
        pictureInPictureGestureHelper.setTopVerticalBoundary(getPipBarrier().getBottom());
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

  public void maybeDismissAudioPicker() {
    audioToggle.hidePicker();
  }

  public void setMicEnabled(boolean isMicEnabled) {
    micToggle.setChecked(isMicEnabled, false);
  }

  public void updateCallParticipants(@NonNull CallParticipantsViewState callParticipantsViewState) {
    lastState = callParticipantsViewState;

    CallParticipantsState            state              = callParticipantsViewState.getCallParticipantsState();
    boolean                          isPortrait         = callParticipantsViewState.isPortrait();
    boolean                          isLandscapeEnabled = callParticipantsViewState.isLandscapeEnabled();
    List<WebRtcCallParticipantsPage> pages              = new ArrayList<>(2);

    if (!state.getGridParticipants().isEmpty()) {
      pages.add(WebRtcCallParticipantsPage.forMultipleParticipants(state.getGridParticipants(), state.getFocusedParticipant(), state.isInPipMode(), isPortrait, isLandscapeEnabled, state.isIncomingRing(), navBarBottomInset));
    }

    if (state.getFocusedParticipant() != CallParticipant.EMPTY && state.getAllRemoteParticipants().size() > 1) {
      pages.add(WebRtcCallParticipantsPage.forSingleParticipant(state.getFocusedParticipant(), state.isInPipMode(), isPortrait, isLandscapeEnabled));
    }

    if (state.getGroupCallState().isNotIdle()) {
      if (state.getCallState() == WebRtcViewModel.State.CALL_PRE_JOIN) {
        setStatus(state.getPreJoinGroupDescription(getContext()));
      } else if (state.getCallState() == WebRtcViewModel.State.CALL_CONNECTED && state.isInOutgoingRingingMode()) {
        setStatus(state.getOutgoingRingingGroupDescription(getContext()));
      } else if (state.getGroupCallState().isRinging()) {
        setStatus(state.getIncomingRingingGroupDescription(getContext()));
      }
    }

    if (state.getGroupCallState().isNotIdle()) {
      boolean enabled = state.getParticipantCount().isPresent();
      collapsedToolbar.getMenu().getItem(0).setVisible(enabled);
      headerToolbar.getMenu().getItem(0).setVisible(enabled);
    } else {
      collapsedToolbar.getMenu().getItem(0).setVisible(false);
      headerToolbar.getMenu().getItem(0).setVisible(false);
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

    localCallParticipant.getVideoSink().getLockableEglBase().performWithValidEglBase(eglBase -> {
      largeLocalRender.init(eglBase);
    });


    videoToggle.setChecked(localCallParticipant.isVideoEnabled(), false);
    smallLocalRender.setRenderInPip(true);

    if (state == WebRtcLocalRenderState.EXPANDED) {
      expandPip(localCallParticipant, focusedParticipant);
      smallLocalRender.setCallParticipant(focusedParticipant);
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

        ContactPhoto localAvatar = new ProfileContactPhoto(localCallParticipant.getRecipient());

        if (!localAvatar.equals(previousLocalAvatar)) {
          previousLocalAvatar = localAvatar;
          GlideApp.with(getContext().getApplicationContext())
                  .load(localAvatar)
                  .transform(new CenterCrop(), new BlurTransformation(getContext(), 0.25f, BlurTransformation.MAX_RADIUS))
                  .diskCacheStrategy(DiskCacheStrategy.ALL)
                  .into(largeLocalRenderNoVideoAvatar);
        }

        smallLocalRenderFrame.setVisibility(View.GONE);
        break;
    }
  }

  public void setRecipient(@NonNull Recipient recipient) {
    if (recipient.getId() == recipientId) {
      return;
    }

    recipientId = recipient.getId();
    largeHeaderAvatar.setRecipient(recipient, false);
    collapsedToolbar.setTitle(recipient.getDisplayName(getContext()));
    recipientName.setText(recipient.getDisplayName(getContext()));
  }

  public void setStatus(@Nullable String status) {
    this.status.setText(status);
    collapsedToolbar.setSubtitle(status);
  }

  private void setStatus(@StringRes int statusRes) {
    setStatus(getContext().getString(statusRes));
  }

  private @NonNull View getPipBarrier() {
    if (collapsedToolbar.isEnabled()) {
      return collapsedToolbar;
    } else {
      return largeHeader;
    }
  }

  public void setStatusFromHangupType(@NonNull HangupMessage.Type hangupType) {
    switch (hangupType) {
      case NORMAL:
      case NEED_PERMISSION:
        setStatus(R.string.RedPhone_ending_call);
        break;
      case ACCEPTED:
        setStatus(R.string.WebRtcCallActivity__answered_on_a_linked_device);
        break;
      case DECLINED:
        setStatus(R.string.WebRtcCallActivity__declined_on_a_linked_device);
        break;
      case BUSY:
        setStatus(R.string.WebRtcCallActivity__busy_on_a_linked_device);
        break;
      default:
        throw new IllegalStateException("Unknown hangup type: " + hangupType);
    }
  }

  public void setStatusFromGroupCallState(@NonNull WebRtcViewModel.GroupCallState groupCallState) {
    switch (groupCallState) {
      case DISCONNECTED:
        setStatus(R.string.WebRtcCallView__disconnected);
        break;
      case RECONNECTING:
        setStatus(R.string.WebRtcCallView__reconnecting);
        break;
      case CONNECTED_AND_JOINING:
        setStatus(R.string.WebRtcCallView__joining);
        break;
      case CONNECTING:
      case CONNECTED_AND_JOINED:
      case CONNECTED:
        setStatus("");
        break;
    }
  }

  public void setWebRtcControls(@NonNull WebRtcControls webRtcControls) {
    Set<View> lastVisibleSet = new HashSet<>(visibleViewSet);

    visibleViewSet.clear();

    if (webRtcControls.adjustForFold()) {
      showParticipantsGuideline.setGuidelineBegin(-1);
      showParticipantsGuideline.setGuidelineEnd(webRtcControls.getFold());
      topFoldGuideline.setGuidelineEnd(webRtcControls.getFold());
      callScreenTopFoldGuideline.setGuidelineEnd(webRtcControls.getFold());
    } else {
      showParticipantsGuideline.setGuidelineBegin(((LayoutParams) statusBarGuideline.getLayoutParams()).guideBegin);
      showParticipantsGuideline.setGuidelineEnd(-1);
      topFoldGuideline.setGuidelineEnd(0);
      callScreenTopFoldGuideline.setGuidelineEnd(0);
    }

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

    if (webRtcControls.displayTopViews()) {
      visibleViewSet.addAll(topViews);
    }

    if (webRtcControls.displayIncomingCallButtons()) {
      visibleViewSet.addAll(incomingCallViews);

      incomingRingStatus.setText(webRtcControls.displayAnswerWithoutVideo() ? R.string.WebRtcCallView__signal_video_call: R.string.WebRtcCallView__signal_call);

      answer.setImageDrawable(AppCompatResources.getDrawable(getContext(), R.drawable.webrtc_call_screen_answer));
    }

    if (webRtcControls.displayAnswerWithoutVideo()) {
      visibleViewSet.add(answerWithoutVideo);
      visibleViewSet.add(answerWithoutVideoLabel);

      answer.setImageDrawable(AppCompatResources.getDrawable(getContext(), R.drawable.webrtc_call_screen_answer_with_video));
    }

    if (!webRtcControls.displayIncomingCallButtons()){
      incomingRingStatus.setVisibility(GONE);
    }

    if (webRtcControls.displayAudioToggle()) {
      visibleViewSet.add(audioToggle);

      audioToggle.setControlAvailability(webRtcControls.isEarpieceAvailableForAudioToggle(),
                                         webRtcControls.isBluetoothHeadsetAvailableForAudioToggle(),
                                         webRtcControls.isWiredHeadsetAvailableForAudioToggle());

      audioToggle.updateAudioOutputState(webRtcControls.getAudioOutput());
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

    if (webRtcControls.showFullScreenShade()) {
      fullScreenShade.setVisibility(VISIBLE);
      visibleViewSet.remove(topGradient);
      visibleViewSet.remove(footerGradient);
    } else {
      fullScreenShade.setVisibility(GONE);
    }

    if (webRtcControls.displayRingToggle()) {
      visibleViewSet.add(ringToggle);
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

    boolean forceUpdate = webRtcControls.adjustForFold() && !controls.adjustForFold();
    controls = webRtcControls;

    if (!controls.isFadeOutEnabled()) {
      controlsVisible = true;
    }

    allTimeVisibleViews.addAll(visibleViewSet);

    if (!visibleViewSet.equals(lastVisibleSet) ||
        !controls.isFadeOutEnabled() ||
        (webRtcControls.showSmallHeader() && largeHeaderAvatar.getVisibility() == View.VISIBLE) ||
        (!webRtcControls.showSmallHeader() && largeHeaderAvatar.getVisibility() == View.GONE) ||
        forceUpdate)
    {

      if (controlsListener != null) {
        controlsListener.showSystemUI();
      }

      throttledDebouncer.publish(() -> fadeInNewUiState(webRtcControls.displaySmallOngoingCallButtons(), webRtcControls.showSmallHeader()));
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

    SimpleAnimationListener animationListener = new SimpleAnimationListener() {
      @Override
      public void onAnimationEnd(Animation animation) {
        pictureInPictureGestureHelper.enableCorners();
        pictureInPictureGestureHelper.adjustPip();
      }
    };

    ViewGroup.LayoutParams layoutParams = smallLocalRenderFrame.getLayoutParams();
    if (layoutParams.width == dimens.x && layoutParams.height == dimens.y) {
      animationListener.onAnimationEnd(null);
      return;
    }

    ResizeAnimation animation = new ResizeAnimation(smallLocalRenderFrame, dimens.x, dimens.y);
    animation.setDuration(PIP_RESIZE_DURATION);
    animation.setAnimationListener(animationListener);

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
    if (controls.isFadeOutEnabled() && largeHeader.getVisibility() == VISIBLE) {
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
    int desiredMargin = ViewUtil.dpToPx(withControlsHeight(pagerBottomMarginDp));
    if (ViewKt.getMarginBottom(callParticipantsPager) == desiredMargin) {
      return;
    }

    Transition transition = new AutoTransition().setDuration(TRANSITION_DURATION_MILLIS);

    TransitionManager.beginDelayedTransition(participantsParent, transition);

    ConstraintSet constraintSet = new ConstraintSet();
    constraintSet.clone(participantsParent);

    constraintSet.setMargin(R.id.call_screen_participants_pager, ConstraintSet.BOTTOM, desiredMargin);
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

  private void fadeInNewUiState(boolean useSmallMargins, boolean showSmallHeader) {
    Transition transition = new AutoTransition().setDuration(TRANSITION_DURATION_MILLIS);

    TransitionManager.beginDelayedTransition(parent, transition);

    ConstraintSet constraintSet = new ConstraintSet();
    constraintSet.clone(parent);

    for (View view : SetUtil.difference(allTimeVisibleViews, visibleViewSet)) {
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

    if (showSmallHeader) {
      collapsedToolbar.setEnabled(true);
      collapsedToolbar.setAlpha(1);
      headerToolbar.setEnabled(false);
      headerToolbar.setAlpha(0);
      largeHeader.setEnabled(false);
      largeHeader.setAlpha(0);
    } else {
      collapsedToolbar.setEnabled(false);
      collapsedToolbar.setAlpha(0);
      headerToolbar.setEnabled(true);
      headerToolbar.setAlpha(1);
      largeHeader.setEnabled(true);
      largeHeader.setAlpha(1);
    }
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
    ringToggle.setBackgroundResource(R.drawable.webrtc_call_screen_ring_toggle);
  }

  private void updateButtonStateForSmallButtons() {
    cameraDirectionToggle.setImageResource(R.drawable.webrtc_call_screen_camera_toggle_small);
    hangup.setImageResource(R.drawable.webrtc_call_screen_hangup_small);
    micToggle.setBackgroundResource(R.drawable.webrtc_call_screen_mic_toggle_small);
    videoToggle.setBackgroundResource(R.drawable.webrtc_call_screen_video_toggle_small);
    audioToggle.setImageResource(R.drawable.webrtc_call_screen_speaker_toggle_small);
    ringToggle.setBackgroundResource(R.drawable.webrtc_call_screen_ring_toggle_small);
  }

  public void switchToSpeakerView() {
    if (pagerAdapter.getItemCount() > 0) {
      callParticipantsPager.setCurrentItem(pagerAdapter.getItemCount() - 1, false);
    }
  }

  public void setRingGroup(boolean shouldRingGroup) {
    ringToggle.setChecked(shouldRingGroup, false);
  }

  public void enableRingGroup(boolean enabled) {
    ringToggle.setActivated(enabled);
  }

  public interface ControlsListener {
    void onStartCall(boolean isVideoCall);
    void onCancelStartCall();
    void onControlsFadeOut();
    void showSystemUI();
    void hideSystemUI();
    void onAudioOutputChanged(@NonNull WebRtcAudioOutput audioOutput);
    @RequiresApi(31)
    void onAudioOutputChanged31(@NonNull WebRtcAudioDevice audioOutput);
    void onVideoChanged(boolean isVideoEnabled);
    void onMicChanged(boolean isMicEnabled);
    void onCameraDirectionChanged();
    void onEndCallPressed();
    void onDenyCallPressed();
    void onAcceptCallWithVoiceOnlyPressed();
    void onAcceptCallPressed();
    void onPageChanged(@NonNull CallParticipantsState.SelectedPage page);
    void onLocalPictureInPictureClicked();
    void onRingGroupChanged(boolean ringGroup, boolean ringingAllowed);
    void onCallInfoClicked();
    void onNavigateUpClicked();
  }
}
