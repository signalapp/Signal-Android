package org.thoughtcrime.securesms.components;

import android.Manifest;
import android.animation.Animator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.LoaderManager;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.TreeSet;

public class AttachmentTypeSelector extends PopupWindow {

  public static final int ADD_GALLERY       = 1;
  public static final int ADD_DOCUMENT      = 2;
  public static final int ADD_SOUND         = 3;
  public static final int ADD_CONTACT_INFO  = 4;
  public static final int TAKE_PHOTO        = 5;
  public static final int ADD_LOCATION      = 6;
  public static final int ADD_GIF           = 7;

  private static final int ANIMATION_DURATION = 300;

  @SuppressWarnings("unused")
  private static final String TAG = AttachmentTypeSelector.class.getSimpleName();

  private final @NonNull LoaderManager       loaderManager;
  private final @NonNull RecentPhotoViewRail recentRail;
  private final @NonNull ImageView           imageButton;
  private final @NonNull ImageView           audioButton;
  private final @NonNull ImageView           documentButton;
  private final @NonNull ImageView           contactButton;
  private final @NonNull ImageView           cameraButton;
  private final @NonNull ImageView           locationButton;
  private final @NonNull ImageView           gifButton;
  private final @NonNull ImageView           closeButton;
  private final @NonNull ImageView           sendButton;
  private final @NonNull TextView            closeSendText;

  private       @Nullable View                      currentAnchor;
  private       @Nullable AttachmentClickedListener listener;
  private final @NonNull  TreeSet<Uri> selectedUris = new TreeSet<>();

  public AttachmentTypeSelector(@NonNull Context context, @NonNull LoaderManager loaderManager, @Nullable AttachmentClickedListener listener) {
    super(context);

    LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    LinearLayout   layout   = (LinearLayout) inflater.inflate(R.layout.attachment_type_selector, null, true);

    this.listener       = listener;
    this.loaderManager  = loaderManager;
    this.recentRail     = ViewUtil.findById(layout, R.id.recent_photos);
    this.imageButton    = ViewUtil.findById(layout, R.id.gallery_button);
    this.audioButton    = ViewUtil.findById(layout, R.id.audio_button);
    this.documentButton = ViewUtil.findById(layout, R.id.document_button);
    this.contactButton  = ViewUtil.findById(layout, R.id.contact_button);
    this.cameraButton   = ViewUtil.findById(layout, R.id.camera_button);
    this.locationButton = ViewUtil.findById(layout, R.id.location_button);
    this.gifButton      = ViewUtil.findById(layout, R.id.giphy_button);
    this.closeButton    = ViewUtil.findById(layout, R.id.close_button);
    this.sendButton     = ViewUtil.findById(layout, R.id.send_button);
    this.closeSendText  = ViewUtil.findById(layout, R.id.close_send_text);

    this.imageButton.setOnClickListener(new PropagatingClickListener(ADD_GALLERY));
    this.audioButton.setOnClickListener(new PropagatingClickListener(ADD_SOUND));
    this.documentButton.setOnClickListener(new PropagatingClickListener(ADD_DOCUMENT));
    this.contactButton.setOnClickListener(new PropagatingClickListener(ADD_CONTACT_INFO));
    this.cameraButton.setOnClickListener(new PropagatingClickListener(TAKE_PHOTO));
    this.locationButton.setOnClickListener(new PropagatingClickListener(ADD_LOCATION));
    this.gifButton.setOnClickListener(new PropagatingClickListener(ADD_GIF));
    this.closeButton.setOnClickListener(new CloseClickListener());
    this.sendButton.setOnClickListener(new SendClickListener());
    this.recentRail.setListener(new RecentPhotoSelectedListener());

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
      ViewUtil.findById(layout, R.id.location_linear_layout).setVisibility(View.INVISIBLE);
    }

    setContentView(layout);
    setWidth(LinearLayout.LayoutParams.MATCH_PARENT);
    setHeight(LinearLayout.LayoutParams.WRAP_CONTENT);
    setBackgroundDrawable(new BitmapDrawable());
    setAnimationStyle(0);
    setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
    setFocusable(true);
    setTouchable(true);

