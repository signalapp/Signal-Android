package org.thoughtcrime.securesms.mediasend;

import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.scribbles.ScribbleFragment;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Encompasses the entire flow of sending media, starting from the selection process to the actual
 * captioning and editing of the content.
 *
 * This activity is intended to be launched via {@link #startActivityForResult(Intent, int)}.
 * It will return the {@link Media} that the user decided to send.
 */
public class MediaSendActivity extends PassphraseRequiredActionBarActivity implements MediaPickerFolderFragment.Controller,
                                                                                      MediaPickerItemFragment.Controller,
                                                                                      MediaSendFragment.Controller,
                                                                                      ScribbleFragment.Controller
{
  public static final String EXTRA_MEDIA     = "media";
  public static final String EXTRA_MESSAGE   = "message";
  public static final String EXTRA_TRANSPORT = "transport";

  private static final int MAX_PUSH = 32;
  private static final int MAX_SMS  = 1;

  private static final String KEY_ADDRESS   = "address";
  private static final String KEY_BODY      = "body";
  private static final String KEY_MEDIA     = "media";
  private static final String KEY_TRANSPORT = "transport";

  private static final String TAG_FOLDER_PICKER = "folder_picker";
  private static final String TAG_ITEM_PICKER   = "item_picker";
  private static final String TAG_SEND          = "send";

  private final DynamicTheme    dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private Recipient          recipient;
  private String             body;
  private TransportOption    transport;
  private MediaSendViewModel viewModel;

  /**
   * Get an intent to launch the media send flow starting with the picker.
   */
  public static Intent getIntent(@NonNull Context context, @NonNull Recipient recipient, @NonNull String body, @NonNull TransportOption transport) {
    Intent intent = new Intent(context, MediaSendActivity.class);
    intent.putExtra(KEY_ADDRESS, recipient.getAddress().serialize());
    intent.putExtra(KEY_TRANSPORT, transport);
    intent.putExtra(KEY_BODY, body);
    return intent;
  }

  /**
   * Get an intent to launch the media send flow with a specific list of media. Will jump right to
   * the editor screen.
   */
  public static Intent getIntent(@NonNull Context context,
                                 @NonNull List<Media> media,
                                 @NonNull Recipient recipient,
                                 @NonNull String body,
                                 @NonNull TransportOption transport)
  {
    Intent intent = getIntent(context, recipient, body, transport);
    intent.putParcelableArrayListExtra(KEY_MEDIA, new ArrayList<>(media));
    return intent;
  }

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    setContentView(R.layout.mediapicker_activity);
    setResult(RESULT_CANCELED);

    if (savedInstanceState != null) {
      return;
    }

    viewModel = ViewModelProviders.of(this, new MediaSendViewModel.Factory(new MediaRepository())).get(MediaSendViewModel.class);
    recipient = Recipient.from(this, Address.fromSerialized(getIntent().getStringExtra(KEY_ADDRESS)), true);
    body      = getIntent().getStringExtra(KEY_BODY);
    transport = getIntent().getParcelableExtra(KEY_TRANSPORT);

    List<Media> media = getIntent().getParcelableArrayListExtra(KEY_MEDIA);

    if (!Util.isEmpty(media)) {
      navigateToMediaSend(media, body, transport);
    } else {
      navigateToFolderPicker(recipient);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  public void onBackPressed() {
    MediaSendFragment sendFragment = (MediaSendFragment) getSupportFragmentManager().findFragmentByTag(TAG_SEND);
    if (sendFragment == null || !sendFragment.isVisible() || !sendFragment.handleBackPress()) {
      super.onBackPressed();
    }
  }

  @Override
  public void onFolderSelected(@NonNull MediaFolder folder) {
    viewModel.onFolderSelected(folder.getBucketId());

    MediaPickerItemFragment fragment = MediaPickerItemFragment.newInstance(folder.getBucketId(),
                                                                           folder.getTitle(),
                                                                           transport.isSms() ? MAX_SMS : MAX_PUSH);

    getSupportFragmentManager().beginTransaction()
                               .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                               .replace(R.id.mediapicker_fragment_container, fragment, TAG_ITEM_PICKER)
                               .addToBackStack(null)
                               .commit();
  }

  @Override
  public void onMediaSelected(@NonNull String bucketId, @NonNull Collection<Media> media) {
    MediaSendFragment fragment = MediaSendFragment.newInstance(body, transport, dynamicLanguage.getCurrentLocale());
    getSupportFragmentManager().beginTransaction()
                               .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                               .replace(R.id.mediapicker_fragment_container, fragment, TAG_SEND)
                               .addToBackStack(null)
                               .commit();
  }

  @Override
  public void onAddMediaClicked(@NonNull String bucketId) {
    MediaPickerFolderFragment folderFragment = MediaPickerFolderFragment.newInstance(recipient);
    MediaPickerItemFragment   itemFragment   = MediaPickerItemFragment.newInstance(bucketId,
                                                                                   "",
                                                                                   transport.isSms() ? MAX_SMS : MAX_PUSH);

    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.mediapicker_fragment_container, folderFragment, TAG_FOLDER_PICKER)
                               .addToBackStack(null)
                               .commit();

    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.mediapicker_fragment_container, itemFragment, TAG_ITEM_PICKER)
                               .addToBackStack(null)
                               .commit();
  }

  @Override
  public void onSendClicked(@NonNull List<Media> media, @NonNull String message, @NonNull TransportOption transport) {
    ArrayList<Media> mediaList = new ArrayList<>(media);
    Intent           intent    = new Intent();

    intent.putParcelableArrayListExtra(EXTRA_MEDIA, mediaList);
    intent.putExtra(EXTRA_MESSAGE, message);
    intent.putExtra(EXTRA_TRANSPORT, transport);
    setResult(RESULT_OK, intent);
    finish();

    overridePendingTransition(R.anim.stationary, R.anim.camera_slide_to_bottom);
  }

  @Override
  public void onNoMediaAvailable() {
    setResult(RESULT_CANCELED);
    finish();
  }

  @Override
  public void onImageEditComplete(@NonNull Uri uri, int width, int height, long size, @NonNull Optional<String> message, @NonNull Optional<TransportOption> transport) {
    throw new UnsupportedOperationException("Callback unsupported.");
  }

  @Override
  public void onImageEditFailure() {
    throw new UnsupportedOperationException("Callback unsupported.");
  }

  @Override
  public void onTouchEventsNeeded(boolean needed) {
    MediaSendFragment fragment = (MediaSendFragment) getSupportFragmentManager().findFragmentByTag(TAG_SEND);
    if (fragment != null) {
      fragment.onTouchEventsNeeded(needed);
    }
  }

  private void navigateToMediaSend(List<Media> media, String body, TransportOption transport) {
    viewModel.setInitialSelectedMedia(media);

    MediaSendFragment sendFragment = MediaSendFragment.newInstance(body, transport, dynamicLanguage.getCurrentLocale());
    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.mediapicker_fragment_container, sendFragment, TAG_SEND)
                               .commit();
  }

  private void navigateToFolderPicker(@NonNull Recipient recipient) {
    MediaPickerFolderFragment folderFragment = MediaPickerFolderFragment.newInstance(recipient);

    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.mediapicker_fragment_container, folderFragment, TAG_FOLDER_PICKER)
                               .commit();
  }
}
