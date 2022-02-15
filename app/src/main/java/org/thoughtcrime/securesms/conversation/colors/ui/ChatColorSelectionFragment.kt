package org.thoughtcrime.securesms.conversation.colors.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class ChatColorSelectionFragment : Fragment(R.layout.chat_color_selection_fragment) {

  private lateinit var viewModel: ChatColorSelectionViewModel

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val args: ChatColorSelectionFragmentArgs = ChatColorSelectionFragmentArgs.fromBundle(requireArguments())

    viewModel = ChatColorSelectionViewModel.getOrCreate(requireActivity(), args.recipientId)

    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    val preview: ChatColorPreviewView = view.findViewById(R.id.preview)
    val recycler: RecyclerView = view.findViewById(R.id.recycler)
    val adapter = ChatColorSelectionAdapter(
      requireContext(),
      Callbacks(args, view)
    )

    recycler.itemAnimator = null
    recycler.adapter = adapter

    toolbar.setNavigationOnClickListener {
      Navigation.findNavController(it).popBackStack()
    }

    viewModel.state.observe(viewLifecycleOwner) { state ->
      preview.setWallpaper(state.wallpaper)

      if (state.chatColors != null) {
        preview.setChatColors(state.chatColors)
      }

      adapter.submitList(state.chatColorModels)
    }

    viewModel.events.observe(viewLifecycleOwner) { event ->
      if (event is ChatColorSelectionViewModel.Event.ConfirmDeletion) {
        if (event.usageCount > 0) {
          showWarningDialogForMultipleUses(event)
        } else {
          showWarningDialogForNoUses(event)
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  private fun showWarningDialogForNoUses(confirmDeletion: ChatColorSelectionViewModel.Event.ConfirmDeletion) {
    MaterialAlertDialogBuilder(requireContext())
      .setMessage(R.string.ChatColorSelectionFragment__delete_chat_color)
      .setPositiveButton(R.string.ChatColorSelectionFragment__delete) { dialog, _ ->
        viewModel.deleteNow(confirmDeletion.chatColors)
        dialog.dismiss()
      }
      .setNegativeButton(android.R.string.cancel) { dialog, _ ->
        dialog.dismiss()
      }
      .show()
  }

  private fun showWarningDialogForMultipleUses(confirmDeletion: ChatColorSelectionViewModel.Event.ConfirmDeletion) {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.ChatColorSelectionFragment__delete_color)
      .setMessage(resources.getQuantityString(R.plurals.ChatColorSelectionFragment__this_custom_color_is_used, confirmDeletion.usageCount, confirmDeletion.usageCount))
      .setPositiveButton(R.string.delete) { dialog, _ ->
        viewModel.deleteNow(confirmDeletion.chatColors)
        dialog.dismiss()
      }
      .setNegativeButton(android.R.string.cancel) { dialog, _ ->
        dialog.dismiss()
      }
      .show()
  }

  inner class Callbacks(
    private val args: ChatColorSelectionFragmentArgs,
    private val view: View
  ) : ChatColorSelectionAdapter.Callbacks {
    override fun onSelect(chatColors: ChatColors) {
      viewModel.save(chatColors)
    }

    override fun onEdit(chatColors: ChatColors) {
      val startPage = if (chatColors.getColors().size == 1) 0 else 1

      val directions = ChatColorSelectionFragmentDirections
        .actionChatColorSelectionFragmentToCustomChatColorCreatorFragment(args.recipientId, startPage)
        .setChatColorId(chatColors.id.longValue)

      Navigation.findNavController(view).safeNavigate(directions)
    }

    override fun onDuplicate(chatColors: ChatColors) {
      viewModel.duplicate(chatColors)
    }

    override fun onDelete(chatColors: ChatColors) {
      viewModel.startDeletion(chatColors)
    }

    override fun onAdd() {
      val directions = ChatColorSelectionFragmentDirections.actionChatColorSelectionFragmentToCustomChatColorCreatorFragment(args.recipientId, 0)
      Navigation.findNavController(view).safeNavigate(directions)
    }
  }
}
