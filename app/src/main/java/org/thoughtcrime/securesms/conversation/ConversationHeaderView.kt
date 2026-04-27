/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.BidiUtil
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImageLarge
import org.thoughtcrime.securesms.conversation.colors.AvatarGradientColors
import org.thoughtcrime.securesms.conversation.v2.data.AvatarDownloadStateCache
import org.thoughtcrime.securesms.fonts.SignalSymbols
import org.thoughtcrime.securesms.fonts.SignalSymbols.buildSignalSymbolAnnotatedString
import org.thoughtcrime.securesms.fonts.SignalSymbols.signalSymbolText
import org.thoughtcrime.securesms.groups.v2.GroupDescriptionUtil
import org.thoughtcrime.securesms.messagerequests.GroupInfo
import org.thoughtcrime.securesms.messagerequests.MessageRequestRecipientInfo
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.LongClickMovementMethod
import org.thoughtcrime.securesms.util.SignalE164Util
import org.signal.core.ui.R as CoreUiR

private val AvatarSize = 74.dp
private val AvatarOverlapAbove = 16.dp
private val AvatarOverlapBelow = AvatarSize - AvatarOverlapAbove
private val BorderShape = RoundedCornerShape(40.dp)

class ConversationHeaderView : AbstractComposeView {
  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  var callbacks: ConversationHeaderCallbacks = ConversationHeaderCallbacks.Empty
  var recipientInfo: MessageRequestRecipientInfo? by mutableStateOf(null, policy = RecipientInfoContentPolicy)
  var avatarDownloadState: AvatarDownloadStateCache.DownloadState by mutableStateOf(AvatarDownloadStateCache.DownloadState.NONE)

  @Composable
  override fun Content() {
    val info = recipientInfo ?: return
    val recipient = info.recipient
    val groupInfo = info.groupInfo
    val isSelf = recipient.isSelf
    val isReleaseNotes = recipient.isReleaseNotes
    val isOfficialAccount = recipient.showVerified

    val showUnverifiedName = if (recipient.isGroup) {
      !info.groupInfo.nameVerified
    } else if (!isOfficialAccount) {
      recipient.nickname.isEmpty && !recipient.isSystemContact
    } else {
      false
    }

    val displayName = if (isSelf) BidiUtil.isolateBidi(context.getString(R.string.note_to_self)) else recipient.getDisplayName(context)
    val phoneNumber = if (!recipient.isGroup && !isOfficialAccount && recipient.shouldShowE164) {
      recipient.e164.map { SignalE164Util.prettyPrint(it) }.orElse(null)?.takeIf { it != displayName }
    } else {
      null
    }

    SignalTheme {
      ConversationHeaderContent(
        recipientId = recipient.id,
        displayName = displayName,
        showVerified = isOfficialAccount,
        isSystemContact = recipient.isSystemContact,
        showChevron = recipient.isIndividual && !isOfficialAccount,
        isSelf = isSelf,
        isReleaseNotes = isReleaseNotes,
        badge = if (!isOfficialAccount) recipient.featuredBadge else null,
        showUnverifiedName = showUnverifiedName,
        isGroup = recipient.isGroup,
        hasWallpaper = recipient.hasWallpaper,
        phoneNumber = phoneNumber,
        groupInfo = if (recipient.isGroup) groupInfo else null,
        groupDescription = if (recipient.isGroup) groupInfo.description else null,
        linkifyGroupDescription = info.messageRequestState?.isAccepted == true,
        sharedGroups = info.sharedGroups,
        showSafetyTips = info.messageRequestState?.isAccepted == false,
        avatarDownloadState = avatarDownloadState,
        shouldBlurAvatar = recipient.shouldBlurAvatar && recipient.hasAvatar,
        callbacks = callbacks
      )
    }
  }
}

