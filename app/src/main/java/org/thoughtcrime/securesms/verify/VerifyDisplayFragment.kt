package org.thoughtcrime.securesms.verify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnScrollChangedListener
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.signal.core.util.ThreadUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.requireParcelableCompat
import org.signal.libsignal.protocol.fingerprint.Fingerprint
import org.signal.libsignal.protocol.fingerprint.FingerprintVersionMismatchException
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.verify.SafetyNumberQrView
import org.thoughtcrime.securesms.components.verify.SafetyNumberQrView.Companion.getSegments
import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable
import org.thoughtcrime.securesms.databinding.VerifyDisplayFragmentBinding
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.max

/**
 * Fragment to display a user's identity key.
 */
class VerifyDisplayFragment : Fragment(), OnScrollChangedListener {
  private lateinit var viewModel: VerifySafetyNumberViewModel

  private val binding by ViewBinderDelegate(VerifyDisplayFragmentBinding::bind)

  private lateinit var safetyNumberAdapter: SafetyNumberAdapter

  private var selectedFingerPrint = 0

  private var callback: Callback? = null

  private var animateCodeChanges = true

  private var animateSuccessOnDraw = false
  private var animateFailureOnDraw = false
  private var currentVerifiedState = false

  override fun onAttach(context: Context) {
    super.onAttach(context)
    callback = if (context is Callback) {
      context
    } else if (parentFragment is Callback) {
      parentFragment as Callback?
    } else {
      throw ClassCastException("Cannot find ScanListener in parent component")
    }
  }

