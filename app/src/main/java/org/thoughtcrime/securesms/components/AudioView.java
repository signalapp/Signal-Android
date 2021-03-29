package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.SimpleColorFilter;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieValueCallback;
import com.pnikosis.materialishprogress.ProgressWheel;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.audio.AudioWaveForm;
import org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackState;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.events.PartProgressEvent;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.mms.SlideClickListener;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class AudioView extends FrameLayout {

  private static final String TAG = Log.tag(AudioView.class);

  private static final int FORWARDS =  1;
  private static final int REVERSE  = -1;

  @NonNull private final AnimatingToggle     controlToggle;
  @NonNull private final View                progressAndPlay;
  @NonNull private final LottieAnimationView playPauseButton;
  @NonNull private final ImageView           downloadButton;
  @NonNull private final ProgressWheel       circleProgress;
  @NonNull private final SeekBar             seekBar;
           private final boolean             smallView;
           private final boolean             autoRewind;

  @Nullable private final TextView duration;

  @ColorInt private final int waveFormPlayedBarsColor;
  @ColorInt private final int waveFormUnplayedBarsColor;
  @ColorInt private final int waveFormThumbTint;

  @Nullable private SlideClickListener downloadListener;
            private int                backwardsCounter;
            private int                lottieDirection;
            private boolean            isPlaying;
            private long               durationMillis;
            private AudioSlide         audioSlide;
            private Callbacks          callbacks;

  private final Observer<VoiceNotePlaybackState> playbackStateObserver = this::onPlaybackState;

  public AudioView(Context context) {
    this(context, null);
  }

  public AudioView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public AudioView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    TypedArray typedArray = null;
    try {
      typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AudioView, 0, 0);

      smallView  = typedArray.getBoolean(R.styleable.AudioView_small, false);
      autoRewind = typedArray.getBoolean(R.styleable.AudioView_autoRewind, false);

      inflate(context, smallView ? R.layout.audio_view_small : R.layout.audio_view, this);

      this.controlToggle   = findViewById(R.id.control_toggle);
      this.playPauseButton = findViewById(R.id.play);
      this.progressAndPlay = findViewById(R.id.progress_and_play);
      this.downloadButton  = findViewById(R.id.download);
      this.circleProgress  = findViewById(R.id.circle_progress);
      this.seekBar         = findViewById(R.id.seek);
      this.duration        = findViewById(R.id.duration);

      lottieDirection = REVERSE;
      this.playPauseButton.setOnClickListener(new PlayPauseClickedListener());
      this.seekBar.setOnSeekBarChangeListener(new SeekBarModifiedListener());

      setTint(typedArray.getColor(R.styleable.AudioView_foregroundTintColor, Color.WHITE));

      this.waveFormPlayedBarsColor   = typedArray.getColor(R.styleable.AudioView_waveformPlayedBarsColor, Color.WHITE);
      this.waveFormUnplayedBarsColor = typedArray.getColor(R.styleable.AudioView_waveformUnplayedBarsColor, Color.WHITE);
      this.waveFormThumbTint         = typedArray.getColor(R.styleable.AudioView_waveformThumbTint, Color.WHITE);

      progressAndPlay.getBackground().setColorFilter(typedArray.getColor(R.styleable.AudioView_progressAndPlayTint, Color.BLACK), PorterDuff.Mode.SRC_IN);
    } finally {
      if (typedArray != null) {
        typedArray.recycle();
      }
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    EventBus.getDefault().unregister(this);
  }

  public Observer<VoiceNotePlaybackState> getPlaybackStateObserver() {
    return playbackStateObserver;
  }

  public void setAudio(final @NonNull AudioSlide audio,
                       final @Nullable Callbacks callbacks,
                       final boolean showControls,
                       final boolean forceHideDuration)
  {
    this.callbacks = callbacks;

    if (duration != null) {
      duration.setVisibility(View.VISIBLE);
    }

    if (seekBar instanceof WaveFormSeekBarView) {
      if (audioSlide != null && !Objects.equals(audioSlide.getUri(), audio.getUri())) {
       WaveFormSeekBarView waveFormView = (WaveFormSeekBarView) seekBar;
       waveFormView.setWaveMode(false);
       seekBar.setProgress(0);
       durationMillis = 0;
      }
    }

    if (showControls && audio.isPendingDownload()) {
      controlToggle.displayQuick(downloadButton);
      seekBar.setEnabled(false);
      downloadButton.setOnClickListener(new DownloadClickedListener(audio));
      if (circleProgress.isSpinning()) circleProgress.stopSpinning();
      circleProgress.setVisibility(View.GONE);
    } else if (showControls && audio.getTransferState() == AttachmentDatabase.TRANSFER_PROGRESS_STARTED) {
      controlToggle.displayQuick(progressAndPlay);
      seekBar.setEnabled(false);
      circleProgress.setVisibility(View.VISIBLE);
      circleProgress.spin();
    } else {
      seekBar.setEnabled(true);
      if (circleProgress.isSpinning()) circleProgress.stopSpinning();
      showPlayButton();
    }

    this.audioSlide = audio;

    if (seekBar instanceof WaveFormSeekBarView) {
      WaveFormSeekBarView waveFormView = (WaveFormSeekBarView) seekBar;
      waveFormView.setColors(waveFormPlayedBarsColor, waveFormUnplayedBarsColor, waveFormThumbTint);
      if (android.os.Build.VERSION.SDK_INT >= 23) {
        new AudioWaveForm(getContext(), audio).getWaveForm(
          data -> {
            durationMillis = data.getDuration(TimeUnit.MILLISECONDS);
            updateProgress(0, 0);
            if (!forceHideDuration && duration != null) {
              duration.setVisibility(VISIBLE);
            }
            waveFormView.setWaveData(data.getWaveForm());
          },
          () -> waveFormView.setWaveMode(false));
      } else {
        waveFormView.setWaveMode(false);
        if (duration != null) {
          duration.setVisibility(GONE);
        }
      }
    }

    if (forceHideDuration && duration != null) {
      duration.setVisibility(View.GONE);
    }
  }

  public void setDownloadClickListener(@Nullable SlideClickListener listener) {
    this.downloadListener = listener;
  }

  public @Nullable Uri getAudioSlideUri() {
    if (audioSlide != null) return audioSlide.getUri();
    else                    return null;
  }

  private void onPlaybackState(@NonNull VoiceNotePlaybackState voiceNotePlaybackState) {
    onDuration(voiceNotePlaybackState.getUri(), voiceNotePlaybackState.getTrackDuration());
    onStart(voiceNotePlaybackState.getUri(), voiceNotePlaybackState.isAutoReset());
    onProgress(voiceNotePlaybackState.getUri(),
               (double) voiceNotePlaybackState.getPlayheadPositionMillis() / voiceNotePlaybackState.getTrackDuration(),
               voiceNotePlaybackState.getPlayheadPositionMillis());
  }

  private void onDuration(@NonNull Uri uri, long durationMillis) {
    if (isTarget(uri)) {
      this.durationMillis = durationMillis;
    }
  }

  private void onStart(@NonNull Uri uri, boolean autoReset) {
    if (!isTarget(uri)) {
      if (hasAudioUri()) {
        onStop(audioSlide.getUri(), autoReset);
      }

      return;
    }

    if (isPlaying) {
      return;
    }

    isPlaying = true;
    togglePlayToPause();
  }

  private void onStop(@NonNull Uri uri, boolean autoReset) {
    if (!isTarget(uri)) {
      return;
    }

    if (!isPlaying) {
      return;
    }

    isPlaying = false;
    togglePauseToPlay();

    if (autoReset || autoRewind || seekBar.getProgress() + 5 >= seekBar.getMax()) {
      backwardsCounter = 4;
      rewind();
    }
  }

  private void onProgress(@NonNull Uri uri, double progress, long millis) {
    if (!isTarget(uri)) {
      return;
    }

    int seekProgress = (int) Math.floor(progress * seekBar.getMax());

    if (seekProgress > seekBar.getProgress() || backwardsCounter > 3) {
      backwardsCounter = 0;
      seekBar.setProgress(seekProgress);
      updateProgress((float) progress, millis);
    } else {
      backwardsCounter++;
    }
  }

  private boolean isTarget(@NonNull Uri uri) {
    return hasAudioUri() && Objects.equals(uri, audioSlide.getUri());
  }

  private boolean hasAudioUri() {
    return audioSlide != null && audioSlide.getUri() != null;
  }

  @Override
  public void setFocusable(boolean focusable) {
    super.setFocusable(focusable);
    this.playPauseButton.setFocusable(focusable);
    this.seekBar.setFocusable(focusable);
    this.seekBar.setFocusableInTouchMode(focusable);
    this.downloadButton.setFocusable(focusable);
  }

  @Override
  public void setClickable(boolean clickable) {
    super.setClickable(clickable);
    this.playPauseButton.setClickable(clickable);
    this.seekBar.setClickable(clickable);
    this.seekBar.setOnTouchListener(clickable ? null : new TouchIgnoringListener());
    this.downloadButton.setClickable(clickable);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    this.playPauseButton.setEnabled(enabled);
    this.seekBar.setEnabled(enabled);
    this.downloadButton.setEnabled(enabled);
  }

  private void updateProgress(float progress, long millis) {
    if (callbacks != null) {
      callbacks.onProgressUpdated(durationMillis, millis);
    }

    if (duration != null && durationMillis > 0) {
      long remainingSecs = TimeUnit.MILLISECONDS.toSeconds(durationMillis - millis);
      duration.setText(getResources().getString(R.string.AudioView_duration, remainingSecs / 60, remainingSecs % 60));
    }

    if (smallView) {
      circleProgress.setInstantProgress(seekBar.getProgress() == 0 ? 1 : progress);
    }
  }

  public void setTint(int foregroundTint) {
    post(()-> this.playPauseButton.addValueCallback(new KeyPath("**"),
                                                    LottieProperty.COLOR_FILTER,
                                                    new LottieValueCallback<>(new SimpleColorFilter(foregroundTint))));

    this.downloadButton.setColorFilter(foregroundTint, PorterDuff.Mode.SRC_IN);
    this.circleProgress.setBarColor(foregroundTint);

    if (this.duration != null) {
      this.duration.setTextColor(foregroundTint);
    }
    this.seekBar.getProgressDrawable().setColorFilter(foregroundTint, PorterDuff.Mode.SRC_IN);
    this.seekBar.getThumb().setColorFilter(foregroundTint, PorterDuff.Mode.SRC_IN);
  }

  public void getSeekBarGlobalVisibleRect(@NonNull Rect rect) {
    seekBar.getGlobalVisibleRect(rect);
  }

  private double getProgress() {
    if (this.seekBar.getProgress() <= 0 || this.seekBar.getMax() <= 0) {
      return 0;
    } else {
      return (double)this.seekBar.getProgress() / (double)this.seekBar.getMax();
    }
  }

  private void togglePlayToPause() {
    startLottieAnimation(FORWARDS);
  }

  private void togglePauseToPlay() {
    startLottieAnimation(REVERSE);
  }

  private void startLottieAnimation(int direction) {
    showPlayButton();

    if (lottieDirection == direction) {
      return;
    }
    lottieDirection = direction;

    playPauseButton.pauseAnimation();
    playPauseButton.setSpeed(direction * 2);
    playPauseButton.resumeAnimation();
  }

  private void showPlayButton() {
    if (!smallView) {
      circleProgress.setVisibility(GONE);
    } else if (seekBar.getProgress() == 0) {
      circleProgress.setInstantProgress(1);
    }
    playPauseButton.setVisibility(VISIBLE);
    controlToggle.displayQuick(progressAndPlay);
  }

  public void stopPlaybackAndReset() {
    if (audioSlide == null || audioSlide.getUri() == null) return;

    if (callbacks != null) {
      callbacks.onStopAndReset(audioSlide.getUri());
      rewind();
    }
  }

  private class PlayPauseClickedListener implements View.OnClickListener {

    @Override
    public void onClick(View v) {
      if (audioSlide == null || audioSlide.getUri() == null) return;

      if (callbacks != null) {
        if (lottieDirection == REVERSE) {
          callbacks.onPlay(audioSlide.getUri(), getProgress());
        } else {
          callbacks.onPause(audioSlide.getUri());
        }
      }
    }
  }

  private void rewind() {
    seekBar.setProgress(0);
    updateProgress(0, 0);
  }

  private class DownloadClickedListener implements View.OnClickListener {
    private final @NonNull AudioSlide slide;

    private DownloadClickedListener(@NonNull AudioSlide slide) {
      this.slide = slide;
    }

    @Override
    public void onClick(View v) {
      if (downloadListener != null) downloadListener.onClick(v, slide);
    }
  }

  private class SeekBarModifiedListener implements SeekBar.OnSeekBarChangeListener {

    private boolean wasPlaying;

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
    }

    @Override
    public synchronized void onStartTrackingTouch(SeekBar seekBar) {
      if (audioSlide == null || audioSlide.getUri() == null) return;

      wasPlaying = isPlaying;
      if (isPlaying) {
        if (callbacks != null) {
          callbacks.onPause(audioSlide.getUri());
        }
      }
    }

    @Override
    public synchronized void onStopTrackingTouch(SeekBar seekBar) {
      if (audioSlide == null || audioSlide.getUri() == null) return;

      if (callbacks != null) {
        if (wasPlaying) {
          callbacks.onSeekTo(audioSlide.getUri(), getProgress());
        }
      }
    }
  }

  private static class TouchIgnoringListener implements OnTouchListener {
    @Override
    public boolean onTouch(View v, MotionEvent event) {
      return true;
    }
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  public void onEventAsync(final PartProgressEvent event) {
    if (audioSlide != null && event.attachment.equals(audioSlide.asAttachment())) {
      circleProgress.setInstantProgress(((float) event.progress) / event.total);
    }
  }

  public interface Callbacks {
    void onPlay(@NonNull Uri audioUri, double progress);
    void onPause(@NonNull Uri audioUri);
    void onSeekTo(@NonNull Uri audioUri, double progress);
    void onStopAndReset(@NonNull Uri audioUri);
    void onProgressUpdated(long durationMillis, long playheadMillis);
  }
}