    loaderManager.initLoader(1, null, recentRail);
  }

  public void show(@NonNull Activity activity, final @NonNull View anchor) {
    if (Permissions.hasAll(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
      recentRail.setVisibility(View.VISIBLE);
      loaderManager.restartLoader(1, null, recentRail);
    } else {
      recentRail.setVisibility(View.GONE);
    }

    this.currentAnchor = anchor;

    showAtLocation(anchor, Gravity.BOTTOM, 0, 0);

    getContentView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout() {
        getContentView().getViewTreeObserver().removeGlobalOnLayoutListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          animateWindowInCircular(anchor, getContentView());
        } else {
          animateWindowInTranslate(getContentView());
        }
      }
    });

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      animateButtonIn(imageButton, ANIMATION_DURATION / 2);
      animateButtonIn(cameraButton, ANIMATION_DURATION / 2);

      animateButtonIn(audioButton, ANIMATION_DURATION / 3);
      animateButtonIn(locationButton, ANIMATION_DURATION / 3);
      animateButtonIn(documentButton, ANIMATION_DURATION / 4);
      animateButtonIn(gifButton, ANIMATION_DURATION / 4);
      animateButtonIn(contactButton, 0);
      animateButtonIn(closeButton, 0);
    }
  }

  @Override
  public void dismiss() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      animateWindowOutCircular(currentAnchor, getContentView());
    } else {
      animateWindowOutTranslate(getContentView());
    }
  }

  public void setListener(@Nullable AttachmentClickedListener listener) {
    this.listener = listener;
  }

  private void animateButtonIn(View button, int delay) {
    AnimationSet animation = new AnimationSet(true);
    Animation scale = new ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f,
                                         Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.0f);

    animation.addAnimation(scale);
    animation.setInterpolator(new OvershootInterpolator(1));
    animation.setDuration(ANIMATION_DURATION);
    animation.setStartOffset(delay);
    button.startAnimation(animation);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private void animateWindowInCircular(@Nullable View anchor, @NonNull View contentView) {
    Pair<Integer, Integer> coordinates = getClickOrigin(anchor, contentView);
    Animator animator = ViewAnimationUtils.createCircularReveal(contentView,
                                                                coordinates.first,
                                                                coordinates.second,
                                                                0,
                                                                Math.max(contentView.getWidth(), contentView.getHeight()));
    animator.setDuration(ANIMATION_DURATION);
    animator.start();
  }

  private void animateWindowInTranslate(@NonNull View contentView) {
    Animation animation = new TranslateAnimation(0, 0, contentView.getHeight(), 0);
    animation.setDuration(ANIMATION_DURATION);

    getContentView().startAnimation(animation);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private void animateWindowOutCircular(@Nullable View anchor, @NonNull View contentView) {
    Pair<Integer, Integer> coordinates = getClickOrigin(anchor, contentView);
    Animator               animator    = ViewAnimationUtils.createCircularReveal(getContentView(),
                                                                                 coordinates.first,
                                                                                 coordinates.second,
                                                                                 Math.max(getContentView().getWidth(), getContentView().getHeight()),
                                                                                 0);

    animator.setDuration(ANIMATION_DURATION);
    animator.addListener(new Animator.AnimatorListener() {
      @Override
      public void onAnimationStart(Animator animation) {
      }

      @Override
      public void onAnimationEnd(Animator animation) {
        AttachmentTypeSelector.super.dismiss();
      }

      @Override
      public void onAnimationCancel(Animator animation) {
      }

      @Override
      public void onAnimationRepeat(Animator animation) {
      }
    });

    animator.start();
  }

  private void animateWindowOutTranslate(@NonNull View contentView) {
    Animation animation = new TranslateAnimation(0, 0, 0, contentView.getTop() + contentView.getHeight());
    animation.setDuration(ANIMATION_DURATION);
    animation.setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {
      }

      @Override
      public void onAnimationEnd(Animation animation) {
        AttachmentTypeSelector.super.dismiss();
      }

      @Override
      public void onAnimationRepeat(Animation animation) {
      }
    });

    getContentView().startAnimation(animation);
  }

  private Pair<Integer, Integer> getClickOrigin(@Nullable View anchor, @NonNull View contentView) {
    if (anchor == null) return new Pair<>(0, 0);

    final int[] anchorCoordinates = new int[2];
    anchor.getLocationOnScreen(anchorCoordinates);
    anchorCoordinates[0] += anchor.getWidth() / 2;
    anchorCoordinates[1] += anchor.getHeight() / 2;

    final int[] contentCoordinates = new int[2];
    contentView.getLocationOnScreen(contentCoordinates);

    int x = anchorCoordinates[0] - contentCoordinates[0];
    int y = anchorCoordinates[1] - contentCoordinates[1];

    return new Pair<>(x, y);
  }

  private void updateCloseSendButton() {
    if (selectedUris.isEmpty()) {
      closeButton.setVisibility(View.VISIBLE);
      sendButton.setVisibility(View.GONE);
      closeSendText.setText("");
    } else {
      closeButton.setVisibility(View.GONE);
      sendButton.setVisibility(View.VISIBLE);
      closeSendText.setText(R.string.attachment_type_selector__send);
    }
  }

  private class RecentPhotoSelectedListener implements RecentPhotoViewRail.OnItemClickedListener {
    @Override
    public boolean onShortPress(Uri uri) {
      if (selectedUris.isEmpty()) {
        selectedUris.add(uri);
        animateWindowOutTranslate(getContentView());
        if (listener != null) {
          listener.onQuickAttachment(selectedUris.toArray(new Uri[selectedUris.size()]));
        }
        return true;
      } else {
        if (selectedUris.contains(uri)) {
          selectedUris.remove(uri);
          updateCloseSendButton();
          return false;
        } else {
          selectedUris.add(uri);
          return true;
        }
      }
    }

    @Override
    public boolean onLongPress(Uri uri) {
      if (selectedUris.contains(uri)) {
        selectedUris.remove(uri);
        updateCloseSendButton();
        return false;
      } else {
        selectedUris.add(uri);
        updateCloseSendButton();
        return true;
      }
    }
  }

  private class PropagatingClickListener implements View.OnClickListener {

    private final int type;

    private PropagatingClickListener(int type) {
      this.type = type;
    }

    @Override
    public void onClick(View v) {
      animateWindowOutTranslate(getContentView());

      if (listener != null) listener.onClick(type);
    }

  }

  private class CloseClickListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      dismiss();
    }
  }

  private class SendClickListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      animateWindowOutTranslate(getContentView());
      listener.onQuickAttachment(selectedUris.toArray(new Uri[selectedUris.size()]));
      if (listener != null) {
        listener.onQuickAttachment(selectedUris.toArray(new Uri[selectedUris.size()]));
      }
    }
  }

  public interface AttachmentClickedListener {
    public void onClick(int type);
    public void onQuickAttachment(Uri[] uris);
  }

}
