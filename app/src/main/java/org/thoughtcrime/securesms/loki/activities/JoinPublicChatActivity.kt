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
import android.widget.GridLayout
import android.widget.Toast
import androidx.activity.viewModels
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
import org.session.libsession.messaging.threads.Address
import org.session.libsession.messaging.threads.DistributionTypes
import org.session.libsession.messaging.threads.recipients.Recipient
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.utilities.logging.Log
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.ConversationActivity
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.loki.fragments.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.loki.fragments.ScanQRCodeWrapperFragmentDelegate
import org.thoughtcrime.securesms.loki.protocol.MultiDeviceProtocol
import org.thoughtcrime.securesms.loki.utilities.OpenGroupUtilities
import org.thoughtcrime.securesms.loki.viewmodel.DefaultGroupsViewModel
import org.thoughtcrime.securesms.loki.viewmodel.State

class JoinPublicChatActivity : PassphraseRequiredActionBarActivity(), ScanQRCodeWrapperFragmentDelegate {

    private val viewModel by viewModels<DefaultGroupsViewModel>()

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
        // add http if just an IP style / host style URL is entered but leave it if scheme is included
        val properString = if (!url.startsWith("http")) "http://$url" else url
        val httpUrl = HttpUrl.parse(properString) ?: return Toast.makeText(this,R.string.invalid_url, Toast.LENGTH_SHORT).show()

        val room = httpUrl.pathSegments().firstOrNull()
        val publicKey = httpUrl.queryParameter("public_key")
        val isV2OpenGroup = !room.isNullOrEmpty()
        showLoader()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (threadID, groupID) = if (isV2OpenGroup) {
                    val server = HttpUrl.Builder().scheme(httpUrl.scheme()).host(httpUrl.host()).apply {
                        if (httpUrl.port() != 80 || httpUrl.port() != 443) {
                            // non-standard port, add to server
                            this.port(httpUrl.port())
                        }
                    }.build()
                    val group = OpenGroupUtilities.addGroup(this@JoinPublicChatActivity, server.toString().removeSuffix("/"), room!!, publicKey!!)
                    val threadID = GroupManager.getOpenGroupThreadID(group.id, this@JoinPublicChatActivity)
                    val groupID = GroupUtil.getEncodedOpenGroupID(group.id.toByteArray())
                    threadID to groupID
                } else {
                    val channel: Long = 1
                    val group = OpenGroupUtilities.addGroup(this@JoinPublicChatActivity, properString, channel)
                    val threadID = GroupManager.getOpenGroupThreadID(group.id, this@JoinPublicChatActivity)
                    val groupID = GroupUtil.getEncodedOpenGroupID(group.id.toByteArray())
                    threadID to groupID
                }
                MultiDeviceProtocol.forceSyncConfigurationNowIfNeeded(this@JoinPublicChatActivity)

                withContext(Dispatchers.Main) {
                    // go to the new conversation and finish this one
                    openConversationActivity(this@JoinPublicChatActivity, threadID, Recipient.from(this@JoinPublicChatActivity, Address.fromSerialized(groupID), false))
                    finish()
                }

            } catch (e: Exception) {
                Log.e("JoinPublicChatActivity", "Fialed to join open group.", e)
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

    private fun populateDefaultGroups(groups: List<DefaultGroup>) {
        defaultRoomsGridLayout.removeAllViews()
        defaultRoomsGridLayout.useDefaultMargins = false
        groups.forEach { defaultGroup ->
            val chip = layoutInflater.inflate(R.layout.default_group_chip, defaultRoomsGridLayout, false) as Chip
            val drawable = defaultGroup.image?.let { bytes ->
                val bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.size)
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chatURLEditText.imeOptions = chatURLEditText.imeOptions or 16777216 // Always use incognito keyboard
        joinPublicChatButton.setOnClickListener { joinPublicChatIfPossible() }
        viewModel.defaultRooms.observe(viewLifecycleOwner) { state ->
            defaultRoomsContainer.isVisible = state is State.Success
            defaultRoomsLoader.isVisible = state is State.Loading
            when (state) {
                State.Loading -> {
                    // show a loader here probs
                }
                is State.Error -> {
                    // hide the loader and the
                }
                is State.Success -> {
                    populateDefaultGroups(state.value)
                }
            }
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