/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalFoundationApi::class)

package org.thoughtcrime.securesms.contactshare.screens.selectcontact

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.flowOf
import org.signal.contacts.SystemContactsRepository
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.fonts.SignalSymbols
import org.signal.core.ui.permissions.PermissionDeniedSheet
import org.signal.glide.compose.GlideImage
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatar
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatarImage
import org.thoughtcrime.securesms.contacts.index.ContactIndexRecord
import org.thoughtcrime.securesms.contacts.index.ContactIndexType
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.recipients.RecipientId
import org.signal.core.ui.R as CoreUiR

private val ROW_HEIGHT = 64.dp
private val SECTION_HEADER_HEIGHT = 52.dp
private val AVATAR_SIZE = 40.dp
private val ROW_START_PADDING = 24.dp
private val AVATAR_TO_NAME_GAP = 16.dp
private val SIGNAL_BADGE_SIZE = 16.dp
private val SEARCH_CORNER_RADIUS = 32.dp
private val FULL_SCREEN_PROMPT_ICON_SIZE = 72.dp
private val CARD_PROMPT_ICON_SIZE = 64.dp

@Composable
fun SelectContactScreen(
  state: SelectContactState,
  rows: LazyPagingItems<SelectContactRow>,
  onEvent: (SelectContactEvent) -> Unit
) {
  Scaffolds.Default(
    title = stringResource(R.string.SelectContactScreen__select_contact),
    onNavigationClick = { onEvent(SelectContactEvent.BackClicked) },
    navigationIconRes = CoreUiR.drawable.symbol_arrow_start_24,
    navigationContentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description)
  ) { contentPadding ->
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Results for a new query start at the top of the index, so the old scroll offset points at an
    // unrelated part of the list. Tracked rather than keyed on the query alone so that a rotation,
    // which recomposes without the query changing, keeps the position the user was at.
    var lastQuery by rememberSaveable { mutableStateOf(state.query) }

    LaunchedEffect(state.query) {
      if (state.query != lastQuery) {
        lastQuery = state.query
        listState.scrollToItem(0)
      }
    }

    Column(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
      SearchField(
        query = state.query,
        onQueryChange = { onEvent(SelectContactEvent.QueryChanged(it)) },
        onSearch = { keyboardController?.hide() }
      )

      when {
        state.isLoading -> {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator()
          }
        }

        state.showFullScreenPermissionPrompt -> {
          FullScreenPermissionPrompt(
            onAllowClick = { onEvent(SelectContactEvent.AllowContactsAccessClicked) },
            onDismissClick = { onEvent(SelectContactEvent.DismissContactsAccessClicked) }
          )
        }

        else -> {
          ContactList(
            state = state,
            rows = rows,
            listState = listState,
            onEvent = onEvent
          )
        }
      }
    }
  }

  if (state.showPermissionDeniedSheet) {
    PermissionDeniedSheet(
      titleRes = R.string.SelectContactScreen__allow_access_to_contacts,
      subtitleRes = R.string.SelectContactScreen__to_find_people_you_know_on_signal,
      onDismiss = { onEvent(SelectContactEvent.PermissionDeniedSheetDismissed) }
    )
  }
}

