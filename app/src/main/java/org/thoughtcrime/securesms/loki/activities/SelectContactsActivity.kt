package org.thoughtcrime.securesms.loki.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import kotlinx.android.synthetic.main.activity_create_closed_group.emptyStateContainer
import kotlinx.android.synthetic.main.activity_create_closed_group.mainContentContainer
import kotlinx.android.synthetic.main.activity_select_contacts.*
import kotlinx.android.synthetic.main.activity_select_contacts.recyclerView
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.mms.GlideApp

//TODO Refactor to avoid using kotlinx.android.synthetic
class SelectContactsActivity : PassphraseRequiredActionBarActivity(), LoaderManager.LoaderCallbacks<List<String>> {
    private var members = listOf<String>()
        set(value) { field = value; selectContactsAdapter.members = value }
    private lateinit var usersToExclude: Set<String>

    private val selectContactsAdapter by lazy {
        SelectContactsAdapter(this, GlideApp.with(this))
    }

    companion object {
        val usersToExcludeKey = "usersToExcludeKey"
        val emptyStateTextKey = "emptyStateTextKey"
        val selectedContactsKey = "selectedContactsKey"
    }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)

        setContentView(R.layout.activity_select_contacts)
        supportActionBar!!.title = resources.getString(R.string.activity_select_contacts_title)

        usersToExclude = intent.getStringArrayExtra(Companion.usersToExcludeKey)?.toSet() ?: setOf()
        val emptyStateText = intent.getStringExtra(Companion.emptyStateTextKey)
        if (emptyStateText != null) {
            emptyStateMessageTextView.text = emptyStateText
        }

        recyclerView.adapter = selectContactsAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        LoaderManager.getInstance(this).initLoader(0, null, this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_done, menu)
        return members.isNotEmpty()
    }
    // endregion

    // region Updating
    override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<List<String>> {
        return SelectContactsLoader(this, usersToExclude)
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

    private fun closeAndReturnSelected() {
        val selectedMembers = selectContactsAdapter.selectedMembers
        val selectedContacts = selectedMembers.toTypedArray()
        val intent = Intent()
        intent.putExtra(selectedContactsKey, selectedContacts)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
    // endregion
}