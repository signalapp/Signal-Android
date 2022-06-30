package org.thoughtcrime.securesms.conversation;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.annimon.stream.function.Predicate;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.InputAwareLayout;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.StorageUtil;

import java.util.Arrays;
import java.util.List;

public class AttachmentKeyboard extends FrameLayout implements InputAwareLayout.InputView {

  private static final List<AttachmentKeyboardButton> DEFAULT_BUTTONS = Arrays.asList(
      AttachmentKeyboardButton.GALLERY,
      AttachmentKeyboardButton.FILE,
      AttachmentKeyboardButton.PAYMENT,
      AttachmentKeyboardButton.CONTACT,
      AttachmentKeyboardButton.LOCATION
  );

  private View                            container;
  private AttachmentKeyboardMediaAdapter  mediaAdapter;
  private AttachmentKeyboardButtonAdapter buttonAdapter;
  private Callback                        callback;

  private RecyclerView mediaList;
  private View         permissionText;
  private View         permissionButton;

  public AttachmentKeyboard(@NonNull Context context) {
    super(context);
    init(context);
  }

  public AttachmentKeyboard(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  private void init(@NonNull Context context) {
    inflate(context, R.layout.attachment_keyboard, this);

    this.container        = findViewById(R.id.attachment_keyboard_container);
    this.mediaList        = findViewById(R.id.attachment_keyboard_media_list);
    this.permissionText   = findViewById(R.id.attachment_keyboard_permission_text);
    this.permissionButton = findViewById(R.id.attachment_keyboard_permission_button);

    RecyclerView buttonList = findViewById(R.id.attachment_keyboard_button_list);

    mediaAdapter  = new AttachmentKeyboardMediaAdapter(GlideApp.with(this), media -> {
      if (callback != null) {
        callback.onAttachmentMediaClicked(media);
      }
    });

    buttonAdapter = new AttachmentKeyboardButtonAdapter(button -> {
      if (callback != null) {
        callback.onAttachmentSelectorClicked(button);
      }
    });

    mediaList.setAdapter(mediaAdapter);
    buttonList.setAdapter(buttonAdapter);

    buttonAdapter.registerAdapterDataObserver(new AttachmentButtonCenterHelper(buttonList));

    mediaList.setLayoutManager(new GridLayoutManager(context, 1, GridLayoutManager.HORIZONTAL, false));
    buttonList.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));

    buttonAdapter.setButtons(DEFAULT_BUTTONS);
  }

  public void setCallback(@NonNull Callback callback) {
    this.callback = callback;
  }

  public void filterAttachmentKeyboardButtons(@Nullable Predicate<AttachmentKeyboardButton> buttonPredicate) {
    if (buttonPredicate == null) {
      buttonAdapter.setButtons(DEFAULT_BUTTONS);
    } else {
      buttonAdapter.setButtons(Stream.of(DEFAULT_BUTTONS).filter(buttonPredicate).toList());
    }
  }

  public void onMediaChanged(@NonNull List<Media> media) {
    if (StorageUtil.canReadFromMediaStore()) {
      mediaAdapter.setMedia(media);
      permissionButton.setVisibility(GONE);
      permissionText.setVisibility(GONE);
    } else {
      permissionButton.setVisibility(VISIBLE);
      permissionText.setVisibility(VISIBLE);

      permissionButton.setOnClickListener(v -> {
        if (callback != null) {
          callback.onAttachmentPermissionsRequested();
        }
      });
    }
  }

  public void setWallpaperEnabled(boolean wallpaperEnabled) {
    if (wallpaperEnabled) {
      container.setBackgroundColor(getContext().getResources().getColor(R.color.wallpaper_compose_background));
    } else {
      container.setBackgroundColor(getContext().getResources().getColor(R.color.signal_background_primary));
    }
    buttonAdapter.setWallpaperEnabled(wallpaperEnabled);
  }

  @Override
  public void show(int height, boolean immediate) {
    ViewGroup.LayoutParams params = getLayoutParams();
    params.height = height;
    setLayoutParams(params);

    setVisibility(VISIBLE);
  }

  @Override
  public void hide(boolean immediate) {
    setVisibility(GONE);
  }

  @Override
  public boolean isShowing() {
    return getVisibility() == VISIBLE;
  }

  public interface Callback {
    void onAttachmentMediaClicked(@NonNull Media media);
    void onAttachmentSelectorClicked(@NonNull AttachmentKeyboardButton button);
    void onAttachmentPermissionsRequested();
  }
}
