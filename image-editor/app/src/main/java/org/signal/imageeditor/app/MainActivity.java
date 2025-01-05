package org.signal.imageeditor.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.signal.imageeditor.app.renderers.UriRenderer;
import org.signal.imageeditor.app.renderers.UrlRenderer;
import org.signal.imageeditor.core.ImageEditorView;
import org.signal.imageeditor.core.RendererContext;
import org.signal.imageeditor.core.model.EditorElement;
import org.signal.imageeditor.core.model.EditorModel;
import org.signal.imageeditor.core.renderers.MultiLineTextRenderer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

public final class MainActivity extends AppCompatActivity {

  private static final String TAG = "MainActivity";

  private ImageEditorView imageEditorView;
  private Menu            menu;

  private final RendererContext.TypefaceProvider typefaceProvider = (context, renderer, invalidate) -> {
    if (Build.VERSION.SDK_INT < 26) {
      return Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
    } else {
      return new Typeface.Builder("")
          .setFallback("sans-serif")
          .setWeight(900)
          .build();
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main_activity);

    Toolbar toolbar = findViewById(R.id.toolbar);
    toolbar.setTitle(R.string.app_name_short);
    setSupportActionBar(toolbar);

    imageEditorView = findViewById(R.id.image_editor);

    imageEditorView.setTypefaceProvider(typefaceProvider);

    imageEditorView.setUndoRedoStackListener((undoAvailable, redoAvailable) -> {
      Log.d("ALAN", String.format("Undo/Redo available: %s, %s", undoAvailable ? "Y" : "N", redoAvailable ? "Y" : "N"));
      if (menu == null) return;
      MenuItem undo = menu.findItem(R.id.action_undo);
      MenuItem redo = menu.findItem(R.id.action_redo);
      if (undo != null) undo.setVisible(undoAvailable);
      if (redo != null) redo.setVisible(redoAvailable);
    });

    EditorModel model = null;
    if (savedInstanceState != null) {
      model = savedInstanceState.getParcelable("MODEL");
      Log.d("ALAN", "Restoring instance " + (model != null ? model.hashCode() : 0));
    }

    if (model == null) {
      model = initialModel();
      Log.d("ALAN", "New instance created " + model.hashCode());
    }

    imageEditorView.setModel(model);

    imageEditorView.setTapListener(new ImageEditorView.TapListener() {
      @Override
      public void onEntityDown(@Nullable EditorElement editorElement) {
        Log.d("ALAN", "Entity down " + editorElement);
      }

      @Override
      public void onEntitySingleTap(@Nullable EditorElement editorElement) {
        Log.d("ALAN", "Entity single tapped " + editorElement);
      }

      @Override
      public void onEntityDoubleTap(@NonNull EditorElement editorElement) {
        Log.d("ALAN", "Entity double tapped " + editorElement);
        if (editorElement.getRenderer() instanceof MultiLineTextRenderer) {
          imageEditorView.startTextEditing(editorElement);
        } else {
          imageEditorView.deleteElement(editorElement);
        }
      }
    });
  }

