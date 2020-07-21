package org.thoughtcrime.securesms.groups.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
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
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import org.thoughtcrime.securesms.R;

import java.util.Objects;

public final class PopupMenuView extends View {

  @MenuRes  private int                    menu;
  @Nullable private PrepareOptionsMenuItem prepareOptionsMenuItemCallback;
  @Nullable private ItemClick              callback;

  public PopupMenuView(Context context) {
    super(context);
    init(null);
  }

  public PopupMenuView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  public PopupMenuView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(attrs);
  }

  private void init(@Nullable AttributeSet attrs) {
    setBackgroundResource(R.drawable.ic_more_vert_24);

    if (attrs != null) {
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.PopupMenuView, 0, 0);
      int        tint       = typedArray.getColor(R.styleable.PopupMenuView_background_tint, Color.BLACK);
      Drawable   drawable   = ContextCompat.getDrawable(getContext(), R.drawable.ic_more_vert_24);

      DrawableCompat.setTint(Objects.requireNonNull(drawable), tint);

      setBackground(drawable);

      typedArray.recycle();
    }

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