@Composable
private fun ContactList(
  state: SelectContactState,
  rows: LazyPagingItems<SelectContactRow>,
  listState: LazyListState,
  onEvent: (SelectContactEvent) -> Unit
) {
  val keyboardController = LocalSoftwareKeyboardController.current

  // Dragging the list is how people ask for the keyboard to get out of the way, and until it does
  // the rows behind it cannot be reached.
  val dismissKeyboardOnDrag = remember(keyboardController) {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y != 0f) {
          keyboardController?.hide()
        }

        return Offset.Zero
      }
    }
  }

  LazyColumn(
    state = listState,
    modifier = Modifier
      .fillMaxSize()
      .nestedScroll(dismissKeyboardOnDrag)
  ) {
    if (state.showSystemPickerButton) {
      item(key = "system-picker-button") {
        OpenSystemContactPickerButton(
          onClick = { onEvent(SelectContactEvent.OpenSystemContactPickerClicked) }
        )
      }
    }

    if (state.showPermissionCard) {
      item(key = "permission-card") {
        PermissionCard(
          onAllowClick = { onEvent(SelectContactEvent.AllowContactsAccessClicked) },
          onDismissClick = { onEvent(SelectContactEvent.DismissContactsAccessClicked) }
        )
      }
    }

    // Paging drives loading off the prefetch distance, so there is no scroll listener here.
    items(
      count = rows.itemCount,
      // Scoped to the query, because a row of a filtered list is not the same list item as the same
      // contact in the unfiltered one. Sharing keys across the two lets the list re-anchor on
      // whichever contact was on screen when the query changed and scroll to wherever it sits in the
      // new results, which undoes the reset that runs when the query changes.
      key = rows.itemKey { row ->
        when (row) {
          is SelectContactRow.Header -> "${state.query}-header-${row.label}"
          is SelectContactRow.Contact -> "${state.query}-contact-${row.contact.position}"
        }
      }
    ) { index ->
      when (val row = rows[index]) {
        is SelectContactRow.Header -> SectionHeader(label = row.label)

        is SelectContactRow.Contact -> {
          ContactRow(
            contact = row.contact,
            onClick = { onEvent(SelectContactEvent.ContactClicked(row.contact)) }
          )
        }

        // A row of the unfiltered list that has not been read yet. Search reports no counts, so it
        // has no placeholders and never lands here.
        null -> Spacer(modifier = Modifier.height(ROW_HEIGHT))
      }
    }

    if (state.showPermissionFooter) {
      item(key = "permission-footer") {
        PermissionDeniedFooter(
          onLearnMoreClick = { onEvent(SelectContactEvent.LearnMoreClicked) }
        )
      }
    }
  }
}

@Composable
private fun SearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  onSearch: () -> Unit
) {
  TextField(
    value = query,
    onValueChange = onQueryChange,
    placeholder = { Text(text = stringResource(R.string.SelectContactScreen__search)) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    trailingIcon = {
      if (query.isNotEmpty()) {
        IconButton(onClick = { onQueryChange("") }) {
          Icon(
            painter = painterResource(CoreUiR.drawable.symbol_x_24),
            contentDescription = stringResource(R.string.SelectContactScreen__clear_search)
          )
        }
      }
    },
    shape = RoundedCornerShape(SEARCH_CORNER_RADIUS),
    colors = TextFieldDefaults.colors(
      focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      focusedIndicatorColor = Color.Transparent,
      unfocusedIndicatorColor = Color.Transparent
    ),
    modifier = Modifier
      .padding(horizontal = 16.dp, vertical = 10.dp)
      .fillMaxWidth()
      .defaultMinSize(minHeight = 44.dp)
  )
}

@Composable
private fun SectionHeader(label: String) {
  Box(
    contentAlignment = Alignment.CenterStart,
    modifier = Modifier
      .fillMaxWidth()
      .height(SECTION_HEADER_HEIGHT)
      .padding(horizontal = ROW_START_PADDING)
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
private fun ContactRow(
  contact: ContactIndexRecord,
  onClick: () -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .height(ROW_HEIGHT)
      .clickable(onClick = onClick)
      .padding(start = ROW_START_PADDING, end = 16.dp)
  ) {
    ContactAvatar(contact = contact)

    Spacer(modifier = Modifier.width(AVATAR_TO_NAME_GAP))

    Text(
      text = contact.displayName,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f, fill = false)
    )

    if (contact.isInAddressBook) {
      Spacer(modifier = Modifier.width(6.dp))

      Icon(
        painter = painterResource(R.drawable.symbol_person_circle_compat_16),
        contentDescription = stringResource(R.string.SelectContactScreen__in_your_contacts),
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(SIGNAL_BADGE_SIZE)
      )
    }
  }
}

/**
 * The address book photo wins over the Signal profile photo, matching the sharing flow's preference
 * for what the user themselves filed the contact under.
 */
@Composable
private fun ContactAvatar(contact: ContactIndexRecord) {
  val modifier = Modifier.size(AVATAR_SIZE)

  when {
    contact.hasPhoto && contact.contactId != null -> {
      if (LocalInspectionMode.current) {
        Image(
          painter = painterResource(R.drawable.ic_avatar_abstract_02),
          contentDescription = null,
          modifier = modifier.clip(CircleShape)
        )
      } else {
        GlideImage(
          model = SystemContactsRepository.photoUriForContact(contact.contactId),
          contentScale = ContentScale.Crop,
          modifier = modifier.clip(CircleShape)
        )
      }
    }

    contact.recipientId != null -> {
      AvatarImage(recipientId = contact.recipientId, modifier = modifier)
    }

    else -> FallbackAvatarImage(fallbackAvatar = contact.fallbackAvatar(), modifier = modifier)
  }
}

/**
 * A company name or bare phone number has no initials worth showing, so it falls back to the person
 * glyph rather than to the first letters of whatever the provider used as a name.
 */
private fun ContactIndexRecord.fallbackAvatar(): FallbackAvatar {
  // No recipient to derive a color from, so address book entries all share the first avatar color.
  return if (!hasPersonalName) {
    FallbackAvatar.Resource.Person(AvatarColor.A100)
  } else {
    FallbackAvatar.forTextOrDefault(displayName, AvatarColor.A100)
  }
}

@Composable
private fun FullScreenPermissionPrompt(
  onAllowClick: () -> Unit,
  onDismissClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 32.dp)
  ) {
    ContactsAccessPromptContent(
      iconSize = FULL_SCREEN_PROMPT_ICON_SIZE,
      iconToTitleGap = 24.dp,
      onAllowClick = onAllowClick,
      onDismissClick = onDismissClick
    )
  }
}

