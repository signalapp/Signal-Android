package org.thoughtcrime.securesms.keyboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.InputAwareConstraintLayout
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardPageFragment
import org.thoughtcrime.securesms.keyboard.gif.GifKeyboardPageFragment
import org.thoughtcrime.securesms.keyboard.sticker.StickerKeyboardPageFragment
import org.thoughtcrime.securesms.util.ThemeUtil
import org.thoughtcrime.securesms.util.ThemedFragment.themeResId
import org.thoughtcrime.securesms.util.ThemedFragment.themedInflate
import org.thoughtcrime.securesms.util.ThemedFragment.withTheme
import org.thoughtcrime.securesms.util.WindowUtil
import org.thoughtcrime.securesms.util.fragments.findListener
import org.thoughtcrime.securesms.util.visible
import kotlin.reflect.KClass

class KeyboardPagerFragment : Fragment(), InputAwareConstraintLayout.InputFragment {

  private lateinit var emojiButton: View
  private lateinit var stickerButton: View
  private lateinit var gifButton: View
  private lateinit var viewModel: KeyboardPagerViewModel

  private val fragments: MutableMap<KClass<*>, Fragment> = mutableMapOf()
  private var currentFragment: Fragment? = null

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return themedInflate(R.layout.keyboard_pager_fragment, inflater, container)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    emojiButton = view.findViewById(R.id.keyboard_pager_fragment_emoji)
    stickerButton = view.findViewById(R.id.keyboard_pager_fragment_sticker)
    gifButton = view.findViewById(R.id.keyboard_pager_fragment_gif)

    viewModel = ViewModelProvider(requireActivity())[KeyboardPagerViewModel::class.java]

    viewModel.page().observe(viewLifecycleOwner, this::onPageSelected)
    viewModel.pages().observe(viewLifecycleOwner) { pages ->
      emojiButton.visible = pages.contains(KeyboardPage.EMOJI) && pages.size > 1
      stickerButton.visible = pages.contains(KeyboardPage.STICKER) && pages.size > 1
      gifButton.visible = pages.contains(KeyboardPage.GIF) && pages.size > 1
    }

    emojiButton.setOnClickListener { viewModel.switchToPage(KeyboardPage.EMOJI) }
    stickerButton.setOnClickListener { viewModel.switchToPage(KeyboardPage.STICKER) }
    gifButton.setOnClickListener { viewModel.switchToPage(KeyboardPage.GIF) }

    onHiddenChanged(false)
  }

  override fun onHiddenChanged(hidden: Boolean) {
    getWindow()?.let { window ->
      if (hidden) {
        WindowUtil.setNavigationBarColor(requireContext(), window, ThemeUtil.getThemedColor(requireContext(), android.R.attr.navigationBarColor))
      } else {
        WindowUtil.setNavigationBarColor(requireContext(), window, ThemeUtil.getThemedColor(requireContext(), R.attr.mediaKeyboardBottomBarBackgroundColor))
      }
    }
  }

  @Suppress("DEPRECATION")
  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    viewModel.page().value?.let(this::onPageSelected)
  }

  private fun getWindow(): Window? {
    var parent: Fragment? = parentFragment
    while (parent != null) {
      if (parent is DialogFragment) {
        return parent.dialog?.window
      }

      parent = parent.parentFragment
    }

    return activity?.window
  }

  private fun onPageSelected(page: KeyboardPage) {
    emojiButton.isSelected = page == KeyboardPage.EMOJI
    stickerButton.isSelected = page == KeyboardPage.STICKER
    gifButton.isSelected = page == KeyboardPage.GIF

    when (page) {
      KeyboardPage.EMOJI -> displayEmojiPage()
      KeyboardPage.GIF -> displayGifPage()
      KeyboardPage.STICKER -> displayStickerPage()
    }

    findListener<MediaKeyboard.MediaKeyboardListener>()?.onKeyboardChanged(page)
  }

  private fun displayEmojiPage() = displayPage(::EmojiKeyboardPageFragment)

  private fun displayGifPage() = displayPage(::GifKeyboardPageFragment)

  private fun displayStickerPage() = displayPage(::StickerKeyboardPageFragment)

  private inline fun <reified F : Fragment> displayPage(fragmentFactory: () -> F) {
    if (currentFragment is F) {
      (currentFragment as? KeyboardPageSelected)?.onPageSelected()
      return
    }

    val transaction = childFragmentManager.beginTransaction()

    currentFragment?.let { transaction.hide(it) }

    var fragment = fragments[F::class]
    if (fragment == null) {
      fragment = fragmentFactory().withTheme(themeResId)
      transaction.add(R.id.fragment_container, fragment)
      fragments[F::class] = fragment
    } else {
      (fragment as? KeyboardPageSelected)?.onPageSelected()
      transaction.show(fragment)
    }

    currentFragment = fragment
    transaction.commitAllowingStateLoss()
  }

  override fun show() {
    findListener<MediaKeyboard.MediaKeyboardListener>()?.onShown()
    if (isAdded && view != null) {
      onHiddenChanged(false)

      viewModel.page().value?.let(this::onPageSelected)
    }
  }

  override fun hide() {
    findListener<MediaKeyboard.MediaKeyboardListener>()?.onHidden()
    if (isAdded && view != null) {
      onHiddenChanged(true)

      val transaction = childFragmentManager.beginTransaction()
      fragments.values.forEach { transaction.remove(it) }
      transaction.commitAllowingStateLoss()
      currentFragment = null
      fragments.clear()
    }
  }
}
