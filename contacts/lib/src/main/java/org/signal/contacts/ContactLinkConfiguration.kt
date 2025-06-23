package org.signal.contacts

import android.accounts.Account

/**
 * Describes how you'd like message and call links added to the system contacts.
 *
 * [appName] The name of the app
 * [messagePrompt] A function that, given a formatted number, will output a string to be used as a label for the message link on a contact
 * [callPrompt] A function that, given a formatted number, will output a string to be used as a label for the call link on a contact
 * [e164Formatter] A function that, given a formatted number, will output an E164 of that number
 * [messageMimetype] The mimetype you'd like to use for the message link
 * [callMimetype] The mimetype you'd like to use for the call link
 */
class ContactLinkConfiguration(
  val account: Account,
  val appName: String,
  val messagePrompt: (String) -> String,
  val callPrompt: (String) -> String,
  val videoCallPrompt: (String) -> String,
  val e164Formatter: (String) -> String?,
  val messageMimetype: String,
  val callMimetype: String,
  val videoCallMimetype: String,
  val syncTag: String
)
