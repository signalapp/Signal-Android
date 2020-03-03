package org.thoughtcrime.securesms.groups.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MenuInflater;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;

import org.thoughtcrime.securesms.R;

public final class PopupMenuView extends View {

  private @MenuRes  int       menu;
  private @Nullable ItemClick callback;

  public PopupMenuView(Context context) {
    super(context);
    init();
  }

  public PopupMenuView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public PopupMenuView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    setBackgroundResource(R.drawable.ic_more_vert_24);

    setOnClickListener(v -> {
      if (callback != null) {
        PopupMenu    popup    = new PopupMenu(getContext(), v);
        MenuInflater inflater = popup.getMenuInflater();

        inflater.inflate(menu, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> callback.onItemClick(item.getItemId()));
        popup.show();
      }
    });
  }

  public void setMenu(@MenuRes int menu, @NonNull ItemClick callback) {
    this.menu     = menu;
    this.callback = callback;
  }

  public interface ItemClick {
    boolean onItemClick(@IdRes int menuItemId);
  }
}
