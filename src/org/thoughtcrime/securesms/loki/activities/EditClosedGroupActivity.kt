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
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_create_closed_group.emptyStateContainer
import kotlinx.android.synthetic.main.activity_create_closed_group.mainContentContainer
import kotlinx.android.synthetic.main.activity_edit_closed_group.*
import kotlinx.android.synthetic.main.activity_linked_devices.recyclerView
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.loki.dialogs.GroupEditingOptionsBottomSheet
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.crypto.ProfileCipher

const val EXTRA_GROUP_ID = "GROUP_ID"
const val REQ_CODE_ADD_USERS = 124
const val LOADER_ID_MEMBERS = 0

class EditClosedGroupActivity : PassphraseRequiredActionBarActivity() {

    private lateinit var memberListAdapter: EditClosedGroupMembersAdapter
    private val originalMembers = HashSet<String>()
    private val members = HashSet<String>()
//    private var adminMembers = HashSet<String>()

    private lateinit var groupID: String
    private lateinit var originalName: String
    private lateinit var newGroupDisplayName: String

    private var nameHasChanged = false

    private var isEditingGroupName = false
        set(value) {
            if (field == value) return
            field = value
            handleIsEditingDisplayNameChanged()
        }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        setContentView(R.layout.activity_edit_closed_group)

        this.groupID = intent.getStringExtra(EXTRA_GROUP_ID)
        this.originalName = DatabaseFactory.getGroupDatabase(this).getGroup(groupID).get().title
        this.newGroupDisplayName = this.originalName

        supportActionBar!!.title = resources.getString(R.string.activity_edit_closed_group_title)

        addMembersClosedGroupButton.setOnClickListener { onAddMembersClick() }

