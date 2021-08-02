package org.thoughtcrime.securesms.components.webrtc

import android.view.View
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayout

object CallParticipantsLayoutStrategies {

  private object Portrait : CallParticipantsLayout.LayoutStrategy {
    override fun getFlexDirection(): Int = FlexDirection.ROW

    override fun setChildLayoutParams(child: View, childPosition: Int, childCount: Int) {
      val params = child.layoutParams as FlexboxLayout.LayoutParams
      if (childCount < 3) {
        params.flexBasisPercent = 1f
      } else {
        if ((childCount % 2) != 0 && childPosition == childCount - 1) {
          params.flexBasisPercent = 1f
        } else {
          params.flexBasisPercent = 0.5f
        }
      }
      child.layoutParams = params
    }
  }

  private object Landscape : CallParticipantsLayout.LayoutStrategy {
    override fun getFlexDirection() = FlexDirection.COLUMN

    override fun setChildLayoutParams(child: View, childPosition: Int, childCount: Int) {
      val params = child.layoutParams as FlexboxLayout.LayoutParams
      if (childCount < 4) {
        params.flexBasisPercent = 1f
      } else {
        if ((childCount % 2) != 0 && childPosition == childCount - 1) {
          params.flexBasisPercent = 1f
        } else {
          params.flexBasisPercent = 0.5f
        }
      }
      child.layoutParams = params
    }
  }

  @JvmStatic
  fun getStrategy(isPortrait: Boolean, isLandscapeEnabled: Boolean): CallParticipantsLayout.LayoutStrategy {
    return if (isPortrait || !isLandscapeEnabled) {
      Portrait
    } else {
      Landscape
    }
  }
}
