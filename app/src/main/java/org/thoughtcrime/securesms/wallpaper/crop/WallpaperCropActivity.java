package org.thoughtcrime.securesms.wallpaper.crop;

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.PorterDuff;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProviders;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BaseActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.imageeditor.ImageEditorView;
import org.thoughtcrime.securesms.imageeditor.model.EditorElement;
import org.thoughtcrime.securesms.imageeditor.model.EditorModel;
import org.thoughtcrime.securesms.imageeditor.renderers.FaceBlurRenderer;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.scribbles.UriGlideRenderer;
import org.thoughtcrime.securesms.util.AsynchronousCallback;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperPreviewActivity;

import java.util.Locale;
import java.util.Objects;

public final class WallpaperCropActivity extends BaseActivity {

  private static final String TAG = Log.tag(WallpaperCropActivity.class);

  private static final String EXTRA_RECIPIENT_ID = "RECIPIENT_ID";
  private static final String EXTRA_IMAGE_URI    = "IMAGE_URI";

  private final DynamicTheme dynamicTheme = new DynamicWallpaperTheme();

  private ImageEditorView        imageEditor;
  private WallpaperCropViewModel viewModel;

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
    viewModel = ViewModelProviders.of(this, factory).get(WallpaperCropViewModel.class);

    imageEditor = findViewById(R.id.image_editor);
    View         receivedBubble = findViewById(R.id.preview_bubble_1);
    TextView     bubble2Text    = findViewById(R.id.chat_wallpaper_bubble2_text);
    View         setWallPaper   = findViewById(R.id.preview_set_wallpaper);
    SwitchCompat blur           = findViewById(R.id.preview_blur);

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
                 receivedBubble.getBackground().setColorFilter(r.getColor().toConversationColor(this), PorterDuff.Mode.SRC_IN);
               }
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

    AlertDialog dialog = SimpleProgressDialog.show(this);
    viewModel.render(this, model, size,
                     new AsynchronousCallback.MainThread<ChatWallpaper, WallpaperCropViewModel.Error>() {
                       @Override public void onComplete(@Nullable ChatWallpaper result) {
                         dialog.dismiss();
                         setResult(RESULT_OK, new Intent().putExtra(ChatWallpaperPreviewActivity.EXTRA_CHAT_WALLPAPER, result));
                         finish();
                       }

                       @Override public void onError(@Nullable WallpaperCropViewModel.Error error) {
                         dialog.dismiss();
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

    EditorModel editorModel = EditorModel.createForWallpaperEditing(ratio);

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
