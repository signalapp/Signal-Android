package org.thoughtcrime.securesms.groups

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.thoughtcrime.securesms.util.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.util.ScanQRCodeWrapperFragmentDelegate

class JoinCommunityFragmentAdapter(
    private val parentFragment: Fragment,
    private val enterCommunityUrlDelegate: EnterCommunityUrlDelegate,
    private val scanQrCodeDelegate: ScanQRCodeWrapperFragmentDelegate
) : FragmentStateAdapter(parentFragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> EnterCommunityUrlFragment().apply { delegate = enterCommunityUrlDelegate }
            1 ->  ScanQRCodeWrapperFragment().apply { delegate = scanQrCodeDelegate }
            else -> throw IllegalStateException()
        }
    }
}