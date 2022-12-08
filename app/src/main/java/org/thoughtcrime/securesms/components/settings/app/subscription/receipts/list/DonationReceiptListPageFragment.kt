package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import android.os.Bundle
import android.view.View
import androidx.constraintlayout.widget.Group
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.TextPreference
import org.thoughtcrime.securesms.database.model.DonationReceiptRecord
import org.thoughtcrime.securesms.util.StickyHeaderDecoration
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.visible

class DonationReceiptListPageFragment : Fragment(R.layout.donation_receipt_list_page_fragment) {

  private val viewModel: DonationReceiptListPageViewModel by viewModels(factoryProducer = {
    DonationReceiptListPageViewModel.Factory(type, DonationReceiptListPageRepository())
  })

  private val sharedViewModel: DonationReceiptListViewModel by viewModels(
    ownerProducer = { requireParentFragment() },
    factoryProducer = {
      DonationReceiptListViewModel.Factory(DonationReceiptListRepository())
    }
  )

  private val type: DonationReceiptRecord.Type?
    get() = requireArguments().getString(ARG_TYPE)?.let { DonationReceiptRecord.Type.fromCode(it) }

  private lateinit var emptyStateGroup: Group

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val adapter = DonationReceiptListAdapter { model ->
      findNavController().safeNavigate(DonationReceiptListFragmentDirections.actionDonationReceiptListFragmentToDonationReceiptDetailFragment(model.record.id))
    }

    view.findViewById<RecyclerView>(R.id.recycler).apply {
      this.adapter = adapter
      addItemDecoration(StickyHeaderDecoration(adapter, false, true, 0))
    }

    emptyStateGroup = view.findViewById(R.id.empty_state)

    LiveDataUtil.combineLatest(
      viewModel.state,
      sharedViewModel.state
    ) { state, badges ->
      state.isLoaded to state.records.map { DonationReceiptListItem.Model(it, getBadgeForRecord(it, badges)) }
    }.observe(viewLifecycleOwner) { (isLoaded, records) ->
      if (records.isNotEmpty()) {
        emptyStateGroup.visible = false
        adapter.submitList(
          records +
            TextPreference(
              title = null,
              summary = DSLSettingsText.from(
                R.string.DonationReceiptListFragment__if_you_have,
                DSLSettingsText.TextAppearanceModifier(R.style.TextAppearance_Signal_Subtitle)
              )
            )
        )
      } else {
        emptyStateGroup.visible = isLoaded
      }
    }
  }

  private fun getBadgeForRecord(record: DonationReceiptRecord, badges: List<DonationReceiptBadge>): Badge? {
    return when (record.type) {
      DonationReceiptRecord.Type.BOOST -> badges.firstOrNull { it.type == DonationReceiptRecord.Type.BOOST }?.badge
      DonationReceiptRecord.Type.GIFT -> badges.firstOrNull { it.type == DonationReceiptRecord.Type.GIFT }?.badge
      else -> badges.firstOrNull { it.level == record.subscriptionLevel }?.badge
    }
  }

  companion object {

    private const val ARG_TYPE = "arg_type"

    fun create(type: DonationReceiptRecord.Type?): Fragment {
      return DonationReceiptListPageFragment().apply {
        arguments = Bundle().apply {
          putString(ARG_TYPE, type?.code)
        }
      }
    }
  }
}
