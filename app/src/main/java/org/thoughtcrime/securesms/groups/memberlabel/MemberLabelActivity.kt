/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.memberlabel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.signal.core.util.getParcelableExtraCompat
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.groups.GroupId

/**
 * Hosts [MemberLabelFragment], allowing navigation to the member label editor from any context.
 */
class MemberLabelActivity : PassphraseRequiredActivity() {
  companion object {
    private const val EXTRA_GROUP_ID = "group_id"

    fun createIntent(context: Context, groupId: GroupId.V2): Intent {
      return Intent(context, MemberLabelActivity::class.java).apply {
        putExtra(EXTRA_GROUP_ID, groupId)
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    if (savedInstanceState == null) {
      val groupId = intent.getParcelableExtraCompat(EXTRA_GROUP_ID, GroupId.V2::class.java)!!
      val fragment = MemberLabelFragment.newInstance(groupId)
      supportFragmentManager.beginTransaction()
        .replace(android.R.id.content, fragment)
        .commit()
    }
  }
}
