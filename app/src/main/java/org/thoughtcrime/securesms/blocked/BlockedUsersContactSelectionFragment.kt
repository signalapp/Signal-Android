/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.blocked

import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.TextFields
import org.thoughtcrime.securesms.ContactSelectionListFragment
import org.thoughtcrime.securesms.ContactSelectionListFragment.OnContactSelectedListener
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.contacts.ContactSelectionDisplayMode
import org.thoughtcrime.securesms.contacts.paged.ChatType
import org.thoughtcrime.securesms.contacts.selection.ContactSelectionArguments
import org.thoughtcrime.securesms.recipients.Recipient.Companion.resolved
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.ViewModelFactory
import java.util.Optional
import java.util.function.Consumer
import kotlin.jvm.optionals.getOrNull

class BlockedUsersContactSelectionFragment : ComposeFragment(), OnContactSelectedListener {
  private val viewModel: BlockedUsersViewModel by activityViewModels(
    factoryProducer = ViewModelFactory.factoryProducer {
      BlockedUsersViewModel(BlockedUsersRepository(requireContext()))
    }
  )

  private val contactSelectionListArguments = ContactSelectionArguments(
    isRefreshable = false,
    selectionLimits = null,
    displaySelectionCount = false,
    displayMode =
    ContactSelectionDisplayMode.FLAG_PUSH or
      ContactSelectionDisplayMode.FLAG_SMS or
      ContactSelectionDisplayMode.FLAG_ACTIVE_GROUPS or
      ContactSelectionDisplayMode.FLAG_INACTIVE_GROUPS or
      ContactSelectionDisplayMode.FLAG_BLOCK
  ).toArgumentBundle()

  @Composable
  override fun FragmentContent() {
    val contactSelectionFragment = remember { ContactSelectionListFragment(this).apply{arguments = contactSelectionListArguments} }

    Scaffolds.Settings(
      title = stringResource(R.string.BlockedUsersActivity__blocked_users),
      navigationIconPainter = painterResource(R.drawable.symbol_arrow_left_24),
      onNavigationClick = { requireActivity().onNavigateUp() }
    ) { paddingValues ->
      Column(
        Modifier
          .padding(paddingValues)
          .fillMaxSize()){
        ContactSelectionTextBox(contactSelectionFragment)
        ContactSelection(contactSelectionFragment)
      }
    }
  }

  @Composable
  fun ContactSelectionTextBox(fragment: ContactSelectionListFragment, modifier: Modifier = Modifier){
    var filterText by remember { mutableStateOf("") }
    var keyboardType by remember{ mutableStateOf(KeyboardType.Text) }

    TextFields.TextField(
      value = filterText,
      onValueChange = { newValue :String ->
        filterText = newValue
        fragment.setQueryFilter(newValue)
      },
      modifier = modifier
        .fillMaxWidth()
        .padding( horizontal = dimensionResource(R.dimen.dsl_settings_gutter)),
      placeholder = {
        Text(text = stringResource(R.string.BlockedUsersActivity__add_blocked_user))
      },
      singleLine = true,
      shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_textfield_corner_radius)),
      colors = TextFieldDefaults.colors(
        unfocusedIndicatorColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
      ),
      keyboardOptions = KeyboardOptions(keyboardType = keyboardType,),
      trailingIcon = {
        KeyboardButton(
          onClick = {
            keyboardType =
              if(keyboardType == KeyboardType.Text) KeyboardType.Phone
              else KeyboardType.Text
          },
          contentDescription =
            if(keyboardType == KeyboardType.Text) stringResource( R.string.contact_filter_toolbar__show_keyboard_description)
            else stringResource( R.string.contact_filter_toolbar__show_dial_pad_description),
          icon =
            if(keyboardType == KeyboardType.Text) R.drawable.ic_number_pad_conversation_filter_24
            else R.drawable.ic_keyboard_24
        )
      },
    )

  }

  @Composable
  fun KeyboardButton(onClick: () -> Unit,
                     contentDescription: String,
                     icon : Int
  ) {
      IconButton(
        onClick = onClick
      ) {
        Icon(
          imageVector = ImageVector.vectorResource(icon),
          contentDescription = contentDescription,
        )
      }
  }

  @Composable
  fun ContactSelection(contactSelectionListFragment: ContactSelectionListFragment){
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { context ->
        FrameLayout(context).apply { id = View.generateViewId() }
      },
      update = { frameLayout ->
        val fragmentManager = requireActivity().supportFragmentManager
        if (fragmentManager.findFragmentById(frameLayout.id) != contactSelectionListFragment) {
          fragmentManager.beginTransaction()
            .replace(frameLayout.id, contactSelectionListFragment)
            .commit()
        }
      }
    )
  }

  override fun onBeforeContactSelected(isFromUnknownSearchKey: Boolean, recipientId: Optional<RecipientId>, number: String?, chatType: Optional<ChatType>, callback: Consumer<Boolean>) {
    val nullableRecipientId = recipientId.getOrNull()
    val displayName = nullableRecipientId?.let { resolved(it).getDisplayName(requireContext()) } ?: number

    val confirmationDialog = MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.BlockedUsersActivity__block_user)
      .setMessage(getString(R.string.BlockedUserActivity__s_will_not_be_able_to, displayName))
      .setPositiveButton(R.string.BlockedUsersActivity__block) { dialog, _ ->
        nullableRecipientId?.let { viewModel.block(it) } ?: viewModel.createAndBlock(number!!)
        dialog.dismiss()
        requireActivity().onNavigateUp()
      }
      .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
      .setCancelable(true)
      .create()
      .apply {
        setOnShowListener { _ ->
          getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.RED)
        }
      }

    confirmationDialog.show()
    callback.accept(false)
  }

  override fun onContactDeselected(recipientId: Optional<RecipientId>, number: String?, chatType: Optional<ChatType>) {
    //Do Nothing
  }

  override fun onSelectionChanged() {
    //Do Nothing
  }
}