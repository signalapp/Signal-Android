package org.thoughtcrime.securesms.wallpaper.crop;

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.logging.Log;
import org.signal.imageeditor.core.ImageEditorView;
import org.signal.imageeditor.core.model.EditorElement;
import org.signal.imageeditor.core.model.EditorModel;
import org.signal.imageeditor.core.renderers.FaceBlurRenderer;
import org.thoughtcrime.securesms.BaseActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ProgressCard;
import org.thoughtcrime.securesms.conversation.colors.ColorizerView;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.scribbles.UriGlideRenderer;
import org.thoughtcrime.securesms.util.AsynchronousCallback;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.Projection;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperPreviewActivity;

import java.util.Collections;
import java.util.Locale;
import java.util.Objects;

public final class WallpaperCropActivity extends BaseActivity {

  private static final String TAG = Log.tag(WallpaperCropActivity.class);

  private static final String EXTRA_RECIPIENT_ID = "RECIPIENT_ID";
  private static final String EXTRA_IMAGE_URI    = "IMAGE_URI";

  private final DynamicTheme dynamicTheme = new DynamicWallpaperTheme();

  private ImageEditorView        imageEditor;
  private WallpaperCropViewModel viewModel;
  private ProgressCard           progressCard;

  public static Intent newIntent(@NonNull Context context,
                                 @Nullable RecipientId recipientId,
                                 @NonNull Uri imageUri)
  {
    Intent intent = new Intent(context, WallpaperCropActivity.class);
    intent.putExtra(EXTRA_RECIPIENT_ID, recipientId);
    intent.putExtra(EXTRA_IMAGE_URI, Objects.requireNonNull(imageUri));
    return intent;
  }

  @Override
  protected void attachBaseContext(@NonNull Context newBase) {
    getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    super.attachBaseContext(newBase);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    dynamicTheme.onCreate(this);
    setContentView(R.layout.chat_wallpaper_crop_activity);

    RecipientId recipientId = getIntent().getParcelableExtra(EXTRA_RECIPIENT_ID);
    Uri         inputImage  = Objects.requireNonNull(getIntent().getParcelableExtra(EXTRA_IMAGE_URI));

    Log.i(TAG, "Cropping wallpaper for " + (recipientId == null ? "default wallpaper" : recipientId));

    WallpaperCropViewModel.Factory factory = new WallpaperCropViewModel.Factory(recipientId);
    viewModel = new ViewModelProvider(this, factory).get(WallpaperCropViewModel.class);

    imageEditor  = findViewById(R.id.image_editor);
    progressCard = findViewById(R.id.wallpaper_crop_progress_card);

    View          sentBubble     = findViewById(R.id.preview_bubble_2);
    TextView      bubble2Text    = findViewById(R.id.chat_wallpaper_bubble2_text);
    View          setWallPaper   = findViewById(R.id.preview_set_wallpaper);
    SwitchCompat  blur           = findViewById(R.id.preview_blur);
    ColorizerView colorizerView  = findViewById(R.id.colorizer);

    setupImageEditor(inputImage);

    setWallPaper.setOnClickListener(v -> setWallpaper());

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar supportActionBar = Objects.requireNonNull(getSupportActionBar());
    supportActionBar.setHomeAsUpIndicator(ContextCompat.getDrawable(this, R.drawable.ic_arrow_left_24));
    supportActionBar.setDisplayHomeAsUpEnabled(true);

    blur.setOnCheckedChangeListener((v, checked) -> viewModel.setBlur(checked));

    viewModel.getBlur()
             .observe(this, blurred -> {
               setBlurred(blurred);
               if (blurred != blur.isChecked()) {
                 blur.setChecked(blurred);
               }
             });

    viewModel.getRecipient()
             .observe(this, r -> {
               if (r.getId().isUnknown()) {
                 bubble2Text.setText(R.string.WallpaperCropActivity__set_wallpaper_for_all_chats);
               } else {
                 bubble2Text.setText(getString(R.string.WallpaperCropActivity__set_wallpaper_for_s, r.getDisplayName(this)));
                 sentBubble.getBackground().setColorFilter(r.getChatColors().getChatBubbleColorFilter());
                 colorizerView.setBackground(r.getChatColors().getChatBubbleMask());
               }
             });

    sentBubble.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      colorizerView.setProjections(Collections.singletonList(Projection.relativeToViewWithCommonRoot(sentBubble, colorizerView, new Projection.Corners(ViewUtil.dpToPx(18)))));
    });
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (super.onOptionsItemSelected(item)) {
      return true;
    }

    int itemId = item.getItemId();

    if (itemId == android.R.id.home) {
      finish();
      return true;
    }

    return false;
  }

  private void setWallpaper() {
    EditorModel model = imageEditor.getModel();

    Point size = new Point(imageEditor.getWidth(), imageEditor.getHeight());

    if (progressCard != null) {
      progressCard.setVisibility(View.VISIBLE);
    }

    viewModel.render(this, model, size,
                     new AsynchronousCallback.MainThread<ChatWallpaper, WallpaperCropViewModel.Error>() {
                       @Override public void onComplete(@Nullable ChatWallpaper result) {
                         if (progressCard != null) {
                           progressCard.setVisibility(View.GONE);
                         }
                         setResult(RESULT_OK, new Intent().putExtra(ChatWallpaperPreviewActivity.EXTRA_CHAT_WALLPAPER, result));
                         finish();
                       }

                       @Override public void onError(@Nullable WallpaperCropViewModel.Error error) {
                         if (progressCard != null) {
                           progressCard.setVisibility(View.GONE);
                         }
                         Toast.makeText(WallpaperCropActivity.this, R.string.WallpaperCropActivity__error_setting_wallpaper, Toast.LENGTH_SHORT).show();
                       }
                     }.toWorkerCallback());
  }

  private void setupImageEditor(@NonNull Uri imageUri) {
    DisplayMetrics displayMetrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
    int   height = displayMetrics.heightPixels;
    int   width  = displayMetrics.widthPixels;
    float ratio  = width / (float) height;

    EditorModel editorModel = EditorModel.createForWallpaperEditing(ratio, ContextCompat.getColor(this, R.color.signal_colorBackground));

    EditorElement image = new EditorElement(new UriGlideRenderer(imageUri, true, width, height, UriGlideRenderer.WEAK_BLUR));
    image.getFlags()
         .setSelectable(false)
         .persist();

    editorModel.addElement(image);

    imageEditor.setModel(editorModel);

    imageEditor.setSizeChangedListener((newWidth, newHeight) -> {
      float newRatio = newWidth / (float) newHeight;
      Log.i(TAG, String.format(Locale.US, "Output size (%d, %d) (ratio %.2f)", newWidth, newHeight, newRatio));

      editorModel.setFixedRatio(newRatio);
    });
  }

  private void setBlurred(boolean blurred) {
    imageEditor.getModel().clearFaceRenderers();

    if (blurred) {
      EditorElement mainImage = imageEditor.getModel().getMainImage();

      if (mainImage != null) {
        EditorElement element = new EditorElement(new FaceBlurRenderer(), EditorModel.Z_MASK);

        element.getFlags()
               .setEditable(false)
               .setSelectable(false)
               .persist();

        mainImage.addElement(element);
        imageEditor.invalidate();
      }
    }
  }

  private static final class DynamicWallpaperTheme extends DynamicTheme {
    protected @StyleRes int getTheme() {
      return R.style.Signal_DayNight_WallpaperCropper;
    }
  }
}
