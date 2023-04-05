package org.thoughtcrime.securesms.scribbles;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.FontUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.signal.imageeditor.core.Bounds;
import org.signal.imageeditor.core.ColorableRenderer;
import org.signal.imageeditor.core.ImageEditorView;
import org.signal.imageeditor.core.Renderer;
import org.signal.imageeditor.core.SelectableRenderer;
import org.signal.imageeditor.core.model.EditorElement;
import org.signal.imageeditor.core.model.EditorModel;
import org.signal.imageeditor.core.renderers.BezierDrawingRenderer;
import org.signal.imageeditor.core.renderers.FaceBlurRenderer;
import org.signal.imageeditor.core.renderers.MultiLineTextRenderer;
import org.signal.libsignal.protocol.util.Pair;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.animation.ResizeAnimation;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.fonts.FontTypefaceProvider;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.mediasend.MediaSendPageFragment;
import org.thoughtcrime.securesms.mediasend.v2.MediaAnimations;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.mms.PushMediaConstraints;
import org.thoughtcrime.securesms.mms.SentMediaQuality;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.scribbles.stickers.AnalogClockStickerRenderer;
import org.thoughtcrime.securesms.scribbles.stickers.DigitalClockStickerRenderer;
import org.thoughtcrime.securesms.scribbles.stickers.FeatureSticker;
import org.thoughtcrime.securesms.scribbles.stickers.TappableRenderer;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.ParcelUtil;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.StorageUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ThrottledDebouncer;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static android.app.Activity.RESULT_OK;