        this.memberListAdapter = EditClosedGroupMembersAdapter(
                this,
                GlideApp.with(this),
                this::onMemberClick
        )
        recyclerView.adapter = this.memberListAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        lblGroupNameDisplay.text = this.originalName
        cntGroupNameDisplay.setOnClickListener { isEditingGroupName = true }
        btnCancelGroupNameEdit.setOnClickListener { isEditingGroupName = false }
        btnSaveGroupNameEdit.setOnClickListener { saveDisplayName() }
        edtGroupName.setImeActionLabel(getString(R.string.save), EditorInfo.IME_ACTION_DONE)
        edtGroupName.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_DONE -> {
                    saveDisplayName()
                    return@setOnEditorActionListener true
                }
                else -> return@setOnEditorActionListener false
            }
        }

        // Setup member list loader.
        LoaderManager.getInstance(this).initLoader(LOADER_ID_MEMBERS, null, object : LoaderManager.LoaderCallbacks<List<String>> {
            override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<List<String>> {
                return EditClosedGroupLoader(groupID, this@EditClosedGroupActivity)
            }

            override fun onLoadFinished(loader: Loader<List<String>>, members: List<String>) {
                // We no longer need any subsequent loading events
                // (they will occur on every activity resume).
                LoaderManager.getInstance(this@EditClosedGroupActivity).destroyLoader(LOADER_ID_MEMBERS)

                originalMembers.clear()
                originalMembers.addAll(members.toHashSet())
                updateMembers(originalMembers)
            }

            override fun onLoaderReset(loader: Loader<List<String>>) {
                updateMembers(setOf())
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_apply, menu)
        return members.isNotEmpty()
    }
    // endregion

    // region Updating
    private fun updateMembers(members: Set<String>) {
        this.members.clear()
        this.members.addAll(members)
        this.memberListAdapter.setItems(members)
        mainContentContainer.visibility = if (members.isEmpty()) View.GONE else View.VISIBLE
        emptyStateContainer.visibility = if (members.isEmpty()) View.VISIBLE else View.GONE
        invalidateOptionsMenu()
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.applyButton -> commitClosedGroupChanges()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun onMemberClick(member: String) {
        val bottomSheet = GroupEditingOptionsBottomSheet()
        bottomSheet.onRemoveTapped = {
            val changedMembers = members - member
            updateMembers(changedMembers)
            bottomSheet.dismiss()
        }
 //       bottomSheet.onAdminTapped = {
 //           bottomSheet.dismiss()
 //       }
        bottomSheet.show(supportFragmentManager, "MEMBER_BOTTOM_SHEET")
    }

    private fun onAddMembersClick() {
        val intent = Intent(this@EditClosedGroupActivity, SelectContactsActivity::class.java)
        startActivityForResult(intent, REQ_CODE_ADD_USERS)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_CODE_ADD_USERS -> {
                if (resultCode != RESULT_OK) return
                if (data == null || data.extras == null || !data.hasExtra(EXTRA_RESULT_SELECTED_CONTACTS)) return

                val selectedContacts = data.extras!!.getStringArray(EXTRA_RESULT_SELECTED_CONTACTS)!!.toSet()
                val changedMembers = members + selectedContacts
                updateMembers(changedMembers)
            }
        }
    }

    private fun handleIsEditingDisplayNameChanged() {
        cntGroupNameEdit.visibility = if (isEditingGroupName) View.VISIBLE else View.INVISIBLE
        cntGroupNameDisplay.visibility = if (isEditingGroupName) View.INVISIBLE else View.VISIBLE
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (isEditingGroupName) {
            edtGroupName.setText(newGroupDisplayName)
            edtGroupName.selectAll()
            edtGroupName.requestFocus()
            inputMethodManager.showSoftInput(edtGroupName, 0)
        } else {
            inputMethodManager.hideSoftInputFromWindow(edtGroupName.windowToken, 0)
        }
    }

    private fun saveDisplayName() {
        val groupDisplayName = edtGroupName.text.toString().trim()
        if (groupDisplayName.isEmpty()) {
            return Toast.makeText(this, R.string.activity_settings_display_name_missing_error, Toast.LENGTH_SHORT).show()
        }
        if (!groupDisplayName.matches(Regex("[a-zA-Z0-9_]+"))) {
            return Toast.makeText(this, R.string.activity_settings_invalid_display_name_error, Toast.LENGTH_SHORT).show()
        }
        if (groupDisplayName.toByteArray().size > ProfileCipher.NAME_PADDED_LENGTH) {
            return Toast.makeText(this, R.string.activity_settings_display_name_too_long_error, Toast.LENGTH_SHORT).show()
        }
        newGroupDisplayName = groupDisplayName
        lblGroupNameDisplay.text = groupDisplayName
        nameHasChanged = true
        isEditingGroupName = false
    }

    private fun commitClosedGroupChanges() {
        val membersHaveChanged = members.size != originalMembers.size || !members.containsAll(originalMembers)

        if (!nameHasChanged && !membersHaveChanged) {
            finish()
            return
        }

        val groupDisplayName = if (nameHasChanged) newGroupDisplayName else originalName

        val finalGroupMembers = members.map {
            Recipient.from(this, Address.fromSerialized(it), false)
        }.toSet()

//        val finalGroupAdmins = adminMembers.map {
//            Recipient.from(this, Address.fromSerialized(it), false)
//        }.toSet()
        val finalGroupAdmins = finalGroupMembers.toSet() //TODO For now, consider all the users are admins.


        if (groupDisplayName.length >= 64) {
            return Toast.makeText(this, R.string.activity_edit_closed_group_group_name_too_long_error, Toast.LENGTH_LONG).show()
        }
        if (finalGroupMembers.size < 2) {
            return Toast.makeText(this, R.string.activity_edit_closed_group_not_enough_group_members_error, Toast.LENGTH_LONG).show()
        }
        if (finalGroupMembers.size > 10) {
            return Toast.makeText(this, R.string.activity_edit_closed_group_too_many_group_members_error, Toast.LENGTH_LONG).show()
        }

        GroupManager.updateGroup(this, groupID, finalGroupMembers, null, groupDisplayName, finalGroupAdmins)
        finish()
    }
}