  private static EditorModel initialModel() {

    EditorModel model = EditorModel.create(0xFF000000);

    EditorElement image = new EditorElement(new UrlRenderer("https://cdn.aarp.net/content/dam/aarp/home-and-family/your-home/2018/06/1140-house-inheriting.imgcache.rev68c065601779c5d76b913cf9ec3a977e.jpg"));
    image.getFlags().setSelectable(false).persist();
    model.addElement(image);

    EditorElement elementC = new EditorElement(new UrlRenderer("https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/SNice.svg/220px-SNice.svg.png"));
    elementC.getLocalMatrix().postScale(0.2f, 0.2f);
    //elementC.getLocalMatrix().postRotate(30);
    model.addElement(elementC);

    EditorElement elementE = new EditorElement(new UrlRenderer("https://www.vitalessentialsraw.com/assets/images/background-images/laying-grey-cat.png"));
    elementE.getLocalMatrix().postScale(0.2f, 0.2f);
    //elementE.getLocalMatrix().postRotate(60);
    model.addElement(elementE);

    EditorElement elementD = new EditorElement(new UrlRenderer("https://petspluslubbocktx.com/files/2016/11/DC-Cat-Weight-Management.png"));
    elementD.getLocalMatrix().postScale(0.2f, 0.2f);
    //elementD.getLocalMatrix().postRotate(60);
    model.addElement(elementD);

    EditorElement elementF = new EditorElement(new UrlRenderer("https://purepng.com/public/uploads/large/purepng.com-black-top-hathatsstandard-sizeblacktop-14215263591972x0zh.png"));
    elementF.getLocalMatrix().postScale(0.2f, 0.2f);
    //elementF.getLocalMatrix().postRotatF(60);
    model.addElement(elementF);

    EditorElement elementG = new EditorElement(new UriRenderer(Uri.parse("file:///android_asset/food/apple.png")));
    elementG.getLocalMatrix().postScale(0.2f, 0.2f);
    //elementG.getLocalMatrix().postRotatG(60);
    model.addElement(elementG);

    EditorElement elementH = new EditorElement(new MultiLineTextRenderer("Hello, World!", 0xff0000ff, MultiLineTextRenderer.Mode.REGULAR));
    //elementH.getLocalMatrix().postScale(0.2f, 0.2f);
    model.addElement(elementH);

    EditorElement elementH2 = new EditorElement(new MultiLineTextRenderer("Hello, World 2!", 0xff0000ff, MultiLineTextRenderer.Mode.REGULAR));
    //elementH.getLocalMatrix().postScale(0.2f, 0.2f);
    model.addElement(elementH2);

    return model;
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putParcelable("MODEL", imageEditorView.getModel());
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.action_menu, menu);
    this.menu = menu;
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int itemId = item.getItemId();
    if (itemId == R.id.action_undo) {
      imageEditorView.getModel().undo();
      Log.d(TAG, String.format("Model is %s", imageEditorView.getModel().isChanged() ? "changed" : "unchanged"));
      return true;
    } else if (itemId == R.id.action_redo) {
      imageEditorView.getModel().redo();
      return true;
    } else if (itemId == R.id.action_crop) {
      imageEditorView.setMode(ImageEditorView.Mode.MoveAndResize);
      imageEditorView.getModel().startCrop();
      return true;
    } else if (itemId == R.id.action_done) {
      imageEditorView.setMode(ImageEditorView.Mode.MoveAndResize);
      imageEditorView.getModel().doneCrop();
      return true;
    } else if (itemId == R.id.action_draw) {
      imageEditorView.setDrawingBrushColor(0xffffff00);
      imageEditorView.startDrawing(0.02f, Paint.Cap.ROUND, false);
      return true;
    } else if (itemId == R.id.action_rotate_left_90) {
      imageEditorView.getModel().rotate90anticlockwise();
      return true;
    } else if (itemId == R.id.action_flip_horizontal) {
      imageEditorView.getModel().flipHorizontal();
      return true;
    } else if (itemId == R.id.action_edit_text) {
      editText();
      return true;
    } else if (itemId == R.id.action_lock_crop_aspect) {
      imageEditorView.getModel().setCropAspectLock(!imageEditorView.getModel().isCropAspectLocked());
      return true;
    } else if (itemId == R.id.action_save) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
          != PackageManager.PERMISSION_GRANTED)
      {
        ActivityCompat.requestPermissions(this,
                                          new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },
                                          0);
      } else {
        Bitmap bitmap = imageEditorView.getModel().render(this, typefaceProvider);
        try {
          Uri uri = saveBmp(bitmap);

          Intent intent = new Intent();
          intent.setAction(Intent.ACTION_VIEW);
          intent.setDataAndType(uri, "image/*");
          startActivity(intent);

        } finally {
          bitmap.recycle();
        }
      }
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void editText() {
    imageEditorView.getModel().getRoot().findElement(new Matrix(), new Matrix(), (element, inverseMatrix) -> {
      if (element.getRenderer() instanceof MultiLineTextRenderer) {
        imageEditorView.startTextEditing(element);
        return true;
      }
      return false;
    }
    );
  }

  private Uri saveBmp(Bitmap bitmap) {
    String path = Environment.getExternalStorageDirectory().toString();

    File filePath = new File(path);
    File imageEditor = new File(filePath, "ImageEditor");
    if (!imageEditor.exists()) {
      imageEditor.mkdir();
    }

    int counter = 0;
    File file;
    do {
      counter++;
      file = new File(imageEditor, String.format(Locale.US, "ImageEditor_%03d.jpg", counter));
    } while (file.exists());

    try {
      try (OutputStream stream = new FileOutputStream(file)) {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
      }
      return Uri.parse(MediaStore.Images.Media.insertImage(getContentResolver(), file.getAbsolutePath(), file.getName(), file.getName()));
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