interface ConversationHeaderCallbacks {
  fun onSafetyTipsClicked(forGroup: Boolean) = Unit
  fun onUnverifiedNameClicked(forGroup: Boolean) = Unit
  fun onTitleClicked() = Unit
  fun onGroupSettingsClicked() = Unit
  fun onShowGroupDescriptionClicked(groupName: String, description: String, linkifyWebLinks: Boolean) = Unit
  fun onAvatarTapToViewClicked() = Unit

  companion object Empty : ConversationHeaderCallbacks
}

private object RecipientInfoContentPolicy : SnapshotMutationPolicy<MessageRequestRecipientInfo?> {
  override fun equivalent(a: MessageRequestRecipientInfo?, b: MessageRequestRecipientInfo?): Boolean {
    if (a === b) return true
    if (a == null || b == null) return false
    return a.recipient.hasSameContent(b.recipient) &&
      a.groupInfo == b.groupInfo &&
      a.sharedGroups == b.sharedGroups &&
      a.messageRequestState == b.messageRequestState
  }
}

@Composable
private fun ConversationHeaderContent(
  recipientId: RecipientId,
  displayName: String,
  showVerified: Boolean = false,
  isSystemContact: Boolean = false,
  showChevron: Boolean = false,
  isSelf: Boolean = false,
  isReleaseNotes: Boolean = false,
  badge: Badge?,
  showUnverifiedName: Boolean,
  isGroup: Boolean,
  hasWallpaper: Boolean = false,
  phoneNumber: String? = null,
  groupInfo: GroupInfo? = null,
  groupDescription: String? = null,
  linkifyGroupDescription: Boolean = false,
  sharedGroups: List<String> = emptyList(),
  showSafetyTips: Boolean = false,
  avatarDownloadState: AvatarDownloadStateCache.DownloadState,
  shouldBlurAvatar: Boolean = false,
  callbacks: ConversationHeaderCallbacks = ConversationHeaderCallbacks.Empty
) {
  Box(
    modifier = Modifier.fillMaxWidth(),
    contentAlignment = Alignment.TopCenter
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .padding(top = AvatarOverlapAbove)
        .width(277.dp)
        .then(
          if (isReleaseNotes) {
            Modifier
              .clip(BorderShape)
              .background(colorResource(R.color.release_notes_header_background))
              .border(width = 2.dp, color = colorResource(R.color.release_notes_header_border), shape = BorderShape)
          } else if (hasWallpaper) {
            Modifier
              .clip(BorderShape)
              .background(if (isSystemInDarkTheme()) SignalTheme.colors.colorTransparentInverse5 else SignalTheme.colors.colorTransparent5)
          } else {
            Modifier.border(width = 2.5.dp, color = SignalTheme.colors.colorSurface3, shape = BorderShape)
          }
        )
        .padding(top = AvatarOverlapBelow + 12.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
    ) {
      HeadlineDisplayName(
        displayName = displayName,
        showVerified = showVerified,
        isSystemContact = isSystemContact,
        showChevron = showChevron,
        modifier = Modifier.clickable { callbacks.onTitleClicked() }
      )

      if (isSelf) {
        OfficialChatPill()
        Text(
          text = stringResource(R.string.ConversationFragment__you_can_add_notes_for_yourself_in_this_conversation),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 8.dp)
        )
      }

      if (isReleaseNotes) {
        OfficialChatPill()
        Text(
          text = stringResource(R.string.ConversationFragment_release_notes_description),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 8.dp)
        )
      }

      if (showUnverifiedName) {
        UnverifiedNamePill(
          onClick = { callbacks.onUnverifiedNameClicked(isGroup) },
          modifier = Modifier.padding(top = 8.dp)
        )
      }

      if (phoneNumber != null) {
        Text(
          text = signalSymbolText(
            text = phoneNumber,
            glyphStart = SignalSymbols.Glyph.PHONE
          ),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
      }

      if (!groupDescription.isNullOrEmpty()) {
        GroupDescription(
          description = groupDescription,
          linkify = linkifyGroupDescription,
          onMoreClicked = { callbacks.onShowGroupDescriptionClicked(displayName.toString(), groupDescription, linkifyGroupDescription) },
          modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
      }

      if (groupInfo != null) {
        GroupMemberSubtitle(
          groupInfo = groupInfo,
          onGroupSettingsClicked = { callbacks.onGroupSettingsClicked() },
          modifier = Modifier.padding(top = 8.dp)
        )
      }

      if (!isSelf && !isReleaseNotes && (sharedGroups.isNotEmpty() || !isGroup)) {
        SharedGroupsDescription(
          sharedGroups = sharedGroups,
          modifier = Modifier.padding(top = 8.dp)
        )
      }

      if (showSafetyTips) {
        Buttons.Small(
          onClick = { callbacks.onSafetyTipsClicked(isGroup) },
          modifier = Modifier.padding(top = 12.dp)
        ) {
          Text(text = stringResource(R.string.ConversationFragment_safety_tips))
        }
      }
    }

    AvatarWithBadge(
      recipientId = recipientId,
      badge = badge,
      useProfile = !isSelf,
      avatarDownloadState = avatarDownloadState,
      shouldBlurAvatar = shouldBlurAvatar,
      onTapToView = callbacks::onAvatarTapToViewClicked
    )
  }
}

