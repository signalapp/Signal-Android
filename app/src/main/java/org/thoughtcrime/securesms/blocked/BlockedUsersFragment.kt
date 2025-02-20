/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.blocked

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.BlockUnblockDialog
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.recipients.Recipient

class BlockedUsersFragment : Fragment() {
  private var listener: Listener? = null
  private val viewModel: BlockedUsersViewModel by activityViewModels()

  override fun onAttach(context: Context) {
    super.onAttach(context)
    listener =
      if(context is Listener) context
      else throw ClassCastException("Expected context to implement Listener")
  }

  override fun onDetach() {
    super.onDetach()
    listener = null
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.blocked_users_fragment, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val empty = view.findViewById<View>(R.id.no_blocked_users)
    val adapter = BlockedUsersAdapter { recipient: Recipient -> handleRecipientClicked(recipient) }

    view.findViewById<View>(R.id.add_blocked_user_touch_target).apply{
      setOnClickListener { _ ->
        listener?.handleAddUserToBlockedList()
      }
    }

    view.findViewById<RecyclerView>(R.id.blocked_users_recycler).apply{
      this.adapter = adapter
    }

    lifecycleScope.launch{
      viewModel.recipients.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collectLatest { list: List<Recipient?> ->
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        adapter.submitList(list)
      }
    }
  }

  private fun handleRecipientClicked(recipient: Recipient) {
    BlockUnblockDialog.showUnblockFor(requireContext(), viewLifecycleOwner.lifecycle, recipient) {
      viewModel.unblock(recipient.id)
    }
  }

  fun interface Listener {
    fun handleAddUserToBlockedList()
  }

}