package org.thoughtcrime.securesms.components.webrtc;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.WebRtcCallActivity;
import org.thoughtcrime.securesms.components.AccessibleToggleButton;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.webrtc.CallParticipantsViewState;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;

public class PigeonWebRtcCallView extends FrameLayout implements View.OnFocusChangeListener{

    private static final String TAG = Log.tag(PigeonWebRtcCallView.class);
    private TextView participants;
    private TextView startCall;
    private TextView volumeToggle;
    private WebRtcCallMenuText speakerToggle;
    private WebRtcCallMenuText micToggle;
    private TextView answer;
    private TextView hangup;
    private TextView decline;

    private WebRtcCallMenuText ringToggle;

    private LinearLayout expandedInfo;
    private TextView recipientName;
    private TextView recipientNumber;

    private TextView label;
    private TextView status;
    private TextView elapsedTime;

    private LinearLayout parent;
    private ControlsListener controlsListener;
    private RecipientId recipientId;
    private boolean controlsVisible = true;

    private WebRtcControls controls = WebRtcControls.NONE;

    private int mFocusHeight;
    private int mNormalHeight;
    private int mNormalPaddingX;
    private int mFocusPaddingX;
    private int mFocusTextSize;
    private int mNormalTextSize;
    private int mFocusedColor;
    private int mNormalColor;

    public PigeonWebRtcCallView(@NonNull Context context) {
        this(context, null);
    }