@Composable
private fun AvatarWithBadge(
  recipientId: RecipientId,
  badge: Badge?,
  useProfile: Boolean = true,
  avatarDownloadState: AvatarDownloadStateCache.DownloadState,
  shouldBlurAvatar: Boolean = false,
  onTapToView: () -> Unit = {}
) {
  val showBlur = shouldBlurAvatar && avatarDownloadState != AvatarDownloadStateCache.DownloadState.IN_PROGRESS
  val showProgress = avatarDownloadState == AvatarDownloadStateCache.DownloadState.IN_PROGRESS
  val showGradient = showBlur || showProgress || avatarDownloadState == AvatarDownloadStateCache.DownloadState.FAILED

  Box(contentAlignment = Alignment.Center) {
    Crossfade(
      targetState = showGradient,
      animationSpec = tween(durationMillis = 220),
      label = "avatar-crossfade"
    ) { gradient ->
      if (gradient) {
        AndroidView(
          factory = { context ->
            ImageView(context).apply {
              scaleType = ImageView.ScaleType.CENTER_CROP
            }
          },
          update = { view ->
            view.setImageDrawable(AvatarGradientColors.getGradientDrawable(Recipient.resolved(recipientId)))
          },
          modifier = Modifier
            .size(AvatarSize)
            .clip(CircleShape)
        )
      } else {
        AvatarImage(
          recipientId = recipientId,
          useProfile = useProfile,
          modifier = Modifier.size(AvatarSize)
        )
      }
    }

    AnimatedVisibility(
      visible = showProgress,
      enter = fadeIn(tween(durationMillis = 220)),
      exit = fadeOut(tween(durationMillis = 220))
    ) {
      var showSpinner by remember { mutableStateOf(false) }

      LaunchedEffect(Unit) {
        delay(800)
        showSpinner = AvatarDownloadStateCache.getDownloadState(recipientId) == AvatarDownloadStateCache.DownloadState.IN_PROGRESS
      }

      if (showSpinner) {
        CircularProgressIndicator(
          strokeWidth = 3.dp,
          color = Color.White,
          modifier = Modifier.size(36.dp)
        )
      }
    }

    AnimatedVisibility(
      visible = showBlur,
      enter = fadeIn(tween(durationMillis = 220)),
      exit = fadeOut(tween(durationMillis = 220))
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
          .size(AvatarSize)
          .clip(CircleShape)
          .clickable(onClick = onTapToView)
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_tap_outline_24),
          contentDescription = null,
          tint = Color.White
        )
        Spacer(Modifier.size(4.dp))
        Text(
          text = stringResource(R.string.MessageRequestProfileView_view),
          style = MaterialTheme.typography.bodySmall,
          color = Color.White
        )
      }
    }

    if (badge != null) {
      BadgeImageLarge(
        badge = badge,
        modifier = Modifier
          .size(36.dp)
          .align(Alignment.BottomEnd)
      )
    }
  }
}

