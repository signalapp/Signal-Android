package org.thoughtcrime.securesms.components;


import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.pnikosis.materialishprogress.ProgressWheel;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.events.PartProgressEvent;
import org.thoughtcrime.securesms.mms.DocumentSlide;
import org.thoughtcrime.securesms.mms.SlideClickListener;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

public class DocumentView extends FrameLayout {

  private static final String TAG = DocumentView.class.getSimpleName();

  private final @NonNull AnimatingToggle controlToggle;
  private final @NonNull ImageView       downloadButton;
  private final @NonNull ProgressWheel   downloadProgress;
  private final @NonNull View            container;
  private final @NonNull ViewGroup       iconContainer;
  private final @NonNull TextView        fileName;
  private final @NonNull TextView        fileSize;
  private final @NonNull TextView        document;

  private @Nullable SlideClickListener downloadListener;
  private @Nullable SlideClickListener viewListener;
  private @Nullable DocumentSlide      documentSlide;

  public DocumentView(@NonNull Context context) {
    this(context, null);
  }

  public DocumentView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public DocumentView(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    inflate(context, R.layout.document_view, this);

    this.container        = findViewById(R.id.document_container);
    this.iconContainer    = findViewById(R.id.icon_container);
    this.controlToggle    = findViewById(R.id.control_toggle);
    this.downloadButton   = findViewById(R.id.download);
    this.downloadProgress = findViewById(R.id.download_progress);
    this.fileName         = findViewById(R.id.file_name);
    this.fileSize         = findViewById(R.id.file_size);
    this.document         = findViewById(R.id.document);

    if (attrs != null) {
      TypedArray typedArray   = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.DocumentView, 0, 0);
      int        titleColor   = typedArray.getInt(R.styleable.DocumentView_doc_titleColor, Color.BLACK);
      int        captionColor = typedArray.getInt(R.styleable.DocumentView_doc_captionColor, Color.BLACK);
      int        downloadTint = typedArray.getInt(R.styleable.DocumentView_doc_downloadButtonTint, Color.WHITE);
      typedArray.recycle();

      fileName.setTextColor(titleColor);
      fileSize.setTextColor(captionColor);
      downloadButton.setColorFilter(downloadTint, PorterDuff.Mode.MULTIPLY);
      downloadProgress.setBarColor(downloadTint);
    }
  }

  public void setDownloadClickListener(@Nullable SlideClickListener listener) {
    this.downloadListener = listener;
  }

  public void setDocumentClickListener(@Nullable SlideClickListener listener) {
    this.viewListener = listener;
  }

  public void setDocument(final @NonNull DocumentSlide documentSlide,
                          final boolean showControls)
  {
    if (showControls && documentSlide.isPendingDownload()) {
      controlToggle.displayQuick(downloadButton);
      downloadButton.setOnClickListener(new DownloadClickedListener(documentSlide));
      if (downloadProgress.isSpinning()) downloadProgress.stopSpinning();
    } else if (showControls && documentSlide.getTransferState() == AttachmentDatabase.TRANSFER_PROGRESS_STARTED) {
      controlToggle.displayQuick(downloadProgress);
      downloadProgress.spin();
    } else {
      controlToggle.displayQuick(iconContainer);
      if (downloadProgress.isSpinning()) downloadProgress.stopSpinning();
    }

    this.documentSlide = documentSlide;

    this.fileName.setText(documentSlide.getFileName().or(getContext().getString(R.string.DocumentView_unknown_file)));
    this.fileSize.setText(Util.getPrettyFileSize(documentSlide.getFileSize()));
    this.document.setText(getFileType(documentSlide.getFileName()));
    this.setOnClickListener(new OpenClickedListener(documentSlide));
  }

  @Override
  public void setFocusable(boolean focusable) {
    super.setFocusable(focusable);
    this.downloadButton.setFocusable(focusable);
  }

  @Override
  public void setClickable(boolean clickable) {
    super.setClickable(clickable);
    this.downloadButton.setClickable(clickable);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    this.downloadButton.setEnabled(enabled);
  }

  private @NonNull String getFileType(Optional<String> fileName) {
    if (!fileName.isPresent()) return "";

    String[] parts = fileName.get().split("\\.");

    if (parts.length < 2) {
      return "";
    }

    String suffix = parts[parts.length - 1];

    if (suffix.length() <= 3) {
      return suffix;
    }

    return "";
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  public void onEventAsync(final PartProgressEvent event) {
    if (documentSlide != null && event.attachment.equals(documentSlide.asAttachment())) {
      downloadProgress.setInstantProgress(((float) event.progress) / event.total);
    }
  }

  private class DownloadClickedListener implements View.OnClickListener {
    private final @NonNull DocumentSlide slide;

    private DownloadClickedListener(@NonNull DocumentSlide slide) {
      this.slide = slide;
    }

    @Override
    public void onClick(View v) {
      if (downloadListener != null) downloadListener.onClick(v, slide);
    }
  }

  private class OpenClickedListener implements View.OnClickListener {
    private final @NonNull DocumentSlide slide;

    private OpenClickedListener(@NonNull DocumentSlide slide) {
      this.slide = slide;
    }

    @Override
    public void onClick(View v) {
      if (!slide.isPendingDownload() && !slide.isInProgress() && viewListener != null) {
        viewListener.onClick(v, slide);
      }
    }
  }

}
