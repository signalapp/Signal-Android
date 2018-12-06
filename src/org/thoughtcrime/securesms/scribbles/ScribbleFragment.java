package org.thoughtcrime.securesms.scribbles;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.logging.Log;
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
import org.thoughtcrime.securesms.util.concurrent.LifecycleBoundTask;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import static android.app.Activity.RESULT_OK;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class ScribbleFragment extends Fragment implements ScribbleHud.EventListener, VerticalSlideColorPicker.OnColorChangeListener {

  private static final String TAG = ScribbleFragment.class.getSimpleName();

  private static final String KEY_IMAGE_URI = "image_uri";
  private static final String KEY_LOCALE    = "locale";
  private static final String KEY_TRANSPORT = "compose_mode";

  public static final int SELECT_STICKER_REQUEST_CODE = 123;

  private Controller    controller;
  private ScribbleHud   scribbleHud;
  private ScribbleView  scribbleView;
  private GlideRequests glideRequests;

  public static ScribbleFragment newInstance(@NonNull Uri imageUri, @NonNull Locale locale, Optional<TransportOption> transport) {
    Bundle args = new Bundle();
    args.putParcelable(KEY_IMAGE_URI, imageUri);
    args.putSerializable(KEY_LOCALE, locale);
    args.putParcelable(KEY_TRANSPORT, transport.orNull());

    ScribbleFragment fragment = new ScribbleFragment();
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (!(getActivity() instanceof Controller)) {
      throw new IllegalStateException("Parent activity must implement Controller interface.");
    }
    controller = (Controller) getActivity();
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.scribble_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    this.glideRequests = GlideApp.with(this);
    this.scribbleHud   = view.findViewById(R.id.scribble_hud);
    this.scribbleView  = view.findViewById(R.id.scribble_view);

    scribbleHud.setEventListener(this);
    scribbleHud.setTransport(Optional.fromNullable(getArguments().getParcelable(KEY_TRANSPORT)));
    scribbleHud.setFullscreen((getActivity().getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) > 0);

    scribbleView.setMotionViewCallback(motionViewCallback);
    scribbleView.setDrawingChangedListener(() -> scribbleHud.setColorPalette(scribbleView.getUniqueColors()));
    scribbleView.setDrawingMode(false);
    scribbleView.setImage(glideRequests, getArguments().getParcelable(KEY_IMAGE_URI));
  }

  public boolean isEmojiKeyboardVisible() {
    return scribbleHud.isInputOpen();
  }

  public void dismissEmojiKeyboard() {
    scribbleHud.dismissEmojiKeyboard();
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
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == RESULT_OK && requestCode == SELECT_STICKER_REQUEST_CODE && data != null) {
      final String stickerFile = data.getStringExtra(StickerSelectActivity.EXTRA_STICKER_FILE);

      LifecycleBoundTask.run(getLifecycle(), () -> {
        try {
          return BitmapFactory.decodeStream(getContext().getAssets().open(stickerFile));
        } catch (IOException e) {
          Log.w(TAG, e);
          return null;
        }
      }, bitmap -> {
        if (bitmap != null) {
          addSticker(bitmap);
        }
      });
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
        Intent intent = new Intent(getContext(), StickerSelectActivity.class);
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
  public void onEditComplete(@NonNull Optional<String> message, Optional<TransportOption> transport) {
    ListenableFuture<Bitmap> future = scribbleView.getRenderedImage(glideRequests);

    future.addListener(new ListenableFuture.Listener<Bitmap>() {
      @Override
      public void onSuccess(Bitmap result) {
        PersistentBlobProvider provider = PersistentBlobProvider.getInstance(getContext());
        ByteArrayOutputStream  baos     = new ByteArrayOutputStream();
        result.compress(Bitmap.CompressFormat.JPEG, 80, baos);

        byte[] data = baos.toByteArray();

        controller.onImageEditComplete(provider.create(getContext(), data, MediaUtil.IMAGE_JPEG, null),
                                       result.getWidth(),
                                       result.getHeight(),
                                       data.length,
                                       message,
                                       transport);
      }

      @Override
      public void onFailure(ExecutionException e) {
        Log.w(TAG, e);
        controller.onImageEditFailure();
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

  public interface Controller {
    void onImageEditComplete(@NonNull Uri uri, int width, int height, long size, @NonNull Optional<String> message, @NonNull Optional<TransportOption> transport);
    void onImageEditFailure();
  }
}
