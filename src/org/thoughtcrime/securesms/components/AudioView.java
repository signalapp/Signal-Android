package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.pnikosis.materialishprogress.ProgressWheel;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.audio.AudioSlidePlayer;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.jobs.PartProgressEvent;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.mms.SlideClickListener;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import de.greenrobot.event.EventBus;

public class AudioView extends FrameLayout implements AudioSlidePlayer.Listener {

  private static final String TAG = AudioView.class.getSimpleName();

  private final @NonNull AnimatingToggle controlToggle;
  private final @NonNull ImageView       playButton;
  private final @NonNull ImageView       pauseButton;
  private final @NonNull ImageView       downloadButton;
  private final @NonNull ProgressWheel   downloadProgress;
  private final @NonNull SeekBar         seekBar;
  private final @NonNull TextView        timestamp;

  private @Nullable SlideClickListener downloadListener;
  private @Nullable AudioSlidePlayer   audioSlidePlayer;
  private int backwardsCounter;

  public AudioView(Context context) {
    this(context, null);
  }

  public AudioView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public AudioView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    inflate(context, R.layout.audio_view, this);

    this.controlToggle    = (AnimatingToggle) findViewById(R.id.control_toggle);
    this.playButton       = (ImageView) findViewById(R.id.play);
    this.pauseButton      = (ImageView) findViewById(R.id.pause);
    this.downloadButton   = (ImageView) findViewById(R.id.download);
    this.downloadProgress = (ProgressWheel) findViewById(R.id.download_progress);
    this.seekBar          = (SeekBar) findViewById(R.id.seek);
    this.timestamp        = (TextView) findViewById(R.id.timestamp);

    this.playButton.setOnClickListener(new PlayClickedListener());
    this.pauseButton.setOnClickListener(new PauseClickedListener());
    this.seekBar.setOnSeekBarChangeListener(new SeekBarModifiedListener());

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AudioView, 0, 0);
      setTint(typedArray.getColor(R.styleable.AudioView_tintColor, Color.WHITE));
      typedArray.recycle();
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().registerSticky(this);
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    EventBus.getDefault().unregister(this);
  }

  public void setAudio(final @NonNull MasterSecret masterSecret,
                       final @NonNull AudioSlide audio,
                       final boolean showControls)
  {

    if (showControls && audio.isPendingDownload()) {
      controlToggle.displayQuick(downloadButton);
      seekBar.setEnabled(false);
      downloadButton.setOnClickListener(new DownloadClickedListener(audio));
      if (downloadProgress.isSpinning()) downloadProgress.stopSpinning();
    } else if (showControls && audio.getTransferState() == AttachmentDatabase.TRANSFER_PROGRESS_STARTED) {
      controlToggle.displayQuick(downloadProgress);
      seekBar.setEnabled(false);
      downloadProgress.spin();
    } else {
      controlToggle.displayQuick(playButton);
      seekBar.setEnabled(true);
      if (downloadProgress.isSpinning()) downloadProgress.stopSpinning();
    }

    this.audioSlidePlayer = AudioSlidePlayer.createFor(getContext(), masterSecret, audio, this);
  }

  public void cleanup() {
    if (this.audioSlidePlayer != null && pauseButton.getVisibility() == View.VISIBLE) {
      this.audioSlidePlayer.stop();
    }
  }

  public void setDownloadClickListener(@Nullable SlideClickListener listener) {
    this.downloadListener = listener;
  }

  @Override
  public void onStart() {
    this.controlToggle.display(this.pauseButton);
  }

  @Override
  public void onStop() {
    this.controlToggle.display(this.playButton);

    if (seekBar.getProgress() + 5 >= seekBar.getMax()) {
      backwardsCounter = 4;
      onProgress(0.0, 0);
    }
  }

  @Override
  public void onProgress(double progress, long millis) {
    int seekProgress = (int)Math.floor(progress * this.seekBar.getMax());

    if (seekProgress > seekBar.getProgress() || backwardsCounter > 3) {
      backwardsCounter = 0;
      this.seekBar.setProgress(seekProgress);
      this.timestamp.setText(String.format("%02d:%02d",
                                           TimeUnit.MILLISECONDS.toMinutes(millis),
                                           TimeUnit.MILLISECONDS.toSeconds(millis)));
    } else {
      backwardsCounter++;
    }
  }

  public void setTint(int tint) {
    this.playButton.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
    this.pauseButton.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
    this.downloadButton.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
    this.downloadProgress.setBarColor(tint);

    this.timestamp.setTextColor(tint);
    this.seekBar.getProgressDrawable().setColorFilter(tint, PorterDuff.Mode.SRC_IN);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      this.seekBar.getThumb().setColorFilter(tint, PorterDuff.Mode.SRC_IN);
    }
  }

  private double getProgress() {
    if (this.seekBar.getProgress() <= 0 || this.seekBar.getMax() <= 0) {
      return 0;
    } else {
      return (double)this.seekBar.getProgress() / (double)this.seekBar.getMax();
    }
  }

  private class PlayClickedListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      try {
        Log.w(TAG, "playbutton onClick");
        if (audioSlidePlayer != null) {
          controlToggle.display(pauseButton);
          audioSlidePlayer.play(getProgress());
        }
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }
  }

  private class PauseClickedListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      Log.w(TAG, "pausebutton onClick");
      if (audioSlidePlayer != null) {
        controlToggle.display(playButton);
        audioSlidePlayer.stop();
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
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

    @Override
    public synchronized void onStartTrackingTouch(SeekBar seekBar) {
      if (audioSlidePlayer != null && pauseButton.getVisibility() == View.VISIBLE) {
        audioSlidePlayer.stop();
      }
    }

    @Override
    public synchronized void onStopTrackingTouch(SeekBar seekBar) {
      try {
        if (audioSlidePlayer != null && pauseButton.getVisibility() == View.VISIBLE) {
          audioSlidePlayer.play(getProgress());
        }
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }
  }

  @SuppressWarnings("unused")
  public void onEventAsync(final PartProgressEvent event) {
    if (audioSlidePlayer != null && event.attachment.equals(this.audioSlidePlayer.getAudioSlide().asAttachment())) {
      Util.runOnMain(new Runnable() {
        @Override
        public void run() {
          downloadProgress.setInstantProgress(((float) event.progress) / event.total);
        }
      });
    }
  }

}
