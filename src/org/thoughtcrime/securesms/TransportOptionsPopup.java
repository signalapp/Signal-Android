package org.thoughtcrime.securesms;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.PopupWindow;

import java.util.LinkedList;
import java.util.List;

public class TransportOptionsPopup implements ListView.OnItemClickListener {

  private final TransportOptionsAdapter adapter;
  private final PopupWindow             popupWindow;
  private final SelectedListener        listener;

  private OnGlobalLayoutListener observer;
  private View                   parent;

  public TransportOptionsPopup(@NonNull Context context, @NonNull SelectedListener listener) {
    this.listener = listener;
    this.adapter  = new TransportOptionsAdapter(context, new LinkedList<TransportOption>());

    View     selectionMenu = LayoutInflater.from(context).inflate(R.layout.transport_selection, null);
    ListView listView      = (ListView) selectionMenu.findViewById(R.id.transport_selection_list);

    listView.setAdapter(adapter);

    this.popupWindow = new PopupWindow(selectionMenu);
    this.popupWindow.setFocusable(true);
    this.popupWindow.setBackgroundDrawable(new BitmapDrawable(context.getResources(), ""));
    this.popupWindow.setOutsideTouchable(true);
    this.popupWindow.setWindowLayoutMode(0, WindowManager.LayoutParams.WRAP_CONTENT);
    this.popupWindow.setWidth(context.getResources().getDimensionPixelSize(R.dimen.transport_selection_popup_width));
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      this.popupWindow.setAnimationStyle(0);
      this.popupWindow.setElevation(context.getResources().getDimensionPixelSize(R.dimen.transport_selection_popup_yoff));
    }

    listView.setOnItemClickListener(this);
  }

  public void display(Context context, final View parent, List<TransportOption> enabledTransports) {
    this.adapter.setEnabledTransports(enabledTransports);
    this.adapter.notifyDataSetChanged();

    final int xoff = context.getResources().getDimensionPixelOffset(R.dimen.transport_selection_popup_xoff);
    final int yoff = context.getResources().getDimensionPixelOffset(R.dimen.transport_selection_popup_yoff);

    popupWindow.showAsDropDown(parent, xoff, yoff);
    animateInIfAvailable();

    this.parent   = parent;
    this.observer = new OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout() {
        popupWindow.update(parent, xoff, yoff, -1, -1);
      }
    };
    parent.getViewTreeObserver().addOnGlobalLayoutListener(observer);
  }

  @TargetApi(VERSION_CODES.LOLLIPOP) private Animator getCircularReveal(View v, boolean in) {
    int outBound = Math.max(v.getWidth(), v.getHeight());
    return ViewAnimationUtils.createCircularReveal(v,
                                                   v.getMeasuredWidth(),
                                                   v.getMeasuredHeight(),
                                                   in ? 0 : outBound,
                                                   in ? outBound : 0)
                             .setDuration(200);
  }

  private void animateInIfAvailable() {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      popupWindow.getContentView().getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
        @Override @TargetApi(VERSION_CODES.LOLLIPOP) public void onGlobalLayout() {
          parent.getViewTreeObserver().removeGlobalOnLayoutListener(this);
          if (popupWindow.getContentView().isAttachedToWindow()) {
            getCircularReveal(popupWindow.getContentView(), true).start();
          }
        }
      });
    }
  }

  private void animateOutIfAvailable() {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      Animator animator = getCircularReveal(popupWindow.getContentView(), false);
      animator.addListener(new AnimatorListener() {
        @Override public void onAnimationStart(Animator animation) {}
        @Override public void onAnimationCancel(Animator animation) {}
        @Override public void onAnimationRepeat(Animator animation) {}
        @Override public void onAnimationEnd(Animator animation) {
          popupWindow.dismiss();
        }
      });
      animator.start();
    } else {
      popupWindow.dismiss();
    }
  }

  public void dismiss() {
    animateOutIfAvailable();
    if (this.observer != null && this.parent != null) {
      parent.getViewTreeObserver().removeGlobalOnLayoutListener(observer);
    }
    this.observer = null;
    this.parent   = null;
  }

  @Override
  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    listener.onSelected((TransportOption)adapter.getItem(position));
  }

  public interface SelectedListener {
    void onSelected(TransportOption option);
  }

}
