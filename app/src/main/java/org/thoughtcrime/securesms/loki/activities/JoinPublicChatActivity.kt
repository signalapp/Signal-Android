package org.thoughtcrime.securesms.loki.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.view.isVisible
import androidx.fragment.app.*
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import kotlinx.android.synthetic.main.activity_join_public_chat.*
import kotlinx.android.synthetic.main.fragment_enter_chat_url.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import okhttp3.HttpUrl
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2.DefaultGroup
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.DistributionTypes
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.PublicKeyValidation
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.ConversationActivity
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.loki.api.OpenGroupManager
import org.thoughtcrime.securesms.loki.fragments.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.loki.fragments.ScanQRCodeWrapperFragmentDelegate
import org.thoughtcrime.securesms.loki.protocol.MultiDeviceProtocol
import org.thoughtcrime.securesms.loki.viewmodel.DefaultGroupsViewModel
import org.thoughtcrime.securesms.loki.viewmodel.State

class JoinPublicChatActivity : PassphraseRequiredActionBarActivity(), ScanQRCodeWrapperFragmentDelegate {
    private val adapter = JoinPublicChatActivityAdapter(this)

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        // Set content view
        setContentView(R.layout.activity_join_public_chat)
        // Set title
        supportActionBar!!.title = resources.getString(R.string.activity_join_public_chat_title)
        // Set up view pager
        viewPager.adapter = adapter
        tabLayout.setupWithViewPager(viewPager)
    }
    // endregion

    // region Updating
    private fun showLoader() {
        loader.visibility = View.VISIBLE
        loader.animate().setDuration(150).alpha(1.0f).start()
    }

    private fun hideLoader() {
        loader.animate().setDuration(150).alpha(0.0f).setListener(object : AnimatorListenerAdapter() {

            override fun onAnimationEnd(animation: Animator?) {
                super.onAnimationEnd(animation)
                loader.visibility = View.GONE
            }
        })
    }
    // endregion

    // region Interaction
    override fun handleQRCodeScanned(url: String) {
        joinPublicChatIfPossible(url)
    }

    fun joinPublicChatIfPossible(url: String) {
        // Add "http" if not entered explicitly
        val stringWithExplicitScheme = if (!url.startsWith("http")) "http://$url" else url
        val url = HttpUrl.parse(stringWithExplicitScheme) ?: return Toast.makeText(this,R.string.invalid_url, Toast.LENGTH_SHORT).show()
        val room = url.pathSegments().firstOrNull()
        val publicKey = url.queryParameter("public_key")
        val isV2OpenGroup = !room.isNullOrEmpty()
        if (isV2OpenGroup && (publicKey == null || !PublicKeyValidation.isValid(publicKey, 64,false))) {
            return Toast.makeText(this, R.string.invalid_public_key, Toast.LENGTH_SHORT).show()
        }
        showLoader()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (threadID, groupID) = if (isV2OpenGroup) {
                    val server = HttpUrl.Builder().scheme(url.scheme()).host(url.host()).apply {
                        if (url.port() != 80 || url.port() != 443) { this.port(url.port()) } // Non-standard port; add to server
                    }.build()

                    val sanitizedServer = server.toString().removeSuffix("/")
                    val openGroupID = "$sanitizedServer.${room!!}"
                    OpenGroupManager.add(sanitizedServer, room, publicKey!!, this@JoinPublicChatActivity)
                    val threadID = GroupManager.getOpenGroupThreadID(openGroupID, this@JoinPublicChatActivity)
                    val groupID = GroupUtil.getEncodedOpenGroupID(openGroupID.toByteArray())
                    threadID to groupID
                } else {
                    throw Exception("No longer supported.")
                }
                MultiDeviceProtocol.forceSyncConfigurationNowIfNeeded(this@JoinPublicChatActivity)
                withContext(Dispatchers.Main) {
                    val recipient = Recipient.from(this@JoinPublicChatActivity, Address.fromSerialized(groupID), false)
                    openConversationActivity(this@JoinPublicChatActivity, threadID, recipient)
                    finish()
                }
            } catch (e: Exception) {
                Log.e("Loki", "Couldn't join open group.", e)
                withContext(Dispatchers.Main) {
                    hideLoader()
                    Toast.makeText(this@JoinPublicChatActivity, R.string.activity_join_public_chat_error, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
        }
    }
    // endregion

    // region Convenience
    private fun openConversationActivity(context: Context, threadId: Long, recipient: Recipient) {
        val intent = Intent(context, ConversationActivity::class.java)
        intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId)
        intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, DistributionTypes.DEFAULT)
        intent.putExtra(ConversationActivity.ADDRESS_EXTRA, recipient.address)
        context.startActivity(intent)
    }
    // endregion
}