@Composable
private fun PermissionCard(
  onAllowClick: () -> Unit,
  onDismissClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.38f), RoundedCornerShape(18.dp))
      .padding(horizontal = 16.dp, vertical = 24.dp)
  ) {
    ContactsAccessPromptContent(
      iconSize = CARD_PROMPT_ICON_SIZE,
      iconToTitleGap = 16.dp,
      onAllowClick = onAllowClick,
      onDismissClick = onDismissClick
    )
  }
}

@Composable
private fun ContactsAccessPromptContent(
  iconSize: Dp,
  iconToTitleGap: Dp,
  onAllowClick: () -> Unit,
  onDismissClick: () -> Unit
) {
  Icon(
    painter = painterResource(R.drawable.permissions_contact_book),
    contentDescription = null,
    tint = Color.Unspecified,
    modifier = Modifier.size(iconSize)
  )

  Spacer(modifier = Modifier.height(iconToTitleGap))

  Text(
    text = stringResource(R.string.SelectContactScreen__find_people_you_know_on_signal),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurface,
    textAlign = TextAlign.Center
  )

  Spacer(modifier = Modifier.height(4.dp))

  Text(
    text = stringResource(R.string.SelectContactScreen__allow_access_to_your_contacts),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center
  )

  Spacer(modifier = Modifier.height(16.dp))

  Row(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
  ) {
    Buttons.MediumTonal(onClick = onDismissClick, modifier = Modifier.weight(1f)) {
      Text(text = stringResource(R.string.SelectContactScreen__no_thanks))
    }

    Buttons.MediumTonal(onClick = onAllowClick, modifier = Modifier.weight(1f)) {
      Text(text = stringResource(R.string.SelectContactScreen__allow_access))
    }
  }
}

@Composable
private fun OpenSystemContactPickerButton(onClick: () -> Unit) {
  Buttons.MediumTonal(
    onClick = onClick,
    colors = ButtonDefaults.filledTonalButtonColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ),
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
  ) {
    Text(
      text = SignalSymbols.signalSymbolText(
        text = stringResource(R.string.SelectContactScreen__open_phone_contacts),
        glyphStart = SignalSymbols.Glyph.PERSON,
        glyphStartWeight = SignalSymbols.Weight.BOLD
      )
    )
  }
}

