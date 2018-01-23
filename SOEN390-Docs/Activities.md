# Activities

* WebRtcCallActivity
  * for making secure audio and video calls
  * accessed by clicking phone call button in convo screen
* CountrySelectionActivity/Fragment
  * lets user select their country name and code
  * used during account registration
* ImportExportActivity/Fragment
  * lets the user import/export sms or plaintext messages for backup
  * accessed by clicking "Import/export" on options menu
* InviteActivity
  * lets user invite a friend to use Signal either by sms or an external service like Facebook
  * accessed by clicking "Invite Friends" on options menu
  * has a ContactSelectionListFragment for selecting contact from user's contacts list
* PromptMmsActivity
  * for settings the mms settings
  * screen before MmsPreferencesActivity
* DeviceProvisioningActivity
  * for linking a device to the app
  * screen before DeviceActivity
* MmsPreferencesActivity/Fragment
  * set the mms settings
* ShareActivity
  * for sharing content with a contact from user's contact list
  * has a ContactSelectionListFragment
* ConversationListActivity/Fragment
  * screen you see when you first load the app
  * shows a list of all the user's convos
  * has search button and options menu
* ConversationListArchiveActivity
  * shows archived convos
  * has a ConversationListFragment
* ConversationActivity/Fragment
  * shows a message thread
  * probably the most heavy activity in the app
  * deals with camera, audio, sending media, etc.
* ConversationPopupActivity
  * extends ConversationActivity
  * not sure what this does
* MessageDetailsActivity
  * when you long click a message and click the "i" to see message details
* GroupCreateActivity
  * accessed when you click "New group" in options menu or when you click "Edit group" within group msg
  * lets user make new group convo or update existing one
* DatabaseMigrationActivity
  * imports sms messages from phone's message database
* DatabaseUpgradeActivity
  * something to do with updates
* ExperienceUpgradeActivity
  * also has to do with updates
* PassphraseCreateActivity
  * generates a secret encryption key for the user
* PassphrasePromptActivity
  * prompts for the user's passphrase when they've chosen to password protect their messages
* NewConversationActivity
  * extends ContactSelectionActivity (abstract)
  * starts a new convo when you select contact from contact list
  * has ContactSelectionListFragment
* PushContactSelectionActivity
  * extends ContactSelectionActivity (abstract)
  * starts a group convo when you select multiple contacts from contact list (with checkboxes)
  * has ContactSelectionListFragment
* GiphyActivity
  * to send a gif using giphy
* PassphraseChangeActivity
  * lets user change their encryption passphrase
* VerifyIdentityActivity
  * accessed when you go to conversation settings and choose "verify safety number"
  * generates 12 numeric codes and a qr code to verify someone's identity
* ApplicationPreferencesActivity
  * application settings screen
* RegistrationActivity
  * register a new user account
  * asks for user's phone number and country and sends verification code
* DeviceActivity
  * lets the user link a device by scanning a QR code
  * has a DeviceAddFragment, DeviceListFragment and DeviceLinkFragment
* LogSubmitActivity
  * submits app logs from device when user chooses to send feedback
* MediaPreviewActivity
  * previews media attachments before sending
* MediaOverviewActivity
  * shows you summary of media and documents shared with recipient
* DummyActivity
* PlayServicesProblemActivity
  * when there's a problem with user's google play services
* SmsSendtoActivity
  * This activity is to send a text/attachment from outside the app by clicking on android "share"   button and choosing "signal" app as a mean to share the selected data
* VoiceCallShare
  * something to do with phone calls
* RecipientPreferenceActivity
  * deals with the conversation settings for a particular contact, e.g. ringtone, colors
* BlockedContactsActivity
  * shows list of blocked contacts
  * accessed by going to settings -> privacy -> blocked contacts
* ScribbleActivity
  * when sending an image, lets you add stickers, text and drawings to the image
  * accessed by clicking edit button in the corner of image preview
* StickerSelectActivity
  * sticker selection screen when you edit
  * accessed from ScribbleActivity when clicking button to add sticker
* CropImageActivity
  * something for cropping images
* CreateProfileActivity
  * change user profile info (avatar, name)
  * accessed by going to settings and clicking your name
* ClearProfileAvatarActivity
  * dialog when removing profile avatar
