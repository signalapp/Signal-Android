/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.paged

import android.view.View
import org.signal.core.util.logging.Log

/**
 * Hooks for observing and intercepting contact selection changes driven by a
 * [ContactSearchViewModel]. Pass an implementation to [ContactSearchView.bind] or
 * [ContactSearch] to intercept selection events (e.g. apply selection limits or show
 * confirmation dialogs) and to react to list commits.
 */
interface ContactSearchCallbacks {

  /**
   * Called before [contactSearchKeys] are added to the selection. Return the keys that should
   * actually be selected — return an empty set to cancel the entire selection, or a filtered
   * subset to allow only some keys through.
   */
  fun onBeforeContactsSelected(view: View?, contactSearchKeys: Set<ContactSearchKey>): Set<ContactSearchKey>

  /** Called after [contactSearchKey] has been removed from the selection. */
  fun onContactDeselected(view: View?, contactSearchKey: ContactSearchKey)

  /** Called after each [androidx.recyclerview.widget.RecyclerView.Adapter.submitList] completes, with the committed list [size]. */
  fun onAdapterListCommitted(size: Int)

  /** No-op implementation — override only the methods you need. */
  open class Simple : ContactSearchCallbacks {
    override fun onBeforeContactsSelected(view: View?, contactSearchKeys: Set<ContactSearchKey>): Set<ContactSearchKey> {
      Log.d(TAG, "onBeforeContactsSelected() Selecting: ${contactSearchKeys.map { it.toString() }}")
      return contactSearchKeys
    }

    override fun onContactDeselected(view: View?, contactSearchKey: ContactSearchKey) {
      Log.i(TAG, "onContactDeselected() Deselected: $contactSearchKey")
    }

    override fun onAdapterListCommitted(size: Int) = Unit

    companion object {
      private val TAG = Log.tag(Simple::class.java)
    }
  }
}