@Composable
private fun PermissionDeniedFooter(onLearnMoreClick: () -> Unit) {
  val learnMore = stringResource(R.string.SelectContactScreen__learn_more)
  val fullText = stringResource(R.string.SelectContactScreen__to_see_your_phone_contacts_here, learnMore)
  val linkColor = MaterialTheme.colorScheme.onSurface

  val text = remember(fullText, learnMore, linkColor, onLearnMoreClick) {
    val linkStart = fullText.lastIndexOf(learnMore)

    buildAnnotatedString {
      if (linkStart < 0) {
        append(fullText)
        return@buildAnnotatedString
      }

      append(fullText.take(linkStart))

      withLink(LinkAnnotation.Clickable(tag = "learn-more") { onLearnMoreClick() }) {
        withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)) {
          append(learnMore)
        }
      }

      append(fullText.substring(linkStart + learnMore.length))
    }
  }

  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
  )
}

@DayNightPreviews
@Composable
private fun SelectContactScreenPreview() {
  Previews.Preview {
    SelectContactScreen(
      state = SelectContactState(isLoading = false, indexCount = PREVIEW_ROWS.size),
      rows = previewRows(PREVIEW_ROWS),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun SelectContactScreenPermissionCardPreview() {
  Previews.Preview {
    SelectContactScreen(
      state = SelectContactState(
        isLoading = false,
        indexCount = 4,
        contactsPermission = SelectContactState.ContactsPermissionState.DENIED
      ),
      rows = previewRows(PREVIEW_ROWS.take(4)),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun SelectContactScreenNoPermissionPreview() {
  Previews.Preview {
    SelectContactScreen(
      state = SelectContactState(
        isLoading = false,
        indexCount = 0,
        contactsPermission = SelectContactState.ContactsPermissionState.DENIED
      ),
      rows = previewRows(emptyList()),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun SelectContactScreenDismissedPreview() {
  Previews.Preview {
    SelectContactScreen(
      state = SelectContactState(
        isLoading = false,
        indexCount = PREVIEW_ROWS.size,
        contactsPermission = SelectContactState.ContactsPermissionState.DISMISSED
      ),
      rows = previewRows(PREVIEW_ROWS),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun SelectContactScreenPermanentlyDeniedPreview() {
  Previews.Preview {
    SelectContactScreen(
      state = SelectContactState(
        isLoading = false,
        indexCount = PREVIEW_ROWS.size,
        contactsPermission = SelectContactState.ContactsPermissionState.PERMANENTLY_DENIED
      ),
      rows = previewRows(PREVIEW_ROWS),
      onEvent = {}
    )
  }
}

private fun previewContact(
  position: Long,
  name: String,
  type: ContactIndexType = ContactIndexType.SYSTEM_ONLY,
  hasPersonalName: Boolean = true
): ContactIndexRecord {
  return ContactIndexRecord(
    position = position,
    type = type,
    section = name.take(1),
    displayName = name,
    recipientId = if (type != ContactIndexType.SYSTEM_ONLY) RecipientId.from(position) else null,
    lookupKey = if (type != ContactIndexType.SIGNAL_ONLY) "lookup-$position" else null,
    contactId = null,
    hasPersonalName = hasPersonalName,
    hasPhoto = false
  )
}

private val PREVIEW_ROWS = listOf(
  SelectContactRow.Header("A"),
  SelectContactRow.Contact(previewContact(1, "Andrew Bell")),
  SelectContactRow.Contact(previewContact(2, "Abby Franklin", ContactIndexType.BOTH)),
  SelectContactRow.Contact(previewContact(3, "Anna Morris", ContactIndexType.SIGNAL_ONLY)),
  SelectContactRow.Header("C"),
  SelectContactRow.Contact(previewContact(4, "Casey Fields", ContactIndexType.BOTH)),
  SelectContactRow.Header("#"),
  SelectContactRow.Contact(previewContact(5, "Pacific Plumbing", hasPersonalName = false)),
  SelectContactRow.Contact(previewContact(6, "+1 555-123-4567", hasPersonalName = false))
)

@Composable
private fun previewRows(rows: List<SelectContactRow>): LazyPagingItems<SelectContactRow> {
  return remember(rows) { flowOf(PagingData.from(rows)) }.collectAsLazyPagingItems()
}
