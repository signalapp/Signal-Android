package org.thoughtcrime.securesms.loki.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_settings.*
import network.loki.messenger.R
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.session.libsignal.service.loki.utilities.toHexString
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.session.libsession.messaging.threads.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.loki.dialogs.ClosedGroupEditingOptionsBottomSheet
import org.thoughtcrime.securesms.loki.protocol.ClosedGroupsProtocol
import org.thoughtcrime.securesms.loki.protocol.ClosedGroupsProtocolV2
import org.thoughtcrime.securesms.loki.utilities.fadeIn
import org.thoughtcrime.securesms.loki.utilities.fadeOut
import org.thoughtcrime.securesms.mms.GlideApp
import org.session.libsession.messaging.threads.recipients.Recipient
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.ThemeUtil
import java.io.IOException

class EditClosedGroupActivity : PassphraseRequiredActionBarActivity() {
    private val originalMembers = HashSet<String>()
    private val members = HashSet<String>()
    private var hasNameChanged = false
    private var isLoading = false
        set(newValue) { field = newValue; invalidateOptionsMenu() }

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

    private lateinit var mainContentContainer: LinearLayout
    private lateinit var cntGroupNameEdit: LinearLayout
    private lateinit var cntGroupNameDisplay: LinearLayout
    private lateinit var edtGroupName: EditText
    private lateinit var emptyStateContainer: LinearLayout
    private lateinit var lblGroupNameDisplay: TextView
    private lateinit var loaderContainer: View

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

        supportActionBar!!.setHomeAsUpIndicator(
                ThemeUtil.getThemedDrawableResId(this, R.attr.actionModeCloseDrawable))

        groupID = intent.getStringExtra(groupIDKey)!!
        originalName = DatabaseFactory.getGroupDatabase(this).getGroup(groupID).get().title
        name = originalName

