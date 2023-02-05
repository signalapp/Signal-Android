package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import org.signal.core.util.DimensionUnit;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideApp;

/**
 * Class for creating simple tooltips to show throughout the app. Utilizes a popup window so you
 * don't have to worry about view hierarchies or anything.
 */
public class TooltipPopup extends PopupWindow {

  public static final int POSITION_ABOVE = 0;
  public static final int POSITION_BELOW = 1;
  public static final int POSITION_START = 2;
  public static final int POSITION_END   = 3;

  private static final int POSITION_LEFT  = 4;
  private static final int POSITION_RIGHT = 5;

  private final View      anchor;
  private final ImageView arrow;
  private final int position;
  private final int startMargin;

  private final MaterialShapeDrawable shapeableBubbleBackground = new MaterialShapeDrawable();

  public static Builder forTarget(@NonNull View anchor) {
    return new Builder(anchor);
  }

  private TooltipPopup(@NonNull View anchor,
                       int rawPosition,
                       @Px int startMargin,
                       @NonNull String text,
                       @ColorInt int backgroundTint,
                       @ColorInt int textColor,
                       @Nullable Object iconGlideModel,
                       @Nullable OnDismissListener dismissListener)
  {
    super(LayoutInflater.from(anchor.getContext()).inflate(R.layout.tooltip, null),
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT);

    this.anchor      = anchor;
    this.position    = getRtlPosition(anchor.getContext(), rawPosition);
    this.startMargin = startMargin;

    switch (rawPosition) {
      case POSITION_ABOVE: arrow = getContentView().findViewById(R.id.tooltip_arrow_bottom); break;
      case POSITION_BELOW: arrow = getContentView().findViewById(R.id.tooltip_arrow_top); break;
      case POSITION_START: arrow = getContentView().findViewById(R.id.tooltip_arrow_end); break;
      case POSITION_END:   arrow = getContentView().findViewById(R.id.tooltip_arrow_start); break;
      default: throw new AssertionError("Invalid position!");
    }

    arrow.setVisibility(View.VISIBLE);

    TextView textView = getContentView().findViewById(R.id.tooltip_text);
    textView.setText(text);

    if (textColor != 0) {
      textView.setTextColor(textColor);
    }

    View bubble = getContentView().findViewById(R.id.tooltip_bubble);

    if (backgroundTint == 0) {
      bubble.getBackground().setColorFilter(ContextCompat.getColor(anchor.getContext(), R.color.tooltip_default_color), PorterDuff.Mode.MULTIPLY);
      arrow.setColorFilter(ContextCompat.getColor(anchor.getContext(), R.color.tooltip_default_color), PorterDuff.Mode.SRC_IN);
      shapeableBubbleBackground.setTint(ContextCompat.getColor(anchor.getContext(), R.color.tooltip_default_color));
    } else {
      bubble.getBackground().setColorFilter(backgroundTint, PorterDuff.Mode.MULTIPLY);
      arrow.setColorFilter(backgroundTint, PorterDuff.Mode.SRC_IN);
      shapeableBubbleBackground.setTint(backgroundTint);
    }

    if (iconGlideModel != null) {
      ImageView iconView = getContentView().findViewById(R.id.tooltip_icon);
      iconView.setVisibility(View.VISIBLE);
      GlideApp.with(anchor.getContext()).load(iconGlideModel).into(iconView);
    }

    setElevation(10);

    getContentView().setOnClickListener(v -> dismiss());

    setOnDismissListener(dismissListener);
    setBackgroundDrawable(null);
    setOutsideTouchable(true);
  }

