package org.thoughtcrime.securesms.scribbles;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.imageeditor.Bounds;
import org.thoughtcrime.securesms.imageeditor.ColorableRenderer;
import org.thoughtcrime.securesms.imageeditor.ImageEditorView;
import org.thoughtcrime.securesms.imageeditor.Renderer;
import org.thoughtcrime.securesms.imageeditor.model.EditorElement;
import org.thoughtcrime.securesms.imageeditor.model.EditorModel;
import org.thoughtcrime.securesms.imageeditor.renderers.FaceBlurRenderer;
import org.thoughtcrime.securesms.imageeditor.renderers.MultiLineTextRenderer;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.mediasend.MediaSendPageFragment;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.mms.PushMediaConstraints;
import org.thoughtcrime.securesms.mms.SentMediaQuality;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.scribbles.widget.VerticalSlideColorPicker;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.ParcelUtil;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.StorageUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;
import org.whispersystems.libsignal.util.Pair;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static android.app.Activity.RESULT_OK;

public final class ImageEditorFragment extends Fragment implements ImageEditorHud.EventListener,
                                                                   VerticalSlideColorPicker.OnColorChangeListener,
                                                                   MediaSendPageFragment {

  private static final String TAG = Log.tag(ImageEditorFragment.class);

  private static final String KEY_IMAGE_URI        = "image_uri";
  private static final String KEY_MODE             = "mode";

  private static final int SELECT_STICKER_REQUEST_CODE = 124;

  private EditorModel restoredModel;

  private Pair<Uri, FaceDetectionResult> cachedFaceDetection;

  @Nullable private EditorElement currentSelection;
            private int           imageMaxHeight;
            private int           imageMaxWidth;

  public static class Data {
    private final Bundle bundle;

    Data(Bundle bundle) {
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
  }

  private Uri              imageUri;
  private Controller       controller;
  private ImageEditorHud   imageEditorHud;
  private ImageEditorView  imageEditorView;

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

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Fragment parent = getParentFragment();
    if (parent instanceof Controller) {
      controller = (Controller) parent;
    } else if (getActivity() instanceof Controller) {
      controller = (Controller) getActivity();
    } else {
      throw new IllegalStateException("Parent activity must implement Controller interface.");
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

    Mode mode = Mode.getByCode(requireArguments().getString(KEY_MODE));

    imageEditorHud  = view.findViewById(R.id.scribble_hud);
    imageEditorView = view.findViewById(R.id.image_editor_view);

    imageEditorHud.setEventListener(this);

    imageEditorView.setTapListener(selectionListener);
    imageEditorView.setDrawingChangedListener(this::refreshUniqueColors);
    imageEditorView.setUndoRedoStackListener(this::onUndoRedoAvailabilityChanged);

    EditorModel editorModel = null;

    if (restoredModel != null) {
      editorModel = restoredModel;
      restoredModel = null;
    }

    if (editorModel == null) {
      switch (mode) {
        case AVATAR_EDIT:
          editorModel = EditorModel.createForAvatarEdit();
          break;
        case AVATAR_CAPTURE:
          editorModel = EditorModel.createForAvatarCapture();
          break;
        default:
          editorModel = EditorModel.create();
          break;
      }

      EditorElement image = new EditorElement(new UriGlideRenderer(imageUri, true, imageMaxWidth, imageMaxHeight));
      image.getFlags().setSelectable(false).persist();
      editorModel.addElement(image);
    }

    if (mode == Mode.AVATAR_CAPTURE || mode == Mode.AVATAR_EDIT) {
      imageEditorHud.setUpForAvatarEditing();
    }

    if (mode == Mode.AVATAR_CAPTURE) {
      imageEditorHud.enterMode(ImageEditorHud.Mode.CROP);
    }

    imageEditorView.setModel(editorModel);

    if (!SignalStore.tooltips().hasSeenBlurHudIconTooltip()) {
      imageEditorHud.showBlurHudTooltip();
      SignalStore.tooltips().markBlurHudIconTooltipSeen();
    }

    refreshUniqueColors();
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
          refreshUniqueColors();
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
        refreshUniqueColors();
      }
    }
  }

  private void startTextEntityEditing(@NonNull EditorElement textElement, boolean selectAll) {
    imageEditorView.startTextEditing(textElement, TextSecurePreferences.isIncognitoKeyboardEnabled(requireContext()), selectAll);
  }

  protected void addText() {
    String                initialText = "";
    int                   color       = imageEditorHud.getActiveColor();
    MultiLineTextRenderer renderer    = new MultiLineTextRenderer(initialText, color);
    EditorElement         element     = new EditorElement(renderer, EditorModel.Z_TEXT);

    imageEditorView.getModel().addElementCentered(element, 1);
    imageEditorView.invalidate();

    currentSelection = element;

    startTextEntityEditing(element, true);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == RESULT_OK && requestCode == SELECT_STICKER_REQUEST_CODE && data != null) {
      final Uri uri = data.getData();
      if (uri != null) {
        UriGlideRenderer renderer = new UriGlideRenderer(uri, true, imageMaxWidth, imageMaxHeight);
        EditorElement    element  = new EditorElement(renderer, EditorModel.Z_STICKERS);
        imageEditorView.getModel().addElementCentered(element, 0.2f);
        currentSelection = element;
        imageEditorHud.setMode(ImageEditorHud.Mode.MOVE_DELETE);
      }
    } else {
      imageEditorHud.setMode(ImageEditorHud.Mode.NONE);
    }
  }

  @Override
  public void onModeStarted(@NonNull ImageEditorHud.Mode mode) {
    imageEditorView.setMode(ImageEditorView.Mode.MoveAndResize);
    imageEditorView.doneTextEditing();

    controller.onTouchEventsNeeded(mode != ImageEditorHud.Mode.NONE);

    switch (mode) {
      case CROP: {
        imageEditorView.getModel().startCrop();
        break;
      }

      case DRAW: {
        imageEditorView.startDrawing(0.01f, Paint.Cap.ROUND, false);
        break;
      }

      case HIGHLIGHT: {
        imageEditorView.startDrawing(0.03f, Paint.Cap.SQUARE, false);
        break;
      }

      case BLUR: {
        imageEditorView.startDrawing(0.052f, Paint.Cap.ROUND, true);
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

      case MOVE_DELETE:
        break;

      case NONE: {
        imageEditorView.getModel().doneCrop();
        currentSelection = null;
        break;
      }
    }
  }

  @Override
  public void onColorChange(int color) {
    imageEditorView.setDrawingBrushColor(color);
    changeEntityColor(color);
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

          Point size = model.getOutputSizeMaxWidth(1000);
          Bitmap render = model.render(ApplicationDependencies.getApplication(), size);
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
  public void onUndo() {
    imageEditorView.getModel().undo();
    refreshUniqueColors();
    imageEditorHud.setBlurFacesToggleEnabled(imageEditorView.getModel().hasFaceRenderer());
  }

  @Override
  public void onDelete() {
    imageEditorView.deleteElement(currentSelection);
    refreshUniqueColors();
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
  public void onCropAspectLock(boolean locked) {
    imageEditorView.getModel().setCropAspectLock(locked);
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

  private void performSaveToDisk() {
    SimpleTask.run(this::renderToSingleUseBlob, uri -> {
      SaveAttachmentTask            saveTask   = new SaveAttachmentTask(requireContext());
      SaveAttachmentTask.Attachment attachment = new SaveAttachmentTask.Attachment(uri, MediaUtil.IMAGE_JPEG, System.currentTimeMillis(), null);
      saveTask.executeOnExecutor(SignalExecutors.BOUNDED, attachment);
    });
  }

  @WorkerThread
  public @NonNull Uri renderToSingleUseBlob() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    Bitmap                image        = imageEditorView.getModel().render(requireContext());

    image.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
    image.recycle();

    return BlobProvider.getInstance()
                       .forData(outputStream.toByteArray())
                       .withMimeType(MediaUtil.IMAGE_JPEG)
                       .createForSingleUseInMemory();
  }

  private void refreshUniqueColors() {
    imageEditorHud.setColorPalette(imageEditorView.getModel().getUniqueColorsIgnoringAlpha());
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
      Renderer         faceBlurRenderer = new FaceBlurRenderer();
      EditorElement    element          = new EditorElement(faceBlurRenderer, EditorModel.Z_MASK);
      Matrix           localMatrix      = element.getLocalMatrix();

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

  private final ImageEditorView.TapListener selectionListener = new ImageEditorView.TapListener() {

     @Override
     public void onEntityDown(@Nullable EditorElement editorElement) {
       if (editorElement != null) {
         controller.onTouchEventsNeeded(true);
       } else {
         currentSelection = null;
         controller.onTouchEventsNeeded(false);
         imageEditorHud.setMode(ImageEditorHud.Mode.NONE);
       }
     }

     @Override
     public void onEntitySingleTap(@Nullable EditorElement editorElement) {
       currentSelection = editorElement;
       if (currentSelection != null) {
         if (editorElement.getRenderer() instanceof MultiLineTextRenderer) {
           setTextElement(editorElement, (ColorableRenderer) editorElement.getRenderer(), imageEditorView.isTextEditing());
         } else {
           imageEditorHud.setMode(ImageEditorHud.Mode.MOVE_DELETE);
         }
       }
     }

     @Override
      public void onEntityDoubleTap(@NonNull EditorElement editorElement) {
        currentSelection = editorElement;
        if (editorElement.getRenderer() instanceof MultiLineTextRenderer) {
          setTextElement(editorElement, (ColorableRenderer) editorElement.getRenderer(), true);
        }
      }

     private void setTextElement(@NonNull EditorElement editorElement,
                                 @NonNull ColorableRenderer colorableRenderer,
                                 boolean startEditing)
     {
       int color = colorableRenderer.getColor();
       imageEditorHud.enterMode(ImageEditorHud.Mode.TEXT);
       imageEditorHud.setActiveColor(color);
       if (startEditing) {
         startTextEntityEditing(editorElement, false);
       }
     }
   };

  public interface Controller {
    void onTouchEventsNeeded(boolean needed);

    void onRequestFullScreen(boolean fullScreen, boolean hideKeyboard);

    void onDoneEditing();
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