    public PigeonWebRtcCallView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        inflate(context, R.layout.pigeon_webrtc_call_view, this);
//        initAnit(context);
    }

    @SuppressWarnings("CodeBlock2Expr")
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        label = findViewById(R.id.call_screen_label);
        status = findViewById(R.id.call_screen_status);
        elapsedTime = findViewById(R.id.elapsedTime);

        expandedInfo = findViewById(R.id.expanded_info);
        recipientName = findViewById(R.id.call_screen_recipient_name);
        recipientNumber = findViewById(R.id.call_screen_recipient_number);

        participants = findViewById(R.id.call_screen_participants_toggle);
        startCall = findViewById(R.id.call_screen_start_call_toggle);
        volumeToggle = findViewById(R.id.call_screen_volume_toggle);
        speakerToggle = findViewById(R.id.call_screen_speaker_toggle);
        micToggle = findViewById(R.id.call_screen_audio_mic_toggle);

        parent = findViewById(R.id.call_screen);
        answer = findViewById(R.id.call_screen_answer_call);
        hangup = findViewById(R.id.call_screen_end_call);
        decline = findViewById(R.id.call_screen_decline_call);
        ringToggle = findViewById(R.id.call_screen_audio_ring_toggle_label);

        Resources res = getContext().getResources();
        mFocusHeight = 56;//res.getDimensionPixelSize(R.dimen.focus_item_height);
        mNormalHeight = 32;//res.getDimensionPixelSize(R.dimen.item_height);
        mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize);
        mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize);
        mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
        mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);
        mFocusedColor = res.getColor(R.color.focused_text_color);
        mNormalColor = res.getColor(R.color.normal_text_color);

        participants.setOnClickListener(unused -> showParticipantsList());
        startCall.setOnClickListener(v -> runIfNonNull(controlsListener, listener -> listener.onStartCall(false)));
        volumeToggle.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onVolumePressed));

        ringToggle.setOnTextChangedListener(new OnTextChangedListener() {
            @Override public void textChanged(boolean on) {
                runIfNonNull(controlsListener, listener -> listener.onRingGroupChanged(on, true));
            }
        });
        ringToggle.setChecked(true);

        speakerToggle.setOnTextChangedListener(new OnTextChangedListener() {
            @Override
            public void textChanged(boolean on) {
                runIfNonNull(controlsListener, listener -> listener.onAudioOutputChanged(on ? WebRtcAudioOutput.SPEAKER : WebRtcAudioOutput.HANDSET));
                setSpeakerEnabled(on);
            }
        });

        micToggle.setOnTextChangedListener(new OnTextChangedListener() {
            @Override
            public void textChanged(boolean on) {
                runIfNonNull(controlsListener, listener -> listener.onMicChanged(on));
            }
        });
        micToggle.setChecked(true);

        hangup.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onEndCallPressed));
        answer.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onAcceptCallPressed));
        decline.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onDenyCallPressed));

        expandedInfo.setOnFocusChangeListener(this);

        participants.setOnFocusChangeListener(this);
        startCall.setOnFocusChangeListener(this);
        volumeToggle.setOnFocusChangeListener(this);
        speakerToggle.setOnFocusChangeListener(this);
        micToggle.setOnFocusChangeListener(this);
        ringToggle.setOnFocusChangeListener(this);
        hangup.setOnFocusChangeListener(this);
        answer.setOnFocusChangeListener(this);
        decline.setOnFocusChangeListener(this);

        expandedInfo.requestFocus();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    public void onWindowSystemUiVisibilityChanged(int visible) {
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (v.getId() == R.id.expanded_info) {
            startFocusAnimation(recipientName, hasFocus);
            startFocusAnimation(recipientNumber, hasFocus);
        } else {
            startFocusAnimation(v, hasFocus);
        }
    }

    public void startFocusAnimation(View v, boolean focused) {
        ValueAnimator va;
        TextView item = (TextView) v;
        String text = (item != null) ? item.getText().toString() : null;
        if (focused) {
            va = ValueAnimator.ofFloat(0, 1);
        } else {
            va = ValueAnimator.ofFloat(1, 0);
        }
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float scale = (float) valueAnimator.getAnimatedValue();
                float height = ((float) (mFocusHeight - mNormalHeight)) * (scale) + (float) mNormalHeight;
                float textsize = ((float) (mFocusTextSize - mNormalTextSize)) * (scale) + mNormalTextSize;
                float padding = (float) mNormalPaddingX - ((float) (mNormalPaddingX - mFocusPaddingX)) * (scale);
                int alpha = (int) ((float) 0x81 + (float) ((0xff - 0x81)) * (scale));
                int color = alpha * 0x1000000 + 0xffffff;
                item.setTextSize((int) textsize);
                item.setTextColor(color);
                item.setPadding(
                        (int) padding, item.getPaddingTop(),
                        item.getPaddingRight(), item.getPaddingBottom());
                item.getLayoutParams().height = (int) height;
            }
        });

        FastOutLinearInInterpolator FastOutLinearInInterpolator = new FastOutLinearInInterpolator();
        va.setInterpolator(FastOutLinearInInterpolator);
        va.setDuration(270);
        va.start();
    }

    public void setControlsListener(@Nullable ControlsListener controlsListener) {
        this.controlsListener = controlsListener;
    }

    public void setMicEnabled(boolean isMicEnabled) {
        if (isMicEnabled) {
            this.micToggle.setText(R.string.WebRtcCallControls_mute_button_description_on);
        } else {
            this.micToggle.setText(R.string.WebRtcCallControls_mute_button_description_off);
        }
    }

    public void setSpeakerEnabled(boolean on) {
        if (on) {
            this.speakerToggle.setText(R.string.WebRtcCallControls_speaker_button_description_off);
        } else {
            this.speakerToggle.setText(R.string.WebRtcCallControls_speaker_button_description_on);
        }
    }

    public void updateCallParticipants(@NonNull CallParticipantsViewState state) {
    }

    public void setRecipient(@NonNull Recipient recipient) {
        if (recipient.getId() == recipientId) {
            return;
        }
        recipientId = recipient.getId();

        recipientName.setText(recipient.getDisplayName(getContext()));
        recipientNumber.setText(recipient.getNumber());
    }

    public void setStatus(@NonNull String status) {
        this.status.setText(status);
    }

    public void setElapsedTime(@NonNull String time) {
        this.elapsedTime.setText(time);
    }

    public void enableElapsedTime(boolean show){
        this.elapsedTime.setVisibility(show ? View.VISIBLE :  View.GONE);
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
        if (webRtcControls.displayStartCallControls()) {
            startCall.setVisibility(View.VISIBLE);
            startCall.setText(webRtcControls.getStartCallButtonText());
            startCall.setEnabled(webRtcControls.isStartCallEnabled());
            label.setText(R.string.MessageRecord_group_call);
        }

        if (webRtcControls.displayRingToggle()) {
            ringToggle.setVisibility(View.VISIBLE);
        }

        if (webRtcControls.displayErrorControls()) {
             controlsListener.onCancelStartCall();
        }

        if (webRtcControls.displayIncomingCallButtons()) {
            status.setText(R.string.WebRtcCallView__signal_call);

            answer.setVisibility(View.VISIBLE);
            decline.setVisibility(View.VISIBLE);
            hangup.setVisibility(View.GONE);
        }

        if (webRtcControls.displayAnswerWithAudio()) {
            status.setText(R.string.WebRtcCallView__signal_video_call);
        }

        if (webRtcControls.displayAudioToggle()) {
            speakerToggle.setVisibility(View.VISIBLE);
        }

        if (webRtcControls.displayEndCall()) {
            answer.setVisibility(View.GONE);
            decline.setVisibility(View.GONE);
            startCall.setVisibility(View.GONE);
            if(label.getText().equals(getContext().getString(R.string.MessageRecord_group_call))) {
                participants.setVisibility(View.VISIBLE);
            }
            hangup.setVisibility(View.VISIBLE);
        }

        if (webRtcControls.displayMuteAudio()) {
            micToggle.setVisibility(View.VISIBLE);
        }
        controls = webRtcControls;
    }

    private static <T> void runIfNonNull(@Nullable T listener, @NonNull Consumer<T> listenerConsumer) {
        if (listener != null) {
            listenerConsumer.accept(listener);
        }
    }

    public void showSpeakerViewHint() {
    }

    public void hideSpeakerViewHint() {
    }

    private boolean showParticipantsList() {
        controlsListener.onShowParticipantsList();
        return true;
    }

    public void setRingGroup(boolean shouldRingGroup) {
        ringToggle.setChecked(shouldRingGroup);
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

        void onVideoChanged(boolean isVideoEnabled);

        void onVolumePressed();

        void onMicChanged(boolean isMicEnabled);

        void onCameraDirectionChanged();

        void onEndCallPressed();

        void onDenyCallPressed();

        void onAcceptCallWithVoiceOnlyPressed();

        void onAcceptCallPressed();

        void onShowParticipantsList();

        void onPageChanged(@NonNull CallParticipantsState.SelectedPage page);

        void onLocalPictureInPictureClicked();

        void onRingGroupChanged(boolean ringGroup, boolean ringingAllowed);
    }
}