        mainContentContainer = findViewById(R.id.mainContentContainer)
        cntGroupNameEdit = findViewById(R.id.cntGroupNameEdit)
        cntGroupNameDisplay = findViewById(R.id.cntGroupNameDisplay)
        edtGroupName = findViewById(R.id.edtGroupName)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)
        lblGroupNameDisplay = findViewById(R.id.lblGroupNameDisplay)
        loaderContainer = findViewById(R.id.loaderContainer)

        findViewById<View>(R.id.addMembersClosedGroupButton).setOnClickListener {
            onAddMembersClick()
        }

        findViewById<RecyclerView>(R.id.rvUserList).apply {
            adapter = memberListAdapter
            layoutManager = LinearLayoutManager(this@EditClosedGroupActivity)
        }

        lblGroupNameDisplay.text = originalName
        cntGroupNameDisplay.setOnClickListener { isEditingName = true }
        findViewById<View>(R.id.btnCancelGroupNameEdit).setOnClickListener { isEditingName = false }
        findViewById<View>(R.id.btnSaveGroupNameEdit).setOnClickListener { saveName() }
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

        LoaderManager.getInstance(this).initLoader(loaderID, null, object : LoaderManager.LoaderCallbacks<List<String>> {

            override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<List<String>> {
                return EditClosedGroupLoader(this@EditClosedGroupActivity, groupID)
            }

            override fun onLoadFinished(loader: Loader<List<String>>, members: List<String>) {
                // We no longer need any subsequent loading events
                // (they will occur on every activity resume).
                LoaderManager.getInstance(this@EditClosedGroupActivity).destroyLoader(loaderID)

                originalMembers.clear()
                originalMembers.addAll(members.toHashSet())
                updateMembers(originalMembers)
            }

            override fun onLoaderReset(loader: Loader<List<String>>) {
                updateMembers(setOf())
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_edit_closed_group, menu)
        return members.isNotEmpty() && !isLoading
    }
    // endregion

    // region Updating
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            addUsersRequestCode -> {
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

        val admins = DatabaseFactory.getGroupDatabase(this).getGroup(groupID).get().admins.map { it.toString() }.toMutableSet()
        admins.remove(TextSecurePreferences.getLocalNumber(this))
        memberListAdapter.setLockedMembers(admins)

        mainContentContainer.visibility = if (members.isEmpty()) View.GONE else View.VISIBLE
        emptyStateContainer.visibility = if (members.isEmpty()) View.VISIBLE else View.GONE

        invalidateOptionsMenu()
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_apply -> if (!isLoading) { commitChanges() }
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
        intent.putExtra(SelectContactsActivity.usersToExcludeKey, members.toTypedArray())
        intent.putExtra(SelectContactsActivity.emptyStateTextKey, "No contacts to add")
        startActivityForResult(intent, addUsersRequestCode)
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
        val hasMemberListChanges = (members != originalMembers)

        if (!hasNameChanged && !hasMemberListChanges) {
            return finish()
        }

        val name = if (hasNameChanged) this.name else originalName

        val members = this.members.map {
            Recipient.from(this, Address.fromSerialized(it), false)
        }.toSet()
        val originalMembers = this.originalMembers.map {
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

        if (members.isEmpty()) {
            return Toast.makeText(this, R.string.activity_edit_closed_group_not_enough_group_members_error, Toast.LENGTH_LONG).show()
        }

        val maxGroupMembers = if (isSSKBasedClosedGroup) ClosedGroupsProtocolV2.groupSizeLimit else legacyGroupSizeLimit
        if (members.size >= maxGroupMembers) {
            return Toast.makeText(this, R.string.activity_create_closed_group_too_many_group_members_error, Toast.LENGTH_LONG).show()
        }

        val userPublicKey = TextSecurePreferences.getLocalNumber(this)!!
        val userAsRecipient = Recipient.from(this, Address.fromSerialized(userPublicKey), false)

        if (!members.contains(userAsRecipient) && !members.map { it.address.toString() }.containsAll(originalMembers.minus(userPublicKey))) {
            val message = "Can't leave while adding or removing other members."
            return Toast.makeText(this@EditClosedGroupActivity, message, Toast.LENGTH_LONG).show()
        }

        if (isSSKBasedClosedGroup) {
            isLoading = true
            loaderContainer.fadeIn()
            val promise: Promise<Any, Exception> = if (!members.contains(Recipient.from(this, Address.fromSerialized(userPublicKey), false))) {
                ClosedGroupsProtocolV2.explicitLeave(this, groupPublicKey!!)
            } else {
                task {
                    val name =
                            if (hasNameChanged) ClosedGroupsProtocolV2.explicitNameChange(this@EditClosedGroupActivity,groupPublicKey!!,name)
                            else Promise.of(Unit)
                    name.get()
                    members.filterNot { it in originalMembers }.let { adds ->
                        if (adds.isNotEmpty()) ClosedGroupsProtocolV2.explicitAddMembers(this@EditClosedGroupActivity, groupPublicKey!!, adds.map { it.address.serialize() })
                        else Promise.of(Unit)
                    }.get()
                    originalMembers.filterNot { it in members }.let { removes ->
                        if (removes.isNotEmpty()) ClosedGroupsProtocolV2.explicitRemoveMembers(this@EditClosedGroupActivity, groupPublicKey!!, removes.map { it.address.serialize() })
                        else Promise.of(Unit)
                    }.get()
                }
            }
            promise.successUi {
                loaderContainer.fadeOut()
                isLoading = false
                finish()
            }.failUi { exception ->
                val message = if (exception is ClosedGroupsProtocol.Error) exception.description else "An error occurred"
                Toast.makeText(this@EditClosedGroupActivity, message, Toast.LENGTH_LONG).show()
                loader.fadeOut()
                isLoading = false
            }
        } else {
            GroupManager.updateGroup(this, groupID, members, null, name, admins)
        }
    }
}