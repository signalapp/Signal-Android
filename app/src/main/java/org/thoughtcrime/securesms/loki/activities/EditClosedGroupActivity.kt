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
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.sending_receiving.groupSizeLimit
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.session.libsession.messaging.threads.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.dialogs.ClosedGroupEditingOptionsBottomSheet
import org.thoughtcrime.securesms.loki.utilities.fadeIn
import org.thoughtcrime.securesms.loki.utilities.fadeOut
import org.thoughtcrime.securesms.mms.GlideApp
import org.session.libsession.messaging.threads.recipients.Recipient
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.ThemeUtil
import org.session.libsignal.utilities.toHexString
import java.io.IOException

class EditClosedGroupActivity : PassphraseRequiredActionBarActivity() {
    private val originalMembers = HashSet<String>()
    private val zombies = HashSet<String>()
    private val members = HashSet<String>()
    private val allMembers: Set<String>
        get() {
            return members + zombies
        }
    private var hasNameChanged = false
    private var isSelfAdmin = false
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
        if (isSelfAdmin)
            EditClosedGroupMembersAdapter(this, GlideApp.with(this), isSelfAdmin, this::onMemberClick)
        else
            EditClosedGroupMembersAdapter(this, GlideApp.with(this), isSelfAdmin)
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
        val groupInfo = DatabaseFactory.getGroupDatabase(this).getGroup(groupID).get()
        originalName = groupInfo.title
        isSelfAdmin = groupInfo.admins.any{ it.serialize() == TextSecurePreferences.getLocalNumber(this) }

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

        LoaderManager.getInstance(this).initLoader(loaderID, null, object : LoaderManager.LoaderCallbacks<GroupMembers> {

            override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<GroupMembers> {
                return EditClosedGroupLoader(this@EditClosedGroupActivity, groupID)
            }

            override fun onLoadFinished(loader: Loader<GroupMembers>, groupMembers: GroupMembers) {
                // We no longer need any subsequent loading events
                // (they will occur on every activity resume).
                LoaderManager.getInstance(this@EditClosedGroupActivity).destroyLoader(loaderID)

                members.clear()
                members.addAll(groupMembers.members.toHashSet())
                zombies.clear()
                zombies.addAll(groupMembers.zombieMembers.toHashSet())
                originalMembers.clear()
                originalMembers.addAll(members + zombies)
                updateMembers()
            }

            override fun onLoaderReset(loader: Loader<GroupMembers>) {
                updateMembers()
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_edit_closed_group, menu)
        return allMembers.isNotEmpty() && !isLoading
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
                members.addAll(selectedContacts)
                updateMembers()
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

    private fun updateMembers() {
        memberListAdapter.setMembers(allMembers)
        memberListAdapter.setZombieMembers(zombies)

        mainContentContainer.visibility = if (allMembers.isEmpty()) View.GONE else View.VISIBLE
        emptyStateContainer.visibility = if (allMembers.isEmpty()) View.VISIBLE else View.GONE

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
            if (zombies.contains(member)) zombies.remove(member)
            else members.remove(member)
            updateMembers()
            bottomSheet.dismiss()
        }
        bottomSheet.show(supportFragmentManager, "GroupEditingOptionsBottomSheet")
    }

    private fun onAddMembersClick() {
        val intent = Intent(this@EditClosedGroupActivity, SelectContactsActivity::class.java)
        intent.putExtra(SelectContactsActivity.usersToExcludeKey, allMembers.toTypedArray())
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
        val hasMemberListChanges = (allMembers != originalMembers)

        if (!hasNameChanged && !hasMemberListChanges) {
            return finish()
        }

        val name = if (hasNameChanged) this.name else originalName

        val members = this.allMembers.map {
            Recipient.from(this, Address.fromSerialized(it), false)
        }.toSet()
        val originalMembers = this.originalMembers.map {
            Recipient.from(this, Address.fromSerialized(it), false)
        }.toSet()

        var isClosedGroup: Boolean
        var groupPublicKey: String?
        try {
            groupPublicKey = GroupUtil.doubleDecodeGroupID(groupID).toHexString()
            isClosedGroup = DatabaseFactory.getLokiAPIDatabase(this).isClosedGroup(groupPublicKey)
        } catch (e: IOException) {
            groupPublicKey = null
            isClosedGroup = false
        }

        if (members.isEmpty()) {
            return Toast.makeText(this, R.string.activity_edit_closed_group_not_enough_group_members_error, Toast.LENGTH_LONG).show()
        }

        val maxGroupMembers = if (isClosedGroup) groupSizeLimit else legacyGroupSizeLimit
        if (members.size >= maxGroupMembers) {
            return Toast.makeText(this, R.string.activity_create_closed_group_too_many_group_members_error, Toast.LENGTH_LONG).show()
        }

        val userPublicKey = TextSecurePreferences.getLocalNumber(this)!!
        val userAsRecipient = Recipient.from(this, Address.fromSerialized(userPublicKey), false)

        if (!members.contains(userAsRecipient) && !members.map { it.address.toString() }.containsAll(originalMembers.minus(userPublicKey))) {
            val message = "Can't leave while adding or removing other members."
            return Toast.makeText(this@EditClosedGroupActivity, message, Toast.LENGTH_LONG).show()
        }

        if (isClosedGroup) {
            isLoading = true
            loaderContainer.fadeIn()
            val promise: Promise<Any, Exception> = if (!members.contains(Recipient.from(this, Address.fromSerialized(userPublicKey), false))) {
                MessageSender.explicitLeave(groupPublicKey!!, true)
            } else {
                task {
                    if (hasNameChanged) {
                        MessageSender.explicitNameChange(groupPublicKey!!, name)
                    }
                    members.filterNot { it in originalMembers }.let { adds ->
                        if (adds.isNotEmpty()) MessageSender.explicitAddMembers(groupPublicKey!!, adds.map { it.address.serialize() })
                    }
                    originalMembers.filterNot { it in members }.let { removes ->
                        if (removes.isNotEmpty()) MessageSender.explicitRemoveMembers(groupPublicKey!!, removes.map { it.address.serialize() })
                    }
                }
            }
            promise.successUi {
                loaderContainer.fadeOut()
                isLoading = false
                finish()
            }.failUi { exception ->
                val message = if (exception is MessageSender.Error) exception.description else "An error occurred"
                Toast.makeText(this@EditClosedGroupActivity, message, Toast.LENGTH_LONG).show()
                loaderContainer.fadeOut()
                isLoading = false
            }
        }
    }

    class GroupMembers(val members: List<String>, val zombieMembers: List<String>) { }
}