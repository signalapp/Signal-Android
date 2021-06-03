package org.thoughtcrime.securesms.keyboard.emoji

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter
import org.thoughtcrime.securesms.keyboard.findListener
import org.thoughtcrime.securesms.keyvalue.SignalStore

private val DELETE_KEY_EVENT: KeyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)

class EmojiKeyboardPageFragment : Fragment(R.layout.keyboard_pager_emoji_page_fragment), EmojiKeyboardProvider.EmojiEventListener, EmojiPageViewGridAdapter.VariationSelectorListener {

  private lateinit var viewModel: EmojiKeyboardPageViewModel
  private lateinit var emojiPager: ViewPager2
  private lateinit var searchView: View
  private lateinit var emojiCategoriesRecycler: RecyclerView
  private lateinit var backspaceView: View
  private lateinit var eventListener: EmojiKeyboardProvider.EmojiEventListener
  private lateinit var callback: Callback
  private lateinit var pagesAdapter: EmojiKeyboardPageAdapter
  private lateinit var categoriesAdapter: EmojiKeyboardPageCategoriesAdapter
  private lateinit var searchBar: KeyboardPageSearchView

  override fun onAttach(context: Context) {
    super.onAttach(context)

    callback = context as Callback
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    emojiPager = view.findViewById(R.id.emoji_pager)
    searchView = view.findViewById(R.id.emoji_search)
    searchBar = view.findViewById(R.id.emoji_keyboard_search_text)
    emojiCategoriesRecycler = view.findViewById(R.id.emoji_categories_recycler)
    backspaceView = view.findViewById(R.id.emoji_backspace)
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)

    viewModel = ViewModelProviders.of(requireActivity()).get(EmojiKeyboardPageViewModel::class.java)

    pagesAdapter = EmojiKeyboardPageAdapter(this, this)

    categoriesAdapter = EmojiKeyboardPageCategoriesAdapter { key ->
      viewModel.onKeySelected(key)

      val page = pagesAdapter.currentList.indexOfFirst {
        (it as EmojiPageMappingModel).key == key
      }

      if (emojiPager.currentItem != page) {
        emojiPager.currentItem = page
      }
    }

    emojiPager.adapter = pagesAdapter
    emojiCategoriesRecycler.adapter = categoriesAdapter

    searchBar.callbacks = EmojiKeyboardPageSearchViewCallbacks()

    searchView.setOnClickListener {
      callback.openEmojiSearch()
    }

    backspaceView.setOnClickListener { eventListener.onKeyEvent(DELETE_KEY_EVENT) }

    viewModel.categories.observe(viewLifecycleOwner) { categories ->
      categoriesAdapter.submitList(categories)
    }

    viewModel.pages.observe(viewLifecycleOwner) { pages ->
      val registerPageCallback: Boolean = pagesAdapter.currentList.isEmpty() && pages.isNotEmpty()
      pagesAdapter.submitList(pages) { updatePagerPosition(registerPageCallback) }
    }

    viewModel.selectedKey.observe(viewLifecycleOwner) { updateCategoryTab() }

    eventListener = findListener() ?: throw AssertionError("No emoji listener found")
  }

  private fun updateCategoryTab() {
    emojiCategoriesRecycler.post {
      val index: Int = categoriesAdapter.currentList.indexOfFirst { (it as? EmojiKeyboardPageCategoryMappingModel)?.key == viewModel.selectedKey.value }

      if (index != -1) {
        emojiCategoriesRecycler.smoothScrollToPosition(index)
      }
    }
  }

  private fun updatePagerPosition(registerPageCallback: Boolean) {
    val page = pagesAdapter.currentList.indexOfFirst {
      (it as EmojiPageMappingModel).key == viewModel.selectedKey.value
    }

    if (emojiPager.currentItem != page && page != -1) {
      emojiPager.setCurrentItem(page, false)
    }

    if (registerPageCallback) {
      emojiPager.registerOnPageChangeCallback(PageChanged(pagesAdapter))
    }
  }

  override fun onEmojiSelected(emoji: String) {
    SignalStore.emojiValues().setPreferredVariation(emoji)
    eventListener.onEmojiSelected(emoji)
    viewModel.addToRecents(emoji)
  }

  override fun onKeyEvent(keyEvent: KeyEvent?) {
    eventListener.onKeyEvent(keyEvent)
  }

  override fun onVariationSelectorStateChanged(open: Boolean) {
    emojiPager.isUserInputEnabled = !open
  }

  private inner class PageChanged(private val adapter: EmojiKeyboardPageAdapter) : ViewPager2.OnPageChangeCallback() {
    override fun onPageSelected(position: Int) {
      val mappingModel: EmojiPageMappingModel = adapter.currentList[position] as EmojiPageMappingModel
      viewModel.onKeySelected(mappingModel.key)
    }
  }

  private inner class EmojiKeyboardPageSearchViewCallbacks : KeyboardPageSearchView.Callbacks {
    override fun onClicked() {
      callback.openEmojiSearch()
    }
  }

  interface Callback {
    fun openEmojiSearch()
  }
}
