package org.thoughtcrime.securesms.badges.self.featured

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeRepository
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.badges.Badges.displayBadges
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.badges.models.FeaturedBadgePreview
import org.thoughtcrime.securesms.components.recyclerview.OnScrollAnimationHelper
import org.thoughtcrime.securesms.components.recyclerview.ToolbarShadowAnimationHelper
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.configure

/**
 * Fragment which allows user to select one of their badges to be their "Featured" badge.
 */
class SelectFeaturedBadgeFragment : DSLSettingsFragment(
  titleId = R.string.BadgesOverviewFragment__featured_badge,
  layoutId = R.layout.select_featured_badge_fragment,
  layoutManagerProducer = Badges::createLayoutManagerForGridWithBadges
) {

  private val viewModel: SelectFeaturedBadgeViewModel by viewModels(factoryProducer = { SelectFeaturedBadgeViewModel.Factory(BadgeRepository(requireContext())) })

  private lateinit var scrollShadow: View

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    scrollShadow = view.findViewById(R.id.scroll_shadow)

    super.onViewCreated(view, savedInstanceState)

    val save: View = view.findViewById(R.id.save)
    save.setOnClickListener {
      viewModel.save()
      findNavController().popBackStack()
    }
  }

  override fun getOnScrollAnimationHelper(toolbarShadow: View): OnScrollAnimationHelper {
    return ToolbarShadowAnimationHelper(scrollShadow)
  }

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    Badge.register(adapter) { badge, isSelected ->
      if (!isSelected) {
        viewModel.setSelectedBadge(badge)
      }
    }

    val previewView: View = requireView().findViewById(R.id.preview)
    val previewViewHolder = FeaturedBadgePreview.ViewHolder(previewView)

    viewModel.state.observe(viewLifecycleOwner) { state ->
      previewViewHolder.bind(FeaturedBadgePreview.Model(state.selectedBadge))
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: SelectFeaturedBadgeState): DSLConfiguration {
    return configure {
      sectionHeaderPref(R.string.SelectFeaturedBadgeFragment__select_a_badge)
      displayBadges(state.allUnlockedBadges, state.selectedBadge)
    }
  }
}
