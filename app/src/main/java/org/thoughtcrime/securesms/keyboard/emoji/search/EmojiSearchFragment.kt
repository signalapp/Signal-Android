package org.thoughtcrime.securesms.keyboard.emoji.search

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout
import org.thoughtcrime.securesms.components.emoji.EmojiEventListener
import org.thoughtcrime.securesms.components.emoji.EmojiPageView
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter
import org.thoughtcrime.securesms.keyboard.emoji.KeyboardPageSearchView
import org.thoughtcrime.securesms.util.ThemedFragment.themedInflate
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.fragments.requireListener

class EmojiSearchFragment : Fragment(), EmojiPageViewGridAdapter.VariationSelectorListener {

  private lateinit var viewModel: EmojiSearchViewModel
  private lateinit var callback: Callback

  override fun onAttach(context: Context) {
    super.onAttach(context)

    callback = requireListener()
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return themedInflate(R.layout.emoji_search_fragment, inflater, container)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val repository = EmojiSearchRepository(requireContext())
    val factory = EmojiSearchViewModel.Factory(repository)

    viewModel = ViewModelProvider(this, factory)[EmojiSearchViewModel::class.java]

    val keyboardAwareLinearLayout: KeyboardAwareLinearLayout = view.findViewById(R.id.kb_aware_layout)
    val eventListener: EmojiEventListener = requireListener()
    val searchBar: KeyboardPageSearchView = view.findViewById(R.id.emoji_search_view)
    val resultsContainer: FrameLayout = view.findViewById(R.id.emoji_search_results_container)
    val noResults: TextView = view.findViewById(R.id.emoji_search_empty)
    val emojiPageView = EmojiPageView(
      requireContext(),
      eventListener,
      this,
      true,
      LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false),
      R.layout.emoji_display_item_list,
      R.layout.emoji_text_display_item_list
    )

    resultsContainer.addView(emojiPageView)

    searchBar.presentForEmojiSearch()
    searchBar.callbacks = SearchCallbacks()

    viewModel.emojiList.observe(viewLifecycleOwner) { results ->
      emojiPageView.setList(results.emojiList, null)

      if (results.emojiList.isNotEmpty() || results.isRecents) {
        emojiPageView.visibility = View.VISIBLE
        noResults.visibility = View.GONE
      } else {
        emojiPageView.visibility = View.INVISIBLE
        noResults.visibility = View.VISIBLE
      }
    }

    view.post {
      keyboardAwareLinearLayout.addOnKeyboardHiddenListener {
        callback.closeEmojiSearch()
      }
    }
  }

  private inner class SearchCallbacks : KeyboardPageSearchView.Callbacks {
    override fun onNavigationClicked() {
      ViewUtil.hideKeyboard(requireContext(), requireView())
    }

    override fun onQueryChanged(query: String) {
      viewModel.onQueryChanged(query)
    }
  }

  interface Callback {
    fun closeEmojiSearch()
  }

  override fun onVariationSelectorStateChanged(open: Boolean) = Unit
}
