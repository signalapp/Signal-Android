package org.thoughtcrime.securesms.groups

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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityJoinPublicChatBinding
import network.loki.messenger.databinding.FragmentEnterChatUrlBinding
import okhttp3.HttpUrl
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2.DefaultGroup
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.PublicKeyValidation
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import org.thoughtcrime.securesms.util.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.util.ScanQRCodeWrapperFragmentDelegate
import org.thoughtcrime.securesms.util.State
import java.util.Locale

class JoinPublicChatActivity : PassphraseRequiredActionBarActivity(), ScanQRCodeWrapperFragmentDelegate {
    private lateinit var binding: ActivityJoinPublicChatBinding
    private val adapter = JoinPublicChatActivityAdapter(this)

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        binding = ActivityJoinPublicChatBinding.inflate(layoutInflater)
        // Set content view
        setContentView(binding.root)
        // Set title
        supportActionBar!!.title = resources.getString(R.string.activity_join_public_chat_title)
        // Set up view pager
        binding.viewPager.adapter = adapter
        binding.tabLayout.setupWithViewPager(binding.viewPager)
    }
    // endregion

    // region Updating
    private fun showLoader() {
        binding.loader.visibility = View.VISIBLE
        binding.loader.animate().setDuration(150).alpha(1.0f).start()
    }

    private fun hideLoader() {
        binding.loader.animate().setDuration(150).alpha(0.0f).setListener(object : AnimatorListenerAdapter() {

            override fun onAnimationEnd(animation: Animator?) {
                super.onAnimationEnd(animation)
                binding.loader.visibility = View.GONE
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
                ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(this@JoinPublicChatActivity)
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
        val intent = Intent(context, ConversationActivityV2::class.java)
        intent.putExtra(ConversationActivityV2.THREAD_ID, threadId)
        intent.putExtra(ConversationActivityV2.ADDRESS, recipient.address)
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
    private lateinit var binding: FragmentEnterChatUrlBinding
    private val viewModel by activityViewModels<DefaultGroupsViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentEnterChatUrlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.chatURLEditText.imeOptions = binding.chatURLEditText.imeOptions or 16777216 // Always use incognito keyboard
        binding.joinPublicChatButton.setOnClickListener { joinPublicChatIfPossible() }
        viewModel.defaultRooms.observe(viewLifecycleOwner) { state ->
            binding.defaultRoomsContainer.isVisible = state is State.Success
            binding.defaultRoomsLoaderContainer.isVisible = state is State.Loading
            binding.defaultRoomsLoader.isVisible = state is State.Loading
            when (state) {
                State.Loading -> {
                    // TODO: Show a binding.loader
                }
                is State.Error -> {
                    // TODO: Hide the binding.loader
                }
                is State.Success -> {
                    populateDefaultGroups(state.value)
                }
            }
        }
    }

    private fun populateDefaultGroups(groups: List<DefaultGroup>) {
        binding.defaultRoomsGridLayout.removeAllViews()
        binding.defaultRoomsGridLayout.useDefaultMargins = false
        groups.iterator().forEach { defaultGroup ->
            val chip = layoutInflater.inflate(R.layout.default_group_chip, binding.defaultRoomsGridLayout, false) as Chip
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
            binding.defaultRoomsGridLayout.addView(chip)
        }
        if ((groups.size and 1) != 0) { // This checks that the number of rooms is even
            layoutInflater.inflate(R.layout.grid_layout_filler, binding.defaultRoomsGridLayout)
        }
    }

    // region Convenience
    private fun joinPublicChatIfPossible() {
        val inputMethodManager = requireContext().getSystemService(BaseActionBarActivity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.chatURLEditText.windowToken, 0)
        val chatURL = binding.chatURLEditText.text.trim().toString().toLowerCase(Locale.US)
        (requireActivity() as JoinPublicChatActivity).joinPublicChatIfPossible(chatURL)
    }
    // endregion
}
// endregion