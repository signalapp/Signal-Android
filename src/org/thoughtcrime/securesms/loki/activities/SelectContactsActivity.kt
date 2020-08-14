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

const val EXTRA_RESULT_SELECTED_CONTACTS = "RESULT_SELECTED_CONTACTS"
//const val RESULT_CREATE_PRIVATE_CHAT = 100;

class SelectContactsActivity : PassphraseRequiredActionBarActivity(), LoaderManager.LoaderCallbacks<List<String>> {
    private var members = listOf<String>()
        set(value) { field = value; selectContactsAdapter.members = value }

    private lateinit var selectContactsAdapter: SelectContactsAdapter

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        setContentView(R.layout.activity_select_contacts)
        supportActionBar!!.title = resources.getString(R.string.activity_select_contacts_title)

        this.selectContactsAdapter = SelectContactsAdapter(this, GlideApp.with(this))
        recyclerView.adapter = selectContactsAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

//        btnCreateNewPrivateChat.setOnClickListener { closeAndCreatePrivateChat() }

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
        when(item.itemId) {
            R.id.doneButton -> closeAndReturnSelected()
        }
        return super.onOptionsItemSelected(item)
    }

//    private fun closeAndCreatePrivateChat() {
//        setResult(RESULT_CREATE_PRIVATE_CHAT)
//        finish()
//    }

    private fun closeAndReturnSelected() {
        val selectedMembers = this.selectContactsAdapter.selectedMembers
        val selectedContacts = selectedMembers.toTypedArray()
        val intent = Intent()
        intent.putExtra(EXTRA_RESULT_SELECTED_CONTACTS, selectedContacts)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
    // endregion
}