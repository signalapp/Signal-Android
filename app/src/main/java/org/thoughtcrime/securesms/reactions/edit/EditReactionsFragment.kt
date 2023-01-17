package org.thoughtcrime.securesms.reactions.edit

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModelProvider
import androidx.transition.ChangeBounds
import androidx.transition.Transition
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.animation.transitions.AlphaTransition
import org.thoughtcrime.securesms.components.emoji.EmojiImageView
import org.thoughtcrime.securesms.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.ViewUtil

private val SELECTED_SIZE = ViewUtil.dpToPx(36)
private val UNSELECTED_SIZE = ViewUtil.dpToPx(26)

/**
 * Edit default reactions that show when long pressing.
 */
class EditReactionsFragment : LoggingFragment(R.layout.edit_reactions_fragment), ReactWithAnyEmojiBottomSheetDialogFragment.Callback {

  private lateinit var toolbar: Toolbar
  private lateinit var reactionViews: List<EmojiImageView>
  private lateinit var scrubber: ConstraintLayout
  private lateinit var mask: View

  private lateinit var defaultSet: ConstraintSet

  private lateinit var viewModel: EditReactionsViewModel

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    toolbar = view.findViewById(R.id.toolbar)
    toolbar.setTitle(R.string.EditReactionsFragment__customize_reactions)
    toolbar.setNavigationOnClickListener {
      requireActivity().onBackPressed()
    }
    configureToolbar()

    reactionViews = listOf(
      view.findViewById(R.id.reaction_1),
      view.findViewById(R.id.reaction_2),
      view.findViewById(R.id.reaction_3),
      view.findViewById(R.id.reaction_4),
      view.findViewById(R.id.reaction_5),
      view.findViewById(R.id.reaction_6)
    )
    reactionViews.forEach { it.setOnClickListener(this::onEmojiClick) }

    scrubber = view.findViewById(R.id.edit_reactions_fragment_scrubber)
    defaultSet = ConstraintSet().apply { clone(scrubber) }

    mask = view.findViewById(R.id.edit_reactions_fragment_reaction_mask)

    view.findViewById<View>(R.id.edit_reactions_reset_emoji).setOnClickListener { viewModel.resetToDefaults() }
    view.findViewById<View>(R.id.edit_reactions_fragment_save).setOnClickListener {
      viewModel.save()
      requireActivity().onBackPressed()
    }

    viewModel = ViewModelProvider(this).get(EditReactionsViewModel::class.java)

    viewModel.reactions.observe(viewLifecycleOwner) { emojis ->
      emojis.forEachIndexed { index, emoji -> reactionViews[index].setImageEmoji(emoji) }
    }

    viewModel.selection.observe(viewLifecycleOwner) { selection ->
      if (selection == EditReactionsViewModel.NO_SELECTION) {
        deselectAll()
        ObjectAnimator.ofFloat(mask, "alpha", 0f).start()
      } else {
        ObjectAnimator.ofFloat(mask, "alpha", 1f).start()
        select(reactionViews[selection])
        ReactWithAnyEmojiBottomSheetDialogFragment.createForEditReactions().show(childFragmentManager, REACT_SHEET_TAG)
      }
    }

    view.setOnClickListener { viewModel.setSelection(EditReactionsViewModel.NO_SELECTION) }
  }

  private fun configureToolbar() {
    @Suppress("DEPRECATION")
    ViewCompat.setOnApplyWindowInsetsListener(toolbar) { _, insets ->
      updateToolbarTopMargin(insets.systemWindowInsetTop)
      insets
    }
  }

  private fun updateToolbarTopMargin(topMargin: Int) {
    val layoutParams = toolbar.layoutParams as ConstraintLayout.LayoutParams
    layoutParams.topMargin = topMargin
    toolbar.layoutParams = layoutParams
  }

  private fun select(emojiImageView: EmojiImageView) {
    val set = ConstraintSet()
    set.clone(scrubber)
    reactionViews.forEach { view ->
      view.clearAnimation()
      view.rotation = 0f
      if (view.id == emojiImageView.id) {
        set.constrainWidth(view.id, SELECTED_SIZE)
        set.constrainHeight(view.id, SELECTED_SIZE)
        set.setAlpha(view.id, 1f)
      } else {
        set.constrainWidth(view.id, UNSELECTED_SIZE)
        set.constrainHeight(view.id, UNSELECTED_SIZE)
        set.setAlpha(view.id, 0.3f)
      }
    }

    TransitionManager.beginDelayedTransition(scrubber, createSelectTransitionSet(emojiImageView))
    set.applyTo(scrubber)
  }

  private fun deselectAll() {
    reactionViews.forEach { it.clearAnimation() }

    TransitionManager.beginDelayedTransition(scrubber, createTransitionSet())
    defaultSet.applyTo(scrubber)
  }

  private fun onEmojiClick(view: View) {
    viewModel.setSelection(reactionViews.indexOf(view))
  }

  override fun onReactWithAnyEmojiDialogDismissed() {
    viewModel.setSelection(EditReactionsViewModel.NO_SELECTION)
  }

  override fun onReactWithAnyEmojiSelected(emoji: String) {
    viewModel.onEmojiSelected(emoji)
  }

  companion object {

    private const val REACT_SHEET_TAG = "REACT_SHEET_TAG"

    private fun createTransitionSet(): Transition {
      return TransitionSet().apply {
        ordering = TransitionSet.ORDERING_TOGETHER
        duration = 250
        addTransition(AlphaTransition())
        addTransition(ChangeBounds())
      }
    }

    private fun createSelectTransitionSet(target: View): Transition {
      return createTransitionSet().addListener(object : Transition.TransitionListener {
        override fun onTransitionEnd(transition: Transition) {
          startRockingAnimation(target)
        }

        override fun onTransitionStart(transition: Transition) = Unit
        override fun onTransitionCancel(transition: Transition) = Unit
        override fun onTransitionPause(transition: Transition) = Unit
        override fun onTransitionResume(transition: Transition) = Unit
      })
    }

    private fun startRockingAnimation(target: View) {
      val startRocking: Animation = AnimationUtils.loadAnimation(target.context, R.anim.rock_start)
      startRocking.setAnimationListener(object : Animation.AnimationListener {
        override fun onAnimationEnd(animation: Animation?) {
          val continualRocking: Animation = AnimationUtils.loadAnimation(target.context, R.anim.rock)
          continualRocking.repeatCount = Animation.INFINITE
          continualRocking.repeatMode = Animation.REVERSE
          target.startAnimation(continualRocking)
        }

        override fun onAnimationStart(animation: Animation?) = Unit
        override fun onAnimationRepeat(animation: Animation?) = Unit
      })

      target.clearAnimation()
      target.startAnimation(startRocking)
    }
  }
}