  private void show() {
    if (anchor.getWidth() == 0 && anchor.getHeight() == 0) {
      anchor.post(this::show);
      return;
    }

    getContentView().measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                             View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

    int tooltipSpacing = anchor.getContext().getResources().getDimensionPixelOffset(R.dimen.tooltip_popup_margin);

    int xoffset;
    int yoffset;

    switch (position) {
      case POSITION_ABOVE:
        xoffset = 0;
        yoffset = -(anchor.getHeight() + getContentView().getMeasuredHeight() + tooltipSpacing);
        onLayout(() -> setArrowHorizontalPosition(arrow, anchor));
        break;
      case POSITION_BELOW:
        xoffset = 0;
        yoffset = tooltipSpacing;
        onLayout(() -> setArrowHorizontalPosition(arrow, anchor));
        break;
      case POSITION_LEFT:
        xoffset = -getContentView().getMeasuredWidth() - tooltipSpacing;
        yoffset = -(getContentView().getMeasuredHeight()/2 + anchor.getHeight()/2);
        onLayout(() -> setArrowVerticalPosition(arrow, anchor));
        break;
      case POSITION_RIGHT:
        xoffset = anchor.getWidth() + tooltipSpacing;
        yoffset = -(getContentView().getMeasuredHeight()/2 + anchor.getHeight()/2);
        onLayout(() -> setArrowVerticalPosition(arrow, anchor));
        break;
      default:
        throw new AssertionError("Invalid tooltip position!");
    }

    switch (position) {
      case POSITION_ABOVE:
        xoffset += startMargin;
        xoffset -= DimensionUnit.DP.toPixels(20);
      case POSITION_BELOW:
        xoffset += startMargin;
        xoffset -= DimensionUnit.DP.toPixels(20);
        break;
      case POSITION_LEFT:
        xoffset += startMargin;
        break;
      case POSITION_RIGHT:
        xoffset -= startMargin;
    }

    View bubble = getContentView().findViewById(R.id.tooltip_bubble);
    ShapeAppearanceModel.Builder  shapeAppearanceModel  = ShapeAppearanceModel.builder()
                                                                              .setAllCornerSizes(DimensionUnit.DP.toPixels(18));

    // If the arrow is within the last 20dp of the right hand side, use RIGHT and set corner to 9dp
    onLayout(() -> {
      if (arrow.getX() > getContentView().getWidth() / 2f) {
        arrow.setImageResource(R.drawable.ic_tooltip_arrow_up_right);
      }

      float arrowEnd = arrow.getX() + arrow.getRight();
      if (arrowEnd > getContentView().getRight() - DimensionUnit.DP.toPixels(20)) {
        shapeableBubbleBackground.setShapeAppearanceModel(shapeAppearanceModel.setTopRightCornerSize(DimensionUnit.DP.toPixels(9f)).build());
        bubble.setBackground(shapeableBubbleBackground);
      } else if (arrowEnd < DimensionUnit.DP.toPixels(20)) {
        shapeableBubbleBackground.setShapeAppearanceModel(shapeAppearanceModel.setTopLeftCornerSize(DimensionUnit.DP.toPixels(9f)).build());
        bubble.setBackground(shapeableBubbleBackground);
      }
    });

    showAsDropDown(anchor, xoffset, yoffset);
  }

  private void onLayout(@NonNull Runnable runnable) {
    getContentView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout() {
        getContentView().getViewTreeObserver().removeOnGlobalLayoutListener(this);
        runnable.run();
      }
    });
  }

  private static void setArrowHorizontalPosition(@NonNull View arrow, @NonNull View anchor) {
    int arrowCenterX  = getAbsolutePosition(arrow)[0] + arrow.getWidth()/2;
    int anchorCenterX = getAbsolutePosition(anchor)[0] + anchor.getWidth()/2;

    arrow.setTranslationX(anchorCenterX - arrowCenterX);
  }

  private static void setArrowVerticalPosition(@NonNull View arrow, @NonNull View anchor) {
    int arrowCenterY  = getAbsolutePosition(arrow)[1] + arrow.getHeight()/2;
    int anchorCenterY = getAbsolutePosition(anchor)[1] + anchor.getHeight()/2;

    arrow.setTranslationY(anchorCenterY - arrowCenterY);
  }

  private static int[] getAbsolutePosition(@NonNull View view) {
    int[] position = new int[2];
    view.getLocationOnScreen(position);
    return position;
  }

  private static int getRtlPosition(@NonNull Context context, int position) {
    if (position == POSITION_ABOVE || position == POSITION_BELOW) {
      return position;
    } else if (context.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
      return position == POSITION_START ? POSITION_RIGHT : POSITION_LEFT;
    } else {
      return position == POSITION_START ? POSITION_LEFT : POSITION_RIGHT;
    }
  }

  public static class Builder {

    private final View anchor;

    private int               backgroundTint;
    private int               textColor;
    private int               textResId;
    private Object            iconGlideModel;
    private OnDismissListener dismissListener;
    private int               setStartMargin;

    private Builder(@NonNull View anchor) {
      this.anchor = anchor;
    }

    public Builder setBackgroundTint(@ColorInt int color) {
      this.backgroundTint = color;
      return this;
    }

    public Builder setTextColor(@ColorInt int color) {
      this.textColor = color;
      return this;
    }

    public Builder setText(@StringRes int stringResId) {
      this.textResId = stringResId;
      return this;
    }

    public Builder setIconGlideModel(Object model) {
      this.iconGlideModel = model;
      return this;
    }

    public Builder setOnDismissListener(OnDismissListener dismissListener) {
      this.dismissListener = dismissListener;
      return this;
    }

    public Builder setStartMargin(@Px int startMargin) {
      this.setStartMargin = startMargin;
      return this;
    }

    public TooltipPopup show(int position) {
      String       text    = anchor.getContext().getString(textResId);
      TooltipPopup tooltip = new TooltipPopup(anchor, position, setStartMargin, text, backgroundTint, textColor, iconGlideModel, dismissListener);

      tooltip.show();

      return tooltip;
    }
  }
}
