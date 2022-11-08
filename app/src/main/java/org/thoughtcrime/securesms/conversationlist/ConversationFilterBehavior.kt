package org.thoughtcrime.securesms.conversationlist

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout.Behavior
import androidx.core.view.ViewCompat
import com.google.android.material.appbar.AppBarLayout
import org.thoughtcrime.securesms.util.FeatureFlags

class ConversationFilterBehavior(context: Context, attributeSet: AttributeSet) : AppBarLayout.Behavior(context, attributeSet) {

  override fun onStartNestedScroll(parent: CoordinatorLayout, child: AppBarLayout, directTargetChild: View, target: View, nestedScrollAxes: Int, type: Int): Boolean {
    if (type == ViewCompat.TYPE_NON_TOUCH || !FeatureFlags.chatFilters()) {
      return false
    } else {
      return super.onStartNestedScroll(parent, child, directTargetChild, target, nestedScrollAxes, type)
    }
  }

  override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout, child: AppBarLayout, target: View, type: Int) {
    super.onStopNestedScroll(coordinatorLayout, child, target, type)
    child.setExpanded(false, true)
  }
}
