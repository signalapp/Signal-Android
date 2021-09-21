package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.tabs.TabLayout
import org.thoughtcrime.securesms.R
import java.util.Objects

/**
 * Custom View for Tabs which will render bold text when the view is selected
 */
class BoldSelectionTabItem @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  private lateinit var unselectedTextView: TextView
  private lateinit var selectedTextView: TextView

  override fun onFinishInflate() {
    super.onFinishInflate()

    unselectedTextView = findViewById(android.R.id.text1)
    selectedTextView = findViewById(R.id.text1_bold)

    unselectedTextView.doAfterTextChanged {
      selectedTextView.text = it
    }
  }

  fun select() {
    unselectedTextView.alpha = 0f
    selectedTextView.alpha = 1f
  }

  fun unselect() {
    unselectedTextView.alpha = 1f
    selectedTextView.alpha = 0f
  }

  companion object {
    @JvmStatic
    fun registerListeners(tabLayout: ControllableTabLayout) {
      val newTabListener = NewTabListener()
      val onTabSelectedListener = OnTabSelectedListener()

      (0 until tabLayout.tabCount).mapNotNull { tabLayout.getTabAt(it) }.forEach {
        newTabListener.onNewTab(it)

        if (it.isSelected) {
          onTabSelectedListener.onTabSelected(it)
        } else {
          onTabSelectedListener.onTabUnselected(it)
        }
      }

      tabLayout.setNewTabListener(newTabListener)
      tabLayout.addOnTabSelectedListener(onTabSelectedListener)
    }
  }

  private class NewTabListener : ControllableTabLayout.NewTabListener {
    override fun onNewTab(tab: TabLayout.Tab) {
      val customView = tab.customView
      if (customView == null) {
        tab.setCustomView(R.layout.bold_selection_tab_item)
      }
    }
  }

  private class OnTabSelectedListener : TabLayout.OnTabSelectedListener {
    override fun onTabSelected(tab: TabLayout.Tab) {
      val view = Objects.requireNonNull(tab.customView) as BoldSelectionTabItem
      view.select()
    }

    override fun onTabUnselected(tab: TabLayout.Tab) {
      val view = Objects.requireNonNull(tab.customView) as BoldSelectionTabItem
      view.unselect()
    }

    override fun onTabReselected(tab: TabLayout.Tab) = Unit
  }
}
