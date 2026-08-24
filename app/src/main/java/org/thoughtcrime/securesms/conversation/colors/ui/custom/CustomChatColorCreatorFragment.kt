package org.thoughtcrime.securesms.conversation.colors.ui.custom

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.SystemWindowInsetsSetter

class CustomChatColorCreatorFragment : Fragment(R.layout.custom_chat_color_creator_fragment) {

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    val tabLayout: TabLayout = view.findViewById(R.id.tab_layout)
    val pager: ViewPager2 = view.findViewById(R.id.pager)
    val adapter = CustomChatColorPagerAdapter(this, requireArguments())
    val tabLayoutMediator = TabLayoutMediator(tabLayout, pager) { tab, position ->
      tab.setText(
        if (position == 0) {
          R.string.CustomChatColorCreatorFragment__solid
        } else {
          R.string.CustomChatColorCreatorFragment__gradient
        }
      )
    }

    toolbar.updateLayoutParams { height = ViewGroup.LayoutParams.WRAP_CONTENT }
    SystemWindowInsetsSetter.attach(toolbar, viewLifecycleOwner, WindowInsetsCompat.Type.statusBars())

    toolbar.setNavigationOnClickListener {
      Navigation.findNavController(it).popBackStack()
    }

    pager.isUserInputEnabled = false
    pager.adapter = adapter

    tabLayoutMediator.attach()

    val startPage = CustomChatColorCreatorFragmentArgs.fromBundle(requireArguments()).startPage
    pager.setCurrentItem(startPage, false)
  }
}
