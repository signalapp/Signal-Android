package org.thoughtcrime.securesms.stories.viewer

import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.viewer.page.StoryViewerPageFragment

class StoryViewerPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

  private var pages: List<RecipientId> = emptyList()

  fun setPages(newPages: List<RecipientId>) {
    val oldPages = pages
    pages = newPages

    val callback = Callback(oldPages, pages)
    DiffUtil.calculateDiff(callback).dispatchUpdatesTo(this)
  }

  override fun getItemCount(): Int = pages.size

  override fun createFragment(position: Int): Fragment {
    return StoryViewerPageFragment.create(pages[position])
  }

  private class Callback(
    private val oldList: List<RecipientId>,
    private val newList: List<RecipientId>
  ) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
      return oldList[oldItemPosition] == newList[newItemPosition]
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
      return oldList[oldItemPosition] == newList[newItemPosition]
    }
  }
}
