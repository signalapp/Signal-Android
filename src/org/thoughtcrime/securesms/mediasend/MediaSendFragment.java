package org.thoughtcrime.securesms.mediasend;

import androidx.lifecycle.ViewModelProviders;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.R;
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
import org.thoughtcrime.securesms.scribbles.ImageEditorHud;
import org.thoughtcrime.securesms.util.CharacterCalculator.CharacterState;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.Util;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Allows the user to edit and caption a set of media items before choosing to send them.
 */
public class MediaSendFragment extends Fragment {

  private static final String TAG = MediaSendFragment.class.getSimpleName();

  private static final String KEY_LOCALE    = "locale";

  private ViewGroup                     playbackControlsContainer;
  private ControllableViewPager         fragmentPager;
  private MediaSendFragmentPagerAdapter fragmentPagerAdapter;

  private MediaSendViewModel viewModel;


  public static MediaSendFragment newInstance(@NonNull Locale locale) {
    Bundle args = new Bundle();
    args.putSerializable(KEY_LOCALE, locale);

    MediaSendFragment fragment = new MediaSendFragment();
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.mediasend_fragment, container, false);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    initViewModel();
    fragmentPager             = view.findViewById(R.id.mediasend_pager);
    playbackControlsContainer = view.findViewById(R.id.mediasend_playback_controls_container);


    fragmentPagerAdapter = new MediaSendFragmentPagerAdapter(getChildFragmentManager());
    fragmentPager.setAdapter(fragmentPagerAdapter);

    FragmentPageChangeListener pageChangeListener = new FragmentPageChangeListener();
    fragmentPager.addOnPageChangeListener(pageChangeListener);
    fragmentPager.post(() -> pageChangeListener.onPageSelected(fragmentPager.getCurrentItem()));
  }

  @Override
  public void onStart() {
    super.onStart();

    fragmentPagerAdapter.restoreState(viewModel.getDrawState());
    viewModel.onImageEditorStarted();
  }

  @Override
  public void onHiddenChanged(boolean hidden) {
    super.onHiddenChanged(hidden);
    if (!hidden) {
      viewModel.onImageEditorStarted();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    fragmentPagerAdapter.saveAllState();
    viewModel.saveDrawState(fragmentPagerAdapter.getSavedState());
  }

  public void onTouchEventsNeeded(boolean needed) {
    if (fragmentPager != null) {
      fragmentPager.setEnabled(!needed);
    }
  }

  public List<Media> getAllMedia() {
    return fragmentPagerAdapter.getAllMedia();
  }

  public @NonNull Map<Uri, Object> getSavedState() {
    return fragmentPagerAdapter.getSavedState();
  }

  public int getCurrentImagePosition() {
    return fragmentPager.getCurrentItem();
  }

  private void initViewModel() {
    viewModel = ViewModelProviders.of(requireActivity(), new MediaSendViewModel.Factory(requireActivity().getApplication(), new MediaRepository())).get(MediaSendViewModel.class);

    viewModel.getSelectedMedia().observe(this, media -> {
      if (Util.isEmpty(media)) {
        return;
      }

      fragmentPagerAdapter.setMedia(media);
    });

    viewModel.getPosition().observe(this, position -> {
      if (position == null || position < 0) return;

      fragmentPager.setCurrentItem(position, true);

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

  public void onRequestFullScreen(boolean fullScreen, ImageEditorHud.Mode mode) {
    if (mode != ImageEditorHud.Mode.TEXT && hud.isKeyboardOpen()) {
      hud.hideSoftkey(composeText, null);
    }

    captionAndRail.setVisibility(fullScreen ? View.GONE : View.VISIBLE);
  }

  private class FragmentPageChangeListener extends ViewPager.SimpleOnPageChangeListener {
    @Override
    public void onPageSelected(int position) {
      viewModel.onPageChanged(position);
    }
  }
}
