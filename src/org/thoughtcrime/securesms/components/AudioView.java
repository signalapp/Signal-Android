package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.SimpleColorFilter;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieValueCallback;
import com.pnikosis.materialishprogress.ProgressWheel;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.audio.AudioSlidePlayer;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.events.PartProgressEvent;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.mms.SlideClickListener;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class AudioView extends FrameLayout implements AudioSlidePlayer.Listener {

  private static final String TAG = AudioView.class.getSimpleName();

  private static final int FORWARDS =  1;
  private static final int REVERSE  = -1;

  @NonNull private final AnimatingToggle     controlToggle;
  @NonNull private final ViewGroup           container;
  @NonNull private final View                progressAndPlay;
  @NonNull private final LottieAnimationView playPauseButton;
  @NonNull private final ImageView           downloadButton;
  @NonNull private final ProgressWheel       downloadProgress;
  @NonNull private final SeekBar             seekBar;
  @NonNull private final TextView            timestamp;

  @Nullable private SlideClickListener downloadListener;
  @Nullable private AudioSlidePlayer   audioSlidePlayer;
            private int                backwardsCounter;
            private int                lottieDirection;
            private boolean            isPlaying;

  public AudioView(Context context) {
    this(context, null);
  }

  public AudioView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public AudioView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    inflate(context, R.layout.audio_view, this);

    this.container         = findViewById(R.id.audio_widget_container);
    this.controlToggle     = findViewById(R.id.control_toggle);
    this.playPauseButton   = findViewById(R.id.play);
    this.progressAndPlay   = findViewById(R.id.progress_and_play);
    this.downloadButton    = findViewById(R.id.download);
    this.downloadProgress  = findViewById(R.id.download_progress);
    this.seekBar           = findViewById(R.id.seek);
    this.timestamp         = findViewById(R.id.timestamp);

    lottieDirection = REVERSE;
    this.playPauseButton.setOnClickListener(new PlayPauseClickedListener());
    this.seekBar.setOnSeekBarChangeListener(new SeekBarModifiedListener());

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AudioView, 0, 0);
      setTint(typedArray.getColor(R.styleable.AudioView_foregroundTintColor, Color.WHITE),
              typedArray.getColor(R.styleable.AudioView_backgroundTintColor, Color.WHITE));
      container.setBackgroundColor(typedArray.getColor(R.styleable.AudioView_widgetBackground, Color.TRANSPARENT));
      typedArray.recycle();
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

  public void setAudio(final @NonNull AudioSlide audio,
                       final boolean showControls)
  {

    if (showControls && audio.isPendingDownload()) {
      controlToggle.displayQuick(downloadButton);
      seekBar.setEnabled(false);
      downloadButton.setOnClickListener(new DownloadClickedListener(audio));
      if (downloadProgress.isSpinning()) downloadProgress.stopSpinning();
    } else if (showControls && audio.getTransferState() == AttachmentDatabase.TRANSFER_PROGRESS_STARTED) {
      controlToggle.displayQuick(progressAndPlay);
      seekBar.setEnabled(false);
      downloadProgress.spin();
    } else {
      seekBar.setEnabled(true);
      if (downloadProgress.isSpinning()) downloadProgress.stopSpinning();
      showPlayButton();
      lottieDirection = REVERSE;
      playPauseButton.cancelAnimation();
      playPauseButton.setFrame(0);
    }

    this.audioSlidePlayer = AudioSlidePlayer.createFor(getContext(), audio, this);
  }

  public void cleanup() {
    if (this.audioSlidePlayer != null && isPlaying) {
      this.audioSlidePlayer.stop();
    }
  }

  public void setDownloadClickListener(@Nullable SlideClickListener listener) {
    this.downloadListener = listener;
  }

  @Override
  public void onStart() {
    isPlaying = true;
    togglePlayToPause();
  }

  @Override
  public void onStop() {
    isPlaying = false;
    togglePauseToPlay();

    if (seekBar.getProgress() + 5 >= seekBar.getMax()) {
      backwardsCounter = 4;
      onProgress(0.0, 0);
    }
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

  @Override
  public void onProgress(double progress, long millis) {
    int seekProgress = (int)Math.floor(progress * this.seekBar.getMax());

    if (seekProgress > seekBar.getProgress() || backwardsCounter > 3) {
      backwardsCounter = 0;
      this.seekBar.setProgress(seekProgress);
      this.timestamp.setText(String.format(Locale.getDefault(), "%02d:%02d",
                                           TimeUnit.MILLISECONDS.toMinutes(millis),
                                           TimeUnit.MILLISECONDS.toSeconds(millis)));
    } else {
      backwardsCounter++;
    }
  }

  public void setTint(int foregroundTint, int backgroundTint) {
    this.playPauseButton.addValueCallback(new KeyPath("**"),
                                          LottieProperty.COLOR_FILTER,
                                          new LottieValueCallback<>(new SimpleColorFilter(foregroundTint)));

    this.downloadButton.setColorFilter(foregroundTint, PorterDuff.Mode.SRC_IN);
    this.downloadProgress.setBarColor(foregroundTint);

    this.timestamp.setTextColor(foregroundTint);
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
    downloadProgress.setInstantProgress(1);
    downloadProgress.setVisibility(VISIBLE);
    playPauseButton.setVisibility(VISIBLE);
    controlToggle.displayQuick(progressAndPlay);
  }

  private class PlayPauseClickedListener implements View.OnClickListener {

    @Override
    public void onClick(View v) {
      if (lottieDirection == REVERSE) {
        try {
          Log.d(TAG, "playbutton onClick");
          if (audioSlidePlayer != null) {
            togglePlayToPause();
            audioSlidePlayer.play(getProgress());
          }
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      } else {
        Log.d(TAG, "pausebutton onClick");
        if (audioSlidePlayer != null) {
          togglePauseToPlay();
          audioSlidePlayer.stop();
        }
      }
    }
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
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

    @Override
    public synchronized void onStartTrackingTouch(SeekBar seekBar) {
      wasPlaying = isPlaying;
      if (audioSlidePlayer != null && isPlaying) {
        audioSlidePlayer.stop();
      }
    }

    @Override
    public synchronized void onStopTrackingTouch(SeekBar seekBar) {
      try {
        if (audioSlidePlayer != null && wasPlaying) {
          audioSlidePlayer.play(getProgress());
        }
      } catch (IOException e) {
        Log.w(TAG, e);
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
    if (audioSlidePlayer != null && event.attachment.equals(audioSlidePlayer.getAudioSlide().asAttachment())) {
      downloadProgress.setInstantProgress(((float) event.progress) / event.total);
    }
  }

}
