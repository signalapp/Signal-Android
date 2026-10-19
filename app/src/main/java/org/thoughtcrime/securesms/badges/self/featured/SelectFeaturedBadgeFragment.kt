package org.thoughtcrime.securesms.badges.self.featured

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.viewModel
import org.signal.core.util.collectFlowOnViewLifecycle
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.badges.Badges.displayBadges
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.badges.models.BadgePreview
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.util.Material3OnScrollHelper
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

/**
 * Fragment which allows user to select one of their badges to be their "Featured" badge.
 */
class SelectFeaturedBadgeFragment : DSLSettingsFragment(
  titleId = R.string.BadgesOverviewFragment__featured_badge,
  layoutId = R.layout.select_featured_badge_fragment,
  layoutManagerProducer = Badges::createLayoutManagerForGridWithBadges
) {

  private val viewModel: SelectFeaturedBadgeViewModel by viewModel { SelectFeaturedBadgeViewModel() }

  private lateinit var scrollShadow: View
  private lateinit var save: View

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    scrollShadow = view.findViewById(R.id.scroll_shadow)

    super.onViewCreated(view, savedInstanceState)

    save = view.findViewById(R.id.save)
    save.setOnClickListener {
      viewModel.onEvent(SelectFeaturedBadgeEvents.Save)
    }
  }

  override fun getMaterial3OnScrollHelper(toolbar: Toolbar?): Material3OnScrollHelper? {
    return Material3OnScrollHelper(
      activity = requireActivity(),
      views = listOf(scrollShadow),
      lifecycleOwner = viewLifecycleOwner
    )
  }

  override fun bindAdapter(adapter: MappingAdapter) {
    Badge.register(adapter) { badge, isSelected, _ ->
      if (!isSelected) {
        viewModel.onEvent(SelectFeaturedBadgeEvents.BadgeSelected(badge))
      }
    }

    val previewView: View = requireView().findViewById(R.id.preview)
    val previewViewHolder = BadgePreview.ViewHolder<BadgePreview.BadgeModel.FeaturedModel>(previewView)

    collectFlowOnViewLifecycle(viewModel.actions) { action ->
      when (action) {
        SelectFeaturedBadgeActions.ShowFailedToUpdateProfile -> Toast.makeText(requireContext(), R.string.SelectFeaturedBadgeFragment__failed_to_update_profile, Toast.LENGTH_LONG).show()
      }
    }

    var hasBoundPreview = false
    collectFlowOnViewLifecycle(viewModel.state) { state ->
      if (state.stage == SelectFeaturedBadgeState.Stage.SAVED) {
        findNavController().popBackStack()
        return@collectFlowOnViewLifecycle
      }

      save.isEnabled = state.stage == SelectFeaturedBadgeState.Stage.READY && state.selectedBadge != null

      if (hasBoundPreview) {
        previewViewHolder.setPayload(listOf(Unit))
      } else {
        hasBoundPreview = true
      }

      previewViewHolder.bind(BadgePreview.BadgeModel.FeaturedModel(state.selectedBadge))
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: SelectFeaturedBadgeState): DSLConfiguration {
    return configure {
      sectionHeaderPref(R.string.SelectFeaturedBadgeFragment__select_a_badge)
      displayBadges(requireContext(), state.allUnlockedBadges, state.selectedBadge)
    }
  }
}
