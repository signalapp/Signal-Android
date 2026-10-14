/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.paging.compose.collectAsLazyPagingItems
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.LocalChatColorProvider
import org.signal.core.ui.navigation.ResultEffect
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.core.ui.navigation.TransitionSpecs
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.index.ContactIndexRepository
import org.thoughtcrime.securesms.contactshare.screens.editname.ContactNameParts
import org.thoughtcrime.securesms.contactshare.screens.editname.EditContactNameResult
import org.thoughtcrime.securesms.contactshare.screens.editname.EditContactNameScreen
import org.thoughtcrime.securesms.contactshare.screens.editname.EditContactNameViewModel
import org.thoughtcrime.securesms.contactshare.screens.selectcontact.ContactIndexSource
import org.thoughtcrime.securesms.contactshare.screens.selectcontact.SelectContactAction
import org.thoughtcrime.securesms.contactshare.screens.selectcontact.SelectContactEvent
import org.thoughtcrime.securesms.contactshare.screens.selectcontact.SelectContactScreen
import org.thoughtcrime.securesms.contactshare.screens.selectcontact.SelectContactViewModel
import org.thoughtcrime.securesms.contactshare.screens.share.ShareContactAction
import org.thoughtcrime.securesms.contactshare.screens.share.ShareContactEvent
import org.thoughtcrime.securesms.contactshare.screens.share.ShareContactRepository
import org.thoughtcrime.securesms.contactshare.screens.share.ShareContactScreen
import org.thoughtcrime.securesms.contactshare.screens.share.ShareContactViewModel
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.rememberRecipientField

private const val EDITED_NAME_RESULT = "edited_name_result"

/**
 * Contact share flow, from picking a contact through to editing its name.
 *
 * @param recipientId the conversation being sent to, not the contact being shared.
 */
@Composable
fun ContactShareNavHost(
  startRoute: ContactShareRoute,
  recipientId: RecipientId,
  onSend: (Contact) -> Unit
) {
  val backStack = rememberNavBackStack(startRoute)
  val resultBus = remember { ResultEventBus() }
  val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
  val navigateBack: (NavKey) -> Unit = { from -> backStack.goBackFrom(from) { backDispatcher?.onBackPressed() } }

  CompositionLocalProvider(
    LocalChatColorProvider provides { id ->
      rememberRecipientField(RecipientId.from(id)) {
        Color(chatColors.asSingleColor())
      }
    }
  ) {
    NavDisplay(
      backStack = backStack,
      onBack = { navigateBack(backStack.last()) },
      entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator()
      ),
      transitionSpec = { TransitionSpecs.HorizontalSlide.transitionSpec },
      popTransitionSpec = { TransitionSpecs.HorizontalSlide.popTransitionSpec },
      predictivePopTransitionSpec = { TransitionSpecs.HorizontalSlide.predictivePopTransitionSpec },
      entryProvider = entryProvider {
        navigationEntries(
          backStack = backStack,
          resultBus = resultBus,
          navigateBack = navigateBack,
          recipientId = recipientId,
          onSend = onSend
        )
      }
    )
  }
}

private fun EntryProviderScope<NavKey>.navigationEntries(
  backStack: NavBackStack<NavKey>,
  resultBus: ResultEventBus,
  navigateBack: (NavKey) -> Unit,
  recipientId: RecipientId,
  onSend: (Contact) -> Unit
) {
  entry<ContactShareRoute.SelectContact> { key ->
    val context = LocalContext.current
    val viewModel: SelectContactViewModel = viewModel {
      SelectContactViewModel(
        source = ContactIndexSource(ContactIndexRepository()),
        savedState = createSavedStateHandle()
      )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = viewModel.rows.collectAsLazyPagingItems()

    val systemContactPicker = rememberLauncherForActivityResult(PickPhoneNumber) { uri: Uri? ->
      viewModel.onEvent(SelectContactEvent.SystemContactPicked(uri))
    }

    CollectActions(viewModel.actions) { action ->
      when (action) {
        SelectContactAction.Exit -> navigateBack(key)
        SelectContactAction.CouldNotOpenContact -> Toast.makeText(context, R.string.SelectContactScreen__couldnt_open_contact, Toast.LENGTH_SHORT).show()
        is SelectContactAction.ContactResolved -> backStack.goTo(key, ContactShareRoute.Share(action.source))
        SelectContactAction.LaunchSystemContactPicker -> systemContactPicker.launch(Unit)
      }
    }

    viewModel.contactsPermission.Content()

    SelectContactScreen(
      state = state,
      rows = rows,
      onEvent = viewModel::onEvent
    )
  }

  entry<ContactShareRoute.Share> { key ->
    val context = LocalContext.current
    val viewModel: ShareContactViewModel = viewModel {
      ShareContactViewModel(
        contactSource = key.source,
        recipientId = recipientId,
        repository = ShareContactRepository(),
        savedState = createSavedStateHandle()
      )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    ResultEffect<ContactNameParts>(resultBus, EDITED_NAME_RESULT) { parts ->
      viewModel.onEvent(ShareContactEvent.NameEdited(parts))
    }

    CollectActions(viewModel.actions) { action ->
      when (action) {
        ShareContactAction.Exit -> navigateBack(key)

        ShareContactAction.InvalidContact -> {
          Toast.makeText(context, R.string.ContactShareEditActivity_invalid_contact, Toast.LENGTH_SHORT).show()
          navigateBack(key)
        }

        is ShareContactAction.EditName -> backStack.goTo(key, ContactShareRoute.EditName(action.parts))
        is ShareContactAction.Send -> onSend(action.contact)
      }
    }

    ShareContactScreen(state = state, onEvent = viewModel::onEvent)
  }

  entry<ContactShareRoute.EditName> { key ->
    val viewModel: EditContactNameViewModel = viewModel { EditContactNameViewModel(key.parts, createSavedStateHandle()) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.results) { result ->
      if (result is EditContactNameResult.Saved) {
        resultBus.sendResult(EDITED_NAME_RESULT, result.parts)
      }
      navigateBack(key)
    }

    EditContactNameScreen(state = state, onEvent = viewModel::onEvent)
  }
}

internal fun NavBackStack<NavKey>.goTo(from: NavKey, route: NavKey) {
  if (lastOrNull() == from) {
    add(route)
  }
}

internal fun NavBackStack<NavKey>.goBackFrom(from: NavKey, onExit: () -> Unit) {
  when {
    lastOrNull() != from -> Unit
    size > 1 -> removeAt(lastIndex)
    else -> onExit()
  }
}

/** Picks limited contact data, since we do not have contacts permission. */
private object PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {
  override fun createIntent(context: Context, input: Unit): Intent {
    return Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
  }

  override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
    return if (resultCode == Activity.RESULT_OK) intent?.data else null
  }
}
