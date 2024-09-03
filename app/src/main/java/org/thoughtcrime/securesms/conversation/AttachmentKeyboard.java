package org.thoughtcrime.securesms.conversation;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.animation.AnimationCompleteListener;
import org.thoughtcrime.securesms.components.InputAwareLayout;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.util.StorageUtil;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AttachmentKeyboard extends FrameLayout implements InputAwareLayout.InputView {

  private static final int                            ANIMATION_DURATION  = 150;
  private static final List<AttachmentKeyboardButton> DEFAULT_BUTTONS     = Arrays.asList(
      AttachmentKeyboardButton.GALLERY,
      AttachmentKeyboardButton.FILE,
      AttachmentKeyboardButton.CONTACT,
      AttachmentKeyboardButton.LOCATION,
      AttachmentKeyboardButton.PAYMENT
  );

  private View                            container;
  private AttachmentKeyboardMediaAdapter  mediaAdapter;
  private AttachmentKeyboardButtonAdapter buttonAdapter;
  private Callback                        callback;

  private RecyclerView   mediaList;
  private TextView       permissionText;
  private MaterialButton permissionButton;
  private MaterialButton manageButton;

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
    this.manageButton     = findViewById(R.id.attachment_keyboard_manage_button);

    RecyclerView buttonList = findViewById(R.id.attachment_keyboard_button_list);
    buttonList.setItemAnimator(null);

    mediaAdapter  = new AttachmentKeyboardMediaAdapter(Glide.with(this), media -> {
      if (callback != null) {
        callback.onAttachmentMediaClicked(media);
      }
    });

    buttonAdapter = new AttachmentKeyboardButtonAdapter(button -> {
      if (callback != null) {
        callback.onAttachmentSelectorClicked(button);
      }
    });

    manageButton.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                         View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

    manageButton.setOnClickListener(v -> {
      if (callback != null) {
        callback.onDisplayMoreContextMenu(v, true, false);
      }
    });

    mediaList.setAdapter(mediaAdapter);
    mediaList.addOnScrollListener(new ScrollListener(manageButton.getMeasuredWidth()));
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
      buttonAdapter.setButtons(DEFAULT_BUTTONS.stream().filter(buttonPredicate).collect(Collectors.toList()));
    }
  }

  public void onMediaChanged(@NonNull List<Media> media) {
    if (StorageUtil.canOnlyReadSelectedMediaStore() && media.isEmpty()) {
      mediaList.setVisibility(GONE);
      manageButton.setVisibility(GONE);
      permissionText.setVisibility(VISIBLE);
      permissionText.setText(getContext().getString(R.string.AttachmentKeyboard_no_photos_found));
      permissionButton.setVisibility(VISIBLE);
      permissionButton.setText(getContext().getString(R.string.AttachmentKeyboard_manage));
      permissionButton.setOnClickListener(v -> {
        if (callback != null) {
          callback.onDisplayMoreContextMenu(v, true, true);
        }
      });
    } else if (StorageUtil.canOnlyReadSelectedMediaStore()) {
      mediaList.setVisibility(VISIBLE);
      mediaAdapter.setMedia(media, true);
      manageButton.setVisibility(VISIBLE);
      permissionText.setVisibility(GONE);
      permissionButton.setVisibility(GONE);
    } else if (StorageUtil.canReadAnyFromMediaStore()) {
      mediaList.setVisibility(VISIBLE);
      mediaAdapter.setMedia(media, false);
      permissionButton.setVisibility(GONE);
      permissionText.setVisibility(GONE);
      manageButton.setVisibility(GONE);
    } else {
      mediaList.setVisibility(GONE);
      manageButton.setVisibility(GONE);
      permissionButton.setVisibility(VISIBLE);
      permissionButton.setText(getContext().getString(R.string.AttachmentKeyboard_allow_access));
      permissionText.setVisibility(VISIBLE);
      permissionText.setText(getContext().getString(R.string.AttachmentKeyboard_Signal_needs_permission_to_show_your_photos_and_videos));
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

  private class ScrollListener extends RecyclerView.OnScrollListener {

    private final int originalWidth;
    private final int iconWidth;

    private ValueAnimator animator;
    private boolean       isCollapsed;

    public ScrollListener(int originalWidth) {
      this.originalWidth = originalWidth;
      this.iconWidth     = manageButton.getIconSize() + manageButton.getPaddingLeft() + manageButton.getPaddingRight();
    }

    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
      if (manageButton == null || recyclerView.getLayoutManager() == null || recyclerView.getAdapter() == null) {
        return;
      }

      GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
      View              childView     = layoutManager.getChildAt(0);
      int               position      = layoutManager.findLastVisibleItemPosition();

      boolean visibleFirstChild = childView != null && childView.getTop() == 0 && layoutManager.getPosition(childView) == 0;
      boolean visibleLastChild  = position == recyclerView.getAdapter().getItemCount() - 1;
      boolean shouldCollapse    = !visibleFirstChild && !visibleLastChild;

      if (shouldCollapse && !isCollapsed) {
        isCollapsed = true;
        if (animator != null) {
          animator.cancel();
        }
        animator = createWidthAnimator(manageButton, originalWidth, iconWidth, new AnimationCompleteListener() {
          @Override
          public void onAnimationEnd(Animator animation) {
            manageButton.setText("");
          }
        });
        animator.start();
      } else if (!shouldCollapse && isCollapsed) {
        isCollapsed = false;
        if (animator != null) {
          animator.cancel();
        }
        manageButton.setText(getContext().getString(R.string.AttachmentKeyboard_manage));
        animator = createWidthAnimator(manageButton, iconWidth, originalWidth, null);
        animator.start();
      }
    }
  }

  private static ValueAnimator createWidthAnimator(@NonNull View view,
                                                    int originalWidth,
                                                    int finalWidth,
                                                    @Nullable AnimationCompleteListener onAnimationComplete)
  {
    ValueAnimator animator = ValueAnimator.ofInt(originalWidth, finalWidth).setDuration(ANIMATION_DURATION);

    animator.addUpdateListener(animation -> {
      ViewGroup.LayoutParams params = view.getLayoutParams();
      params.width = (int) animation.getAnimatedValue();
      view.setLayoutParams(params);
    });

    if (onAnimationComplete != null) {
      animator.addListener(onAnimationComplete);
    }

    return animator;
  }

  public interface Callback {
    void onAttachmentMediaClicked(@NonNull Media media);
    void onAttachmentSelectorClicked(@NonNull AttachmentKeyboardButton button);
    void onAttachmentPermissionsRequested();
    void onDisplayMoreContextMenu(View v, boolean showAbove, boolean showAtStart);
  }
}
