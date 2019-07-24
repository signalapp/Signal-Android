package org.thoughtcrime.securesms.components;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.thoughtcrime.securesms.R;

public class RemovableEditableMediaView extends FrameLayout {

  private final @NonNull ImageView remove;
  private final @NonNull ImageView edit;

  private final int removeSize;
  private final int editSize;

  private @Nullable View current;

  public RemovableEditableMediaView(Context context) {
    this(context, null);
  }

  public RemovableEditableMediaView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public RemovableEditableMediaView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    this.remove     = (ImageView)LayoutInflater.from(context).inflate(R.layout.media_view_remove_button, this, false);
    this.edit       = (ImageView)LayoutInflater.from(context).inflate(R.layout.media_view_edit_button, this, false);

    this.removeSize = getResources().getDimensionPixelSize(R.dimen.media_bubble_remove_button_size);
    this.editSize   = getResources().getDimensionPixelSize(R.dimen.media_bubble_edit_button_size);

    this.remove.setVisibility(View.GONE);
    this.edit.setVisibility(View.GONE);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();
    this.addView(remove);
    this.addView(edit);
  }

  public void display(@Nullable View view, boolean editable) {
    edit.setVisibility(editable ? View.VISIBLE : View.GONE);
    
    if (view == current) return;
    if (current != null) current.setVisibility(View.GONE);

    if (view != null) {
      view.setPadding(view.getPaddingLeft(), removeSize / 2, removeSize / 2, view.getPaddingRight());
      edit.setPadding(0, 0, removeSize / 2, 0);

      view.setVisibility(View.VISIBLE);
      remove.setVisibility(View.VISIBLE);
    } else {
      remove.setVisibility(View.GONE);
      edit.setVisibility(View.GONE);
    }

    current = view;
  }

  public void setRemoveClickListener(View.OnClickListener listener) {
    this.remove.setOnClickListener(listener);
  }

  public void setEditClickListener(View.OnClickListener listener) {
    this.edit.setOnClickListener(listener);
  }
}
