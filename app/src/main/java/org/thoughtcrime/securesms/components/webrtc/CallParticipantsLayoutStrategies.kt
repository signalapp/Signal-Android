package org.thoughtcrime.securesms.components.webrtc

import android.view.View
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayout
import org.thoughtcrime.securesms.events.CallParticipant
import org.webrtc.RendererCommon

object CallParticipantsLayoutStrategies {

  private object Portrait : CallParticipantsLayout.LayoutStrategy {
    override fun getFlexDirection(): Int = FlexDirection.ROW

    override fun setChildScaling(callParticipant: CallParticipant, callParticipantView: CallParticipantView, isPortrait: Boolean, childCount: Int) {
      if (callParticipant.isScreenSharing) {
        callParticipantView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
      } else {
        val matchOrientationScaling = if (isPortrait || childCount < 3) RendererCommon.ScalingType.SCALE_ASPECT_FILL else RendererCommon.ScalingType.SCALE_ASPECT_BALANCED
        val mismatchOrientationScaling = if (childCount == 1) RendererCommon.ScalingType.SCALE_ASPECT_FIT else matchOrientationScaling
        callParticipantView.setScalingType(matchOrientationScaling, mismatchOrientationScaling)
      }
    }

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

    override fun setChildScaling(callParticipant: CallParticipant, callParticipantView: CallParticipantView, isPortrait: Boolean, childCount: Int) {
      if (callParticipant.isScreenSharing) {
        callParticipantView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
      } else {
        callParticipantView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL, RendererCommon.ScalingType.SCALE_ASPECT_BALANCED)
      }
    }

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
