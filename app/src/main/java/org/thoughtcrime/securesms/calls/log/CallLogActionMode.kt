package org.thoughtcrime.securesms.calls.log

import android.content.res.Resources
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import org.thoughtcrime.securesms.R

class CallLogActionMode(
  private val callback: Callback
) : ActionMode.Callback {

  private var actionMode: ActionMode? = null
  private var count: Int = 0

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
    callback.onResetSelectionState()
    endIfActive()
  }

  fun isInActionMode(): Boolean {
    return actionMode != null
  }

  fun getCount(): Int {
    return if (actionMode != null) count else 0
  }

  fun setCount(count: Int) {
    this.count = count
    actionMode?.title = getTitle(count)
  }

  fun start() {
    actionMode = callback.startActionMode(this)
  }

  fun end() {
    callback.onActionModeWillEnd()
    actionMode?.finish()
    count = 0
    actionMode = null
  }

  private fun getTitle(callLogsSelected: Int): String {
    return callback.getResources().getQuantityString(R.plurals.ConversationListFragment_s_selected, callLogsSelected, callLogsSelected)
  }

  private fun endIfActive() {
    if (actionMode != null) {
      end()
    }
  }

  interface Callback {
    fun startActionMode(callback: ActionMode.Callback): ActionMode?
    fun onActionModeWillEnd()
    fun getResources(): Resources
    fun onResetSelectionState()
  }
}
