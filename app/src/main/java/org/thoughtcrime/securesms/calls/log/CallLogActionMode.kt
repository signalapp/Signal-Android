package org.thoughtcrime.securesms.calls.log

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.fragments.requireListener

class CallLogActionMode(
  private val fragment: CallLogFragment,
  private val onResetSelectionState: () -> Unit
) : ActionMode.Callback {

  private var actionMode: ActionMode? = null

  override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
    mode?.title = getTitle(1)
    return true
  }

  override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
    return false
  }

  override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
    return true
  }

  override fun onDestroyActionMode(mode: ActionMode?) {
    onResetSelectionState()
    endIfActive()
  }

  fun setCount(count: Int) {
    actionMode?.title = getTitle(count)
  }

  fun start() {
    actionMode = (fragment.requireActivity() as AppCompatActivity).startSupportActionMode(this)
    fragment.requireListener<CallLogFragment.Callback>().onMultiSelectStarted()
  }

  fun end() {
    fragment.requireListener<CallLogFragment.Callback>().onMultiSelectFinished()
    actionMode?.finish()
    actionMode = null
  }

  private fun getTitle(callLogsSelected: Int): String {
    return fragment.requireContext().resources.getQuantityString(R.plurals.ConversationListFragment_s_selected, callLogsSelected, callLogsSelected)
  }

  private fun endIfActive() {
    if (actionMode != null) {
      end()
    }
  }
}