@Composable
private fun UnverifiedNamePill(
  onClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  Text(
    text = signalSymbolText(
      text = stringResource(R.string.ConversationFragment_name_not_verified),
      glyphStart = SignalSymbols.Glyph.PERSON_QUESTION,
      glyphStartWeight = SignalSymbols.Weight.BOLD,
      glyphStartSize = 14.sp
    ),
    style = MaterialTheme.typography.bodyMedium,
    fontWeight = FontWeight.Medium,
    color = SignalTheme.colors.colorOnWarning,
    modifier = modifier
      .clip(RoundedCornerShape(26.dp))
      .clickable(onClick = onClick)
      .background(SignalTheme.colors.colorWarning)
      .padding(horizontal = 12.dp, vertical = 4.dp)
  )
}

@Composable
private fun SharedGroupsDescription(
  sharedGroups: List<String>,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val description = when (sharedGroups.size) {
    0 -> stringResource(R.string.ConversationUpdateItem_no_groups_in_common_review_requests_carefully)
    1 -> stringResource(R.string.MessageRequestProfileView_member_of_one_group, sharedGroups[0])
    2 -> stringResource(R.string.MessageRequestProfileView_member_of_two_groups, sharedGroups[0], sharedGroups[1])
    else -> {
      val others = sharedGroups.size - 2
      stringResource(
        R.string.MessageRequestProfileView_member_of_many_groups,
        sharedGroups[0],
        sharedGroups[1],
        context.resources.getQuantityString(R.plurals.MessageRequestProfileView_member_of_d_additional_groups, others, others)
      )
    }
  }

  Text(
    text = signalSymbolText(
      text = description,
      glyphStart = SignalSymbols.Glyph.GROUP
    ),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurface,
    textAlign = TextAlign.Center,
    modifier = modifier
  )
}

@Composable
private fun GroupMemberSubtitle(
  groupInfo: GroupInfo,
  onGroupSettingsClicked: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val memberCount = groupInfo.fullMemberCount

  val styledText = if (groupInfo.isMember) {
    val names = groupInfo.membersPreview.map { it.getDisplayName(context) }
    val othersCount = memberCount - 3
    val othersText = if (othersCount > 0) pluralStringResource(R.plurals.MessageRequestProfileView_other_members, othersCount, othersCount) else null

    val fullText = when (names.size) {
      0 -> stringResource(R.string.MessageRequestProfileView_group_members_zero)
      1 -> stringResource(R.string.MessageRequestProfileView_group_members_one_and_you, names[0])
      2 -> stringResource(R.string.MessageRequestProfileView_group_members_two_and_you, names[0], names[1])
      else -> stringResource(R.string.MessageRequestProfileView_group_members_other, names[0], names[1], names[2], othersText ?: "")
    }

    buildSignalSymbolAnnotatedString(glyphStart = SignalSymbols.Glyph.GROUP) {
      if (othersText != null) {
        val othersStart = fullText.indexOf(othersText)
        if (othersStart >= 0) {
          append(fullText.take(othersStart))
          withLink(LinkAnnotation.Clickable(tag = "group_settings", styles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.onSurface))) { onGroupSettingsClicked() }) {
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
              append(othersText)
            }
          }
          append(fullText.substring(othersStart + othersText.length))
        } else {
          append(fullText)
        }
      } else {
        append(fullText)
      }
    }
  } else {
    buildSignalSymbolAnnotatedString(glyphStart = SignalSymbols.Glyph.GROUP) {
      withLink(LinkAnnotation.Clickable(tag = "group_settings", styles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.onSurface))) { onGroupSettingsClicked() }) {
        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
          append(pluralStringResource(R.plurals.ConversationFragment_group_member_count, memberCount, memberCount))
        }
      }
    }
  }

  Text(
    text = styledText,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurface,
    textAlign = TextAlign.Center,
    modifier = modifier
  )
}

