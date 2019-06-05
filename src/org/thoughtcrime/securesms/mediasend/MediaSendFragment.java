package org.thoughtcrime.securesms.mediasend;

import android.annotation.SuppressLint;
import androidx.lifecycle.ViewModelProviders;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.components.ComposeText;
import org.thoughtcrime.securesms.components.ControllableViewPager;
import org.thoughtcrime.securesms.components.InputAwareLayout;
import org.thoughtcrime.securesms.components.SendButton;
import org.thoughtcrime.securesms.components.emoji.EmojiEditText;
import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider;
import org.thoughtcrime.securesms.components.emoji.EmojiToggle;
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard;
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher;
import org.thoughtcrime.securesms.imageeditor.model.EditorModel;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediapreview.MediaRailAdapter;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.scribbles.ImageEditorFragment;
import org.thoughtcrime.securesms.util.CharacterCalculator.CharacterState;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.concurrent.SettableFuture;
import org.thoughtcrime.securesms.util.views.Stub;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Allows the user to edit and caption a set of media items before choosing to send them.
 */
public class MediaSendFragment extends Fragment implements ViewTreeObserver.OnGlobalLayoutListener,
                                                           MediaRailAdapter.RailItemListener,
                                                           InputAwareLayout.OnKeyboardShownListener,
                                                           InputAwareLayout.OnKeyboardHiddenListener
{

  private static final String TAG = MediaSendFragment.class.getSimpleName();

  private static final String KEY_ADDRESS   = "address";
  private static final String KEY_TRANSPORT = "transport";
  private static final String KEY_LOCALE    = "locale";

  private InputAwareLayout  hud;
  private View              captionAndRail;
  private SendButton        sendButton;
  private ComposeText       composeText;
  private ViewGroup         composeContainer;
  private EmojiEditText     captionText;
  private EmojiToggle       emojiToggle;
  private Stub<MediaKeyboard> emojiDrawer;
  private ViewGroup         playbackControlsContainer;
  private TextView          charactersLeft;

  private ControllableViewPager         fragmentPager;
  private MediaSendFragmentPagerAdapter fragmentPagerAdapter;
  private RecyclerView                  mediaRail;
  private MediaRailAdapter              mediaRailAdapter;

  private int                visibleHeight;
  private MediaSendViewModel viewModel;
  private Controller         controller;
  private Locale             locale;

  private final Rect visibleBounds = new Rect();

  public static MediaSendFragment newInstance(@NonNull Recipient recipient, @NonNull TransportOption transport, @NonNull Locale locale) {
    Bundle args = new Bundle();
    args.putParcelable(KEY_ADDRESS, recipient.getAddress());
    args.putParcelable(KEY_TRANSPORT, transport);
    args.putSerializable(KEY_LOCALE, locale);

    MediaSendFragment fragment = new MediaSendFragment();
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);

    if (!(requireActivity() instanceof Controller)) {
      throw new IllegalStateException("Parent activity must implement controller interface.");
    }

    controller = (Controller) requireActivity();
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return ThemeUtil.getThemedInflater(requireActivity(), inflater, R.style.TextSecure_DarkTheme)
                    .inflate(R.layout.mediasend_fragment, container, false);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    locale = (Locale) getArguments().getSerializable(KEY_LOCALE);

    initViewModel();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    hud                       = view.findViewById(R.id.mediasend_hud);
    captionAndRail            = view.findViewById(R.id.mediasend_caption_and_rail);
    sendButton                = view.findViewById(R.id.mediasend_send_button);
    composeText               = view.findViewById(R.id.mediasend_compose_text);
    composeContainer          = view.findViewById(R.id.mediasend_compose_container);
    captionText               = view.findViewById(R.id.mediasend_caption);
    emojiToggle               = view.findViewById(R.id.mediasend_emoji_toggle);
    emojiDrawer               = new Stub<>(view.findViewById(R.id.mediasend_emoji_drawer_stub));
    fragmentPager             = view.findViewById(R.id.mediasend_pager);
    mediaRail                 = view.findViewById(R.id.mediasend_media_rail);
    playbackControlsContainer = view.findViewById(R.id.mediasend_playback_controls_container);
    charactersLeft            = view.findViewById(R.id.mediasend_characters_left);

    View sendButtonBkg = view.findViewById(R.id.mediasend_send_button_bkg);

    sendButton.setOnClickListener(v -> {
      if (hud.isKeyboardOpen()) {
        hud.hideSoftkey(composeText, null);
      }

      processMedia(fragmentPagerAdapter.getAllMedia(), fragmentPagerAdapter.getSavedState());
    });

    sendButton.addOnTransportChangedListener((newTransport, manuallySelected) -> {
      presentCharactersRemaining();
      composeText.setTransport(newTransport);
      sendButtonBkg.getBackground().setColorFilter(newTransport.getBackgroundColor(), PorterDuff.Mode.MULTIPLY);
      sendButtonBkg.getBackground().invalidateSelf();
    });

    ComposeKeyPressedListener composeKeyPressedListener = new ComposeKeyPressedListener();

    composeText.setOnKeyListener(composeKeyPressedListener);
    composeText.addTextChangedListener(composeKeyPressedListener);
    composeText.setOnClickListener(composeKeyPressedListener);
    composeText.setOnFocusChangeListener(composeKeyPressedListener);

    captionText.clearFocus();
    composeText.requestFocus();

    fragmentPagerAdapter = new MediaSendFragmentPagerAdapter(getChildFragmentManager());
    fragmentPager.setAdapter(fragmentPagerAdapter);

    FragmentPageChangeListener pageChangeListener = new FragmentPageChangeListener();
    fragmentPager.addOnPageChangeListener(pageChangeListener);
    fragmentPager.post(() -> pageChangeListener.onPageSelected(fragmentPager.getCurrentItem()));

    mediaRailAdapter = new MediaRailAdapter(GlideApp.with(this), this, true);
    mediaRail.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
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

    TransportOption transportOption = getArguments().getParcelable(KEY_TRANSPORT);

    sendButton.setTransport(transportOption);
    sendButton.disableTransport(transportOption.getType() == TransportOption.Type.SMS ? TransportOption.Type.TEXTSECURE : TransportOption.Type.SMS);

    composeText.append(viewModel.getBody());

    Recipient recipient   = Recipient.from(requireContext(), getArguments().getParcelable(KEY_ADDRESS), false);
    String    displayName = Optional.fromNullable(recipient.getName())
                                    .or(Optional.fromNullable(recipient.getProfileName())
                                                .or(recipient.getAddress().serialize()));
    composeText.setHint(getString(R.string.MediaSendActivity_message_to_s, displayName), null);
    composeText.setOnEditorActionListener((v, actionId, event) -> {
      boolean isSend = actionId == EditorInfo.IME_ACTION_SEND;
      if (isSend) sendButton.performClick();
      return isSend;
    });

    if (TextSecurePreferences.isSystemEmojiPreferred(getContext())) {
      emojiToggle.setVisibility(View.GONE);
    } else {
      emojiToggle.setOnClickListener(this::onEmojiToggleClicked);
    }
  }

  @Override
  public void onStart() {
    super.onStart();

    fragmentPagerAdapter.restoreState(viewModel.getDrawState());
    viewModel.onImageEditorStarted();

    requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
  }

  @Override
  public void onHiddenChanged(boolean hidden) {
    super.onHiddenChanged(hidden);
  }

  @Override
  public void onStop() {
    super.onStop();
    fragmentPagerAdapter.saveAllState();
    viewModel.saveDrawState(fragmentPagerAdapter.getSavedState());
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
  public void onRailItemClicked(int distanceFromActive) {
    viewModel.onPageChanged(fragmentPager.getCurrentItem() + distanceFromActive);
  }

  @Override
  public void onRailItemDeleteClicked(int distanceFromActive) {
    viewModel.onMediaItemRemoved(requireContext(), fragmentPager.getCurrentItem() + distanceFromActive);
  }

  @Override
  public void onKeyboardShown() {
    if (sendButton.getSelectedTransport().isSms()) {
      mediaRail.setVisibility(View.GONE);
      composeContainer.setVisibility(View.VISIBLE);
      captionText.setVisibility(View.GONE);
    } else {
      if (captionText.hasFocus()) {
        mediaRail.setVisibility(View.VISIBLE);
        composeContainer.setVisibility(View.GONE);
        captionText.setVisibility(View.VISIBLE);
      } else if (composeText.hasFocus()) {
        mediaRail.setVisibility(View.VISIBLE);
        composeContainer.setVisibility(View.VISIBLE);
        captionText.setVisibility(View.GONE);
      } else {
        mediaRail.setVisibility(View.GONE);
        composeContainer.setVisibility(View.VISIBLE);
        captionText.setVisibility(View.GONE);
      }
    }
  }

  @Override
  public void onKeyboardHidden() {
    composeContainer.setVisibility(View.VISIBLE);

    if (sendButton.getSelectedTransport().isSms()) {
      mediaRail.setVisibility(View.GONE);
      captionText.setVisibility(View.GONE);
    } else {
      mediaRail.setVisibility(View.VISIBLE);

      if (!Util.isEmpty(viewModel.getSelectedMedia().getValue()) && viewModel.getSelectedMedia().getValue().size() > 1) {
        captionText.setVisibility(View.VISIBLE);
      }
    }
  }

  public void onTouchEventsNeeded(boolean needed) {
    if (fragmentPager != null) {
      fragmentPager.setEnabled(!needed);
    }
  }

  public boolean handleBackPress() {
    if (hud.isInputOpen()) {
      hud.hideCurrentInput(composeText);
      return true;
    }
    return false;
  }

  private void initViewModel() {
    viewModel = ViewModelProviders.of(requireActivity(), new MediaSendViewModel.Factory(requireActivity().getApplication(), new MediaRepository())).get(MediaSendViewModel.class);

    viewModel.getSelectedMedia().observe(this, media -> {
      if (Util.isEmpty(media)) {
        controller.onNoMediaAvailable();
        return;
      }

      fragmentPagerAdapter.setMedia(media);

      mediaRail.setVisibility(sendButton.getSelectedTransport().isSms() ? View.GONE : View.VISIBLE);
      captionText.setVisibility((media.size() > 1 || media.get(0).getCaption().isPresent()) ? View.VISIBLE : View.GONE);
      mediaRailAdapter.setMedia(media);
    });

    viewModel.getPosition().observe(this, position -> {
      if (position == null || position < 0) return;

      fragmentPager.setCurrentItem(position, true);
      mediaRailAdapter.setActivePosition(position);
      mediaRail.smoothScrollToPosition(position);

      if (fragmentPagerAdapter.getAllMedia().size() > position) {
        captionText.setText(fragmentPagerAdapter.getAllMedia().get(position).getCaption().or(""));
      }

      View playbackControls = fragmentPagerAdapter.getPlaybackControls(position);

      if (playbackControls != null) {
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        playbackControls.setLayoutParams(params);
        playbackControlsContainer.removeAllViews();
        playbackControlsContainer.addView(playbackControls);
      } else {
        playbackControlsContainer.removeAllViews();
      }
    });

    viewModel.getBucketId().observe(this, bucketId -> {
      if (bucketId == null) return;

      mediaRailAdapter.setAddButtonListener(() -> controller.onAddMediaClicked(bucketId));
    });
  }

  private EmojiEditText getActiveInputField() {
    if (captionText.hasFocus()) return captionText;
    else                        return composeText;
  }


  private void presentCharactersRemaining() {
    String          messageBody     = composeText.getTextTrimmed();
    TransportOption transportOption = sendButton.getSelectedTransport();
    CharacterState  characterState  = transportOption.calculateCharacters(messageBody);

    if (characterState.charactersRemaining <= 15 || characterState.messagesSpent > 1) {
      charactersLeft.setText(String.format(locale,
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
      emojiDrawer.get().setProviders(0, new EmojiKeyboardProvider(requireContext(), new EmojiKeyboardProvider.EmojiEventListener() {
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
  private void processMedia(@NonNull List<Media> mediaList, @NonNull Map<Uri, Object> savedState) {
    Map<Media, ListenableFuture<Bitmap>> futures = new HashMap<>();

    for (Media media : mediaList) {
      Object state = savedState.get(media.getUri());

      if (state instanceof ImageEditorFragment.Data) {
        EditorModel model = ((ImageEditorFragment.Data) state).readModel();
        if (model != null && model.isChanged()) {
          futures.put(media, render(requireContext(), model));
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
          dialog = new AlertDialog.Builder(new ContextThemeWrapper(requireContext(), R.style.TextSecure_MediaSendProgressDialog))
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
        Context     context      = requireContext();
        List<Media> updatedMedia = new ArrayList<>(mediaList.size());

        for (Media media : mediaList) {
          if (futures.containsKey(media)) {
            try {
              Bitmap                 bitmap   = futures.get(media).get();
              ByteArrayOutputStream  baos     = new ByteArrayOutputStream();
              bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);

              Uri uri = BlobProvider.getInstance()
                                    .forData(baos.toByteArray())
                                    .withMimeType(MediaUtil.IMAGE_JPEG)
                                    .createForSingleSessionOnDisk(context, e -> Log.w(TAG, "Failed to write to disk.", e));

              Media updated = new Media(uri, MediaUtil.IMAGE_JPEG, media.getDate(), bitmap.getWidth(), bitmap.getHeight(), baos.size(), media.getBucketId(), media.getCaption());

              updatedMedia.add(updated);
              renderTimer.split("item");
            } catch (InterruptedException | ExecutionException | IOException e) {
              Log.w(TAG, "Failed to render image. Using base image.");
              updatedMedia.add(media);
            }
          } else {
            updatedMedia.add(media);
          }
        }
        return updatedMedia;
      }

      @Override
      protected void onPostExecute(List<Media> media) {
        controller.onSendClicked(media, composeText.getTextTrimmed(), sendButton.getSelectedTransport());
        Util.cancelRunnableOnMain(progressTimer);
        if (dialog != null) {
          dialog.dismiss();
        }
        renderTimer.stop(TAG);
      }
    }.execute();
  }

  private static ListenableFuture<Bitmap> render(@NonNull Context context, @NonNull EditorModel model) {
    SettableFuture<Bitmap> future = new SettableFuture<>();

    AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> future.set(model.render(context)));

    return future;
  }

  public void onRequestFullScreen(boolean fullScreen) {
    captionAndRail.setVisibility(fullScreen ? View.GONE : View.VISIBLE);
  }

  private class FragmentPageChangeListener extends ViewPager.SimpleOnPageChangeListener {
    @Override
    public void onPageSelected(int position) {
      viewModel.onPageChanged(position);
    }
  }

  private class ComposeKeyPressedListener implements View.OnKeyListener, View.OnClickListener, TextWatcher, View.OnFocusChangeListener {

    int beforeLength;

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
          if (TextSecurePreferences.isEnterSendsEnabled(requireContext())) {
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

  public interface Controller {
    void onAddMediaClicked(@NonNull String bucketId);
    void onSendClicked(@NonNull List<Media> media, @NonNull String body, @NonNull TransportOption transport);
    void onNoMediaAvailable();
  }
}
