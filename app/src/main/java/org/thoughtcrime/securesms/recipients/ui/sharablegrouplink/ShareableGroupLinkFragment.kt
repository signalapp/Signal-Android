package org.thoughtcrime.securesms.recipients.ui.sharablegrouplink

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.v2.GroupLinkUrlAndStatus
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog

/**
 * Fragment providing user options to manage group links.
 */
class ShareableGroupLinkFragment : DSLSettingsFragment(
  titleId = R.string.ShareableGroupLinkDialogFragment__group_link
) {

  private var busyDialog: SimpleProgressDialog.DismissibleDialog? = null

  private val groupId: GroupId.V2
    get() = GroupId.parseOrThrow(ShareableGroupLinkFragmentArgs.fromBundle(requireArguments()).groupId).requireV2()

  private val viewModel: ShareableGroupLinkViewModel by viewModels(
    factoryProducer = {
      val repository = ShareableGroupLinkRepository(requireContext(), groupId)

      ShareableGroupLinkViewModel.Factory(groupId, repository)
    }
  )

  override fun bindAdapter(adapter: MappingAdapter) {
    LiveDataUtil.combineLatest(viewModel.groupLink, viewModel.canEdit) { groupLink, canEdit ->
      Pair(groupLink, canEdit)
    }.observe(viewLifecycleOwner) { (groupLink, canEdit) ->
      adapter.submitList(getConfiguration(groupLink, canEdit).toMappingModelList())
    }

    viewModel.toasts.observe(viewLifecycleOwner, this::toast)

    viewModel.busy.observe(
      viewLifecycleOwner,
      { busy ->
        if (busy) {
          if (busyDialog == null) {
            busyDialog = SimpleProgressDialog.showDelayed(requireContext())
          }
        } else {
          busyDialog?.dismiss()
          busyDialog = null
        }
      }
    )
  }

  private fun toast(@StringRes message: Int) {
    Toast.makeText(requireContext(), getString(message), Toast.LENGTH_SHORT).show()
  }

  private fun getConfiguration(groupLink: GroupLinkUrlAndStatus, canEdit: Boolean): DSLConfiguration {
    return configure {
      switchPref(
        title = DSLSettingsText.from(R.string.ShareableGroupLinkDialogFragment__group_link),
        summary = if (groupLink.isEnabled) DSLSettingsText.from(formatForFullWidthWrapping(groupLink.url)) else null,
        isChecked = groupLink.isEnabled,
        isEnabled = canEdit,
        onClick = {
          viewModel.onToggleGroupLink()
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.ShareableGroupLinkDialogFragment__share),
        icon = DSLSettingsIcon.from(R.drawable.ic_share_24_tinted),
        isEnabled = groupLink.isEnabled,
        onClick = {
          GroupLinkBottomSheetDialogFragment.show(childFragmentManager, groupId)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.ShareableGroupLinkDialogFragment__reset_link),
        icon = DSLSettingsIcon.from(R.drawable.ic_reset_24_tinted),
        isEnabled = groupLink.isEnabled && canEdit,
        onClick = {
          onResetGroupLink()
        }
      )

      dividerPref()

      switchPref(
        title = DSLSettingsText.from(R.string.ShareableGroupLinkDialogFragment__require_admin_approval),
        summary = DSLSettingsText.from(R.string.ShareableGroupLinkDialogFragment__require_an_admin_to_approve_new_members_joining_via_the_group_link),
        isEnabled = groupLink.isEnabled && canEdit,
        isChecked = groupLink.isRequiresApproval,
        onClick = {
          viewModel.onToggleApproveMembers()
        }
      )
    }
  }

  private fun onResetGroupLink() {
    MaterialAlertDialogBuilder(requireContext())
      .setMessage(R.string.ShareableGroupLinkDialogFragment__are_you_sure_you_want_to_reset_the_group_link)
      .setPositiveButton(R.string.ShareableGroupLinkDialogFragment__reset_link) { _, _ -> viewModel.onResetLink() }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  /**
   * Inserts zero width space characters between each character in the original ensuring it takes
   * the full width of the TextView.
   */
  private fun formatForFullWidthWrapping(url: String): CharSequence {
    val chars = CharArray(url.length * 2)
    for (i in url.indices) {
      chars[i * 2] = url[i]
      chars[i * 2 + 1] = '\u200B'
    }

    return String(chars)
  }
}