public final class ImageEditorFragment extends Fragment implements ImageEditorHudV2.EventListener,
                                                                   MediaSendPageFragment,
                                                                   TextEntryDialogFragment.Controller
{

  private static final String TAG = Log.tag(ImageEditorFragment.class);

  public static final boolean CAN_RENDER_EMOJI = FontUtil.canRenderEmojiAtFontSize(1024);

  private static final float PORTRAIT_ASPECT_RATIO  = 9 / 16f;

  private static final String KEY_IMAGE_URI = "image_uri";
  private static final String KEY_MODE      = "mode";

  private static final int SELECT_STICKER_REQUEST_CODE = 124;

  private static final int DRAW_HUD_PROTECTION = ViewUtil.dpToPx(72);
  private static final int CROP_HUD_PROTECTION = ViewUtil.dpToPx(144);

  private EditorModel restoredModel;

  private Pair<Uri, FaceDetectionResult> cachedFaceDetection;

  @Nullable private EditorElement currentSelection;
  private           int           imageMaxHeight;
  private           int           imageMaxWidth;

  private final ThrottledDebouncer deleteFadeDebouncer = new ThrottledDebouncer(500);
  private       float              initialDialImageDegrees;
  private       float              initialDialScale;
  private       float              minDialScaleDown;

  public static class Data {
    private final Bundle bundle;

    public Data(Bundle bundle) {
      this.bundle = bundle;
    }

    public Data() {
      this(new Bundle());
    }

    void writeModel(@NonNull EditorModel model) {
      byte[] bytes = ParcelUtil.serialize(model);
      bundle.putByteArray("MODEL", bytes);
    }

    @Nullable
    public EditorModel readModel() {
      byte[] bytes = bundle.getByteArray("MODEL");
      if (bytes == null) {
        return null;
      }
      return ParcelUtil.deserialize(bytes, EditorModel.CREATOR);
    }

    public @NonNull Bundle getBundle() {
      return bundle;
    }
  }

  private Uri              imageUri;
  private Controller       controller;
  private ImageEditorHudV2 imageEditorHud;
  private ImageEditorView  imageEditorView;
  private boolean          hasMadeAnEditThisSession;
  private boolean          wasInTrashHitZone;

  public static ImageEditorFragment newInstanceForAvatarCapture(@NonNull Uri imageUri) {
    ImageEditorFragment fragment = newInstance(imageUri);
    fragment.requireArguments().putString(KEY_MODE, Mode.AVATAR_CAPTURE.code);
    return fragment;
  }

  public static ImageEditorFragment newInstanceForAvatarEdit(@NonNull Uri imageUri) {
    ImageEditorFragment fragment = newInstance(imageUri);
    fragment.requireArguments().putString(KEY_MODE, Mode.AVATAR_EDIT.code);
    return fragment;
  }

  public static ImageEditorFragment newInstance(@NonNull Uri imageUri) {
    Bundle args = new Bundle();
    args.putParcelable(KEY_IMAGE_URI, imageUri);
    args.putString(KEY_MODE, Mode.NORMAL.code);

    ImageEditorFragment fragment = new ImageEditorFragment();
    fragment.setArguments(args);
    fragment.setUri(imageUri);
    return fragment;
  }

  public void setMode(ImageEditorHudV2.Mode mode) {
    ImageEditorHudV2.Mode currentMode = imageEditorHud.getMode();
    if (currentMode == mode) {
      return;
    }

    imageEditorHud.setMode(mode);
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    updateViewPortScaling(imageEditorHud.getMode(), imageEditorHud.getMode(), newConfig.orientation, true);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Fragment parent = getParentFragment();
    if (parent instanceof Controller) {
      controller = (Controller) parent;
    } else if (getActivity() instanceof Controller) {
      controller = (Controller) getActivity();
    } else {
      throw new IllegalStateException("Parent must implement Controller interface.");
    }

    Bundle arguments = getArguments();
    if (arguments != null) {
      imageUri = arguments.getParcelable(KEY_IMAGE_URI);
    }

    if (imageUri == null) {
      throw new AssertionError("No KEY_IMAGE_URI supplied");
    }

    MediaConstraints mediaConstraints = new PushMediaConstraints(SentMediaQuality.HIGH);

    imageMaxWidth  = mediaConstraints.getImageMaxWidth(requireContext());
    imageMaxHeight = mediaConstraints.getImageMaxHeight(requireContext());
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.image_editor_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    controller.restoreState();

    Mode mode = Mode.getByCode(requireArguments().getString(KEY_MODE));

    if (mode == Mode.AVATAR_CAPTURE || mode == Mode.AVATAR_EDIT) {
      view.setPadding(
          0,
          ViewUtil.getStatusBarHeight(view),
          0,
          ViewUtil.getNavigationBarHeight(view)
      );
    }

    imageEditorHud  = view.findViewById(R.id.scribble_hud);
    imageEditorView = view.findViewById(R.id.image_editor_view);

    imageEditorView.setTypefaceProvider(new FontTypefaceProvider());
    if (!CAN_RENDER_EMOJI) {
      imageEditorView.addTextInputFilter(new RemoveEmojiTextFilter());
    }

    int width = getResources().getDisplayMetrics().widthPixels;
    int height = (int) ((16 / 9f) * width);
    imageEditorView.setMinimumHeight(height);
    imageEditorView.requestLayout();
    imageEditorHud.setBottomOfImageEditorView(getResources().getDisplayMetrics().heightPixels - height);

    imageEditorHud.setEventListener(this);

    imageEditorView.setDragListener(dragListener);
    imageEditorView.setTapListener(selectionListener);
    imageEditorView.setDrawingChangedListener(stillTouching -> onDrawingChanged(stillTouching, true));
    imageEditorView.setUndoRedoStackListener(this::onUndoRedoAvailabilityChanged);

    EditorModel editorModel = null;

    if (restoredModel != null) {
      editorModel   = restoredModel;
      restoredModel = null;
    }

    @ColorInt int blackoutColor = ContextCompat.getColor(requireContext(), R.color.signal_colorBackground);
    if (editorModel == null) {
      switch (mode) {
        case AVATAR_EDIT:
          editorModel = EditorModel.createForAvatarEdit(blackoutColor);
          break;
        case AVATAR_CAPTURE:
          editorModel = EditorModel.createForAvatarCapture(blackoutColor);
          break;
        default:
          editorModel = EditorModel.create(blackoutColor);
          break;
      }

      EditorElement image = new EditorElement(new UriGlideRenderer(imageUri, true, imageMaxWidth, imageMaxHeight, UriGlideRenderer.STRONG_BLUR, mainImageRequestListener));
      image.getFlags().setSelectable(false).persist();
      editorModel.addElement(image);
    } else {
      controller.onMainImageLoaded();
    }

    if (mode == Mode.AVATAR_CAPTURE || mode == Mode.AVATAR_EDIT) {
      imageEditorHud.setUpForAvatarEditing();
    }

    if (mode == Mode.AVATAR_CAPTURE) {
      imageEditorHud.enterMode(ImageEditorHudV2.Mode.CROP);
    }

    if (mode == Mode.AVATAR_EDIT) {
      imageEditorHud.enterMode(ImageEditorHudV2.Mode.DRAW);
    }

    imageEditorView.setModel(editorModel);

    if (!SignalStore.tooltips().hasSeenBlurHudIconTooltip()) {
      imageEditorHud.showBlurHudTooltip();
      SignalStore.tooltips().markBlurHudIconTooltipSeen();
    }

    onDrawingChanged(false, false);

    requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), onBackPressedCallback);
  }

  @Override
  public void setUri(@NonNull Uri uri) {
    this.imageUri = uri;
  }

  @NonNull
  @Override
  public Uri getUri() {
    return imageUri;
  }

  @Nullable
  @Override
  public View getPlaybackControls() {
    return null;
  }

  @Override
  public Object saveState() {
    Data data = new Data();
    data.writeModel(imageEditorView.getModel());
    return data;
  }

  @Override
  public void restoreState(@NonNull Object state) {
    if (state instanceof Data) {

      Data        data  = (Data) state;
      EditorModel model = data.readModel();

      if (model != null) {
        if (imageEditorView != null) {
          imageEditorView.setModel(model);
          onDrawingChanged(false, false);
        } else {
          this.restoredModel = model;
        }
      }
    } else {
      Log.w(TAG, "Received a bad saved state. Received class: " + state.getClass().getName());
    }
  }

  @Override
  public void notifyHidden() {
  }

  private void changeEntityColor(int selectedColor) {
    if (currentSelection != null) {
      Renderer renderer = currentSelection.getRenderer();
      if (renderer instanceof ColorableRenderer) {
        ((ColorableRenderer) renderer).setColor(selectedColor);
        onDrawingChanged(false, true);
      }
    }
  }

  private void startTextEntityEditing(@NonNull EditorElement textElement, boolean selectAll) {
    imageEditorView.startTextEditing(textElement);

    TextEntryDialogFragment.Companion.show(
        getChildFragmentManager(),
        textElement,
        TextSecurePreferences.isIncognitoKeyboardEnabled(requireContext()),
        selectAll,
        imageEditorHud.getColorIndex()
    );
  }

  @Override
  public void zoomToFitText(@NonNull EditorElement editorElement, @NonNull MultiLineTextRenderer textRenderer) {
    imageEditorView.zoomToFitText(editorElement, textRenderer);
  }

  @Override
  public void onTextStyleToggle() {
    if (currentSelection != null && currentSelection.getRenderer() instanceof MultiLineTextRenderer) {
      ((MultiLineTextRenderer) currentSelection.getRenderer()).nextMode();
    }
  }

  @Override
  public void onTextEntryDialogDismissed(boolean hasText) {
    imageEditorView.doneTextEditing();

    if (hasText) {
      imageEditorHud.setMode(ImageEditorHudV2.Mode.MOVE_TEXT);
    } else {
      onUndo();
      imageEditorHud.setMode(ImageEditorHudV2.Mode.DRAW);
    }
  }

  protected void addText() {
    String                initialText = "";
    int                   color       = imageEditorHud.getActiveColor();
    MultiLineTextRenderer renderer    = new MultiLineTextRenderer(initialText, color, MultiLineTextRenderer.Mode.REGULAR);
    EditorElement         element     = new EditorElement(renderer, EditorModel.Z_TEXT);

    imageEditorView.getModel().addElementCentered(element, 1);
    imageEditorView.invalidate();

    setCurrentSelection(element);

    startTextEntityEditing(element, true);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == RESULT_OK && requestCode == SELECT_STICKER_REQUEST_CODE && data != null) {
      Renderer renderer = null;
      if (data.hasExtra(ImageEditorStickerSelectActivity.EXTRA_FEATURE_STICKER)) {
        FeatureSticker sticker = FeatureSticker.fromType(data.getStringExtra(ImageEditorStickerSelectActivity.EXTRA_FEATURE_STICKER));
        switch (sticker) {
          case DIGITAL_CLOCK:
            renderer = new DigitalClockStickerRenderer(System.currentTimeMillis());
            break;
          case ANALOG_CLOCK:
            renderer = new AnalogClockStickerRenderer(System.currentTimeMillis());
            break;
        }
      } else {
        final Uri uri = data.getData();
        if (uri != null) {
          renderer = new UriGlideRenderer(uri, true, imageMaxWidth, imageMaxHeight);
        }
      }
      if (renderer != null) {
        EditorElement element = new EditorElement(renderer, EditorModel.Z_STICKERS);
        imageEditorView.getModel().addElementCentered(element, 0.4f);
        setCurrentSelection(element);
        hasMadeAnEditThisSession = true;
        imageEditorHud.setMode(ImageEditorHudV2.Mode.MOVE_STICKER);
      }
    } else {
      imageEditorHud.setMode(ImageEditorHudV2.Mode.DRAW);
    }
  }

  @Override
  public void onModeStarted(@NonNull ImageEditorHudV2.Mode mode, @NonNull ImageEditorHudV2.Mode previousMode) {
    onBackPressedCallback.setEnabled(shouldHandleOnBackPressed(mode));

    imageEditorView.setMode(ImageEditorView.Mode.MoveAndResize);
    imageEditorView.doneTextEditing();

    controller.onTouchEventsNeeded(mode != ImageEditorHudV2.Mode.NONE);

    updateViewPortScaling(mode, previousMode, getResources().getConfiguration().orientation, false);

    if (mode != ImageEditorHudV2.Mode.CROP) {
      imageEditorView.getModel().doneCrop();
    }

    imageEditorView.getModel()
                   .getTrash()
                   .getFlags()
                   .setVisible(mode == ImageEditorHudV2.Mode.DELETE)
                   .persist();

    updateHudDialRotation();

    switch (mode) {
      case CROP: {
        imageEditorView.getModel().startCrop();
        break;
      }

      case DRAW:
      case HIGHLIGHT: {
        onBrushWidthChange();
        break;
      }

      case BLUR: {
        onBrushWidthChange();
        imageEditorHud.setBlurFacesToggleEnabled(imageEditorView.getModel().hasFaceRenderer());
        break;
      }

      case TEXT: {
        addText();
        break;
      }

      case INSERT_STICKER: {
        Intent intent = new Intent(getContext(), ImageEditorStickerSelectActivity.class);
        startActivityForResult(intent, SELECT_STICKER_REQUEST_CODE);
        break;
      }

      case MOVE_STICKER:
        break;

      case MOVE_TEXT:
        break;

      case NONE: {
        setCurrentSelection(null);
        hasMadeAnEditThisSession = false;
        break;
      }
    }
  }

  private void updateViewPortScaling(
      @NonNull ImageEditorHudV2.Mode mode,
      @NonNull ImageEditorHudV2.Mode previousMode,
      int orientation,
      boolean force)
  {
    boolean shouldScaleViewPortForCurrentMode  = shouldScaleViewPort(mode);
    boolean shouldScaleViewPortForPreviousMode = shouldScaleViewPort(previousMode);
    int     hudProtection                      = getHudProtection(mode);

    if (shouldScaleViewPortForCurrentMode != shouldScaleViewPortForPreviousMode || force) {
      if (shouldScaleViewPortForCurrentMode) {
        scaleViewPortForDrawing(orientation, hudProtection);
      } else {
        restoreViewPortScaling(orientation);
      }
    }
  }

  private int getHudProtection(ImageEditorHudV2.Mode mode) {
    if (mode == ImageEditorHudV2.Mode.CROP) {
      return CROP_HUD_PROTECTION;
    } else {
      return DRAW_HUD_PROTECTION;
    }
  }

  @Override
  public void onColorChange(int color) {
    imageEditorView.setDrawingBrushColor(color);
    changeEntityColor(color);
  }

  @Override
  public void onTextColorChange(int colorIndex) {
    imageEditorHud.setColorIndex(colorIndex);
    onColorChange(imageEditorHud.getActiveColor());
  }

  @Override
  public void onBrushWidthChange() {
    ImageEditorHudV2.Mode mode = imageEditorHud.getMode();
    imageEditorView.startDrawing(imageEditorHud.getActiveBrushWidth(), mode == ImageEditorHudV2.Mode.HIGHLIGHT ? Paint.Cap.SQUARE : Paint.Cap.ROUND, mode == ImageEditorHudV2.Mode.BLUR);
  }

  @Override
  public void onBlurFacesToggled(boolean enabled) {
    EditorModel   model     = imageEditorView.getModel();
    EditorElement mainImage = model.getMainImage();
    if (mainImage == null) {
      imageEditorHud.hideBlurToast();
      return;
    }

    if (!enabled) {
      model.clearFaceRenderers();
      imageEditorHud.hideBlurToast();
      return;
    }

    Matrix inverseCropPosition = model.getInverseCropPosition();

    if (cachedFaceDetection != null) {
      if (cachedFaceDetection.first().equals(getUri()) && cachedFaceDetection.second().position.equals(inverseCropPosition)) {
        renderFaceBlurs(cachedFaceDetection.second());
        imageEditorHud.showBlurToast();
        return;
      } else {
        cachedFaceDetection = null;
      }
    }

    AlertDialog progress = SimpleProgressDialog.show(requireContext());
    mainImage.getFlags().setChildrenVisible(false);

    SimpleTask.run(getLifecycle(), () -> {
      if (mainImage.getRenderer() != null) {
        Bitmap bitmap = ((UriGlideRenderer) mainImage.getRenderer()).getBitmap();
        if (bitmap != null) {
          FaceDetector detector = new AndroidFaceDetector();

          Point  size   = model.getOutputSizeMaxWidth(1000);
          Bitmap render = model.render(ApplicationDependencies.getApplication(), size, new FontTypefaceProvider());
          try {
            return new FaceDetectionResult(detector.detect(render), new Point(render.getWidth(), render.getHeight()), inverseCropPosition);
          } finally {
            render.recycle();
            mainImage.getFlags().reset();
          }
        }
      }

      return new FaceDetectionResult(Collections.emptyList(), new Point(0, 0), new Matrix());
    }, result -> {
      mainImage.getFlags().reset();
      renderFaceBlurs(result);
      progress.dismiss();
      imageEditorHud.showBlurToast();
    });
  }


  @Override
  public void onClearAll() {
    if (imageEditorView != null) {
      imageEditorView.getModel().clearUndoStack();
      updateHudDialRotation();
    }
  }

  @Override
  public void onCancel() {
    if (hasMadeAnEditThisSession) {
      new MaterialAlertDialogBuilder(requireContext())
          .setTitle(R.string.MediaReviewImagePageFragment__discard_changes)
          .setMessage(R.string.MediaReviewImagePageFragment__youll_lose_any_changes)
          .setPositiveButton(R.string.MediaReviewImagePageFragment__discard, (d, w) -> {
            d.dismiss();
            imageEditorHud.setMode(ImageEditorHudV2.Mode.NONE);
            controller.onCancelEditing();
          })
          .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
          .show();
    } else {
      imageEditorHud.setMode(ImageEditorHudV2.Mode.NONE);
      controller.onCancelEditing();
    }
  }

  @Override
  public void onUndo() {
    imageEditorView.getModel().undo();
    imageEditorHud.setBlurFacesToggleEnabled(imageEditorView.getModel().hasFaceRenderer());
    updateHudDialRotation();
  }

  @Override
  public void onDelete() {
    imageEditorView.deleteElement(currentSelection);
  }

  @Override
  public void onSave() {
    SaveAttachmentTask.showWarningDialog(requireContext(), (dialogInterface, i) -> {
      if (StorageUtil.canWriteToMediaStore()) {
        performSaveToDisk();
        return;
      }

      Permissions.with(this)
                 .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                 .ifNecessary()
                 .withPermanentDenialDialog(getString(R.string.MediaPreviewActivity_signal_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied))
                 .onAnyDenied(() -> Toast.makeText(requireContext(), R.string.MediaPreviewActivity_unable_to_write_to_external_storage_without_permission, Toast.LENGTH_LONG).show())
                 .onAllGranted(this::performSaveToDisk)
                 .execute();
    });
  }

  @Override
  public void onFlipHorizontal() {
    imageEditorView.getModel().flipHorizontal();
  }

  @Override
  public void onRotate90AntiClockwise() {
    imageEditorView.getModel().rotate90anticlockwise();
  }

  @Override
  public void onCropAspectLock() {
    imageEditorView.getModel().setCropAspectLock(!imageEditorView.getModel().isCropAspectLocked());
  }

  @Override
  public boolean isCropAspectLocked() {
    return imageEditorView.getModel().isCropAspectLocked();
  }

  @Override
  public void onRequestFullScreen(boolean fullScreen, boolean hideKeyboard) {
    controller.onRequestFullScreen(fullScreen, hideKeyboard);
  }

  @Override
  public void onDone() {
    controller.onDoneEditing();
  }

  @Override
  public void onDialRotationGestureStarted() {
    float localScaleX = imageEditorView.getModel().getMainImage().getLocalScaleX();
    minDialScaleDown = initialDialScale / localScaleX;
    imageEditorView.getModel().pushUndoPoint();
    imageEditorView.getModel().updateUndoRedoAvailabilityState();
    initialDialImageDegrees = (float) Math.toDegrees(imageEditorView.getModel().getMainImage().getLocalRotationAngle());
  }

  @Override
  public void onDialRotationGestureFinished() {
    imageEditorView.getModel().getMainImage().commitEditorMatrix();
    imageEditorView.getModel().postEdit(true);
    imageEditorView.invalidate();
  }

  @Override
  public void onDialRotationChanged(float degrees) {
    imageEditorView.setMainImageEditorMatrixRotation(degrees - initialDialImageDegrees, minDialScaleDown);
  }

  private void updateHudDialRotation() {
    imageEditorHud.setDialRotation(getRotationDegreesRounded(imageEditorView.getModel().getMainImage()));
    initialDialScale = imageEditorView.getModel().getMainImage().getLocalScaleX();
  }

  private ResizeAnimation resizeAnimation;

  private void scaleViewPortForDrawing(int orientation, int protection) {
    if (resizeAnimation != null) {
      resizeAnimation.cancel();
    }

    float aspectRatio  = getAspectRatioForOrientation(orientation);
    int   targetWidth  = getWidthForOrientation(orientation) - ViewUtil.dpToPx(32);
    int   targetHeight = (int) ((1 / aspectRatio) * targetWidth);
    int   maxHeight    = getHeightForOrientation(orientation) - protection;

    if (targetHeight > maxHeight) {
      targetHeight = maxHeight;
      targetWidth  = Math.round(targetHeight * aspectRatio);
    }

    resizeAnimation = new ResizeAnimation(imageEditorView, targetWidth, targetHeight);
    resizeAnimation.setDuration(250);
    resizeAnimation.setInterpolator(MediaAnimations.getInterpolator());
    imageEditorView.startAnimation(resizeAnimation);
  }

  private void restoreViewPortScaling(int orientation) {
    if (resizeAnimation != null) {
      resizeAnimation.cancel();
    }

    int   maxHeight     = getHeightForOrientation(orientation);
    float aspectRatio   = getAspectRatioForOrientation(orientation);
    int   targetWidth   = getWidthForOrientation(orientation);
    int   targetHeight  = (int) ((1 / aspectRatio) * targetWidth);

    if (targetHeight > maxHeight) {
      targetHeight = maxHeight;
      targetWidth  = Math.round(targetHeight * aspectRatio);
    }

    resizeAnimation = new ResizeAnimation(imageEditorView, targetWidth, targetHeight);
    resizeAnimation.setDuration(250);
    resizeAnimation.setInterpolator(MediaAnimations.getInterpolator());
    imageEditorView.startAnimation(resizeAnimation);
  }

  private int getHeightForOrientation(int orientation) {
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      return Math.max(getResources().getDisplayMetrics().heightPixels, getResources().getDisplayMetrics().widthPixels);
    } else {
      return Math.min(getResources().getDisplayMetrics().heightPixels, getResources().getDisplayMetrics().widthPixels);
    }
  }

  private int getWidthForOrientation(int orientation) {
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      return Math.min(getResources().getDisplayMetrics().heightPixels, getResources().getDisplayMetrics().widthPixels);
    } else {
      return Math.max(getResources().getDisplayMetrics().heightPixels, getResources().getDisplayMetrics().widthPixels);
    }
  }

  private float getAspectRatioForOrientation(int orientation) {
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      return PORTRAIT_ASPECT_RATIO;
    } else {
      return 1f / PORTRAIT_ASPECT_RATIO;
    }
  }

  private static boolean shouldScaleViewPort(@NonNull ImageEditorHudV2.Mode mode) {
    return mode != ImageEditorHudV2.Mode.NONE;
  }

  private void performSaveToDisk() {
    SimpleTask.run(this::renderToSingleUseBlob, uri -> {
      SaveAttachmentTask            saveTask   = new SaveAttachmentTask(requireContext());
      SaveAttachmentTask.Attachment attachment = new SaveAttachmentTask.Attachment(uri, MediaUtil.IMAGE_JPEG, System.currentTimeMillis(), null);
      saveTask.executeOnExecutor(SignalExecutors.BOUNDED, attachment);
    });
  }

  @WorkerThread
  public @NonNull Uri renderToSingleUseBlob() {
    return renderToSingleUseBlob(requireContext(), imageEditorView.getModel());
  }

  @WorkerThread
  public static @NonNull Uri renderToSingleUseBlob(@NonNull Context context, @NonNull EditorModel editorModel) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    Bitmap                image        = editorModel.render(context, new FontTypefaceProvider());

    image.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
    image.recycle();

    return BlobProvider.getInstance()
                       .forData(outputStream.toByteArray())
                       .withMimeType(MediaUtil.IMAGE_JPEG)
                       .createForSingleUseInMemory();
  }

  private void onDrawingChanged(boolean stillTouching, boolean isUserEdit) {
    if (isUserEdit) {
      hasMadeAnEditThisSession = true;

    }
  }

  private void onUndoRedoAvailabilityChanged(boolean undoAvailable, boolean redoAvailable) {
    imageEditorHud.setUndoAvailability(undoAvailable);
  }

  private void renderFaceBlurs(@NonNull FaceDetectionResult result) {
    List<FaceDetector.Face> faces = result.faces;

    if (faces.isEmpty()) {
      cachedFaceDetection = null;
      return;
    }

    imageEditorView.getModel().pushUndoPoint();

    Matrix faceMatrix = new Matrix();

    for (FaceDetector.Face face : faces) {
      Renderer      faceBlurRenderer = new FaceBlurRenderer();
      EditorElement element          = new EditorElement(faceBlurRenderer, EditorModel.Z_MASK);
      Matrix        localMatrix      = element.getLocalMatrix();

      faceMatrix.setRectToRect(Bounds.FULL_BOUNDS, face.getBounds(), Matrix.ScaleToFit.FILL);

      localMatrix.set(result.position);
      localMatrix.preConcat(faceMatrix);

      element.getFlags().setEditable(false)
             .setSelectable(false)
             .persist();

      imageEditorView.getModel().addElementWithoutPushUndo(element);
    }

    imageEditorView.invalidate();

    cachedFaceDetection = new Pair<>(getUri(), result);
  }

  private boolean shouldHandleOnBackPressed(ImageEditorHudV2.Mode mode) {
    return mode == ImageEditorHudV2.Mode.CROP ||
           mode == ImageEditorHudV2.Mode.DRAW ||
           mode == ImageEditorHudV2.Mode.HIGHLIGHT ||
           mode == ImageEditorHudV2.Mode.BLUR ||
           mode == ImageEditorHudV2.Mode.TEXT ||
           mode == ImageEditorHudV2.Mode.MOVE_STICKER ||
           mode == ImageEditorHudV2.Mode.MOVE_TEXT ||
           mode == ImageEditorHudV2.Mode.INSERT_STICKER;
  }

  private void onPopEditorMode() {
    setCurrentSelection(null);

    switch (imageEditorHud.getMode()) {
      case NONE:
        return;
      case CROP:
        onCancel();
        break;
      case DRAW:
      case HIGHLIGHT:
      case BLUR:
        if (Mode.getByCode(requireArguments().getString(KEY_MODE)) == Mode.NORMAL) {
          onCancel();
        } else {
          controller.onTouchEventsNeeded(true);
          imageEditorHud.setMode(ImageEditorHudV2.Mode.CROP);
        }
        break;
      case INSERT_STICKER:
      case MOVE_STICKER:
      case MOVE_TEXT:
      case DELETE:
      case TEXT:
        controller.onTouchEventsNeeded(true);
        imageEditorHud.setMode(ImageEditorHudV2.Mode.DRAW);
        break;
    }
  }

  private final RequestListener<Bitmap> mainImageRequestListener = new RequestListener<Bitmap>() {
    @Override public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
      controller.onMainImageFailedToLoad();
      return false;
    }

    @Override public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
      controller.onMainImageLoaded();
      return false;
    }
  };

  public float getRotationDegreesRounded(@Nullable EditorElement editorElement) {
    if (editorElement == null) {
      return 0f;
    }
    return Math.round(Math.toDegrees(editorElement.getLocalRotationAngle()));
  }

  private final ImageEditorView.DragListener dragListener = new ImageEditorView.DragListener() {
    @Override
    public void onDragStarted(@Nullable EditorElement editorElement) {
      if (imageEditorHud.getMode() == ImageEditorHudV2.Mode.CROP) {
        updateHudDialRotation();
        return;
      }

      if (editorElement == null || editorElement.getRenderer() instanceof BezierDrawingRenderer) {
        setCurrentSelection(null);
      } else {
        setCurrentSelection(editorElement);
      }

      if (imageEditorView.getMode() == ImageEditorView.Mode.MoveAndResize) {
        imageEditorHud.setMode(ImageEditorHudV2.Mode.DELETE);
      } else {
        imageEditorHud.animate().alpha(0f);
      }
    }

    @Override
    public void onDragMoved(@Nullable EditorElement editorElement, boolean isInTrashHitZone) {
      if (imageEditorHud.getMode() == ImageEditorHudV2.Mode.CROP || editorElement == null) {
        updateHudDialRotation();
        return;
      }

      if (isInTrashHitZone) {
        deleteFadeDebouncer.publish(() -> {
          if (!wasInTrashHitZone) {
            wasInTrashHitZone = true;
            if (imageEditorHud.isHapticFeedbackEnabled()) {
              imageEditorHud.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
          }
          
          editorElement.animatePartialFadeOut(imageEditorView::invalidate);
        });
      } else {
        deleteFadeDebouncer.publish(() -> {
          wasInTrashHitZone = false;
          editorElement.animatePartialFadeIn(imageEditorView::invalidate);
        });
      }
    }

    @Override
    public void onDragEnded(@Nullable EditorElement editorElement, boolean isInTrashHitZone) {
      wasInTrashHitZone = false;
      imageEditorHud.animate().alpha(1f);
      if (imageEditorHud.getMode() == ImageEditorHudV2.Mode.CROP) {
        updateHudDialRotation();
        return;
      }

      if (isInTrashHitZone) {
        deleteFadeDebouncer.clear();
        onDelete();
        setCurrentSelection(null);
        onPopEditorMode();
      } else if (editorElement != null && editorElement.getRenderer() instanceof MultiLineTextRenderer) {
        editorElement.animatePartialFadeIn(imageEditorView::invalidate);

        if (imageEditorHud.getMode() != ImageEditorHudV2.Mode.TEXT) {
          imageEditorHud.setMode(ImageEditorHudV2.Mode.MOVE_TEXT);
        }
      } else if (editorElement != null && editorElement.getRenderer() instanceof UriGlideRenderer){
        editorElement.animatePartialFadeIn(imageEditorView::invalidate);
        imageEditorHud.setMode(ImageEditorHudV2.Mode.MOVE_STICKER);
      }
    }
  };

  private final ImageEditorView.TapListener selectionListener = new ImageEditorView.TapListener() {

    @Override
    public void onEntityDown(@Nullable EditorElement editorElement) {
      if (editorElement != null) {
        controller.onTouchEventsNeeded(true);
      }
    }

    @Override
    public void onEntitySingleTap(@Nullable EditorElement editorElement) {
      setCurrentSelection(editorElement);
      if (currentSelection != null) {
        if (editorElement.getRenderer() instanceof MultiLineTextRenderer) {
          setTextElement(editorElement, (ColorableRenderer) editorElement.getRenderer(), imageEditorView.isTextEditing());
        } else {
          if (editorElement.getRenderer() instanceof TappableRenderer) {
            ((TappableRenderer) editorElement.getRenderer()).onTapped();
          }
          imageEditorHud.setMode(ImageEditorHudV2.Mode.MOVE_STICKER);
        }
      } else {
        onPopEditorMode();
      }
    }

    @Override
    public void onEntityDoubleTap(@NonNull EditorElement editorElement) {
      setCurrentSelection(editorElement);
      if (editorElement.getRenderer() instanceof MultiLineTextRenderer) {
        setTextElement(editorElement, (ColorableRenderer) editorElement.getRenderer(), true);
      }
    }

    private void setTextElement(@NonNull EditorElement editorElement,
                                @NonNull ColorableRenderer colorableRenderer,
                                boolean startEditing)
    {
      int color = colorableRenderer.getColor();
      imageEditorHud.enterMode(ImageEditorHudV2.Mode.TEXT);
      imageEditorHud.setActiveColor(color);
      if (startEditing) {
        startTextEntityEditing(editorElement, false);
      }
    }
  };

  private void setCurrentSelection(@Nullable EditorElement currentSelection) {
    setSelectionState(this.currentSelection, false);

    this.currentSelection = currentSelection;

    setSelectionState(this.currentSelection, true);

    imageEditorView.invalidate();
  }

  private void setSelectionState(@Nullable EditorElement editorElement, boolean selected) {
    if (editorElement != null && editorElement.getRenderer() instanceof SelectableRenderer) {
      ((SelectableRenderer) editorElement.getRenderer()).onSelected(selected);
    }
    imageEditorView.getModel().setSelected(selected ? editorElement : null);
  }

  private final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(false) {
    @Override
    public void handleOnBackPressed() {
      onPopEditorMode();
    }
  };

  public interface Controller {
    void onTouchEventsNeeded(boolean needed);

    void onRequestFullScreen(boolean fullScreen, boolean hideKeyboard);

    void onDoneEditing();

    void onCancelEditing();

    void onMainImageLoaded();

    void onMainImageFailedToLoad();

    void restoreState();
  }

  private static class FaceDetectionResult {
    private final List<FaceDetector.Face> faces;
    private final Matrix                  position;

    private FaceDetectionResult(@NonNull List<FaceDetector.Face> faces, @NonNull Point imageSize, @NonNull Matrix position) {
      this.faces    = faces;
      this.position = new Matrix(position);

      Matrix imageProjectionMatrix = new Matrix();
      imageProjectionMatrix.setRectToRect(new RectF(0, 0, imageSize.x, imageSize.y), Bounds.FULL_BOUNDS, Matrix.ScaleToFit.FILL);
      this.position.preConcat(imageProjectionMatrix);
    }
  }

  private enum Mode {

    NORMAL("normal"),
    AVATAR_CAPTURE("avatar_capture"),
    AVATAR_EDIT("avatar_edit");

    private final String code;

    Mode(@NonNull String code) {
      this.code = code;
    }

    String getCode() {
      return code;
    }

    static Mode getByCode(@Nullable String code) {
      if (code == null) {
        return NORMAL;
      }

      for (Mode mode : values()) {
        if (Objects.equals(code, mode.code)) {
          return mode;
        }
      }

      return NORMAL;
    }
  }
}
