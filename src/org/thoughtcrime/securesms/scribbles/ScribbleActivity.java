package org.thoughtcrime.securesms.scribbles;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.providers.PersistentBlobProvider;
import org.thoughtcrime.securesms.scribbles.viewmodel.Font;
import org.thoughtcrime.securesms.scribbles.viewmodel.Layer;
import org.thoughtcrime.securesms.scribbles.viewmodel.TextLayer;
import org.thoughtcrime.securesms.scribbles.widget.MotionView;
import org.thoughtcrime.securesms.scribbles.widget.ScribbleView;
import org.thoughtcrime.securesms.scribbles.widget.VerticalSlideColorPicker;
import org.thoughtcrime.securesms.scribbles.widget.entity.ImageEntity;
import org.thoughtcrime.securesms.scribbles.widget.entity.MotionEntity;
import org.thoughtcrime.securesms.scribbles.widget.entity.TextEntity;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class ScribbleActivity extends PassphraseRequiredActionBarActivity implements ScribbleHud.EventListener, VerticalSlideColorPicker.OnColorChangeListener {

  private static final String TAG = ScribbleActivity.class.getName();

  public static final int SELECT_STICKER_REQUEST_CODE = 123;
  public static final int SCRIBBLE_REQUEST_CODE       = 31424;

  private ScribbleHud   scribbleHud;
  private ScribbleView  scribbleView;
  private GlideRequests glideRequests;

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    setContentView(R.layout.scribble_activity);

    this.glideRequests = GlideApp.with(this);
    this.scribbleHud   = findViewById(R.id.scribble_hud);
    this.scribbleView  = findViewById(R.id.scribble_view);

    scribbleHud.setEventListener(this);

    scribbleView.setMotionViewCallback(motionViewCallback);
    scribbleView.setDrawingChangedListener(() -> scribbleHud.setColorPalette(scribbleView.getUniqueColors()));
    scribbleView.setDrawingMode(false);
    scribbleView.setImage(glideRequests, getIntent().getData());

    if (Build.VERSION.SDK_INT >= 19) {
      getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN       |
                                                       View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                                                       View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
  }

  private void addSticker(final Bitmap pica) {
    Util.runOnMain(() -> {
      Layer       layer  = new Layer();
      ImageEntity entity = new ImageEntity(layer, pica, scribbleView.getWidth(), scribbleView.getHeight());

      scribbleView.addEntityAndPosition(entity);
    });
  }

  private void changeTextEntityColor(int selectedColor) {
    TextEntity textEntity = currentTextEntity();

    if (textEntity == null) {
      return;
    }

    textEntity.getLayer().getFont().setColor(selectedColor);
    textEntity.updateEntity();
    scribbleView.invalidate();
    scribbleHud.setColorPalette(scribbleView.getUniqueColors());
  }

  private void startTextEntityEditing() {
    TextEntity textEntity = currentTextEntity();
    if (textEntity != null) {
      scribbleView.startEditing(textEntity);
    }
  }

  @Nullable
  private TextEntity currentTextEntity() {
    if (scribbleView != null && scribbleView.getSelectedEntity() instanceof TextEntity) {
      return ((TextEntity) scribbleView.getSelectedEntity());
    } else {
      return null;
    }
  }

  protected void addTextSticker() {
    TextLayer  textLayer  = createTextLayer();
    TextEntity textEntity = new TextEntity(textLayer, scribbleView.getWidth(), scribbleView.getHeight());
    scribbleView.addEntityAndPosition(textEntity);

    PointF center = textEntity.absoluteCenter();
    center.y = center.y * 0.5F;
    textEntity.moveCenterTo(center);

    scribbleView.invalidate();

    startTextEntityEditing();
    changeTextEntityColor(scribbleHud.getActiveColor());
  }

  private TextLayer createTextLayer() {
    TextLayer textLayer = new TextLayer();
    Font font = new Font();

    font.setColor(scribbleHud.getActiveColor());
    font.setSize(TextLayer.Limits.INITIAL_FONT_SIZE);

    textLayer.setFont(font);

    return textLayer;
  }

  @SuppressLint("StaticFieldLeak")
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == RESULT_OK) {
      if (requestCode == SELECT_STICKER_REQUEST_CODE) {
        if (data != null) {
          final String stickerFile = data.getStringExtra(StickerSelectActivity.EXTRA_STICKER_FILE);

          new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected @Nullable
            Bitmap doInBackground(Void... params) {
              try {
                return BitmapFactory.decodeStream(getAssets().open(stickerFile));
              } catch (IOException e) {
                Log.w(TAG, e);
                return null;
              }
            }

            @Override
            protected void onPostExecute(@Nullable Bitmap bitmap) {
              addSticker(bitmap);
            }
          }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
      }
    }
  }

  @Override
  public void onModeStarted(@NonNull ScribbleHud.Mode mode) {
    switch (mode) {
      case DRAW:
        scribbleView.setDrawingMode(true);
        scribbleView.setDrawingBrushWidth(ScribbleView.DEFAULT_BRUSH_WIDTH);
        break;

      case HIGHLIGHT:
        scribbleView.setDrawingMode(true);
        scribbleView.setDrawingBrushWidth(ScribbleView.DEFAULT_BRUSH_WIDTH * 3);
        break;

      case TEXT:
        scribbleView.setDrawingMode(false);
        addTextSticker();
        break;

      case STICKER:
        scribbleView.setDrawingMode(false);
        Intent intent = new Intent(this, StickerSelectActivity.class);
        startActivityForResult(intent, SELECT_STICKER_REQUEST_CODE);
        break;

      case NONE:
        scribbleView.clearSelection();
        scribbleView.setDrawingMode(false);
        break;
    }
  }

  @Override
  public void onColorChange(int color) {
    scribbleView.setDrawingBrushColor(color);
    changeTextEntityColor(color);
  }

  @Override
  public void onUndo() {
    scribbleView.undoDrawing();
    scribbleHud.setColorPalette(scribbleView.getUniqueColors());
  }

  @Override
  public void onDelete() {
    scribbleView.deleteSelected();
    scribbleHud.setColorPalette(scribbleView.getUniqueColors());
  }

  @Override
  public void onSave() {
    ListenableFuture<Bitmap> future = scribbleView.getRenderedImage(glideRequests);

    future.addListener(new ListenableFuture.Listener<Bitmap>() {
      @Override
      public void onSuccess(Bitmap result) {
        PersistentBlobProvider provider = PersistentBlobProvider.getInstance(ScribbleActivity.this);
        ByteArrayOutputStream  baos     = new ByteArrayOutputStream();
        result.compress(Bitmap.CompressFormat.JPEG, 80, baos);

        byte[] data = baos.toByteArray();
        baos   = null;
        result = null;

        Uri    uri    = provider.create(ScribbleActivity.this, data, MediaUtil.IMAGE_JPEG, null);
        Intent intent = new Intent();
        intent.setData(uri);
        setResult(RESULT_OK, intent);

        finish();
      }

      @Override
      public void onFailure(ExecutionException e) {
        Log.w(TAG, e);
        Toast.makeText(ScribbleActivity.this, R.string.ScribbleActivity_save_failure, Toast.LENGTH_SHORT).show();
        finish();
      }
    });
  }

  private final MotionView.MotionViewCallback motionViewCallback = new MotionView.MotionViewCallback() {
    @Override
    public void onEntitySelected(@Nullable MotionEntity entity) {
      if (entity == null) {
        scribbleHud.enterMode(ScribbleHud.Mode.NONE);
      } else if (entity instanceof TextEntity) {
        int textColor = ((TextEntity) entity).getLayer().getFont().getColor();

        scribbleHud.enterMode(ScribbleHud.Mode.TEXT);
        scribbleHud.setActiveColor(textColor);
      } else {
        scribbleHud.enterMode(ScribbleHud.Mode.STICKER);
      }
    }

    @Override
    public void onEntityDoubleTap(@NonNull MotionEntity entity) {
      startTextEntityEditing();
    }
  };
}
