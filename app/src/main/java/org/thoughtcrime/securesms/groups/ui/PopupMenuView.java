package org.thoughtcrime.securesms.groups.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;

import org.thoughtcrime.securesms.R;

public final class PopupMenuView extends View {

  @MenuRes  private int                    menu;
  @Nullable private PrepareOptionsMenuItem prepareOptionsMenuItemCallback;
  @Nullable private ItemClick              callback;

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

        if (prepareOptionsMenuItemCallback != null) {
          Menu menu = popup.getMenu();
          for (int i = menu.size() - 1; i >= 0; i--) {
            MenuItem item = menu.getItem(i);
            if (!prepareOptionsMenuItemCallback.onPrepareOptionsMenuItem(item)) {
              menu.removeItem(item.getItemId());
            }
          }
        }

        popup.setOnMenuItemClickListener(item -> callback.onItemClick(item.getItemId()));
        popup.show();
      }
    });
  }

  public void setMenu(@MenuRes int menu, @NonNull ItemClick callback) {
    this.menu                           = menu;
    this.prepareOptionsMenuItemCallback = null;
    this.callback                       = callback;
  }

  public void setMenu(@MenuRes int menu, @NonNull PrepareOptionsMenuItem prepareOptionsMenuItem, @NonNull ItemClick callback) {
    this.menu                           = menu;
    this.prepareOptionsMenuItemCallback = prepareOptionsMenuItem;
    this.callback                       = callback;
  }

  public interface PrepareOptionsMenuItem {

    /**
     * Chance to change the {@link MenuItem} after inflation.
     *
     * @return true to keep the {@link MenuItem}. false to remove the {@link MenuItem}.
     */
    boolean onPrepareOptionsMenuItem(@NonNull MenuItem menuItem);
  }

  public interface ItemClick {
    boolean onItemClick(@IdRes int menuItemId);
  }
}
