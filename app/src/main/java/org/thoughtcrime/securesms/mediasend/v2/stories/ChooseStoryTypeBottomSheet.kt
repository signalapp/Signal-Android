package org.thoughtcrime.securesms.mediasend.v2.stories

import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsBottomSheetFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.components.settings.conversation.preferences.LargeIconClickPreference
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.signal.core.ui.R as CoreUiR

class ChooseStoryTypeBottomSheet : DSLSettingsBottomSheetFragment(
  layoutId = R.layout.dsl_settings_bottom_sheet
) {
  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    LargeIconClickPreference.register(adapter)
    adapter.submitList(getConfiguration().toMappingModelList())
  }

  private fun getConfiguration(): DSLConfiguration {
    return configure {
      textPref(
        title = DSLSettingsText.from(
          stringId = R.string.ChooseStoryTypeBottomSheet__choose_your_story_type,
          DSLSettingsText.CenterModifier,
          DSLSettingsText.TitleMediumModifier
        )
      )

      customPref(
        LargeIconClickPreference.Model(
          title = DSLSettingsText.from(
            stringId = R.string.ChooseStoryTypeBottomSheet__new_custom_story
          ),
          summary = DSLSettingsText.from(
            stringId = R.string.ChooseStoryTypeBottomSheet__visible_only_to
          ),
          icon = DSLSettingsIcon.from(
            iconId = R.drawable.symbol_stories_24,
            iconTintId = CoreUiR.color.signal_colorOnSurface,
            backgroundId = R.drawable.circle_tintable,
            backgroundTint = CoreUiR.color.signal_colorSurface5,
            insetPx = DimensionUnit.DP.toPixels(8f).toInt()
          ),
          onClick = {
            dismissAllowingStateLoss()
            requireListener<Callback>().onNewStoryClicked()
          }
        )
      )

      customPref(
        LargeIconClickPreference.Model(
          title = DSLSettingsText.from(
            stringId = R.string.ChooseStoryTypeBottomSheet__group_story
          ),
          summary = DSLSettingsText.from(
            stringId = R.string.ChooseStoryTypeBottomSheet__share_to_an_existing_group
          ),
          icon = DSLSettingsIcon.from(
            iconId = R.drawable.ic_group_outline_24,
            iconTintId = CoreUiR.color.signal_colorOnSurface,
            backgroundId = R.drawable.circle_tintable,
            backgroundTint = CoreUiR.color.signal_colorSurface5,
            insetPx = DimensionUnit.DP.toPixels(8f).toInt()
          ),
          onClick = {
            dismissAllowingStateLoss()
            requireListener<Callback>().onGroupStoryClicked()
          }
        )
      )

      space(DimensionUnit.DP.toPixels(32f).toInt())
    }
  }

  interface Callback {
    fun onNewStoryClicked()
    fun onGroupStoryClicked()
  }
}
