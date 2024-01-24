package org.thoughtcrime.securesms.megaphone;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.airbnb.lottie.LottieAnimationView;

import org.thoughtcrime.securesms.R;

public class PopupMegaphoneView extends FrameLayout {

  private LottieAnimationView image;
  private TextView            titleText;
  private TextView            bodyText;
  private View                xButton;

  private Megaphone                 megaphone;
  private MegaphoneActionController megaphoneListener;

  public PopupMegaphoneView(@NonNull Context context) {
    super(context);
    init(context);
  }

  public PopupMegaphoneView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  private void init(@NonNull Context context) {
    inflate(context, R.layout.popup_megaphone_view, this);

    this.image     = findViewById(R.id.popup_megaphone_image);
    this.titleText = findViewById(R.id.popup_megaphone_title);
    this.bodyText  = findViewById(R.id.popup_megaphone_body);
    this.xButton   = findViewById(R.id.popup_x);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();

    if (megaphone != null && megaphoneListener != null && megaphone.getOnVisibleListener() != null) {
      megaphone.getOnVisibleListener().onEvent(megaphone, megaphoneListener);
    }
  }

  public void present(@NonNull Megaphone megaphone, @NonNull MegaphoneActionController megaphoneListener) {
    this.megaphone         = megaphone;
    this.megaphoneListener = megaphoneListener;

    if (megaphone.getImageRequestBuilder() != null) {
      image.setVisibility(VISIBLE);
      megaphone.getImageRequestBuilder().into(image);
    } else if (megaphone.getLottieRes() != 0) {
      image.setVisibility(VISIBLE);
      image.setAnimation(megaphone.getLottieRes());
      image.playAnimation();
    } else {
      image.setVisibility(GONE);
    }

    if (megaphone.getTitle().hasText()) {
      titleText.setVisibility(VISIBLE);
      titleText.setText(megaphone.getTitle().resolve(getContext()));
    } else {
      titleText.setVisibility(GONE);
    }

    if (megaphone.getBody().hasText()) {
      bodyText.setVisibility(VISIBLE);
      bodyText.setText(megaphone.getBody().resolve(getContext()));
    } else {
      bodyText.setVisibility(GONE);
    }

    if (megaphone.hasButton()) {
      xButton.setOnClickListener(v -> megaphone.getButtonClickListener().onEvent(megaphone, megaphoneListener));
    } else {
      xButton.setOnClickListener(v -> megaphoneListener.onMegaphoneCompleted(megaphone.getEvent()));
    }

  }
}
