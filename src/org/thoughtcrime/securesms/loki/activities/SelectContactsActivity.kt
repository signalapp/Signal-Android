package org.thoughtcrime.securesms.loki.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.LoaderManager
import android.support.v4.content.Loader
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import kotlinx.android.synthetic.main.activity_create_closed_group.*
import kotlinx.android.synthetic.main.activity_linked_devices.recyclerView
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.mms.GlideApp

const val EXTRA_SELECTED_CONTACTS = "SELECTED_CONTACTS_RESULT"

class SelectContactsActivity : PassphraseRequiredActionBarActivity(), LoaderManager.LoaderCallbacks<List<String>> {
    private var members = listOf<String>()
        set(value) { field = value; selectContactsAdapter.members = value }

    private val selectContactsAdapter by lazy {
        val glide = GlideApp.with(this)
        val result = SelectContactsAdapter(this, glide)
        result
    }

    private val selectedMembers: Set<String>
        get() { return selectContactsAdapter.selectedMembers }

    companion object {
        public val createNewPrivateChatResultCode = 100
    }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        setContentView(R.layout.activity_select_contacts)
        supportActionBar!!.title = resources.getString(R.string.activity_select_contacts_title)
        recyclerView.adapter = selectContactsAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        btnCreateNewPrivateChat.setOnClickListener { createNewPrivateChat() }
        LoaderManager.getInstance(this).initLoader(0, null, this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_done, menu)
        return members.isNotEmpty()
    }
    // endregion

    // region Updating
    override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<List<String>> {
        return SelectContactsLoader(this)
    }

    override fun onLoadFinished(loader: Loader<List<String>>, members: List<String>) {
        update(members)
    }

    override fun onLoaderReset(loader: Loader<List<String>>) {
        update(listOf())
    }

    private fun update(members: List<String>) {
        this.members = members
        mainContentContainer.visibility = if (members.isEmpty()) View.GONE else View.VISIBLE
        emptyStateContainer.visibility = if (members.isEmpty()) View.VISIBLE else View.GONE
        invalidateOptionsMenu()
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when(id) {
            R.id.doneButton -> returnContacts()
            else -> { /* Do nothing */ }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun createNewPrivateChat() {
        setResult(createNewPrivateChatResultCode)
        finish()
    }

    private fun returnContacts() {
        val selectedMembers = this.selectedMembers
        val selectedContacts = selectedMembers.toTypedArray()
        val data = Intent()
        data.putExtra(EXTRA_SELECTED_CONTACTS, selectedContacts)
        setResult(Activity.RESULT_OK, data)
        finish()
    }
}