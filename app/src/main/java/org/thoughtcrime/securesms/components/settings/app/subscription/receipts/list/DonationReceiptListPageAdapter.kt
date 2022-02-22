package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.thoughtcrime.securesms.database.model.DonationReceiptRecord

class DonationReceiptListPageAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
  override fun getItemCount(): Int = 3

  override fun createFragment(position: Int): Fragment {
    return when (position) {
      0 -> DonationReceiptListPageFragment.create(null)
      1 -> DonationReceiptListPageFragment.create(DonationReceiptRecord.Type.RECURRING)
      2 -> DonationReceiptListPageFragment.create(DonationReceiptRecord.Type.BOOST)
      else -> error("Unsupported position $position")
    }
  }
}