  override fun onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup?, bundle: Bundle?): View? {
    return ViewUtil.inflate(inflater, viewGroup!!, R.layout.verify_display_fragment)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    initializeViewModel()

    updateVerifyButton(requireArguments().getBoolean(VERIFIED_STATE, false), false)

    binding.verifyButton.setOnClickListener { updateVerifyButton(!currentVerifiedState, true) }
    binding.scrollView.viewTreeObserver?.addOnScrollChangedListener(this)
    binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressed() }
    binding.toolbar.setTitle(R.string.AndroidManifest__verify_safety_number)

    safetyNumberAdapter = SafetyNumberAdapter()
    binding.verifyViewPager.adapter = safetyNumberAdapter
    binding.verifyViewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
      override fun onPageSelected(position: Int) {
        selectedFingerPrint = position
      }
    })
    val peekSize = resources.getDimensionPixelSize(R.dimen.safety_number_qr_peek)
    val pageTransformer = ViewPager2.PageTransformer { page: View, position: Float ->
      val remainingWidth = (max(0, page.width - ((page as ViewGroup).getChildAt(0).width))) / 2f
      val peekWidth = peekSize.toFloat().coerceAtMost(remainingWidth / 2f)
      page.translationX = -position * (peekWidth + remainingWidth)
    }
    binding.verifyViewPager.setPageTransformer(pageTransformer)
    binding.verifyViewPager.offscreenPageLimit = 1
    TabLayoutMediator(binding.dotIndicators, binding.verifyViewPager) { _: TabLayout.Tab?, _: Int -> }.attach()

    viewModel.recipient.observe(this) { recipient: Recipient -> setRecipientText(recipient) }
    viewModel.getFingerprints().observe(viewLifecycleOwner) { fingerprints: List<SafetyNumberFingerprint>? ->
      if (fingerprints == null) {
        return@observe
      }
      val multipleCards = fingerprints.size > 1
      binding.dotIndicators.visible = multipleCards

      if (fingerprints.isEmpty()) {
        val resolved = viewModel.recipient.resolve()
        Log.w(TAG, String.format(Locale.ENGLISH, "Could not show proper verification! verifyV2: %s, hasUuid: %s, hasE164: %s", RemoteConfig.verifyV2, resolved.serviceId.isPresent, resolved.e164.isPresent))
        MaterialAlertDialogBuilder(requireContext())
          .setMessage(getString(R.string.VerifyIdentityActivity_you_must_first_exchange_messages_in_order_to_view, resolved.getDisplayName(requireContext())))
          .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int -> requireActivity().finish() }
          .setOnDismissListener { dialog: DialogInterface ->
            requireActivity().finish()
            dialog.dismiss()
          }
          .show()
        return@observe
      }
      safetyNumberAdapter.setFingerprints(fingerprints)
    }
    binding.verifyViewPager.currentItem = selectedFingerPrint
  }

  private fun initializeViewModel() {
    val recipientId = requireArguments().requireParcelableCompat(RECIPIENT_ID, RecipientId::class.java)
    val localIdentity = requireArguments().requireParcelableCompat(LOCAL_IDENTITY, IdentityKeyParcelable::class.java).get()!!
    val remoteIdentity = requireArguments().requireParcelableCompat(REMOTE_IDENTITY, IdentityKeyParcelable::class.java).get()!!

    viewModel = ViewModelProvider(this, VerifySafetyNumberViewModel.Factory(recipientId, localIdentity, remoteIdentity)).get(VerifySafetyNumberViewModel::class.java)
  }

  override fun onResume() {
    super.onResume()
    setRecipientText(viewModel.recipient.get())
    val selectedSnapshot = selectedFingerPrint
    if (animateSuccessOnDraw) {
      animateSuccessOnDraw = false
      ThreadUtil.postToMain {
        animateSuccess(selectedSnapshot)
      }
      ThreadUtil.postToMain {
        binding.verifyViewPager.currentItem = selectedSnapshot
      }
    } else if (animateFailureOnDraw) {
      animateFailureOnDraw = false
      ThreadUtil.postToMain {
        animateFailure(selectedSnapshot)
      }
      ThreadUtil.postToMain {
        binding.verifyViewPager.currentItem = selectedSnapshot
      }
    }
    ThreadUtil.postToMain { onScrollChanged() }
  }

  override fun onCreateContextMenu(
    menu: ContextMenu,
    view: View,
    menuInfo: ContextMenuInfo?
  ) {
    super.onCreateContextMenu(menu, view, menuInfo)
    val fingerprints = viewModel.getFingerprints().value
    if (!fingerprints.isNullOrEmpty()) {
      val inflater = requireActivity().menuInflater
      inflater.inflate(R.menu.verify_display_fragment_context_menu, menu)
    }
  }

  override fun onContextItemSelected(item: MenuItem): Boolean {
    if (currentFingerprint == null) return super.onContextItemSelected(item)
    return if (item.itemId == R.id.menu_copy) {
      handleCopyToClipboard(currentFingerprint)
      true
    } else if (item.itemId == R.id.menu_compare) {
      handleCompareWithClipboard()
      true
    } else {
      super.onContextItemSelected(item)
    }
  }

  private val currentFingerprint: Fingerprint?
    get() {
      val fingerprints = viewModel.getFingerprints().value ?: return null
      return fingerprints[binding.verifyViewPager.currentItem].fingerprint
    }

  fun setScannedFingerprint(scanned: String) {
    animateCodeChanges = false

    val fingerprints = viewModel.getFingerprints().value
    var haveMatchingVersion = false
    if (fingerprints != null) {
      for (i in fingerprints.indices) {
        try {
          if (fingerprints[i].fingerprint.scannableFingerprint.compareTo(scanned.toByteArray(StandardCharsets.ISO_8859_1))) {
            animateSuccessOnDraw = true
          } else {
            animateFailureOnDraw = true
          }
          haveMatchingVersion = true
          selectedFingerPrint = i
          break
        } catch (e: FingerprintVersionMismatchException) {
          Log.w(TAG, e)
        } catch (e: Exception) {
          Log.w(TAG, e)
          showAlertDialog(R.string.VerifyIdentityActivity_the_scanned_qr_code_is_not_a_correctly_formatted_safety_number)
          animateFailureOnDraw = true
          return
        }
      }
    }
    if (!haveMatchingVersion) {
      showAlertDialog(R.string.VerifyIdentityActivity_your_contact_is_running_a_newer_version_of_Signal)
      animateFailureOnDraw = true
    }
  }

  private fun showAlertDialog(stringResId: Int) {
    MaterialAlertDialogBuilder(requireContext())
      .setMessage(stringResId)
      .setPositiveButton(R.string.VerifyDisplayFragment__scan_result_dialog_ok, null)
      .show()
  }

  private fun getFormattedSafetyNumbers(fingerprint: Fingerprint): String {
    val segments = getSegments(fingerprint)
    val result = StringBuilder()
    for (i in segments.indices) {
      result.append(segments[i])
      if (i != segments.size - 1) {
        if ((i + 1) % 4 == 0) result.append('\n') else result.append(' ')
      }
    }
    return result.toString()
  }

  private fun handleCopyToClipboard(fingerprint: Fingerprint?) {
    Util.writeTextToClipboard(requireContext(), "Safety numbers", getFormattedSafetyNumbers(fingerprint!!))
  }

  private fun handleCompareWithClipboard() {
    val clipboardData = Util.readTextFromClipboard(requireActivity())
    if (clipboardData == null) {
      showAlertDialog(R.string.VerifyIdentityActivity_no_safety_number_to_compare_was_found_in_the_clipboard)
      return
    }
    val numericClipboardData = clipboardData.replace("\\D".toRegex(), "")
    if (TextUtils.isEmpty(numericClipboardData) || numericClipboardData.length != 60) {
      showAlertDialog(R.string.VerifyIdentityActivity_no_safety_number_to_compare_was_found_in_the_clipboard)
      return
    }
    var success = false
    val fingerprints = viewModel.getFingerprints().value
    if (fingerprints != null) {
      for (i in fingerprints.indices) {
        val (_, _, _, _, _, fingerprint1) = fingerprints[i]
        if (fingerprint1.displayableFingerprint.displayText == numericClipboardData) {
          binding.verifyViewPager.currentItem = i
          animateSuccess(i)
          success = true
          break
        }
      }
    }
    if (!success) {
      animateFailure(selectedFingerPrint)
    }
  }

  private fun animateSuccess(position: Int) {
    animateCodeChanges = false

    safetyNumberAdapter.notifyItemChanged(position, true)
  }

  private fun animateFailure(position: Int) {
    animateCodeChanges = false

    safetyNumberAdapter.notifyItemChanged(position, false)
  }

  private fun handleShare(fingerprint: Fingerprint) {
    val shareString = """
        ${getString(R.string.VerifyIdentityActivity_our_signal_safety_number)}
        ${getFormattedSafetyNumbers(fingerprint)}
        
    """.trimIndent()
    val intent = Intent().apply {
      action = Intent.ACTION_SEND
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, shareString)
    }
    try {
      startActivity(Intent.createChooser(intent, getString(R.string.VerifyIdentityActivity_share_safety_number_via)))
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(activity, R.string.VerifyIdentityActivity_no_app_to_share_to, Toast.LENGTH_LONG).show()
    }
  }

  private fun setRecipientText(recipient: Recipient) {
    binding.description.text = getString(R.string.verify_display_fragment__pnp_verify_safety_numbers_explanation_with_s, recipient.getDisplayName(requireContext()))
    binding.description.setLink("https://signal.org/redirect/safety-numbers")
    binding.description.setLinkColor(ContextCompat.getColor(requireContext(), R.color.signal_colorPrimary))
  }

  private fun updateVerifyButton(verified: Boolean, update: Boolean) {
    currentVerifiedState = verified
    if (verified) {
      binding.verifyButton.setText(R.string.verify_display_fragment__clear_verification)
    } else {
      binding.verifyButton.setText(R.string.verify_display_fragment__mark_as_verified)
    }
    if (update) {
      viewModel.updateSafetyNumberVerification(verified)
    }
  }

  override fun onScrollChanged() {
    val fingerprints = viewModel.getFingerprints().value
    if (binding.scrollView.canScrollVertically(-1) && fingerprints != null && fingerprints.size <= 1) {
      if (binding.toolbarShadow.visibility != View.VISIBLE) {
        ViewUtil.fadeIn(binding.toolbarShadow, 250)
      }
    } else {
      if (binding.toolbarShadow.visibility != View.GONE) {
        ViewUtil.fadeOut(binding.toolbarShadow, 250)
      }
    }
    if (binding.scrollView.canScrollVertically(1)) {
      if (binding.verifyIdentityBottomShadow.visibility != View.VISIBLE) {
        ViewUtil.fadeIn(binding.verifyIdentityBottomShadow, 250)
      }
    } else {
      ViewUtil.fadeOut(binding.verifyIdentityBottomShadow, 250)
    }
  }

  internal interface Callback {
    fun onQrCodeContainerClicked()
  }

  private class SafetyNumberQrViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val safetyNumberQrView: SafetyNumberQrView

    init {
      safetyNumberQrView = itemView.findViewById(R.id.safety_qr_view)
    }
  }

  private inner class SafetyNumberAdapter : RecyclerView.Adapter<SafetyNumberQrViewHolder>() {
    private var fingerprints: List<SafetyNumberFingerprint>? = null

    fun setFingerprints(fingerprints: List<SafetyNumberFingerprint>?) {
      if (fingerprints == this.fingerprints) {
        return
      }
      this.fingerprints = fingerprints?.let { ArrayList(it) }
      notifyDataSetChanged()
    }

    init {
      setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SafetyNumberQrViewHolder {
      return SafetyNumberQrViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.safety_number_qr_page_fragment, parent, false))
    }

    override fun onBindViewHolder(holder: SafetyNumberQrViewHolder, position: Int) {
      val (version, _, _, _, _, fingerprint1) = fingerprints!![position]
      holder.safetyNumberQrView.setFingerprintViews(fingerprint1, animateCodeChanges)
      holder.safetyNumberQrView.setSafetyNumberType(version == 2)
      holder.safetyNumberQrView.shareButton.setOnClickListener { v: View? -> handleShare(fingerprints!![position].fingerprint) }
      holder.safetyNumberQrView.qrCodeContainer.setOnClickListener { v: View? -> callback!!.onQrCodeContainerClicked() }
      registerForContextMenu(holder.safetyNumberQrView.numbersContainer)
    }

    override fun onBindViewHolder(holder: SafetyNumberQrViewHolder, position: Int, payloads: List<Any>) {
      super.onBindViewHolder(holder, position, payloads)
      for (payload in payloads) {
        if (payload is Boolean) {
          if (payload) {
            holder.safetyNumberQrView.animateVerifiedSuccess()
          } else {
            holder.safetyNumberQrView.animateVerifiedFailure()
          }
          break
        }
      }
    }

    override fun getItemId(position: Int): Long {
      return fingerprints!![position].version.toLong()
    }

    override fun getItemCount(): Int {
      return if (fingerprints != null) fingerprints!!.size else 0
    }
  }

  companion object {
    private val TAG = Log.tag(VerifyDisplayFragment::class.java)

    private const val RECIPIENT_ID = "recipient_id"
    private const val REMOTE_IDENTITY = "remote_identity"
    private const val LOCAL_IDENTITY = "local_identity"
    private const val LOCAL_NUMBER = "local_number"
    private const val VERIFIED_STATE = "verified_state"

    fun create(
      recipientId: RecipientId,
      remoteIdentity: IdentityKeyParcelable,
      localIdentity: IdentityKeyParcelable,
      localNumber: String,
      verifiedState: Boolean
    ): VerifyDisplayFragment {
      val fragment = VerifyDisplayFragment()
      fragment.arguments = Bundle().apply {
        putParcelable(RECIPIENT_ID, recipientId)
        putParcelable(REMOTE_IDENTITY, remoteIdentity)
        putParcelable(LOCAL_IDENTITY, localIdentity)
        putString(LOCAL_NUMBER, localNumber)
        putBoolean(VERIFIED_STATE, verifiedState)
      }
      return fragment
    }
  }
}
