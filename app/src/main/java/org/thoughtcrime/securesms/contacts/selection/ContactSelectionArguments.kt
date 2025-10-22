package org.thoughtcrime.securesms.contacts.selection

import android.content.Intent
import android.os.Bundle
import org.signal.core.util.getParcelableArrayListCompat
import org.signal.core.util.getParcelableArrayListExtraCompat
import org.signal.core.util.getParcelableCompat
import org.signal.core.util.getParcelableExtraCompat
import org.thoughtcrime.securesms.contacts.ContactSelectionDisplayMode
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.recipients.RecipientId

@Suppress("KotlinConstantConditions")
data class ContactSelectionArguments(
  val displayMode: Int = Defaults.DISPLAY_MODE,
  val isRefreshable: Boolean = Defaults.IS_REFRESHABLE,
  val enableCreateNewGroup: Boolean = Defaults.ENABLE_CREATE_NEW_GROUP,
  val enableFindByUsername: Boolean = Defaults.ENABLE_FIND_BY_USERNAME,
  val enableFindByPhoneNumber: Boolean = Defaults.ENABLE_FIND_BY_PHONE_NUMBER,
  val includeRecents: Boolean = Defaults.INCLUDE_RECENTS,
  val includeChatTypes: Boolean = Defaults.INCLUDE_CHAT_TYPES,
  val selectionLimits: SelectionLimits? = Defaults.SELECTION_LIMITS,
  val currentSelection: Set<RecipientId> = Defaults.CURRENT_SELECTION,
  val canSelectSelf: Boolean = Defaults.canSelectSelf(selectionLimits),
  val displayChips: Boolean = Defaults.DISPLAY_CHIPS,
  val recyclerPadBottom: Int = Defaults.RECYCLER_PADDING_BOTTOM,
  val recyclerChildClipping: Boolean = Defaults.RECYCLER_CHILD_CLIPPING
) {

  fun toArgumentBundle(): Bundle {
    return Bundle().apply {
      putInt(DISPLAY_MODE, displayMode)
      putBoolean(REFRESHABLE, isRefreshable)
      putBoolean(ENABLE_CREATE_NEW_GROUP, enableCreateNewGroup)
      putBoolean(ENABLE_FIND_BY_USERNAME, enableFindByUsername)
      putBoolean(ENABLE_FIND_BY_PHONE_NUMBER, enableFindByPhoneNumber)
      putBoolean(RECENTS, includeRecents)
      putBoolean(INCLUDE_CHAT_TYPES, includeChatTypes)
      putParcelable(SELECTION_LIMITS, selectionLimits)
      putParcelableArrayList(CURRENT_SELECTION, ArrayList(currentSelection))
      putBoolean(CAN_SELECT_SELF, canSelectSelf)
      putBoolean(DISPLAY_CHIPS, displayChips)
      putInt(RV_PADDING_BOTTOM, recyclerPadBottom)
      putBoolean(RV_CLIP, recyclerChildClipping)
    }
  }

  companion object {
    const val DISPLAY_MODE = "display_mode"
    const val REFRESHABLE = "refreshable"
    const val ENABLE_CREATE_NEW_GROUP = "enable_create_new_group"
    const val ENABLE_FIND_BY_USERNAME = "enable_find_by_username"
    const val ENABLE_FIND_BY_PHONE_NUMBER = "enable_find_by_phone"
    const val RECENTS = "recents"
    const val INCLUDE_CHAT_TYPES = "include_chat_types"
    const val SELECTION_LIMITS = "selection_limits"
    const val CURRENT_SELECTION = "current_selection"
    const val CAN_SELECT_SELF = "can_select_self"
    const val DISPLAY_CHIPS = "display_chips"
    const val RV_PADDING_BOTTOM = "recycler_view_padding_bottom"
    const val RV_CLIP = "recycler_view_clipping"

    @JvmStatic
    fun fromBundle(bundle: Bundle, intent: Intent): ContactSelectionArguments {
      val selectionLimits = bundle.getParcelableCompat(SELECTION_LIMITS, SelectionLimits::class.java)
        ?: intent.getParcelableExtraCompat(SELECTION_LIMITS, SelectionLimits::class.java)

      val currentSelection = bundle.getParcelableArrayListCompat(CURRENT_SELECTION, RecipientId::class.java)
        ?: intent.getParcelableArrayListExtraCompat(CURRENT_SELECTION, RecipientId::class.java)
        ?: emptyList()

      return ContactSelectionArguments(
        displayMode = bundle.getInt(DISPLAY_MODE, intent.getIntExtra(DISPLAY_MODE, Defaults.DISPLAY_MODE)),
        isRefreshable = bundle.getBoolean(REFRESHABLE, intent.getBooleanExtra(REFRESHABLE, Defaults.IS_REFRESHABLE)),
        enableCreateNewGroup = bundle.getBoolean(ENABLE_CREATE_NEW_GROUP, intent.getBooleanExtra(ENABLE_CREATE_NEW_GROUP, Defaults.ENABLE_CREATE_NEW_GROUP)),
        enableFindByUsername = bundle.getBoolean(ENABLE_FIND_BY_USERNAME, intent.getBooleanExtra(ENABLE_FIND_BY_USERNAME, Defaults.ENABLE_FIND_BY_USERNAME)),
        enableFindByPhoneNumber = bundle.getBoolean(ENABLE_FIND_BY_PHONE_NUMBER, intent.getBooleanExtra(ENABLE_FIND_BY_PHONE_NUMBER, Defaults.ENABLE_FIND_BY_PHONE_NUMBER)),
        includeRecents = bundle.getBoolean(RECENTS, intent.getBooleanExtra(RECENTS, Defaults.INCLUDE_RECENTS)),
        includeChatTypes = bundle.getBoolean(INCLUDE_CHAT_TYPES, intent.getBooleanExtra(INCLUDE_CHAT_TYPES, Defaults.INCLUDE_CHAT_TYPES)),
        selectionLimits = selectionLimits,
        currentSelection = currentSelection.toSet(),
        canSelectSelf = bundle.getBoolean(CAN_SELECT_SELF, intent.getBooleanExtra(CAN_SELECT_SELF, Defaults.canSelectSelf(selectionLimits))),
        displayChips = bundle.getBoolean(DISPLAY_CHIPS, intent.getBooleanExtra(DISPLAY_CHIPS, Defaults.DISPLAY_CHIPS)),
        recyclerPadBottom = bundle.getInt(RV_PADDING_BOTTOM, intent.getIntExtra(RV_PADDING_BOTTOM, Defaults.RECYCLER_PADDING_BOTTOM)),
        recyclerChildClipping = bundle.getBoolean(RV_CLIP, intent.getBooleanExtra(RV_CLIP, Defaults.RECYCLER_CHILD_CLIPPING))
      )
    }
  }

  object Defaults {
    const val DISPLAY_MODE = ContactSelectionDisplayMode.FLAG_ALL
    const val IS_REFRESHABLE = true
    const val ENABLE_CREATE_NEW_GROUP = false
    const val ENABLE_FIND_BY_USERNAME = false
    const val ENABLE_FIND_BY_PHONE_NUMBER = false
    const val INCLUDE_RECENTS = false
    const val INCLUDE_CHAT_TYPES = false
    val SELECTION_LIMITS: SelectionLimits? = null
    val CURRENT_SELECTION: Set<RecipientId> = emptySet()
    const val DISPLAY_CHIPS = true
    const val RECYCLER_PADDING_BOTTOM = -1
    const val RECYCLER_CHILD_CLIPPING = true

    fun canSelectSelf(selectionLimits: SelectionLimits?): Boolean = selectionLimits == null
  }
}
