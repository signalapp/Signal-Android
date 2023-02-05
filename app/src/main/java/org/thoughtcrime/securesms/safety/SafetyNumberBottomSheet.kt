package org.thoughtcrime.securesms.safety

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.FragmentManager
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.ui.error.SafetyNumberChangeDialog
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.util.Preconditions

/**
 * Object responsible for the construction of SafetyNumberBottomSheetFragment and Arg objects.
 */
object SafetyNumberBottomSheet {

  private const val TAG = "SafetyNumberBottomSheet"
  private const val ARGS = "args"

  /**
   * Create a factory to generate a legacy dialog for the given recipient id.
   */
  @JvmStatic
  fun forRecipientId(recipientId: RecipientId): Factory {
    return object : Factory {
      override fun show(fragmentManager: FragmentManager) {
        SafetyNumberChangeDialog.show(fragmentManager, recipientId)
      }
    }
  }

  /**
   * Create a factory to generate a legacy dialog for a given recipient id to display when
   * trying to place an outgoing call.
   */
  @JvmStatic
  fun forCall(recipientId: RecipientId): Factory {
    return object : Factory {
      override fun show(fragmentManager: FragmentManager) {
        SafetyNumberChangeDialog.showForCall(fragmentManager, recipientId)
      }
    }
  }

  /**
   * Create a factory to display a legacy dialog for a given list of records when trying
   * to place a group call.
   */
  @JvmStatic
  fun forGroupCall(identityRecords: List<IdentityRecord>): Factory {
    return object : Factory {
      override fun show(fragmentManager: FragmentManager) {
        SafetyNumberChangeDialog.showForGroupCall(fragmentManager, identityRecords)
      }
    }
  }

  /**
   * Create a factory to display a legacy dialog for a given list of recipient ids during
   * a group call
   */
  @JvmStatic
  fun forDuringGroupCall(recipientIds: Collection<RecipientId>): Factory {
    return object : Factory {
      override fun show(fragmentManager: FragmentManager) {
        SafetyNumberChangeDialog.showForDuringGroupCall(fragmentManager, recipientIds)
      }
    }
  }

  /**
   * Create a factory to generate a sheet for the given message record. This will try
   * to resend the message automatically when the user confirms.
   *
   * @param context Not held on to, so any context is fine.
   * @param messageRecord The message record containing failed identities.
   */
  @JvmStatic
  fun forMessageRecord(context: Context, messageRecord: MessageRecord): Factory {
    val args = SafetyNumberBottomSheetArgs(
      untrustedRecipients = messageRecord.identityKeyMismatches.map { it.getRecipientId(context) },
      destinations = getDestinationFromRecord(messageRecord),
      messageId = MessageId(messageRecord.id)
    )

    return SheetFactory(args)
  }

  /**
   * Create a factory to generate a sheet for the given identity records and destinations.
   *
   * @param identityRecords The list of untrusted records from the thrown error
   * @param destinations The list of locations the user was trying to send content
   */
  @JvmStatic
  fun forIdentityRecordsAndDestinations(identityRecords: List<IdentityRecord>, destinations: List<ContactSearchKey>): Factory {
    val args = SafetyNumberBottomSheetArgs(
      identityRecords.map { it.recipientId },
      destinations.filterIsInstance<ContactSearchKey.RecipientSearchKey>().map { it.requireRecipientSearchKey() }
    )

    return SheetFactory(args)
  }

  /**
   * Create a factory to generate a sheet for the given identity records and single destination.
   *
   * @param identityRecords The list of untrusted records from the thrown error
   * @param destination The location the user was trying to send content
   */
  @JvmStatic
  fun forIdentityRecordsAndDestination(identityRecords: List<IdentityRecord>, destination: ContactSearchKey): Factory {
    val args = SafetyNumberBottomSheetArgs(
      identityRecords.map { it.recipientId },
      listOf(destination).filterIsInstance<ContactSearchKey.RecipientSearchKey>().map { it.requireRecipientSearchKey() }
    )

    return SheetFactory(args)
  }

  /**
   * @return The parcelized arguments inside the bundle
   * @throws IllegalArgumentException if the bundle does not contain the correct parcelized arguments.
   */
  fun getArgsFromBundle(bundle: Bundle): SafetyNumberBottomSheetArgs {
    val args = bundle.getParcelable<SafetyNumberBottomSheetArgs>(ARGS)
    Preconditions.checkArgument(args != null)
    return args!!
  }

  private fun getDestinationFromRecord(messageRecord: MessageRecord): List<ContactSearchKey.RecipientSearchKey> {
    val key = if ((messageRecord as? MmsMessageRecord)?.storyType?.isStory == true) {
      ContactSearchKey.RecipientSearchKey(messageRecord.recipient.id, true)
    } else {
      ContactSearchKey.RecipientSearchKey(messageRecord.recipient.id, false)
    }

    return listOf(key)
  }

  /**
   * Similar to the normal companion object "show" methods, but with automatically provided arguments.
   */
  interface Factory {
    fun show(fragmentManager: FragmentManager)
  }

  private class SheetFactory(private val args: SafetyNumberBottomSheetArgs) : Factory {
    override fun show(fragmentManager: FragmentManager) {
      val dialogFragment = SafetyNumberBottomSheetFragment().apply {
        arguments = Bundle().apply {
          putParcelable(ARGS, args)
        }
      }

      dialogFragment.show(fragmentManager, TAG)
    }
  }

  /**
   * Callbacks for the bottom sheet. These are optional, and are invoked by the bottom sheet itself.
   *
   * Since the bottom sheet utilizes findListener to locate the callback implementor, child fragments will
   * get precedence over their parents and activities have the least precedence.
   */
  interface Callbacks {
    /**
     * Invoked when the user presses "send anyway" and the parent should automatically perform a resend.
     */
    fun sendAnywayAfterSafetyNumberChangedInBottomSheet(destinations: List<ContactSearchKey.RecipientSearchKey>)

    /**
     * Invoked when the user presses "send anyway" and a message was automatically resent.
     */
    fun onMessageResentAfterSafetyNumberChangeInBottomSheet()

    /**
     * Invoked when the user dismisses the sheet without performing a send.
     */
    fun onCanceled()
  }
}
