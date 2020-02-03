package org.thoughtcrime.securesms.loki.redesign.activities

import android.os.Bundle
import android.support.v4.app.LoaderManager
import android.support.v4.content.Loader
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_linked_devices.*
import kotlinx.android.synthetic.main.view_user.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.mms.GlideApp

class CreateClosedGroupActivity : PassphraseRequiredActionBarActivity(), MemberClickListener, LoaderManager.LoaderCallbacks<List<String>> {
    private var members = listOf<String>()
        set(value) { field = value; createClosedGroupAdapter.members = value }

    private val createClosedGroupAdapter by lazy {
        val result = CreateClosedGroupAdapter(this)
        result.glide = GlideApp.with(this)
        result.memberClickListener = this
        result
    }

    private val selectedMembers: Set<String>
        get() { return createClosedGroupAdapter.selectedMembers }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        setContentView(R.layout.activity_create_closed_group)
        supportActionBar!!.title = "New Closed Group"
        recyclerView.adapter = createClosedGroupAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        LoaderManager.getInstance(this).initLoader(0, null, this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_create_closed_group, menu)
        return true
    }
    // endregion

    // region Updating
    override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<List<String>> {
        return CreateClosedGroupLoader(this)
    }

    override fun onLoadFinished(loader: Loader<List<String>>, members: List<String>) {
        update(members)
    }

    override fun onLoaderReset(loader: Loader<List<String>>) {
        update(listOf())
    }

    private fun update(members: List<String>) {
        this.members = members
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when(id) {
            R.id.createClosedGroupButton -> createClosedGroup()
            else -> { /* Do nothing */ }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onMemberClick(member: String) {
        createClosedGroupAdapter.onMemberClick(member)
    }

    private fun createClosedGroup() {
        val name = nameTextView.text.trim()
        if (name.isEmpty()) {
            return Toast.makeText(this, "Please enter a group name", Toast.LENGTH_LONG).show()
        }
        if (name.length >= 64) {
            return Toast.makeText(this, "Please enter a shorter group name", Toast.LENGTH_LONG).show()
        }
        val selectedMembers = this.selectedMembers
        if (selectedMembers.count() < 2) {
            return Toast.makeText(this, "Please pick at least 2 group members", Toast.LENGTH_LONG).show()
        }
        // TODO: Create group
    }
    // endregion
}