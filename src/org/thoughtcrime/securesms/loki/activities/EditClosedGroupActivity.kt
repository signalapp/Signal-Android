package org.thoughtcrime.securesms.loki.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.LoaderManager
import android.support.v4.content.Loader
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_create_closed_group.emptyStateContainer
import kotlinx.android.synthetic.main.activity_create_closed_group.mainContentContainer
import kotlinx.android.synthetic.main.activity_edit_closed_group.*
import kotlinx.android.synthetic.main.activity_edit_closed_group.displayNameContainer
import kotlinx.android.synthetic.main.activity_edit_closed_group.displayNameTextView
import kotlinx.android.synthetic.main.activity_linked_devices.recyclerView
import kotlinx.android.synthetic.main.activity_settings.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.loki.dialogs.GroupEditingOptionsBottomSheet
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.crypto.ProfileCipher

class EditClosedGroupActivity : PassphraseRequiredActionBarActivity(), MemberClickListener, LoaderManager.LoaderCallbacks<List<String>> {
    private var members = listOf<String>()
        set(value) { field = value; editClosedGroupAdapter.members = value }
    private lateinit var groupID: String
    private var membersToRemove = setOf<String>()
    private var membersToAdd = setOf<String>()
    private var admins = setOf<String>()
    private val originalName by lazy { DatabaseFactory.getGroupDatabase(this).getGroup(groupID).get().title }
    private var nameHasChanged = false
    private lateinit var newGroupDisplayName: String


    private val editClosedGroupAdapter by lazy {
        val result = EditClosedGroupAdapter(this)
        result.glide = GlideApp.with(this)
        result.memberClickListener = this
        result
    }
    private var isEditingGroupName = false
        set(value) { field = value; handleIsEditingDisplayNameChanged() }

    companion object {
        @JvmField
        public val GROUP_ID = "GROUP_ID"
    }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        groupID = intent.getStringExtra(GROUP_ID)
        setContentView(R.layout.activity_edit_closed_group)
        supportActionBar!!.title = resources.getString(R.string.activity_edit_closed_group_title)
        displayNameContainer.setOnClickListener { showEditDisplayNameUI() }
        displayNameTextView.text = originalName
        cancelEditButton.setOnClickListener { cancelEditingDisplayName() }
        saveEditButton.setOnClickListener { saveDisplayName() }
        recyclerView.adapter = editClosedGroupAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        addMembersClosedGroupButton.setOnClickListener { onAddMember() }
        LoaderManager.getInstance(this).initLoader(0, null, this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_apply, menu)
        return members.isNotEmpty()
    }
    // endregion

    // region Updating
    override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<List<String>> {
        return EditClosedGroupLoader(groupID, this)
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
            R.id.applyButton -> modifyClosedGroup()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showEditDisplayNameUI() {
        isEditingGroupName = true
    }
    private fun cancelEditingDisplayName() {
        isEditingGroupName = false
    }

    override fun onMemberClick(member: String) {
        val bottomSheet = GroupEditingOptionsBottomSheet()
        bottomSheet.onRemoveTapped = {
            membersToRemove = membersToRemove + member
            members = members - member
            bottomSheet.dismiss()
        }
 //       bottomSheet.onAdminTapped = {
 //           bottomSheet.dismiss()
 //       }
        bottomSheet.show(supportFragmentManager, "closeBottomSheet")
    }

    private fun onAddMember() {
        val intent = Intent(this@EditClosedGroupActivity, SelectContactsActivity::class.java)
        startActivityForResult(intent, 124)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            if (data != null && data.hasExtra("Selected Contacts Result")) {
                val returnedContacts = data.extras.getStringArray("Selected Contacts Result")
                var selectedContacts = returnedContacts.toSet()
                membersToAdd = selectedContacts + membersToAdd
                members = members + membersToAdd
            }
        }
    }

    private fun handleIsEditingDisplayNameChanged() {
        cancelEditButton.visibility = if (isEditingGroupName) View.VISIBLE else View.GONE
        saveEditButton.visibility = if (isEditingGroupName) View.VISIBLE else View.GONE
        displayNameTextView.visibility = if (isEditingGroupName) View.INVISIBLE else View.VISIBLE
        groupNameEditText.visibility = if (isEditingGroupName) View.VISIBLE else View.INVISIBLE
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (isEditingGroupName) {
            groupNameEditText.requestFocus()
            inputMethodManager.showSoftInput(groupNameEditText, 0)
        } else {
            inputMethodManager.hideSoftInputFromWindow(groupNameEditText.windowToken, 0)
        }
    }

    private fun saveDisplayName() {
        var groupDisplayName = displayNameEditText.text.toString().trim()
        // something about the implementation of displayNameEdit text makes it null insead of whatever the user inputs, causes a crash
        if (groupDisplayName.isEmpty()) {
            return Toast.makeText(this, R.string.activity_settings_display_name_missing_error, Toast.LENGTH_SHORT).show()
        }
        if (!groupDisplayName.matches(Regex("[a-zA-Z0-9_]+"))) {
            return Toast.makeText(this, R.string.activity_settings_invalid_display_name_error, Toast.LENGTH_SHORT).show()
        }
        if (groupDisplayName.toByteArray().size > ProfileCipher.NAME_PADDED_LENGTH) {
            return Toast.makeText(this, R.string.activity_settings_display_name_too_long_error, Toast.LENGTH_SHORT).show()
        }
        isEditingGroupName = false
        nameHasChanged = true
        newGroupDisplayName = groupDisplayName

    }

    private fun modifyClosedGroup() {
        if (!nameHasChanged && membersToRemove.isEmpty() && membersToAdd.isEmpty()) { finish() } else {
            var groupDisplayName = originalName
            if (nameHasChanged) {
                groupDisplayName = newGroupDisplayName
            }
            val finalGroupMembers = members.map {
                Recipient.from(this, Address.fromSerialized(it), false)
            }.toSet()
            val finalGroupAdmins = admins.map {
                Recipient.from(this, Address.fromSerialized(it), false)
            }.toSet()
            GroupManager.updateGroup(this, groupID, finalGroupMembers, null, groupDisplayName, finalGroupAdmins)
            finish()
        }
    }
}