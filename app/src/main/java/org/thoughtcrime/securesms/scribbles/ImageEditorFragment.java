package org.thoughtcrime.securesms.scribbles;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.imageeditor.ColorableRenderer;
import org.thoughtcrime.securesms.imageeditor.ImageEditorView;
import org.thoughtcrime.securesms.imageeditor.Renderer;
import org.thoughtcrime.securesms.imageeditor.model.EditorElement;
import org.thoughtcrime.securesms.imageeditor.model.EditorModel;
import org.thoughtcrime.securesms.imageeditor.renderers.MultiLineTextRenderer;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediasend.MediaSendPageFragment;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.mms.PushMediaConstraints;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.scribbles.widget.VerticalSlideColorPicker;
import org.thoughtcrime.securesms.stickers.StickerSearchRepository;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.ParcelUtil;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

import java.io.ByteArrayOutputStream;

import static android.app.Activity.RESULT_OK;

public final class ImageEditorFragment extends Fragment implements ImageEditorHud.EventListener,
                                                                   VerticalSlideColorPicker.OnColorChangeListener,
                                                                   MediaSendPageFragment {

  private static final String TAG = Log.tag(ImageEditorFragment.class);

  private static final String KEY_IMAGE_URI      = "image_uri";
  private static final String KEY_IS_AVATAR_MODE = "avatar_mode";

  private static final int SELECT_STICKER_REQUEST_CODE = 124;

  private EditorModel restoredModel;

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

  private Uri             imageUri;
  private Controller      controller;
  private ImageEditorHud  imageEditorHud;
  private ImageEditorView imageEditorView;

  public static ImageEditorFragment newInstanceForAvatar(@NonNull Uri imageUri) {
    ImageEditorFragment fragment = newInstance(imageUri);
    fragment.requireArguments().putBoolean(KEY_IS_AVATAR_MODE, true);
    return fragment;
  }

  public static ImageEditorFragment newInstance(@NonNull Uri imageUri) {
    Bundle args = new Bundle();
    args.putParcelable(KEY_IMAGE_URI, imageUri);

    ImageEditorFragment fragment = new ImageEditorFragment();
    fragment.setArguments(args);
    fragment.setUri(imageUri);
    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (!(getActivity() instanceof Controller)) {
      throw new IllegalStateException("Parent activity must implement Controller interface.");
    }
    controller = (Controller) getActivity();
    Bundle arguments = getArguments();
    if (arguments != null) {
      imageUri = arguments.getParcelable(KEY_IMAGE_URI);
    }

    if (imageUri == null) {
      throw new AssertionError("No KEY_IMAGE_URI supplied");
    }

    MediaConstraints mediaConstraints = new PushMediaConstraints();

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

    boolean isAvatarMode = requireArguments().getBoolean(KEY_IS_AVATAR_MODE, false);

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
      editorModel = isAvatarMode ? EditorModel.createForCircleEditing() : EditorModel.create();
      EditorElement image = new EditorElement(new UriGlideRenderer(imageUri, true, imageMaxWidth, imageMaxHeight));
      image.getFlags().setSelectable(false).persist();
      editorModel.addElement(image);
    }

    if (isAvatarMode) {
      imageEditorHud.setUpForAvatarEditing();
      imageEditorHud.enterMode(ImageEditorHud.Mode.CROP);
    }

    imageEditorView.setModel(editorModel);

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
        imageEditorView.startDrawing(0.01f, Paint.Cap.ROUND);
        break;
      }

      case HIGHLIGHT: {
        imageEditorView.startDrawing(0.03f, Paint.Cap.SQUARE);
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
  public void onUndo() {
    imageEditorView.getModel().undo();
    refreshUniqueColors();
  }

  @Override
  public void onDelete() {
    imageEditorView.deleteElement(currentSelection);
    refreshUniqueColors();
  }

  @Override
  public void onSave() {
    SaveAttachmentTask.showWarningDialog(requireContext(), (dialogInterface, i) -> {
      Permissions.with(this)
                 .request(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                 .ifNecessary()
                 .withPermanentDenialDialog(getString(R.string.MediaPreviewActivity_signal_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied))
                 .onAnyDenied(() -> Toast.makeText(requireContext(), R.string.MediaPreviewActivity_unable_to_write_to_external_storage_without_permission, Toast.LENGTH_LONG).show())
                 .onAllGranted(() -> {
                   SimpleTask.run(() -> {
                     ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                     Bitmap                image        = imageEditorView.getModel().render(requireContext());

                     image.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);

                     return BlobProvider.getInstance()
                                        .forData(outputStream.toByteArray())
                                        .withMimeType(MediaUtil.IMAGE_JPEG)
                                        .createForSingleUseInMemory();

                   }, uri -> {
                     SaveAttachmentTask saveTask = new SaveAttachmentTask(requireContext());
                     SaveAttachmentTask.Attachment attachment = new SaveAttachmentTask.Attachment(uri, MediaUtil.IMAGE_JPEG, System.currentTimeMillis(), null);
                     saveTask.executeOnExecutor(SignalExecutors.BOUNDED, attachment);
                   });
                 })
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

  private void refreshUniqueColors() {
    imageEditorHud.setColorPalette(imageEditorView.getModel().getUniqueColorsIgnoringAlpha());
  }

  private void onUndoRedoAvailabilityChanged(boolean undoAvailable, boolean redoAvailable) {
    imageEditorHud.setUndoAvailability(undoAvailable);
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
}
