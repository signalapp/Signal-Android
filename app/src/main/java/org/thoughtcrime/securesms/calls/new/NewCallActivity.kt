package org.thoughtcrime.securesms.calls.new

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.app.ActivityCompat
import androidx.core.view.MenuProvider
import org.thoughtcrime.securesms.ContactSelectionActivity
import org.thoughtcrime.securesms.ContactSelectionListFragment
import org.thoughtcrime.securesms.InviteActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.ContactSelectionDisplayMode

class NewCallActivity : ContactSelectionActivity(), ContactSelectionListFragment.NewCallCallback {

  override fun onCreate(icicle: Bundle?, ready: Boolean) {
    super.onCreate(icicle, ready)
    requireNotNull(supportActionBar)
    supportActionBar?.setTitle(R.string.NewCallActivity__new_call)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    addMenuProvider(NewCallMenuProvider())
  }

  override fun onSelectionChanged() = Unit

  companion object {
    fun createIntent(context: Context): Intent {
      return Intent(context, NewCallActivity::class.java)
        .putExtra(
          ContactSelectionListFragment.DISPLAY_MODE,
          ContactSelectionDisplayMode.none()
            .withPush()
            .withActiveGroups()
            .withGroupMembers()
            .build()
        )
    }
  }

  override fun onInvite() {
    startActivity(Intent(this, InviteActivity::class.java))
  }

  private inner class NewCallMenuProvider : MenuProvider {
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
      menuInflater.inflate(R.menu.new_call_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
      when (menuItem.itemId) {
        android.R.id.home -> ActivityCompat.finishAfterTransition(this@NewCallActivity)
        R.id.menu_refresh -> onRefresh()
        R.id.menu_invite -> startActivity(Intent(this@NewCallActivity, InviteActivity::class.java))
      }

      return true
    }
  }
}
