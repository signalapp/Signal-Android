package org.thoughtcrime.securesms.megaphone;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;

public class PopupMegaphoneView extends FrameLayout {

  private ImageView image;
  private TextView  titleText;
  private TextView  bodyText;
  private View      xButton;

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

    if (megaphone.getImageRequest() != null) {
      image.setVisibility(VISIBLE);
      megaphone.getImageRequest().into(image);
    } else {
      image.setVisibility(GONE);
    }

    if (megaphone.getTitle() != 0) {
      titleText.setVisibility(VISIBLE);
      titleText.setText(megaphone.getTitle());
    } else {
      titleText.setVisibility(GONE);
    }

    if (megaphone.getBody() != 0) {
      bodyText.setVisibility(VISIBLE);
      bodyText.setText(megaphone.getBody());
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
