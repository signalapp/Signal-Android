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
import org.thoughtcrime.securesms.loki.dialogs.ClosedGroupEditingOptionsBottomSheet
import org.thoughtcrime.securesms.loki.protocol.ClosedGroupsProtocol
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.GroupUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.utilities.toHexString
import java.io.IOException

class EditClosedGroupActivity : PassphraseRequiredActionBarActivity() {
    private val originalMembers = HashSet<String>()
    private val members = HashSet<String>()
    private var hasNameChanged = false

    private lateinit var groupID: String
    private lateinit var originalName: String
    private lateinit var name: String

    private var isEditingName = false
        set(value) {
            if (field == value) return
            field = value
            handleIsEditingNameChanged()
        }

    private val memberListAdapter by lazy {
        EditClosedGroupMembersAdapter(this, GlideApp.with(this), this::onMemberClick)
    }

    companion object {
        @JvmStatic val groupIDKey = "groupIDKey"
        private val loaderID = 0
        val addUsersRequestCode = 124
        val legacyGroupSizeLimit = 10
    }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)

        setContentView(R.layout.activity_edit_closed_group)
        supportActionBar!!.title = resources.getString(R.string.activity_edit_closed_group_title)

        groupID = intent.getStringExtra(Companion.groupIDKey)
        originalName = DatabaseFactory.getGroupDatabase(this).getGroup(groupID).get().title
        name = originalName

        addMembersClosedGroupButton.setOnClickListener { onAddMembersClick() }

        recyclerView.adapter = memberListAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        lblGroupNameDisplay.text = originalName
        cntGroupNameDisplay.setOnClickListener { isEditingName = true }
        btnCancelGroupNameEdit.setOnClickListener { isEditingName = false }
        btnSaveGroupNameEdit.setOnClickListener { saveName() }
        edtGroupName.setImeActionLabel(getString(R.string.save), EditorInfo.IME_ACTION_DONE)
        edtGroupName.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_DONE -> {
                    saveName()
                    return@setOnEditorActionListener true
                }
                else -> return@setOnEditorActionListener false
            }
        }

        LoaderManager.getInstance(this).initLoader(Companion.loaderID, null, object : LoaderManager.LoaderCallbacks<List<String>> {

            override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<List<String>> {
                return EditClosedGroupLoader(this@EditClosedGroupActivity, groupID)
            }

            override fun onLoadFinished(loader: Loader<List<String>>, members: List<String>) {
                // We no longer need any subsequent loading events
                // (they will occur on every activity resume).
                LoaderManager.getInstance(this@EditClosedGroupActivity).destroyLoader(Companion.loaderID)

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
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            Companion.addUsersRequestCode -> {
                if (resultCode != RESULT_OK) return
                if (data == null || data.extras == null || !data.hasExtra(SelectContactsActivity.selectedContactsKey)) return

                val selectedContacts = data.extras!!.getStringArray(SelectContactsActivity.selectedContactsKey)!!.toSet()
                val changedMembers = members + selectedContacts
                updateMembers(changedMembers)
            }
        }
    }

    private fun handleIsEditingNameChanged() {
        cntGroupNameEdit.visibility = if (isEditingName) View.VISIBLE else View.INVISIBLE
        cntGroupNameDisplay.visibility = if (isEditingName) View.INVISIBLE else View.VISIBLE
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (isEditingName) {
            edtGroupName.setText(name)
            edtGroupName.selectAll()
            edtGroupName.requestFocus()
            inputMethodManager.showSoftInput(edtGroupName, 0)
        } else {
            inputMethodManager.hideSoftInputFromWindow(edtGroupName.windowToken, 0)
        }
    }

    private fun updateMembers(members: Set<String>) {
        this.members.clear()
        this.members.addAll(members)
        memberListAdapter.setMembers(members)

        val userPublicKey = TextSecurePreferences.getLocalNumber(this)
        memberListAdapter.setLockedMembers(arrayListOf(userPublicKey))

        mainContentContainer.visibility = if (members.isEmpty()) View.GONE else View.VISIBLE
        emptyStateContainer.visibility = if (members.isEmpty()) View.VISIBLE else View.GONE

        invalidateOptionsMenu()
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.applyButton -> commitChanges()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun onMemberClick(member: String) {
        val bottomSheet = ClosedGroupEditingOptionsBottomSheet()
        bottomSheet.onRemoveTapped = {
            val changedMembers = members - member
            updateMembers(changedMembers)
            bottomSheet.dismiss()
        }
        bottomSheet.show(supportFragmentManager, "GroupEditingOptionsBottomSheet")
    }

    private fun onAddMembersClick() {
        val intent = Intent(this@EditClosedGroupActivity, SelectContactsActivity::class.java)
        intent.putExtra(SelectContactsActivity.Companion.usersToExcludeKey, members.toTypedArray())
        startActivityForResult(intent, Companion.addUsersRequestCode)
    }

    private fun saveName() {
        val name = edtGroupName.text.toString().trim()
        if (name.isEmpty()) {
            return Toast.makeText(this, R.string.activity_edit_closed_group_group_name_missing_error, Toast.LENGTH_SHORT).show()
        }
        if (name.length >= 64) {
            return Toast.makeText(this, R.string.activity_edit_closed_group_group_name_too_long_error, Toast.LENGTH_SHORT).show()
        }
        this.name = name
        lblGroupNameDisplay.text = name
        hasNameChanged = true
        isEditingName = false
    }

    private fun commitChanges() {
        val hasMemberListChanges = members != originalMembers

        if (!hasNameChanged && !hasMemberListChanges) {
            return finish()
        }

        val name = if (hasNameChanged) this.name else originalName

        val members = this.members.map {
            Recipient.from(this, Address.fromSerialized(it), false)
        }.toSet()

        val admins = members.toSet() //TODO For now, consider all the users to be admins.

        var isSSKBasedClosedGroup: Boolean
        var groupPublicKey: String?
        try {
            groupPublicKey = ClosedGroupsProtocol.doubleDecodeGroupID(groupID).toHexString()
            isSSKBasedClosedGroup = DatabaseFactory.getSSKDatabase(this).isSSKBasedClosedGroup(groupPublicKey)
        } catch (e: IOException) {
            groupPublicKey = null
            isSSKBasedClosedGroup = false
        }

        if (members.size < 2) {
            return Toast.makeText(this, R.string.activity_edit_closed_group_not_enough_group_members_error, Toast.LENGTH_LONG).show()
        }

        val maxGroupMembers = if (isSSKBasedClosedGroup) ClosedGroupsProtocol.groupSizeLimit else Companion.legacyGroupSizeLimit
        if (members.size > maxGroupMembers) {
            // TODO: Update copy for SSK based closed groups
            return Toast.makeText(this, R.string.activity_edit_closed_group_too_many_group_members_error, Toast.LENGTH_LONG).show()
        }

        if (isSSKBasedClosedGroup) {
            ClosedGroupsProtocol.update(this, groupPublicKey!!, members.map { it.address.serialize() },
                name, admins.map { it.address.serialize() })
        } else {
            GroupManager.updateGroup(this, groupID, members, null, name, admins)
        }
        finish()
    }
}