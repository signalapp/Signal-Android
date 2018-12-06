package org.thoughtcrime.securesms.scribbles;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.Toast;

import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.whispersystems.libsignal.util.guava.Optional;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class ScribbleActivity extends PassphraseRequiredActionBarActivity implements ScribbleFragment.Controller {

  private static final String TAG = ScribbleActivity.class.getSimpleName();

  public static final int SCRIBBLE_REQUEST_CODE = 31424;

  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  @Override
  protected void onPreCreate() {
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    setContentView(R.layout.scribble_activity);

    if (savedInstanceState == null) {
      ScribbleFragment fragment = ScribbleFragment.newInstance(getIntent().getData(), dynamicLanguage.getCurrentLocale(), Optional.absent());
      getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, fragment).commit();
    }

    if (Build.VERSION.SDK_INT >= 19) {
      getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
                                                       View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicLanguage.onResume(this);
  }

  @Override
  public void onImageEditComplete(@NonNull Uri uri, int width, int height, long size, @NonNull Optional<String> message, @NonNull Optional<TransportOption> transport) {
    Intent intent = new Intent();
    intent.setData(uri);
    setResult(RESULT_OK, intent);

    finish();
  }

  @Override
  public void onImageEditFailure() {
    Toast.makeText(ScribbleActivity.this, R.string.ScribbleActivity_save_failure, Toast.LENGTH_SHORT).show();
    finish();
  }
}
