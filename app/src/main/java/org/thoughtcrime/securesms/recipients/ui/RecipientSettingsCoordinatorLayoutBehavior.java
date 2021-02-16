package org.thoughtcrime.securesms.recipients.ui;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.appbar.AppBarLayout;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.lang.ref.WeakReference;

public final class RecipientSettingsCoordinatorLayoutBehavior extends CoordinatorLayout.Behavior<View> {

  private static final Interpolator INTERPOLATOR = new DecelerateInterpolator();

  private final ViewReference avatarTargetRef = new ViewReference(R.id.avatar_target);
  private final ViewReference nameRef         = new ViewReference(R.id.name);
  private final ViewReference nameTargetRef   = new ViewReference(R.id.name_target);
  private final Rect          targetRect      = new Rect();
  private final Rect          childRect       = new Rect();

  public RecipientSettingsCoordinatorLayoutBehavior(@NonNull Context context, @Nullable AttributeSet attrs) {
  }

  @Override
  public boolean layoutDependsOn(@NonNull CoordinatorLayout parent, @NonNull View child, @NonNull View dependency) {
    return dependency instanceof AppBarLayout;
  }

  @Override
  public boolean onDependentViewChanged(@NonNull CoordinatorLayout parent, @NonNull View child, @NonNull View dependency) {
    AppBarLayout appBarLayout = (AppBarLayout) dependency;
    int          range        = appBarLayout.getTotalScrollRange();
    float        factor       = INTERPOLATOR.getInterpolation(-appBarLayout.getY() / range);

    updateAvatarPositionAndScale(parent, child, factor);
    updateNamePosition(parent, factor);

    return true;
  }

  private void updateAvatarPositionAndScale(@NonNull CoordinatorLayout parent, @NonNull View child, float factor) {
    View target = avatarTargetRef.require(parent);

    targetRect.set(target.getLeft(), target.getTop(), target.getRight(), target.getBottom());
    childRect.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());

    float widthScale  = 1f - (1f - (targetRect.width() / (float) childRect.width())) * factor;
    float heightScale = 1f - (1f - (targetRect.height() / (float) childRect.height())) * factor;

    float superimposedLeft = childRect.left + (childRect.width() - targetRect.width()) / 2f;
    float superimposedTop  = childRect.top + (childRect.height() - targetRect.height()) / 2f;

    float xTranslation = (targetRect.left - superimposedLeft) * factor;
    float yTranslation = (targetRect.top - superimposedTop) * factor;

    child.setScaleX(widthScale);
    child.setScaleY(heightScale);
    child.setTranslationX(xTranslation);
    child.setTranslationY(yTranslation);
  }

  private void updateNamePosition(@NonNull CoordinatorLayout parent, float factor) {
    TextView child  = (TextView) nameRef.require(parent);
    View     target = nameTargetRef.require(parent);

    targetRect.set(target.getLeft(), target.getTop(), target.getRight(), target.getBottom());
    childRect.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());

    if (child.getMaxWidth() != targetRect.width()) {
      child.setMaxWidth(targetRect.width());
    }

    float deltaTop   = targetRect.top - childRect.top;
    float deltaStart = getStart(parent, targetRect) - getStart(parent, childRect);

    float yTranslation = deltaTop * factor;
    float xTranslation = deltaStart * factor;

    child.setTranslationY(yTranslation);
    child.setTranslationX(xTranslation);
  }

  private static int getStart(@NonNull CoordinatorLayout parent, @NonNull Rect rect) {
    return ViewUtil.isLtr(parent) ? rect.left : rect.right;
  }

  private static final class ViewReference {

    private WeakReference<View> ref = new WeakReference<>(null);

    private final @IdRes int idRes;

    private ViewReference(@IdRes int idRes) {
      this.idRes = idRes;
    }

    private @NonNull View require(@NonNull View parent) {
      View view = ref.get();

      if (view == null) {
        view = getChildOrThrow(parent, idRes);
        ref  = new WeakReference<>(view);
      }

      return view;
    }

    private static @NonNull View getChildOrThrow(@NonNull View parent, @IdRes int id) {
      View child = parent.findViewById(id);

      if (child == null) {
        throw new AssertionError("Can't find view with ID " + R.id.avatar_target);
      } else {
        return child;
      }
    }
  }
}
