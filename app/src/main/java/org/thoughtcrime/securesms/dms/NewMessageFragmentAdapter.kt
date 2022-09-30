package org.thoughtcrime.securesms.dms

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.thoughtcrime.securesms.util.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.util.ScanQRCodeWrapperFragmentDelegate

class NewMessageFragmentAdapter(
    private val parentFragment: Fragment,
    private val enterPublicKeyDelegate: EnterPublicKeyDelegate,
    private val scanPublicKeyDelegate: ScanQRCodeWrapperFragmentDelegate
) : FragmentStateAdapter(parentFragment) {

    override fun getItemCount(): Int  = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> EnterPublicKeyFragment().apply { delegate = enterPublicKeyDelegate }
            1 ->  ScanQRCodeWrapperFragment().apply { delegate = scanPublicKeyDelegate }
            else -> throw IllegalStateException()
        }
    }

}