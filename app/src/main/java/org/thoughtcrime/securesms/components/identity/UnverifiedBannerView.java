package org.thoughtcrime.securesms.components.identity;


import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.IdentityDatabase.IdentityRecord;

import java.util.List;

public class UnverifiedBannerView extends LinearLayout {

  private static final String TAG = UnverifiedBannerView.class.getSimpleName();

  private View      container;
  private TextView  text;
  private ImageView closeButton;

  public UnverifiedBannerView(Context context) {
    super(context);
    initialize();
  }

  public UnverifiedBannerView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
  public UnverifiedBannerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public UnverifiedBannerView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  private void initialize() {
    LayoutInflater.from(getContext()).inflate(R.layout.unverified_banner_view, this, true);
    this.container   = findViewById(R.id.container);
    this.text        = findViewById(R.id.unverified_text);
    this.closeButton = findViewById(R.id.cancel);
  }

  public void display(@NonNull final String text,
                      @NonNull final List<IdentityRecord> unverifiedIdentities,
                      @NonNull final ClickListener clickListener,
                      @NonNull final DismissListener dismissListener)
  {
    this.text.setText(text);
    setVisibility(View.VISIBLE);

    this.container.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Log.i(TAG, "onClick()");
        clickListener.onClicked(unverifiedIdentities);
      }
    });

    this.closeButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        hide();
        dismissListener.onDismissed(unverifiedIdentities);
      }
    });
  }

  public void hide() {
    setVisibility(View.GONE);
  }

  public interface DismissListener {
    public void onDismissed(List<IdentityRecord> unverifiedIdentities);
  }

  public interface ClickListener {
    public void onClicked(List<IdentityRecord> unverifiedIdentities);
  }

}