@Composable
private fun GroupDescription(
  description: String,
  linkify: Boolean,
  onMoreClicked: () -> Unit,
  modifier: Modifier = Modifier
) {
  AndroidView(
    factory = { context ->
      EmojiTextView(context).apply {
        layoutParams = android.view.ViewGroup.LayoutParams(
          android.view.ViewGroup.LayoutParams.MATCH_PARENT,
          android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setTextAppearance(CoreUiR.style.Signal_Text_BodyMedium)
        gravity = Gravity.CENTER
        movementMethod = LongClickMovementMethod.getInstance(context)
      }
    },
    update = { view ->
      GroupDescriptionUtil.setText(view.context, view, description, linkify) {
        onMoreClicked()
      }
    },
    modifier = modifier.fillMaxWidth()
  )
}

@Composable
private fun OfficialChatPill() {
  val pillShape = RoundedCornerShape(26.dp)

  Text(
    text = signalSymbolText(
      text = stringResource(R.string.ConversationFragment_official_chat),
      glyphStart = SignalSymbols.Glyph.OFFICIAL_BADGE,
      glyphStartWeight = SignalSymbols.Weight.BOLD
    ),
    style = MaterialTheme.typography.labelLarge,
    fontWeight = FontWeight.Medium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier
      .padding(top = 8.dp)
      .clip(pillShape)
      .background(MaterialTheme.colorScheme.primaryContainer)
      .padding(horizontal = 12.dp, vertical = 4.dp)
  )
}

@DayNightPreviews
@Composable
private fun ConversationHeaderPreview() {
  Previews.Preview {
    ConversationHeaderContent(
      recipientId = RecipientId.from(1),
      displayName = "Katie Hall",
      showChevron = true,
      badge = null,
      showUnverifiedName = true,
      isGroup = false,
      phoneNumber = "+1 (555) 867-5309",
      sharedGroups = emptyList(),
      showSafetyTips = true,
      avatarDownloadState = AvatarDownloadStateCache.DownloadState.NONE
    )
  }
}

@DayNightPreviews
@Composable
private fun ConversationHeaderWithGroupsPreview() {
  Previews.Preview {
    ConversationHeaderContent(
      recipientId = RecipientId.from(1),
      displayName = "Katie Hall",
      showChevron = true,
      badge = null,
      showUnverifiedName = false,
      isGroup = false,
      sharedGroups = listOf("NYC Rock Climbers", "Dinner Party"),
      avatarDownloadState = AvatarDownloadStateCache.DownloadState.NONE
    )
  }
}

@DayNightPreviews
@Composable
private fun ConversationHeaderGroupPreview() {
  Previews.Preview {
    ConversationHeaderContent(
      recipientId = RecipientId.from(1),
      displayName = "Trail Crew",
      badge = null,
      showUnverifiedName = true,
      isGroup = true,
      groupInfo = GroupInfo(fullMemberCount = 12, isMember = false),
      avatarDownloadState = AvatarDownloadStateCache.DownloadState.NONE
    )
  }
}

@DayNightPreviews
@Composable
private fun ConversationHeaderNoteToSelfPreview() {
  Previews.Preview {
    ConversationHeaderContent(
      recipientId = RecipientId.from(1),
      displayName = "Note to Self",
      showVerified = true,
      isSelf = true,
      badge = null,
      showUnverifiedName = false,
      isGroup = false,
      avatarDownloadState = AvatarDownloadStateCache.DownloadState.NONE
    )
  }
}
