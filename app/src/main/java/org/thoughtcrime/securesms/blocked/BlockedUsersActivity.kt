/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.blocked

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.ContactSelectionListFragment
import org.thoughtcrime.securesms.ContactSelectionListFragment.OnContactSelectedListener
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.blocked.BlockedUsersViewModel.EventType
import org.thoughtcrime.securesms.components.ContactFilterView
import org.thoughtcrime.securesms.contacts.ContactSelectionDisplayMode
import org.thoughtcrime.securesms.contacts.paged.ChatType
import org.thoughtcrime.securesms.recipients.Recipient.Companion.resolved
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.ViewModelFactory
import org.thoughtcrime.securesms.util.ViewUtil
import java.util.Optional
import java.util.function.Consumer
import kotlin.jvm.optionals.getOrNull

class BlockedUsersActivity : PassphraseRequiredActivity(), BlockedUsersFragment.Listener, OnContactSelectedListener {
  companion object {
    private const val CONTACT_SELECTION_FRAGMENT: String = "Contact.Selection.Fragment"
  }

  private val dynamicTheme: DynamicTheme = DynamicNoActionBarTheme()
  private val lifecycleDisposable = LifecycleDisposable()
  private val viewModel : BlockedUsersViewModel by viewModels(
    factoryProducer = ViewModelFactory.factoryProducer {
      BlockedUsersViewModel(BlockedUsersRepository(this))
    }
  )

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)
    lifecycleDisposable.bindTo(this)
    dynamicTheme.onCreate(this)
    setContentView(R.layout.blocked_users_activity)

    findViewById<Toolbar>(R.id.toolbar).apply{
      setNavigationOnClickListener { _ ->
        onBackPressedDispatcher.onBackPressed()
      }
    }

    val contactFilterView = findViewById<ContactFilterView>(R.id.contact_filter_edit_text).apply{
      setOnFilterChangedListener { query: String? ->
        supportFragmentManager.findFragmentByTag(CONTACT_SELECTION_FRAGMENT)?.let{ fragment ->
          (fragment as ContactSelectionListFragment).setQueryFilter(query)
        }
      }
      setHint(R.string.BlockedUsersActivity__add_blocked_user)
    }

    supportFragmentManager.addOnBackStackChangedListener {
      if (supportFragmentManager.backStackEntryCount == 1) {
        contactFilterView.visibility = View.VISIBLE
        contactFilterView.focusAndShowKeyboard()
      } else {
        contactFilterView.visibility = View.GONE
        ViewUtil.hideKeyboard(this, contactFilterView)
      }
    }

    supportFragmentManager.beginTransaction()
      .add(R.id.fragment_container, BlockedUsersFragment())
      .commit()

    val container = findViewById<View>(R.id.fragment_container)
    lifecycleDisposable.add(
      viewModel.getEvents().subscribe {
        event: BlockedUsersViewModel.Event -> handleEvent(container, event)
      }
    )
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }

  override fun onBeforeContactSelected(isFromUnknownSearchKey: Boolean,
                                       recipientId: Optional<RecipientId>,
                                       number: String?,
                                       chatType: Optional<ChatType>,
                                       callback: Consumer<Boolean>) {
    val nullableRecipientId = recipientId.getOrNull()
    val displayName = nullableRecipientId?.let { resolved(it).getDisplayName(this) } ?: number

    val confirmationDialog = MaterialAlertDialogBuilder(this)
      .setTitle(R.string.BlockedUsersActivity__block_user)
      .setMessage(getString(R.string.BlockedUserActivity__s_will_not_be_able_to, displayName))
      .setPositiveButton(R.string.BlockedUsersActivity__block) { dialog, _ ->
        nullableRecipientId?.let { viewModel.block(it) } ?: viewModel.createAndBlock(number!!)
        dialog.dismiss()
        onBackPressedDispatcher.onBackPressed()
      }
      .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
      .setCancelable(true)
      .create()
      .apply{
        setOnShowListener { _ ->
          getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED)
        }
      }

    confirmationDialog.show()
    callback.accept(false)
  }


  override fun onContactDeselected(recipientId: Optional<RecipientId?>, number: String?, chatType: Optional<ChatType?>) {
    //Do Nothing
  }

  override fun onSelectionChanged() {
    //Do Nothing
  }

  override fun handleAddUserToBlockedList() {
    intent.apply {
      putExtra(ContactSelectionListFragment.REFRESHABLE, false)
      putExtra(ContactSelectionListFragment.SELECTION_LIMITS, 1)
      putExtra(ContactSelectionListFragment.HIDE_COUNT, true)
      putExtra(
        ContactSelectionListFragment.DISPLAY_MODE,
        ContactSelectionDisplayMode.FLAG_PUSH or
          ContactSelectionDisplayMode.FLAG_SMS or
          ContactSelectionDisplayMode.FLAG_ACTIVE_GROUPS or
          ContactSelectionDisplayMode.FLAG_INACTIVE_GROUPS or
          ContactSelectionDisplayMode.FLAG_BLOCK
      )
    }

    supportFragmentManager.beginTransaction()
      .replace(R.id.fragment_container, ContactSelectionListFragment(), CONTACT_SELECTION_FRAGMENT)
      .addToBackStack(null)
      .commit()
  }

  private fun handleEvent(view: View, event: BlockedUsersViewModel.Event) {
    val displayName = event.recipient?.getDisplayName(this) ?: event.number

    @StringRes val messageResId = when (event.eventType) {
      EventType.BLOCK_SUCCEEDED -> R.string.BlockedUsersActivity__s_has_been_blocked
      EventType.BLOCK_FAILED -> R.string.BlockedUsersActivity__failed_to_block_s
      EventType.UNBLOCK_SUCCEEDED -> R.string.BlockedUsersActivity__s_has_been_unblocked
    }

    Snackbar.make(view, getString(messageResId, displayName), Snackbar.LENGTH_SHORT).show()
  }
}