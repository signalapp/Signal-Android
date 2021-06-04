package org.thoughtcrime.securesms.conversation.v2

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import network.loki.messenger.R

class ConversationActionModeCallback : ActionMode.Callback {

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val inflater = mode.menuInflater
        inflater.inflate(R.menu.conversation_item_action_menu, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu): Boolean {
        return false
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {

    }
}