// region Adapter
private class JoinPublicChatActivityAdapter(val activity: JoinPublicChatActivity) : FragmentPagerAdapter(activity.supportFragmentManager) {

    override fun getCount(): Int {
        return 2
    }

    override fun getItem(index: Int): Fragment {
        return when (index) {
            0 -> EnterChatURLFragment()
            1 -> {
                val result = ScanQRCodeWrapperFragment()
                result.delegate = activity
                result.message = activity.resources.getString(R.string.activity_join_public_chat_scan_qr_code_explanation)
                result
            }
            else -> throw IllegalStateException()
        }
    }

    override fun getPageTitle(index: Int): CharSequence {
        return when (index) {
            0 -> activity.resources.getString(R.string.activity_join_public_chat_enter_group_url_tab_title)
            1 -> activity.resources.getString(R.string.activity_join_public_chat_scan_qr_code_tab_title)
            else -> throw IllegalStateException()
        }
    }
}
// endregion

// region Enter Chat URL Fragment
class EnterChatURLFragment : Fragment() {
    private val viewModel by activityViewModels<DefaultGroupsViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_enter_chat_url, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chatURLEditText.imeOptions = chatURLEditText.imeOptions or 16777216 // Always use incognito keyboard
        joinPublicChatButton.setOnClickListener { joinPublicChatIfPossible() }
        viewModel.defaultRooms.observe(viewLifecycleOwner) { state ->
            defaultRoomsContainer.isVisible = state is State.Success
            defaultRoomsLoader.isVisible = state is State.Loading
            when (state) {
                State.Loading -> {
                    // TODO: Show a loader
                }
                is State.Error -> {
                    // TODO: Hide the loader
                }
                is State.Success -> {
                    populateDefaultGroups(state.value)
                }
            }
        }
    }

    private fun populateDefaultGroups(groups: List<DefaultGroup>) {
        defaultRoomsGridLayout.removeAllViews()
        defaultRoomsGridLayout.useDefaultMargins = false
        groups.forEach { defaultGroup ->
            val chip = layoutInflater.inflate(R.layout.default_group_chip, defaultRoomsGridLayout, false) as Chip
            val drawable = defaultGroup.image?.let { bytes ->
                val bitmap = BitmapFactory.decodeByteArray(bytes,0, bytes.size)
                RoundedBitmapDrawableFactory.create(resources,bitmap).apply {
                    isCircular = true
                }
            }
            chip.chipIcon = drawable
            chip.text = defaultGroup.name
            chip.setOnClickListener {
                (requireActivity() as JoinPublicChatActivity).joinPublicChatIfPossible(defaultGroup.joinURL)
            }

            defaultRoomsGridLayout.addView(chip)
        }
        if ((groups.size and 1) != 0) { // This checks that the number of rooms is even
            layoutInflater.inflate(R.layout.grid_layout_filler, defaultRoomsGridLayout)
        }
    }

    // region Convenience
    private fun joinPublicChatIfPossible() {
        val inputMethodManager = requireContext().getSystemService(BaseActionBarActivity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(chatURLEditText.windowToken, 0)
        val chatURL = chatURLEditText.text.trim().toString().toLowerCase()
        (requireActivity() as JoinPublicChatActivity).joinPublicChatIfPossible(chatURL)
    }
    // endregion
}
// endregion