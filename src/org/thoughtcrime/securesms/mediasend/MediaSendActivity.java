package org.thoughtcrime.securesms.mediasend;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.util.Supplier;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.TransportOptions;
import org.thoughtcrime.securesms.components.ComposeText;
import org.thoughtcrime.securesms.components.InputAwareLayout;
import org.thoughtcrime.securesms.components.SendButton;
import org.thoughtcrime.securesms.components.TooltipPopup;
import org.thoughtcrime.securesms.components.emoji.EmojiEditText;
import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider;
import org.thoughtcrime.securesms.components.emoji.EmojiToggle;
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard;
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.imageeditor.model.EditorModel;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediapreview.MediaRailAdapter;
import org.thoughtcrime.securesms.mediasend.MediaSendViewModel.ViewOnceState;
import org.thoughtcrime.securesms.mms.GifSlide;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.scribbles.ImageEditorFragment;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.CharacterCalculator.CharacterState;
import org.thoughtcrime.securesms.util.Function3;
import org.thoughtcrime.securesms.util.IOFunction;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.views.Stub;
import org.thoughtcrime.securesms.video.VideoUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Encompasses the entire flow of sending media, starting from the selection process to the actual
 * captioning and editing of the content.
 *
 * This activity is intended to be launched via {@link #startActivityForResult(Intent, int)}.
 * It will return the {@link Media} that the user decided to send.
 */
public class MediaSendActivity extends PassphraseRequiredActionBarActivity implements MediaPickerFolderFragment.Controller,
                                                                                      MediaPickerItemFragment.Controller,
                                                                                      ImageEditorFragment.Controller,
                                                                                      CameraFragment.Controller,
                                                                                      CameraContactSelectionFragment.Controller,
                                                                                      ViewTreeObserver.OnGlobalLayoutListener,
                                                                                      MediaRailAdapter.RailItemListener,
                                                                                      InputAwareLayout.OnKeyboardShownListener,
                                                                                      InputAwareLayout.OnKeyboardHiddenListener
{
  private static final String TAG = MediaSendActivity.class.getSimpleName();

  public static final String EXTRA_MEDIA     = "media";
  public static final String EXTRA_MESSAGE   = "message";
  public static final String EXTRA_TRANSPORT = "transport";
  public static final String EXTRA_VIEW_ONCE = "view_once";


  private static final String KEY_RECIPIENT = "recipient_id";
  private static final String KEY_BODY      = "body";
  private static final String KEY_MEDIA     = "media";
  private static final String KEY_TRANSPORT = "transport";
  private static final String KEY_IS_CAMERA = "is_camera";

  private static final String TAG_FOLDER_PICKER = "folder_picker";
  private static final String TAG_ITEM_PICKER   = "item_picker";
  private static final String TAG_SEND          = "send";
  private static final String TAG_CAMERA        = "camera";
  private static final String TAG_CONTACTS      = "contacts";

  private @Nullable LiveRecipient recipient;

  private TransportOption    transport;
  private MediaSendViewModel viewModel;

  private InputAwareLayout    hud;
  private View                captionAndRail;
  private SendButton          sendButton;
  private ViewGroup           sendButtonContainer;
  private ComposeText         composeText;
  private ViewGroup           composeRow;
  private ViewGroup           composeContainer;
  private ViewGroup           countButton;
  private TextView            countButtonText;
  private View                continueButton;
  private ImageView           revealButton;
  private EmojiEditText       captionText;
  private EmojiToggle         emojiToggle;
  private Stub<MediaKeyboard> emojiDrawer;
  private TextView            charactersLeft;
  private RecyclerView        mediaRail;
  private MediaRailAdapter    mediaRailAdapter;

  private int visibleHeight;

  private final Rect visibleBounds = new Rect();

  /**
   * Get an intent to launch the media send flow starting with the picker.
   */
  public static Intent buildGalleryIntent(@NonNull Context context, @NonNull Recipient recipient, @NonNull String body, @NonNull TransportOption transport) {
    Intent intent = new Intent(context, MediaSendActivity.class);
    intent.putExtra(KEY_RECIPIENT, recipient.getId());
    intent.putExtra(KEY_TRANSPORT, transport);
    intent.putExtra(KEY_BODY, body);
    return intent;
  }

  public static Intent buildCameraFirstIntent(@NonNull Context context) {
    Intent intent = new Intent(context, MediaSendActivity.class);
    intent.putExtra(KEY_TRANSPORT, TransportOptions.getPushTransportOption(context));
    intent.putExtra(KEY_BODY, "");
    intent.putExtra(KEY_IS_CAMERA, true);
    return intent;
  }

  /**
   * Get an intent to launch the media send flow starting with the picker.
   */
  public static Intent buildCameraIntent(@NonNull Context context, @NonNull Recipient recipient, @NonNull TransportOption transport) {
    Intent intent = buildGalleryIntent(context, recipient, "", transport);
    intent.putExtra(KEY_IS_CAMERA, true);
    return intent;
  }

  /**
   * Get an intent to launch the media send flow with a specific list of media. Will jump right to
   * the editor screen.
   */
  public static Intent buildEditorIntent(@NonNull Context context,
                                         @NonNull List<Media> media,
                                         @NonNull Recipient recipient,
                                         @NonNull String body,
                                         @NonNull TransportOption transport)
  {
    Intent intent = buildGalleryIntent(context, recipient, body, transport);
    intent.putParcelableArrayListExtra(KEY_MEDIA, new ArrayList<>(media));
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    setContentView(R.layout.mediasend_activity);
    setResult(RESULT_CANCELED);

    if (savedInstanceState != null) {
      return;
    }

    hud                 = findViewById(R.id.mediasend_hud);
    captionAndRail      = findViewById(R.id.mediasend_caption_and_rail);
    sendButton          = findViewById(R.id.mediasend_send_button);
    sendButtonContainer = findViewById(R.id.mediasend_send_button_bkg);
    composeText         = findViewById(R.id.mediasend_compose_text);
    composeRow          = findViewById(R.id.mediasend_compose_row);
    composeContainer    = findViewById(R.id.mediasend_compose_container);
    countButton         = findViewById(R.id.mediasend_count_button);
    countButtonText     = findViewById(R.id.mediasend_count_button_text);
    continueButton      = findViewById(R.id.mediasend_continue_button);
    revealButton        = findViewById(R.id.mediasend_reveal_toggle);
    captionText         = findViewById(R.id.mediasend_caption);
    emojiToggle         = findViewById(R.id.mediasend_emoji_toggle);
    charactersLeft      = findViewById(R.id.mediasend_characters_left);
    mediaRail           = findViewById(R.id.mediasend_media_rail);
    emojiDrawer         = new Stub<>(findViewById(R.id.mediasend_emoji_drawer_stub));

    RecipientId recipientId = getIntent().getParcelableExtra(KEY_RECIPIENT);
    if (recipientId != null) {
      recipient = Recipient.live(recipientId);
    }

    viewModel = ViewModelProviders.of(this, new MediaSendViewModel.Factory(getApplication(), new MediaRepository())).get(MediaSendViewModel.class);
    transport = getIntent().getParcelableExtra(KEY_TRANSPORT);

    viewModel.setTransport(transport);
    viewModel.setRecipient(recipient != null ? recipient.get() : null);
    viewModel.onBodyChanged(getIntent().getStringExtra(KEY_BODY));

    List<Media> media    = getIntent().getParcelableArrayListExtra(KEY_MEDIA);
    boolean     isCamera = getIntent().getBooleanExtra(KEY_IS_CAMERA, false);

    if (isCamera) {
      Fragment fragment = CameraFragment.newInstance();
      getSupportFragmentManager().beginTransaction()
                                 .replace(R.id.mediasend_fragment_container, fragment, TAG_CAMERA)
                                 .commit();

    } else if (!Util.isEmpty(media)) {
      viewModel.onSelectedMediaChanged(this, media);

      Fragment fragment = MediaSendFragment.newInstance(Locale.getDefault());
      getSupportFragmentManager().beginTransaction()
                                 .replace(R.id.mediasend_fragment_container, fragment, TAG_SEND)
                                 .commit();
    } else {
      MediaPickerFolderFragment fragment = MediaPickerFolderFragment.newInstance(this, recipient != null ? recipient.get() : null);
      getSupportFragmentManager().beginTransaction()
                                 .replace(R.id.mediasend_fragment_container, fragment, TAG_FOLDER_PICKER)
                                 .commit();
    }

    sendButton.setOnClickListener(v -> {
      if (hud.isKeyboardOpen()) {
        hud.hideSoftkey(composeText, null);
      }

      sendButton.setEnabled(false);

      MediaSendFragment fragment = getMediaSendFragment();

      if (fragment != null) {
        processMedia(fragment.getAllMedia(), fragment.getSavedState(), processedMedia -> {
          setActivityResultAndFinish(processedMedia, composeText.getTextTrimmed(), transport);
        });
      } else {
        throw new AssertionError("No editor fragment available!");
      }
    });

    sendButton.setOnLongClickListener(v -> true);

    sendButton.addOnTransportChangedListener((newTransport, manuallySelected) -> {
      presentCharactersRemaining();
      composeText.setTransport(newTransport);
      sendButtonContainer.getBackground().setColorFilter(newTransport.getBackgroundColor(), PorterDuff.Mode.MULTIPLY);
      sendButtonContainer.getBackground().invalidateSelf();
    });

    ComposeKeyPressedListener composeKeyPressedListener = new ComposeKeyPressedListener();

    composeText.setOnKeyListener(composeKeyPressedListener);
    composeText.addTextChangedListener(composeKeyPressedListener);
    composeText.setOnClickListener(composeKeyPressedListener);
    composeText.setOnFocusChangeListener(composeKeyPressedListener);

    captionText.clearFocus();
    composeText.requestFocus();

    mediaRailAdapter = new MediaRailAdapter(GlideApp.with(this), this, true);
    mediaRail.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
    mediaRail.setAdapter(mediaRailAdapter);

    hud.getRootView().getViewTreeObserver().addOnGlobalLayoutListener(this);
    hud.addOnKeyboardShownListener(this);
    hud.addOnKeyboardHiddenListener(this);

    captionText.addTextChangedListener(new SimpleTextWatcher() {
      @Override
      public void onTextChanged(String text) {
        viewModel.onCaptionChanged(text);
      }
    });

    sendButton.setTransport(transport);
    sendButton.disableTransport(transport.getType() == TransportOption.Type.SMS ? TransportOption.Type.TEXTSECURE : TransportOption.Type.SMS);

    countButton.setOnClickListener(v -> navigateToMediaSend(Locale.getDefault()));

    composeText.append(viewModel.getBody());

    if (recipient != null) {
      recipient.observe(this, this::presentRecipient);
    }

    presentRecipient(recipient != null ? recipient.get() : null);

    composeText.setOnEditorActionListener((v, actionId, event) -> {
      boolean isSend = actionId == EditorInfo.IME_ACTION_SEND;
      if (isSend) sendButton.performClick();
      return isSend;
    });

    if (TextSecurePreferences.isSystemEmojiPreferred(this)) {
      emojiToggle.setVisibility(View.GONE);
    } else {
      emojiToggle.setOnClickListener(this::onEmojiToggleClicked);
    }

    initViewModel();

    revealButton.setOnClickListener(v -> viewModel.onRevealButtonToggled());
    continueButton.setOnClickListener(v -> navigateToContactSelect());
  }

  @Override
  public void onBackPressed() {
    MediaSendFragment sendFragment  = (MediaSendFragment) getSupportFragmentManager().findFragmentByTag(TAG_SEND);

    if (sendFragment == null || !sendFragment.isVisible() || !hud.isInputOpen()) {
      super.onBackPressed();
    } else {
      hud.hideCurrentInput(composeText);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onFolderSelected(@NonNull MediaFolder folder) {
    viewModel.onFolderSelected(folder.getBucketId());

    MediaPickerItemFragment fragment = MediaPickerItemFragment.newInstance(folder.getBucketId(), folder.getTitle(), viewModel.getMaxSelection());
    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.mediasend_fragment_container, fragment, TAG_ITEM_PICKER)
                               .addToBackStack(null)
                               .commit();
  }

  @Override
  public void onMediaSelected(@NonNull Media media) {
    viewModel.onSingleMediaSelected(this, media);
    navigateToMediaSend(Locale.getDefault());
  }

  @Override
  public void onTouchEventsNeeded(boolean needed) {
    MediaSendFragment fragment = (MediaSendFragment) getSupportFragmentManager().findFragmentByTag(TAG_SEND);
    if (fragment != null) {
      fragment.onTouchEventsNeeded(needed);
    }
  }

  @Override
  public void onCameraError() {
    Toast.makeText(this, R.string.MediaSendActivity_camera_unavailable, Toast.LENGTH_SHORT).show();
    setResult(RESULT_CANCELED, new Intent());
    finish();
  }

  @Override
  public void onVideoCaptureError() {
    Vibrator vibrator = ServiceUtil.getVibrator(this);
    vibrator.vibrate(50);
  }

  @Override
  public void onImageCaptured(@NonNull byte[] data, int width, int height) {
    Log.i(TAG, "Camera image captured.");
    onMediaCaptured(() -> data,
                    ignored -> (long) data.length,
                    (blobProvider, bytes, ignored) -> blobProvider.forData(bytes),
                    MediaUtil.IMAGE_JPEG,
                    width,
                    height);
  }

  @Override
  public void onVideoCaptured(@NonNull FileDescriptor fd) {
    Log.i(TAG, "Camera video captured.");
    onMediaCaptured(() -> new FileInputStream(fd),
                    fin -> fin.getChannel().size(),
                    BlobProvider::forData,
                    VideoUtil.RECORDED_VIDEO_CONTENT_TYPE,
                    0,
                    0);
  }


  private <T> void onMediaCaptured(Supplier<T> dataSupplier,
                                   IOFunction<T, Long> getLength,
                                   Function3<BlobProvider, T, Long, BlobProvider.BlobBuilder> createBlobBuilder,
                                   String mimeType,
                                   int width,
                                   int height)
  {
    SimpleTask.run(getLifecycle(), () -> {
      try {

        T    data   = dataSupplier.get();
        long length = getLength.apply(data);

        Uri uri = createBlobBuilder.apply(BlobProvider.getInstance(), data, length)
            .withMimeType(mimeType)
            .createForSingleSessionOnDisk(this);

        return new Media(
            uri,
            mimeType,
            System.currentTimeMillis(),
            width,
            height,
            length,
            Optional.of(Media.ALL_MEDIA_BUCKET_ID),
            Optional.absent()
        );
      } catch (IOException e) {
        return null;
      }
    }, media -> {
      if (media == null) {
        onNoMediaAvailable();
        return;
      }

      Log.i(TAG, "Camera capture stored: " + media.getUri().toString());

      viewModel.onMediaCaptured(media);
      navigateToMediaSend(Locale.getDefault());
    });
  }

  @Override
  public int getDisplayRotation() {
    return getWindowManager().getDefaultDisplay().getRotation();
  }

  @Override
  public void onCameraCountButtonClicked() {
    navigateToMediaSend(Locale.getDefault());
  }

  @Override
  public void onGalleryClicked() {
    MediaPickerFolderFragment folderFragment = MediaPickerFolderFragment.newInstance(this, recipient != null ? recipient.get() : null);

    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.mediasend_fragment_container, folderFragment, TAG_FOLDER_PICKER)
                               .setCustomAnimations(R.anim.slide_from_bottom, R.anim.stationary, R.anim.slide_to_bottom, R.anim.stationary)
                               .addToBackStack(null)
                               .commit();
  }

  @Override
  public void onRequestFullScreen(boolean fullScreen, boolean hideKeyboard) {
    if (captionAndRail != null) {
      captionAndRail.setVisibility(fullScreen ? View.GONE : View.VISIBLE);
    }
    if (hideKeyboard && hud.isKeyboardOpen()) {
      hud.hideSoftkey(composeText, null);
    }
  }

  @Override
  public void onGlobalLayout() {
    hud.getRootView().getWindowVisibleDisplayFrame(visibleBounds);

    int currentVisibleHeight = visibleBounds.height();

    if (currentVisibleHeight != visibleHeight) {
      hud.getLayoutParams().height = currentVisibleHeight;
      hud.layout(visibleBounds.left, visibleBounds.top, visibleBounds.right, visibleBounds.bottom);
      hud.requestLayout();

      visibleHeight = currentVisibleHeight;
    }
  }

  @Override
  public void onKeyboardHidden() {
    viewModel.onKeyboardHidden(sendButton.getSelectedTransport().isSms());
  }

  @Override
  public void onKeyboardShown() {
    viewModel.onKeyboardShown(composeText.hasFocus(), captionText.hasFocus(), sendButton.getSelectedTransport().isSms());
  }

  @Override
  public void onRailItemClicked(int distanceFromActive) {
    if (getMediaSendFragment() != null) {
      viewModel.onPageChanged(getMediaSendFragment().getCurrentImagePosition() + distanceFromActive);
    }
  }

  @Override
  public void onRailItemDeleteClicked(int distanceFromActive) {
    if (getMediaSendFragment() != null) {
      viewModel.onMediaItemRemoved(this, getMediaSendFragment().getCurrentImagePosition() + distanceFromActive);
    }
  }

  @Override
  public void onCameraSelected() {
    navigateToCamera();
  }

  @Override
  public void onCameraContactsSendClicked(@NonNull List<Recipient> recipients) {
    MediaSendFragment fragment = getMediaSendFragment();

    if (fragment != null) {
      processMedia(fragment.getAllMedia(), fragment.getSavedState(), processedMedia -> {
        sendMessages(recipients, processedMedia, composeText.getTextTrimmed(), transport);
      });
    } else {
      throw new AssertionError("No editor fragment available!");
    }
  }

  public void onAddMediaClicked(@NonNull String bucketId) {
    hud.hideCurrentInput(composeText);

    // TODO: Get actual folder title somehow
    MediaPickerFolderFragment folderFragment = MediaPickerFolderFragment.newInstance(this, recipient != null ? recipient.get() : null);
    MediaPickerItemFragment   itemFragment   = MediaPickerItemFragment.newInstance(bucketId, "", viewModel.getMaxSelection());

    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.mediasend_fragment_container, folderFragment, TAG_FOLDER_PICKER)
                               .addToBackStack(null)
                               .commit();

    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.mediasend_fragment_container, itemFragment, TAG_ITEM_PICKER)
                               .addToBackStack(null)
                               .commit();
  }

  public void onNoMediaAvailable() {
    setResult(RESULT_CANCELED);
    finish();
  }

  private void initViewModel() {
    viewModel.getHudState().observe(this, state -> {
      if (state == null) return;

      hud.setVisibility(state.isHudVisible() ? View.VISIBLE : View.GONE);
      composeContainer.setVisibility(state.isComposeVisible() ? View.VISIBLE : (state.getViewOnceState() == ViewOnceState.GONE ? View.GONE : View.INVISIBLE));
      captionText.setVisibility(state.isCaptionVisible() ? View.VISIBLE : View.GONE);

      int captionBackground;

      if (state.getRailState() == MediaSendViewModel.RailState.VIEWABLE) {
        captionBackground = R.color.core_grey_90;
      } else if (state.getViewOnceState() == ViewOnceState.ENABLED) {
        captionBackground = 0;
      } else {
        captionBackground = R.color.transparent_black_40;
      }

      captionAndRail.setBackgroundResource(captionBackground);

      switch (state.getButtonState()) {
        case SEND:
          sendButtonContainer.setVisibility(View.VISIBLE);
          continueButton.setVisibility(View.GONE);
          countButton.setVisibility(View.GONE);
          break;
        case COUNT:
          sendButtonContainer.setVisibility(View.GONE);
          continueButton.setVisibility(View.GONE);
          countButton.setVisibility(View.VISIBLE);
          countButtonText.setText(String.valueOf(state.getSelectionCount()));
          break;
        case CONTINUE:
          sendButtonContainer.setVisibility(View.GONE);
          countButton.setVisibility(View.GONE);
          continueButton.setVisibility(View.VISIBLE);

          if (!TextSecurePreferences.hasSeendCameraFirstTooltip(this)) {
            TooltipPopup.forTarget(continueButton)
                        .setText(R.string.MediaSendActivity_select_recipients)
                        .show(TooltipPopup.POSITION_ABOVE);
            TextSecurePreferences.setHasSeenCameraFirstTooltip(this, true);
          }
          break;
        case GONE:
          sendButtonContainer.setVisibility(View.GONE);
          countButton.setVisibility(View.GONE);
          break;
      }

      switch (state.getViewOnceState()) {
        case ENABLED:
          revealButton.setVisibility(View.VISIBLE);
          revealButton.setImageResource(R.drawable.ic_view_once_32);
          break;
        case DISABLED:
          revealButton.setVisibility(View.VISIBLE);
          revealButton.setImageResource(R.drawable.ic_view_infinite_32);
          break;
        case GONE:
          revealButton.setVisibility(View.GONE);
          break;
      }

      switch (state.getRailState()) {
        case INTERACTIVE:
          mediaRail.setVisibility(View.VISIBLE);
          mediaRailAdapter.setEditable(true);
          mediaRailAdapter.setInteractive(true);
          break;
        case VIEWABLE:
          mediaRail.setVisibility(View.VISIBLE);
          mediaRailAdapter.setEditable(false);
          mediaRailAdapter.setInteractive(false);
          break;
        case GONE:
          mediaRail.setVisibility(View.GONE);
          break;
      }

      if (composeContainer.getVisibility() == View.GONE && sendButtonContainer.getVisibility() == View.GONE) {
        composeRow.setVisibility(View.GONE);
      } else {
        composeRow.setVisibility(View.VISIBLE);
      }
    });

    viewModel.getSelectedMedia().observe(this, media -> {
      mediaRailAdapter.setMedia(media);
    });

    viewModel.getPosition().observe(this, position -> {
      if (position == null || position < 0) return;

      MediaSendFragment fragment = getMediaSendFragment();
      if (fragment != null && fragment.getAllMedia().size() > position) {
        captionText.setText(fragment.getAllMedia().get(position).getCaption().or(""));
      }

      mediaRailAdapter.setActivePosition(position);
      mediaRail.smoothScrollToPosition(position);
    });

    viewModel.getBucketId().observe(this, bucketId -> {
      if (bucketId == null) return;
      mediaRailAdapter.setAddButtonListener(() -> onAddMediaClicked(bucketId));
    });

    viewModel.getError().observe(this, error -> {
      if (error == null) return;

      switch (error) {
        case NO_ITEMS:
          onNoMediaAvailable();
          break;
        case ITEM_TOO_LARGE:
          Toast.makeText(this, R.string.MediaSendActivity_an_item_was_removed_because_it_exceeded_the_size_limit, Toast.LENGTH_LONG).show();
          break;
        case TOO_MANY_ITEMS:
          int maxSelection = viewModel.getMaxSelection();
          Toast.makeText(this, getResources().getQuantityString(R.plurals.MediaSendActivity_cant_share_more_than_n_items, maxSelection, maxSelection), Toast.LENGTH_SHORT).show();
          break;
      }
    });
  }

  private void presentRecipient(@Nullable Recipient recipient) {
    if (recipient == null) {
      composeText.setHint(R.string.MediaSendActivity_message);
    } else if (recipient.isLocalNumber()) {
      composeText.setHint(getString(R.string.note_to_self), null);
    } else {
      composeText.setHint(getString(R.string.MediaSendActivity_message_to_s, recipient.getDisplayName(this)), null);
    }

  }

  private void navigateToMediaSend(@NonNull Locale locale) {
    MediaSendFragment fragment     = MediaSendFragment.newInstance(locale);
    String            backstackTag = null;

    if (getSupportFragmentManager().findFragmentByTag(TAG_SEND) != null) {
      getSupportFragmentManager().popBackStack(TAG_SEND, FragmentManager.POP_BACK_STACK_INCLUSIVE);
      backstackTag = TAG_SEND;
    }

    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.mediasend_fragment_container, fragment, TAG_SEND)
                               .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                               .addToBackStack(backstackTag)
                               .commit();
  }

  private void navigateToCamera() {
    Permissions.with(this)
               .request(Manifest.permission.CAMERA)
               .ifNecessary()
               .withRationaleDialog(getString(R.string.ConversationActivity_to_capture_photos_and_video_allow_signal_access_to_the_camera), R.drawable.ic_camera_solid_24)
               .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_the_camera_permission_to_take_photos_or_video))
               .onAllGranted(() -> {
                 Fragment fragment = getOrCreateCameraFragment();
                 getSupportFragmentManager().beginTransaction()
                                            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                                            .replace(R.id.mediasend_fragment_container, fragment, TAG_CAMERA)
                                            .addToBackStack(null)
                                            .commit();
               })
               .onAnyDenied(() -> Toast.makeText(MediaSendActivity.this, R.string.ConversationActivity_signal_needs_camera_permissions_to_take_photos_or_video, Toast.LENGTH_LONG).show())
               .execute();
  }

  private void navigateToContactSelect() {
    if (hud.isInputOpen()) {
      hud.hideCurrentInput(composeText);
    }

    Permissions.with(this)
               .request(Manifest.permission.READ_CONTACTS)
               .ifNecessary()
               .withPermanentDenialDialog(getString(R.string.MediaSendActivity_signal_needs_contacts_permission_in_order_to_show_your_contacts_but_it_has_been_permanently_denied))
               .onAllGranted(() -> {
                 Fragment contactFragment = CameraContactSelectionFragment.newInstance();
                 Fragment editorFragment  = getSupportFragmentManager().findFragmentByTag(TAG_SEND);

                 if (editorFragment == null) {
                   throw new AssertionError("No editor fragment available!");
                 }

                 getSupportFragmentManager().beginTransaction()
                                            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                                            .add(R.id.mediasend_fragment_container, contactFragment, TAG_CONTACTS)
                                            .hide(editorFragment)
                                            .addToBackStack(null)
                                            .commit();
               })
               .onAnyDenied(() -> Toast.makeText(MediaSendActivity.this, R.string.MediaSendActivity_signal_needs_access_to_your_contacts, Toast.LENGTH_LONG).show())
               .execute();
  }

  private Fragment getOrCreateCameraFragment() {
    Fragment fragment = getSupportFragmentManager().findFragmentByTag(TAG_CAMERA);
    return fragment != null ? fragment : CameraFragment.newInstance();
  }

  private EmojiEditText getActiveInputField() {
    if (captionText.hasFocus()) return captionText;
    else                        return composeText;
  }


  private void presentCharactersRemaining() {
    String          messageBody     = composeText.getTextTrimmed();
    TransportOption transportOption = sendButton.getSelectedTransport();
    CharacterState characterState  = transportOption.calculateCharacters(messageBody);

    if (characterState.charactersRemaining <= 15 || characterState.messagesSpent > 1) {
      charactersLeft.setText(String.format(Locale.getDefault(),
                                           "%d/%d (%d)",
                                           characterState.charactersRemaining,
                                           characterState.maxTotalMessageSize,
                                           characterState.messagesSpent));
      charactersLeft.setVisibility(View.VISIBLE);
    } else {
      charactersLeft.setVisibility(View.GONE);
    }
  }


  private void onEmojiToggleClicked(View v) {
    if (!emojiDrawer.resolved()) {
      emojiDrawer.get().setProviders(0, new EmojiKeyboardProvider(this, new EmojiKeyboardProvider.EmojiEventListener() {
        @Override
        public void onKeyEvent(KeyEvent keyEvent) {
          getActiveInputField().dispatchKeyEvent(keyEvent);
        }

        @Override
        public void onEmojiSelected(String emoji) {
          getActiveInputField().insertEmoji(emoji);
        }
      }));
      emojiToggle.attach(emojiDrawer.get());
    }

    if (hud.getCurrentInput() == emojiDrawer.get()) {
      hud.showSoftkey(composeText);
    } else {
      hud.hideSoftkey(composeText, () -> hud.post(() -> hud.show(composeText, emojiDrawer.get())));
    }
  }

  @SuppressLint("StaticFieldLeak")
  private void processMedia(@NonNull List<Media> mediaList, @NonNull Map<Uri, Object> savedState, @NonNull OnProcessComplete callback) {
    Map<Media, EditorModel> modelsToRender = new HashMap<>();

    for (Media media : mediaList) {
      Object state = savedState.get(media.getUri());

      if (state instanceof ImageEditorFragment.Data) {
        EditorModel model = ((ImageEditorFragment.Data) state).readModel();
        if (model != null && model.isChanged()) {
          modelsToRender.put(media, model);
        }
      }
    }

    new AsyncTask<Void, Void, List<Media>>() {

      private Stopwatch   renderTimer;
      private Runnable    progressTimer;
      private AlertDialog dialog;

      @Override
      protected void onPreExecute() {
        renderTimer   = new Stopwatch("ProcessMedia");
        progressTimer = () -> {
          dialog = new AlertDialog.Builder(new ContextThemeWrapper(MediaSendActivity.this, R.style.TextSecure_MediaSendProgressDialog))
                                  .setView(R.layout.progress_dialog)
                                  .setCancelable(false)
                                  .create();
          dialog.show();
          dialog.getWindow().setLayout(getResources().getDimensionPixelSize(R.dimen.mediasend_progress_dialog_size),
                                       getResources().getDimensionPixelSize(R.dimen.mediasend_progress_dialog_size));
        };
        Util.runOnMainDelayed(progressTimer, 250);
      }

      @Override
      protected List<Media> doInBackground(Void... voids) {
        Context               context      = MediaSendActivity.this;
        List<Media>           updatedMedia = new ArrayList<>(mediaList.size());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        for (Media media : mediaList) {
          EditorModel modelToRender = modelsToRender.get(media);
          if (modelToRender != null) {
            Bitmap bitmap = modelToRender.render(context);
            try {
              outputStream.reset();
              bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);

              Uri uri = BlobProvider.getInstance()
                                    .forData(outputStream.toByteArray())
                                    .withMimeType(MediaUtil.IMAGE_JPEG)
                                    .createForSingleSessionOnDisk(context);

              Media updated = new Media(uri, MediaUtil.IMAGE_JPEG, media.getDate(), bitmap.getWidth(), bitmap.getHeight(), outputStream.size(), media.getBucketId(), media.getCaption());

              updatedMedia.add(updated);
              renderTimer.split("item");
            } catch (IOException e) {
              Log.w(TAG, "Failed to render image. Using base image.");
              updatedMedia.add(media);
            } finally {
              bitmap.recycle();
            }
          } else {
            updatedMedia.add(media);
          }
        }
        return updatedMedia;
      }

      @Override
      protected void onPostExecute(List<Media> media) {
        callback.onComplete(media);
        Util.cancelRunnableOnMain(progressTimer);
        if (dialog != null) {
          dialog.dismiss();
        }
        renderTimer.stop(TAG);
      }
    }.executeOnExecutor(SignalExecutors.BOUNDED);
  }

  private @Nullable MediaSendFragment getMediaSendFragment() {
    return (MediaSendFragment) getSupportFragmentManager().findFragmentByTag(TAG_SEND);
  }

  private void setActivityResultAndFinish(@NonNull List<Media> media, @NonNull String message, @NonNull TransportOption transport) {
    viewModel.onSendClicked();

    ArrayList<Media> mediaList = new ArrayList<>(media);

    if (mediaList.size() > 0) {
      Intent intent = new Intent();

      intent.putParcelableArrayListExtra(EXTRA_MEDIA, mediaList);
      intent.putExtra(EXTRA_MESSAGE, viewModel.isViewOnce() ? "" : message);
      intent.putExtra(EXTRA_TRANSPORT, transport);
      intent.putExtra(EXTRA_VIEW_ONCE, viewModel.isViewOnce());

      setResult(RESULT_OK, intent);
    } else {
      setResult(RESULT_CANCELED);
    }

    finish();

    overridePendingTransition(R.anim.stationary, R.anim.camera_slide_to_bottom);
  }

  private void sendMessages(@NonNull List<Recipient> recipients, @NonNull List<Media> media, @NonNull String body, @NonNull TransportOption transport) {
    SimpleTask.run(() -> {
      List<OutgoingSecureMediaMessage> messages = new ArrayList<>(recipients.size());

      for (Recipient recipient : recipients) {
        SlideDeck            slideDeck = buildSlideDeck(media);
        OutgoingMediaMessage message   = new OutgoingMediaMessage(recipient,
                                                                  body,
                                                                  slideDeck.asAttachments(),
                                                                  System.currentTimeMillis(),
                                                                  -1,
                                                                  recipient.getExpireMessages() * 1000,
                                                                  viewModel.isViewOnce(),
                                                                  ThreadDatabase.DistributionTypes.DEFAULT,
                                                                  null,
                                                                  Collections.emptyList(),
                                                                  Collections.emptyList(),
                                                                  Collections.emptyList(),
                                                                  Collections.emptyList());

        messages.add(new OutgoingSecureMediaMessage(message));

        // XXX We must do this to avoid sending out messages to the same recipient with the same
        //     sentTimestamp. If we do this, they'll be considered dupes by the receiver.
        Util.sleep(5);
      }

      MessageSender.sendMediaBroadcast(this, messages);
      return null;
    }, (nothing) -> {
      finish();
    });
  }

  private @NonNull SlideDeck buildSlideDeck(@NonNull List<Media> mediaList) {
    SlideDeck slideDeck = new SlideDeck();

    for (Media mediaItem : mediaList) {
      if (MediaUtil.isVideoType(mediaItem.getMimeType())) {
        slideDeck.addSlide(new VideoSlide(this, mediaItem.getUri(), 0, mediaItem.getCaption().orNull()));
      } else if (MediaUtil.isGif(mediaItem.getMimeType())) {
        slideDeck.addSlide(new GifSlide(this, mediaItem.getUri(), 0, mediaItem.getWidth(), mediaItem.getHeight(), mediaItem.getCaption().orNull()));
      } else if (MediaUtil.isImageType(mediaItem.getMimeType())) {
        slideDeck.addSlide(new ImageSlide(this, mediaItem.getUri(), 0, mediaItem.getWidth(), mediaItem.getHeight(), mediaItem.getCaption().orNull(), null));
      } else {
        Log.w(TAG, "Asked to send an unexpected mimeType: '" + mediaItem.getMimeType() + "'. Skipping.");
      }
    }

    return slideDeck;
  }

  private class ComposeKeyPressedListener implements View.OnKeyListener, View.OnClickListener, TextWatcher, View.OnFocusChangeListener {

    int beforeLength;

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
          if (TextSecurePreferences.isEnterSendsEnabled(getApplicationContext())) {
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public void onClick(View v) {
      hud.showSoftkey(composeText);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,int after) {
      beforeLength = composeText.getTextTrimmed().length();
    }

    @Override
    public void afterTextChanged(Editable s) {
      presentCharactersRemaining();
      viewModel.onBodyChanged(s);
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before,int count) {}

    @Override
    public void onFocusChange(View v, boolean hasFocus) {}
  }

  private interface OnProcessComplete {
    void onComplete(@NonNull List<Media> media);
  }
}
