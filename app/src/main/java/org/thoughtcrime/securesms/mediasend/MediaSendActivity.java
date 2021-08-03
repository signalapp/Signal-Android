package org.thoughtcrime.securesms.mediasend;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.net.Uri;
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
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.util.Pair;
import androidx.core.util.Supplier;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.TransportOptions;
import org.thoughtcrime.securesms.components.ComposeText;
import org.thoughtcrime.securesms.components.InputAwareLayout;
import org.thoughtcrime.securesms.components.SendButton;
import org.thoughtcrime.securesms.components.TooltipPopup;
import org.thoughtcrime.securesms.components.emoji.EmojiEditText;
import org.thoughtcrime.securesms.components.emoji.EmojiEventListener;
import org.thoughtcrime.securesms.components.emoji.EmojiToggle;
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard;
import org.thoughtcrime.securesms.components.mention.MentionAnnotation;
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher;
import org.thoughtcrime.securesms.conversation.ui.mentions.MentionsPickerViewModel;
import org.thoughtcrime.securesms.imageeditor.model.EditorModel;
import org.thoughtcrime.securesms.keyboard.KeyboardPage;
import org.thoughtcrime.securesms.keyboard.KeyboardPagerViewModel;
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardPageFragment;
import org.thoughtcrime.securesms.keyboard.emoji.search.EmojiSearchFragment;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.mediapreview.MediaRailAdapter;
import org.thoughtcrime.securesms.mediasend.MediaSendViewModel.HudState;
import org.thoughtcrime.securesms.mediasend.MediaSendViewModel.ViewOnceState;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.SentMediaQuality;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.scribbles.ImageEditorFragment;
import org.thoughtcrime.securesms.util.CharacterCalculator.CharacterState;
import org.thoughtcrime.securesms.util.Function3;
import org.thoughtcrime.securesms.util.IOFunction;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;
import org.thoughtcrime.securesms.util.views.Stub;
import org.thoughtcrime.securesms.video.VideoUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Encompasses the entire flow of sending media, starting from the selection process to the actual
 * captioning and editing of the content.
 *
 * This activity is intended to be launched via {@link #startActivityForResult(Intent, int)}.
 * It will return the {@link Media} that the user decided to send.
 */
public class MediaSendActivity extends PassphraseRequiredActivity implements MediaPickerFolderFragment.Controller,
                                                                             MediaPickerItemFragment.Controller,
                                                                             ImageEditorFragment.Controller,
                                                                             MediaSendVideoFragment.Controller,
                                                                             CameraFragment.Controller,
                                                                             CameraContactSelectionFragment.Controller,
                                                                             ViewTreeObserver.OnGlobalLayoutListener,
                                                                             MediaRailAdapter.RailItemListener,
                                                                             InputAwareLayout.OnKeyboardShownListener,
                                                                             InputAwareLayout.OnKeyboardHiddenListener,
                                                                             EmojiEventListener,
                                                                             EmojiKeyboardPageFragment.Callback,
                                                                             EmojiSearchFragment.Callback
{
  private static final String TAG = Log.tag(MediaSendActivity.class);

  public static final String EXTRA_RESULT    = "result";

  private static final String KEY_RECIPIENT  = "recipient_id";
  private static final String KEY_RECIPIENTS = "recipient_ids";
  private static final String KEY_BODY       = "body";
  private static final String KEY_MEDIA      = "media";
  private static final String KEY_TRANSPORT  = "transport";
  private static final String KEY_IS_CAMERA  = "is_camera";

  private static final String TAG_FOLDER_PICKER = "folder_picker";
  private static final String TAG_ITEM_PICKER   = "item_picker";
  private static final String TAG_SEND          = "send";
  private static final String TAG_CAMERA        = "camera";
  private static final String TAG_CONTACTS      = "contacts";

  private @Nullable LiveRecipient recipient;

  private TransportOption         transport;
  private MediaSendViewModel      viewModel;
  private MentionsPickerViewModel mentionsViewModel;

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
  private AppCompatImageView  qualityButton;
  private EmojiEditText       captionText;
  private EmojiToggle         emojiToggle;
  private Stub<MediaKeyboard> emojiDrawer;
  private Stub<View>          mentionSuggestions;
  private TextView            charactersLeft;
  private RecyclerView        mediaRail;
  private MediaRailAdapter    mediaRailAdapter;

  private int visibleHeight;

  private final Rect visibleBounds = new Rect();

  /**
   * Get an intent to launch the media send flow starting with the picker.
   */
  public static Intent buildGalleryIntent(@NonNull Context context, @NonNull Recipient recipient, @Nullable CharSequence body, @NonNull TransportOption transport) {
    Intent intent = new Intent(context, MediaSendActivity.class);
    intent.putExtra(KEY_RECIPIENT, recipient.getId());
    intent.putExtra(KEY_TRANSPORT, transport);
    intent.putExtra(KEY_BODY, body == null ? "" : body);
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
                                         @NonNull CharSequence body,
                                         @NonNull TransportOption transport)
  {
    Intent intent = buildGalleryIntent(context, recipient, body, transport);
    intent.putParcelableArrayListExtra(KEY_MEDIA, new ArrayList<>(media));
    return intent;
  }

  public static Intent buildShareIntent(@NonNull  Context context,
                                        @NonNull  List<Media> media,
                                        @NonNull  List<RecipientId> recipientIds,
                                        @Nullable CharSequence body,
                                        @NonNull  TransportOption transportOption)
  {
    Intent intent = new Intent(context, MediaSendActivity.class);
    intent.putParcelableArrayListExtra(KEY_MEDIA, new ArrayList<>(media));
    intent.putExtra(KEY_TRANSPORT, transportOption);
    intent.putExtra(KEY_BODY, body == null ? "" : body);
    intent.putParcelableArrayListExtra(KEY_RECIPIENTS, new ArrayList<>(recipientIds));
    return intent;
  }

  @Override
  protected void attachBaseContext(@NonNull Context newBase) {
    getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    super.attachBaseContext(newBase);
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
    qualityButton       = findViewById(R.id.mediasend_quality_toggle);
    captionText         = findViewById(R.id.mediasend_caption);
    emojiToggle         = findViewById(R.id.mediasend_emoji_toggle);
    charactersLeft      = findViewById(R.id.mediasend_characters_left);
    mediaRail           = findViewById(R.id.mediasend_media_rail);
    emojiDrawer         = new Stub<>(findViewById(R.id.mediasend_emoji_drawer_stub));
    mentionSuggestions  = new Stub<>(findViewById(R.id.mediasend_mention_suggestions_stub));

    RecipientId recipientId = getIntent().getParcelableExtra(KEY_RECIPIENT);
    if (recipientId != null) {
      Log.i(TAG, "Preparing for " + recipientId);
      recipient = Recipient.live(recipientId);
    }

    List<RecipientId> recipientIds = getIntent().getParcelableArrayListExtra(KEY_RECIPIENTS);
    if (recipientIds != null && recipientIds.size() > 0) {
      Log.i(TAG, "Preparing for " + recipientIds);
    }


    viewModel = ViewModelProviders.of(this, new MediaSendViewModel.Factory(getApplication(), new MediaRepository())).get(MediaSendViewModel.class);
    transport = getIntent().getParcelableExtra(KEY_TRANSPORT);

    MeteredConnectivityObserver meteredConnectivityObserver = new MeteredConnectivityObserver(this, this);
    meteredConnectivityObserver.isMetered().observe(this, viewModel::onMeteredConnectivityStatusChanged);
    viewModel.onMeteredConnectivityStatusChanged(Optional.fromNullable(meteredConnectivityObserver.isMetered().getValue()).or(false));

    viewModel.setTransport(transport);
    viewModel.setRecipient(recipient != null ? recipient.get() : null);
    viewModel.onBodyChanged(getIntent().getCharSequenceExtra(KEY_BODY));

    List<Media> media    = getIntent().getParcelableArrayListExtra(KEY_MEDIA);
    boolean     isCamera = getIntent().getBooleanExtra(KEY_IS_CAMERA, false);

    if (isCamera) {
      Fragment fragment = CameraFragment.newInstance();
      getSupportFragmentManager().beginTransaction()
                                 .replace(R.id.mediasend_fragment_container, fragment, TAG_CAMERA)
                                 .commit();

    } else if (!Util.isEmpty(media)) {
      viewModel.onSelectedMediaChanged(this, media);

      Fragment fragment = MediaSendFragment.newInstance();
      getSupportFragmentManager().beginTransaction()
                                 .replace(R.id.mediasend_fragment_container, fragment, TAG_SEND)
                                 .commit();
    } else {
      MediaPickerFolderFragment fragment = MediaPickerFolderFragment.newInstance(this, recipient != null ? recipient.get() : null);
      getSupportFragmentManager().beginTransaction()
                                 .replace(R.id.mediasend_fragment_container, fragment, TAG_FOLDER_PICKER)
                                 .commit();
    }

    sendButton.setOnClickListener(v -> onSendClicked());

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

    countButton.setOnClickListener(v -> navigateToMediaSend());

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

    if (SignalStore.settings().isPreferSystemEmoji()) {
      emojiToggle.setVisibility(View.GONE);
    } else {
      emojiToggle.setOnClickListener(this::onEmojiToggleClicked);
    }

    initializeMentionsViewModel();
    initViewModel();

    revealButton.setOnClickListener(v -> viewModel.onRevealButtonToggled());

    qualityButton.setOnClickListener(v -> QualitySelectorBottomSheetDialog.show(getSupportFragmentManager()));

    continueButton.setOnClickListener(v -> {
      continueButton.setEnabled(false);
      if (recipientIds == null || recipientIds.isEmpty()) {
        navigateToContactSelect();
      } else {
        SimpleTask.run(getLifecycle(),
                       () -> Stream.of(recipientIds).map(Recipient::resolved).toList(),
                       this::onCameraContactsSendClicked);
      }
    });
  }

  @Override
  public void onBackPressed() {
    MediaSendFragment sendFragment  = (MediaSendFragment) getSupportFragmentManager().findFragmentByTag(TAG_SEND);

    if (sendFragment == null || !sendFragment.isVisible() || !hud.isInputOpen()) {
      if (captionAndRail != null) {
        captionAndRail.setVisibility(View.VISIBLE);
      }
      super.onBackPressed();
    } else {
      hud.hideCurrentInput(composeText);
    }
  }

  @SuppressLint("MissingSuperCall")
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
    navigateToMediaSend();
  }

  @Override
  public void onVideoBeginEdit(@NonNull Uri uri) {
    viewModel.onVideoBeginEdit(uri);
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

        return new Media(uri,
                         mimeType,
                         System.currentTimeMillis(),
                         width,
                         height,
                         length,
                         0,
                         false,
                         false,
                         Optional.of(Media.ALL_MEDIA_BUCKET_ID),
                         Optional.absent(),
                         Optional.absent());
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
      navigateToMediaSend();
    });
  }

  @Override
  public int getDisplayRotation() {
    return getWindowManager().getDefaultDisplay().getRotation();
  }

  @Override
  public void onCameraCountButtonClicked() {
    navigateToMediaSend();
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
  public void onDoneEditing() {
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
    onSend(recipients);
  }

  private void onSendClicked() {
    onSend(Collections.emptyList());
  }

  private void onSend(@NonNull List<Recipient> recipients) {
    MediaSendFragment fragment = getMediaSendFragment();

    if (fragment == null) {
      throw new AssertionError("No editor fragment available!");
    }

    if (hud.isKeyboardOpen()) {
      hud.hideSoftkey(composeText, null);
    }

    sendButton.setEnabled(false);

    fragment.pausePlayback();

    SimpleProgressDialog.DismissibleDialog dialog = SimpleProgressDialog.showDelayed(this, 300, 0);
    viewModel.onSendClicked(buildModelsToTransform(fragment, viewModel.getSentMediaQuality().getValue()), recipients, composeText.getMentions())
             .observe(this, result -> {
               dialog.dismiss();
               if (recipients.size() > 1) {
                 finishWithoutSettingResults();
               } else {
                 setActivityResultAndFinish(result);
               }
             });
  }

  private static Map<Media, MediaTransform> buildModelsToTransform(@NonNull MediaSendFragment fragment, @Nullable SentMediaQuality sentMediaQuality) {
    List<Media>                mediaList      = fragment.getAllMedia();
    Map<Uri, Object>           savedState     = fragment.getSavedState();
    Map<Media, MediaTransform> modelsToRender = new HashMap<>();

    for (Media media : mediaList) {
      Object state = savedState.get(media.getUri());

      if (state instanceof ImageEditorFragment.Data) {
        EditorModel model = ((ImageEditorFragment.Data) state).readModel();
        if (model != null && model.isChanged()) {
          modelsToRender.put(media, new ImageEditorModelRenderMediaTransform(model));
        }
      }

      if (state instanceof MediaSendVideoFragment.Data) {
        MediaSendVideoFragment.Data data = (MediaSendVideoFragment.Data) state;
        if (data.durationEdited) {
          modelsToRender.put(media, new VideoTrimTransform(data));
        }
      }

      if (sentMediaQuality == SentMediaQuality.HIGH) {
        MediaTransform existingTransform = modelsToRender.get(media);
        if (existingTransform == null) {
          modelsToRender.put(media, new SentMediaQualityTransform(sentMediaQuality));
        } else {
          modelsToRender.put(media, new CompositeMediaTransform(existingTransform, new SentMediaQualityTransform(sentMediaQuality)));
        }
      }
    }

    return modelsToRender;
  }

  private void onAddMediaClicked(@NonNull String bucketId) {
    Permissions.with(this)
               .request(Manifest.permission.READ_EXTERNAL_STORAGE)
               .ifNecessary()
               .withPermanentDenialDialog(getString(R.string.AttachmentKeyboard_Signal_needs_permission_to_show_your_photos_and_videos))
               .onAllGranted(() -> {
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
               })
               .onAnyDenied(() -> Toast.makeText(MediaSendActivity.this, R.string.AttachmentKeyboard_Signal_needs_permission_to_show_your_photos_and_videos, Toast.LENGTH_LONG).show())
               .execute();
  }

  private void onNoMediaAvailable() {
    setResult(RESULT_CANCELED);
    finish();
  }

  private void initViewModel() {
    LiveData<Pair<HudState, Boolean>> hudStateAndMentionShowing = LiveDataUtil.combineLatest(viewModel.getHudState(),
                                                                                             mentionsViewModel != null ? mentionsViewModel.isShowing()
                                                                                                                       : new MutableLiveData<>(false),
                                                                                             Pair::new);

    hudStateAndMentionShowing.observe(this, p -> {
      HudState state                  = Objects.requireNonNull(p.first);
      boolean  isMentionPickerShowing = Objects.requireNonNull(p.second);
      int      captionBackground      = R.color.transparent_black_40;

      if (state.getRailState() == MediaSendViewModel.RailState.VIEWABLE) {
        captionBackground = R.color.core_grey_90;
      } else if (isMentionPickerShowing){
        captionBackground = R.color.signal_background_dialog;
      }

      captionAndRail.setBackgroundResource(captionBackground);
    });

    viewModel.getHudState().observe(this, state -> {
      if (state == null) return;

      hud.setVisibility(state.isHudVisible() ? View.VISIBLE : View.GONE);
      composeContainer.setVisibility(state.isComposeVisible() ? View.VISIBLE : (state.getViewOnceState() == ViewOnceState.GONE ? View.GONE : View.INVISIBLE));
      captionText.setVisibility(state.isCaptionVisible() ? View.VISIBLE : View.GONE);

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

          if (!TextSecurePreferences.hasSeenCameraFirstTooltip(this) && !getIntent().hasExtra(KEY_RECIPIENTS)) {
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
          revealButton.setImageResource(R.drawable.ic_view_once_28);
          break;
        case DISABLED:
          revealButton.setVisibility(View.VISIBLE);
          revealButton.setImageResource(R.drawable.ic_view_infinite_28);
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

    viewModel.getShowMediaQualityToggle().observe(this, show -> qualityButton.setVisibility(show && !Util.isLowMemory(this) ? View.VISIBLE : View.GONE));
    viewModel.getSentMediaQuality().observe(this, q -> qualityButton.setImageResource(q == SentMediaQuality.STANDARD ? R.drawable.ic_quality_standard_32 : R.drawable.ic_quality_high_32));

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
        case ITEM_TOO_LARGE_OR_INVALID_TYPE:
          Toast.makeText(this, R.string.MediaSendActivity_an_item_was_removed_because_it_exceeded_the_size_limit_or_had_an_unknown_type, Toast.LENGTH_LONG).show();
          break;
        case ONLY_ITEM_TOO_LARGE:
          Toast.makeText(this, R.string.MediaSendActivity_an_item_was_removed_because_it_exceeded_the_size_limit, Toast.LENGTH_LONG).show();
          onNoMediaAvailable();
          break;
        case ONLY_ITEM_IS_INVALID_TYPE:
          Toast.makeText(this, R.string.MediaSendActivity_an_item_was_removed_because_it_had_an_unknown_type, Toast.LENGTH_LONG).show();
          onNoMediaAvailable();
        case TOO_MANY_ITEMS:
          int maxSelection = viewModel.getMaxSelection();
          Toast.makeText(this, getResources().getQuantityString(R.plurals.MediaSendActivity_cant_share_more_than_n_items, maxSelection, maxSelection), Toast.LENGTH_SHORT).show();
          break;
      }
    });

    viewModel.getEvents().observe(this, event -> {
      switch (event) {
        case VIEW_ONCE_TOOLTIP:
          TooltipPopup.forTarget(revealButton)
                      .setText(R.string.MediaSendActivity_tap_here_to_make_this_message_disappear_after_it_is_viewed)
                      .setBackgroundTint(getResources().getColor(R.color.core_ultramarine))
                      .setTextColor(getResources().getColor(R.color.core_white))
                      .setOnDismissListener(() -> TextSecurePreferences.setHasSeenViewOnceTooltip(this, true))
                      .show(TooltipPopup.POSITION_ABOVE);
          break;
      }
    });
  }

  private void initializeMentionsViewModel() {
    if (recipient == null) {
      return;
    }

    mentionsViewModel = ViewModelProviders.of(this, new MentionsPickerViewModel.Factory()).get(MentionsPickerViewModel.class);

    recipient.observe(this, mentionsViewModel::onRecipientChange);
    composeText.setMentionQueryChangedListener(query -> {
      if (recipient.get().isPushV2Group()) {
        if (!mentionSuggestions.resolved()) {
          mentionSuggestions.get();
        }
        mentionsViewModel.onQueryChange(query);
      }
    });

    composeText.setMentionValidator(annotations -> {
      if (!recipient.get().isPushV2Group()) {
        return annotations;
      }

      Set<String> validRecipientIds = Stream.of(recipient.get().getParticipants())
                                            .map(r -> MentionAnnotation.idToMentionAnnotationValue(r.getId()))
                                            .collect(Collectors.toSet());

      return Stream.of(annotations)
                   .filter(a -> !validRecipientIds.contains(a.getValue()))
                   .toList();
    });

    mentionsViewModel.getSelectedRecipient().observe(this, recipient -> {
      composeText.replaceTextWithMention(recipient.getDisplayName(this), recipient.getId());
    });

    MentionPickerPlacer mentionPickerPlacer = new MentionPickerPlacer();

    mentionsViewModel.isShowing().observe(this, isShowing -> {
      if (isShowing) {
        composeRow.getViewTreeObserver().addOnGlobalLayoutListener(mentionPickerPlacer);
      } else {
        composeRow.getViewTreeObserver().removeOnGlobalLayoutListener(mentionPickerPlacer);
      }
      mentionPickerPlacer.onGlobalLayout();
    });
  }

  private void presentRecipient(@Nullable Recipient recipient) {
    if (recipient == null) {
      composeText.setHint(R.string.MediaSendActivity_message);
    } else if (recipient.isSelf()) {
      composeText.setHint(getString(R.string.note_to_self), null);
    } else {
      composeText.setHint(getString(R.string.MediaSendActivity_message_to_s, recipient.getDisplayName(this)), null);
    }

  }

  private void navigateToMediaSend() {
    MediaSendFragment fragment     = MediaSendFragment.newInstance();
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
               .withRationaleDialog(getString(R.string.ConversationActivity_to_capture_photos_and_video_allow_signal_access_to_the_camera), R.drawable.ic_camera_24)
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
    String          messageBody     = composeText.getTextTrimmed().toString();
    TransportOption transportOption = sendButton.getSelectedTransport();
    CharacterState  characterState  = transportOption.calculateCharacters(messageBody);

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
      KeyboardPagerViewModel keyboardPagerViewModel = ViewModelProviders.of(this).get(KeyboardPagerViewModel.class);
      keyboardPagerViewModel.setOnlyPage(KeyboardPage.EMOJI);

      emojiToggle.attach(emojiDrawer.get());
    }

    if (hud.getCurrentInput() == emojiDrawer.get()) {
      hud.showSoftkey(composeText);
    } else {
      hud.hideSoftkey(composeText, () -> hud.post(() -> hud.show(composeText, emojiDrawer.get())));
    }
  }

  @Override
  public void onKeyEvent(KeyEvent keyEvent) {
    getActiveInputField().dispatchKeyEvent(keyEvent);
  }

  @Override
  public void onEmojiSelected(String emoji) {
    getActiveInputField().insertEmoji(emoji);
  }

  private @Nullable MediaSendFragment getMediaSendFragment() {
    return (MediaSendFragment) getSupportFragmentManager().findFragmentByTag(TAG_SEND);
  }

  private void finishWithoutSettingResults() {
    Intent intent = new Intent();
    setResult(RESULT_OK, intent);

    finish();
    overridePendingTransition(R.anim.stationary, R.anim.camera_slide_to_bottom);
  }

  private void setActivityResultAndFinish(@NonNull MediaSendActivityResult result) {
    Intent intent = new Intent();
    intent.putExtra(EXTRA_RESULT, result);
    setResult(RESULT_OK, intent);

    finish();
    overridePendingTransition(R.anim.stationary, R.anim.camera_slide_to_bottom);
  }

  @Override
  public void openEmojiSearch() {
    if (emojiDrawer.resolved()) {
      emojiDrawer.get().onOpenEmojiSearch();
    }
  }

  @Override
  public void closeEmojiSearch() {
    if (emojiDrawer.resolved()) {
      emojiDrawer.get().onCloseEmojiSearch();
    }
  }

  private class ComposeKeyPressedListener implements View.OnKeyListener, View.OnClickListener, TextWatcher, View.OnFocusChangeListener {

    int beforeLength;

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
          if (SignalStore.settings().isEnterKeySends()) {
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
    public void onFocusChange(View v, boolean hasFocus) {
      if (hasFocus && hud.getCurrentInput() == emojiDrawer.get()) {
        hud.showSoftkey(composeText);
      }
    }
  }

  private class MentionPickerPlacer implements ViewTreeObserver.OnGlobalLayoutListener {

    private final int       composeMargin;
    private final ViewGroup parent;
    private final Rect      composeCoordinates;
    private       int       previousBottomMargin;

    public MentionPickerPlacer() {
      parent             = findViewById(android.R.id.content);
      composeMargin      = ViewUtil.dpToPx(12);
      composeCoordinates = new Rect();
    }

    @Override
    public void onGlobalLayout() {
      composeRow.getDrawingRect(composeCoordinates);
      parent.offsetDescendantRectToMyCoords(composeRow, composeCoordinates);

      int marginBottom = parent.getHeight() - composeCoordinates.top + composeMargin;

      if (marginBottom != previousBottomMargin) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mentionSuggestions.get().getLayoutParams();
        params.setMargins(0, 0, 0, marginBottom);
        mentionSuggestions.get().setLayoutParams(params);

        previousBottomMargin = marginBottom;
      }
    }
  }
}
