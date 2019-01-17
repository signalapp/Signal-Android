package org.thoughtcrime.securesms.camera;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.ImageView;
import android.widget.Toast;

import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.components.GlideDrawableListeningTarget;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.providers.MemoryBlobProvider;
import org.thoughtcrime.securesms.providers.PersistentBlobProvider;
import org.thoughtcrime.securesms.scribbles.ScribbleFragment;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.concurrent.AssertedSuccessListener;
import org.thoughtcrime.securesms.util.concurrent.LifecycleBoundTask;
import org.thoughtcrime.securesms.util.concurrent.SettableFuture;
import org.whispersystems.libsignal.util.guava.Optional;

public class CameraActivity extends PassphraseRequiredActionBarActivity implements Camera1Fragment.Controller,
                                                                                   ScribbleFragment.Controller
{

  private static final String TAG = CameraActivity.class.getSimpleName();

  private static final String TAG_CAMERA  = "camera";
  private static final String TAG_EDITOR  = "editor";

  private static final String KEY_TRANSPORT = "transport";

  public static final String EXTRA_MESSAGE   = "message";
  public static final String EXTRA_TRANSPORT = "transport";
  public static final String EXTRA_WIDTH     = "width";
  public static final String EXTRA_HEIGHT    = "height";
  public static final String EXTRA_SIZE      = "size";

  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private ImageView       snapshot;
  private TransportOption transport;
  private Uri             captureUri;
  private boolean         imageSent;

  public static Intent getIntent(@NonNull Context context, @NonNull TransportOption transport) {
    Intent intent = new Intent(context, CameraActivity.class);
    intent.putExtra(KEY_TRANSPORT, transport);
    return intent;
  }

  @Override
  protected void onPreCreate() {
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState, boolean ready) {
    setContentView(R.layout.camera_activity);

    snapshot  = findViewById(R.id.camera_snapshot);
    transport = getIntent().getParcelableExtra(KEY_TRANSPORT);

    if (savedInstanceState == null) {
      Camera1Fragment fragment = Camera1Fragment.newInstance();
      getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, fragment, TAG_CAMERA).commit();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicLanguage.onResume(this);
  }

  @Override
  public void onBackPressed() {
    ScribbleFragment editorFragment = (ScribbleFragment) getSupportFragmentManager().findFragmentByTag(TAG_EDITOR);
    if (editorFragment != null && editorFragment.isEmojiKeyboardVisible()) {
      editorFragment.dismissEmojiKeyboard();
    } else {
      if (editorFragment != null && captureUri != null) {
        Log.i(TAG, "Cleaning up unused capture: " + captureUri);
        MemoryBlobProvider.getInstance().delete(captureUri);
        captureUri = null;
      }
      super.onBackPressed();
      overridePendingTransition(R.anim.stationary, R.anim.camera_slide_to_bottom);
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();

    if (captureUri != null) {
      Log.i(TAG, "Cleaning up capture in onDestroy: " + captureUri);
      MemoryBlobProvider.getInstance().delete(captureUri);
    }
  }

  @Override
  public void onCameraError() {
    Toast.makeText(this, R.string.CameraActivity_camera_unavailable, Toast.LENGTH_SHORT).show();
    setResult(RESULT_CANCELED, new Intent());
    finish();
  }

  @Override
  public void onImageCaptured(@NonNull byte[] data) {
    Log.i(TAG, "Fast image captured.");

    captureUri = MemoryBlobProvider.getInstance().createUri(data);
    Log.i(TAG, "Fast image stored: " + captureUri.toString());

    SettableFuture<Boolean> result = new SettableFuture<>();
    GlideApp.with(this).load(new DecryptableStreamUriLoader.DecryptableUri(captureUri)).into(new GlideDrawableListeningTarget(snapshot, result));
    result.addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        ScribbleFragment fragment = ScribbleFragment.newInstance(captureUri, dynamicLanguage.getCurrentLocale(), Optional.of(transport));
        getSupportFragmentManager().beginTransaction()
                                   .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                                   .replace(R.id.fragment_container, fragment, TAG_EDITOR)
                                   .addToBackStack(null)
                                   .commit();
      }
    });
  }

  @Override
  public int getDisplayRotation() {
    return getWindowManager().getDefaultDisplay().getRotation();
  }

  @Override
  public void onImageEditComplete(@NonNull Uri uri, int width, int height, long size, @NonNull Optional<String> message, @NonNull Optional<TransportOption> transport) {
    imageSent = true;

    Intent intent = new Intent();
    intent.setData(uri);
    intent.putExtra(EXTRA_WIDTH, width);
    intent.putExtra(EXTRA_HEIGHT, height);
    intent.putExtra(EXTRA_SIZE, size);
    intent.putExtra(EXTRA_MESSAGE, message.or(""));
    intent.putExtra(EXTRA_TRANSPORT, transport.orNull());
    setResult(RESULT_OK, intent);
    finish();

    overridePendingTransition(R.anim.stationary, R.anim.camera_slide_to_bottom);
  }

  @Override
  public void onImageEditFailure() {
    Log.w(TAG, "Failed to save edited image.");
    Toast.makeText(this, R.string.CameraActivity_image_save_failure, Toast.LENGTH_SHORT).show();
    finish();
  }
}
