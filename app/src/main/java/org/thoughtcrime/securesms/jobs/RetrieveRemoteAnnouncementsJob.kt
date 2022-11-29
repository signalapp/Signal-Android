package org.thoughtcrime.securesms.jobs

import androidx.core.os.LocaleListCompat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.json.JSONObject
import org.signal.core.util.Hex
import org.signal.core.util.ThreadUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.RemoteMegaphoneTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.RemoteMegaphoneRecord
import org.thoughtcrime.securesms.database.model.addButton
import org.thoughtcrime.securesms.database.model.addLink
import org.thoughtcrime.securesms.database.model.addStyle
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Data
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.releasechannel.ReleaseChannel
import org.thoughtcrime.securesms.s3.S3
import org.thoughtcrime.securesms.transport.RetryLaterException
import org.thoughtcrime.securesms.util.LocaleFeatureFlags
import org.whispersystems.signalservice.internal.ServiceResponse
import java.io.IOException
import java.lang.Integer.max
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Retrieves and processes release channel messages.
 */
class RetrieveRemoteAnnouncementsJob private constructor(private val force: Boolean, parameters: Parameters) : BaseJob(parameters) {

  companion object {
    const val KEY = "RetrieveReleaseChannelJob"
    private const val MANIFEST = "${S3.DYNAMIC_PATH}/release-notes/release-notes-v2.json"
    private const val BASE_RELEASE_NOTE = "${S3.STATIC_PATH}/release-notes"
    private const val KEY_FORCE = "force"

    private val TAG = Log.tag(RetrieveRemoteAnnouncementsJob::class.java)
    private val RETRIEVE_FREQUENCY = TimeUnit.DAYS.toMillis(3)

    @JvmStatic
    @JvmOverloads
    fun enqueue(force: Boolean = false) {
      if (!SignalStore.account().isRegistered) {
        Log.i(TAG, "Not registered, skipping.")
        return
      }

      if (!force && System.currentTimeMillis() < SignalStore.releaseChannelValues().nextScheduledCheck) {
        Log.i(TAG, "Too soon to check for updated release notes")
        return
      }

      val job = RetrieveRemoteAnnouncementsJob(
        force,
        Parameters.Builder()
          .setQueue("RetrieveReleaseChannelJob")
          .setMaxInstancesForFactory(1)
          .setMaxAttempts(3)
          .addConstraint(NetworkConstraint.KEY)
          .build()
      )

      ApplicationDependencies.getJobManager()
        .startChain(CreateReleaseChannelJob.create())
        .then(job)
        .enqueue()
    }
  }

  override fun serialize(): Data = Data.Builder().putBoolean(KEY_FORCE, force).build()

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override fun onRun() {
    if (!SignalStore.account().isRegistered) {
      Log.i(TAG, "Not registered, skipping.")
      return
    }

    val values = SignalStore.releaseChannelValues()

    if (values.releaseChannelRecipientId == null) {
      Log.w(TAG, "Release Channel recipient is null, this shouldn't happen, will try to create on next run")
      return
    }

    if (!force && System.currentTimeMillis() < values.nextScheduledCheck) {
      Log.i(TAG, "Too soon to check for updated release notes")
      return
    }

    val manifestMd5: ByteArray? = S3.getObjectMD5(MANIFEST)

    if (manifestMd5 == null) {
      Log.i(TAG, "Unable to retrieve manifest MD5")
      return
    }

    when {
      values.highestVersionNoteReceived == 0 -> {
        Log.i(TAG, "First check, saving code and skipping download")
        values.highestVersionNoteReceived = BuildConfig.CANONICAL_VERSION_CODE
      }
      !force && MessageDigest.isEqual(manifestMd5, values.previousManifestMd5) -> {
        Log.i(TAG, "Manifest has not changed since last fetch.")
      }
      else -> fetchManifest(manifestMd5)
    }

    values.previousManifestMd5 = manifestMd5
    values.nextScheduledCheck = System.currentTimeMillis() + RETRIEVE_FREQUENCY
  }

  private fun fetchManifest(manifestMd5: ByteArray) {
    Log.i(TAG, "Updating release notes to ${Hex.toStringCondensed(manifestMd5)}")

    val allReleaseNotes: ReleaseNotes? = S3.getAndVerifyObject(MANIFEST, ReleaseNotes::class.java, manifestMd5).result.orElse(null)

    if (allReleaseNotes != null) {
      updateReleaseNotes(allReleaseNotes.announcements)
      updateMegaphones(allReleaseNotes.megaphones ?: emptyList())
    } else {
      Log.w(TAG, "Unable to retrieve manifest json")
    }
  }

  @Suppress("UsePropertyAccessSyntax")
  private fun updateReleaseNotes(announcements: List<ReleaseNote>) {
    val values = SignalStore.releaseChannelValues()

    if (Recipient.resolved(values.releaseChannelRecipientId!!).isBlocked) {
      Log.i(TAG, "Release channel is blocked, do not bother with updates")
      values.highestVersionNoteReceived = announcements.mapNotNull { it.androidMinVersion?.toIntOrNull() }.maxOrNull() ?: values.highestVersionNoteReceived
      return
    }

    if (!values.hasMetConversationRequirement) {
      if ((SignalDatabase.threads.getArchivedConversationListCount(ConversationFilter.OFF) + SignalDatabase.threads.getUnarchivedConversationListCount(ConversationFilter.OFF)) < 6) {
        Log.i(TAG, "User does not have enough conversations to show release channel")
        values.nextScheduledCheck = System.currentTimeMillis() + RETRIEVE_FREQUENCY
        return
      } else {
        values.hasMetConversationRequirement = true
      }
    }

    val resolvedNotes: List<FullReleaseNote?> = announcements
      .asSequence()
      .filter {
        val minVersion = it.androidMinVersion?.toIntOrNull()
        if (minVersion != null) {
          minVersion > values.highestVersionNoteReceived && minVersion <= BuildConfig.CANONICAL_VERSION_CODE
        } else {
          false
        }
      }
      .filter { it.countries == null || LocaleFeatureFlags.shouldShowReleaseNote(it.uuid, it.countries) }
      .sortedBy { it.androidMinVersion!!.toInt() }
      .map { resolveReleaseNote(it) }
      .toList()

    if (resolvedNotes.any { it == null }) {
      Log.w(TAG, "Some release notes did not resolve, aborting.")
      throw RetryLaterException()
    }

    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(values.releaseChannelRecipientId!!))
    var highestVersion = values.highestVersionNoteReceived
    var addedNewNotes = false

    resolvedNotes
      .filterNotNull()
      .forEach { note ->
        val body = "${note.translation.title}\n\n${note.translation.body}"
        val bodyRangeList = BodyRangeList.newBuilder()
          .addStyle(BodyRangeList.BodyRange.Style.BOLD, 0, note.translation.title.length)

        if (note.releaseNote.link?.isNotEmpty() == true && note.translation.linkText?.isNotEmpty() == true) {
          val linkIndex = body.indexOf(note.translation.linkText)
          if (linkIndex != -1 && linkIndex + note.translation.linkText.length < body.length) {
            bodyRangeList.addLink(note.releaseNote.link, linkIndex, note.translation.linkText.length)
          }
        }

        if (note.releaseNote.ctaId?.isNotEmpty() == true && note.translation.callToActionText?.isNotEmpty() == true) {
          bodyRangeList.addButton(note.translation.callToActionText, note.releaseNote.ctaId, body.lastIndex, 0)
        }

        ThreadUtil.sleep(5)
        val insertResult: MessageTable.InsertResult? = ReleaseChannel.insertReleaseChannelMessage(
          recipientId = values.releaseChannelRecipientId!!,
          body = body,
          threadId = threadId,
          messageRanges = bodyRangeList.build(),
          image = note.translation.image,
          imageWidth = note.translation.imageWidth?.toIntOrNull() ?: 0,
          imageHeight = note.translation.imageHeight?.toIntOrNull() ?: 0
        )

        if (insertResult != null) {
          addedNewNotes = addedNewNotes || (note.releaseNote.includeBoostMessage ?: true)
          SignalDatabase.attachments.getAttachmentsForMessage(insertResult.messageId)
            .forEach { ApplicationDependencies.getJobManager().add(AttachmentDownloadJob(insertResult.messageId, it.attachmentId, false)) }

          ApplicationDependencies.getMessageNotifier().updateNotification(context, ConversationId.forConversation(insertResult.threadId))
          TrimThreadJob.enqueueAsync(insertResult.threadId)

          highestVersion = max(highestVersion, note.releaseNote.androidMinVersion!!.toInt())
        }
      }

    if (addedNewNotes) {
      ThreadUtil.sleep(5)
      SignalDatabase.sms.insertBoostRequestMessage(values.releaseChannelRecipientId!!, threadId)
    }

    values.highestVersionNoteReceived = highestVersion
  }

  private fun updateMegaphones(megaphones: List<RemoteMegaphone>) {
    val resolvedMegaphones: List<FullRemoteMegaphone?> = megaphones
      .asSequence()
      .filter { it.androidMinVersion != null }
      .map { resolveMegaphone(it) }
      .toList()

    if (resolvedMegaphones.any { it == null }) {
      Log.w(TAG, "Some megaphones did not resolve, will retry later.")
      throw RetryLaterException()
    }

    val manifestMegaphones: MutableSet<String> = mutableSetOf()
    val existingMegaphones: Map<String, RemoteMegaphoneRecord> = SignalDatabase.remoteMegaphones.getAll().associateBy { it.uuid }

    resolvedMegaphones
      .filterNotNull()
      .forEach { megaphone ->
        val uuid = megaphone.remoteMegaphone.uuid
        manifestMegaphones += uuid
        if (existingMegaphones.contains(uuid)) {
          SignalDatabase.remoteMegaphones.update(
            uuid = uuid,
            priority = megaphone.remoteMegaphone.priority,
            countries = megaphone.remoteMegaphone.countries,
            title = megaphone.translation.title,
            body = megaphone.translation.body,
            primaryActionText = megaphone.translation.primaryCtaText,
            secondaryActionText = megaphone.translation.secondaryCtaText
          )
        } else {
          val record = RemoteMegaphoneRecord(
            uuid = uuid,
            priority = megaphone.remoteMegaphone.priority,
            countries = megaphone.remoteMegaphone.countries,
            minimumVersion = megaphone.remoteMegaphone.androidMinVersion!!.toInt(),
            doNotShowBefore = megaphone.remoteMegaphone.dontShowBeforeEpochSeconds?.let { TimeUnit.SECONDS.toMillis(it) } ?: 0,
            doNotShowAfter = megaphone.remoteMegaphone.dontShowAfterEpochSeconds?.let { TimeUnit.SECONDS.toMillis(it) } ?: Long.MAX_VALUE,
            showForNumberOfDays = megaphone.remoteMegaphone.showForNumberOfDays ?: 0,
            conditionalId = megaphone.remoteMegaphone.conditionalId,
            primaryActionId = RemoteMegaphoneRecord.ActionId.from(megaphone.remoteMegaphone.primaryCtaId),
            secondaryActionId = RemoteMegaphoneRecord.ActionId.from(megaphone.remoteMegaphone.secondaryCtaId),
            imageUrl = megaphone.translation.image,
            title = megaphone.translation.title,
            body = megaphone.translation.body,
            primaryActionText = megaphone.translation.primaryCtaText,
            secondaryActionText = megaphone.translation.secondaryCtaText,
            primaryActionData = megaphone.remoteMegaphone.primaryCtaData?.takeIf { it is ObjectNode }?.let { JSONObject(it.toString()) },
            secondaryActionData = megaphone.remoteMegaphone.secondaryCtaData?.takeIf { it is ObjectNode }?.let { JSONObject(it.toString()) }
          )

          SignalDatabase.remoteMegaphones.insert(record)

          if (record.imageUrl != null) {
            ApplicationDependencies.getJobManager().add(FetchRemoteMegaphoneImageJob(record.uuid, record.imageUrl))
          }
        }
      }

    val megaphonesToDelete = existingMegaphones
      .filterKeys { !manifestMegaphones.contains(it) }
      .filterValues { it.minimumVersion != RemoteMegaphoneTable.VERSION_FINISHED }

    if (megaphonesToDelete.isNotEmpty()) {
      Log.i(TAG, "Clearing ${megaphonesToDelete.size} stale megaphones ${megaphonesToDelete.keys}")
      for ((uuid, megaphone) in megaphonesToDelete) {
        if (megaphone.imageUri != null) {
          BlobProvider.getInstance().delete(context, megaphone.imageUri)
        }
        SignalDatabase.remoteMegaphones.clear(uuid)
      }
    }
  }

  private fun resolveReleaseNote(releaseNote: ReleaseNote): FullReleaseNote? {
    val potentialNoteUrls = "$BASE_RELEASE_NOTE/${releaseNote.uuid}".getLocaleUrls()
    for (potentialUrl: String in potentialNoteUrls) {
      val translationJson: ServiceResponse<TranslatedReleaseNote> = S3.getAndVerifyObject(potentialUrl, TranslatedReleaseNote::class.java)

      if (translationJson.result.isPresent) {
        return FullReleaseNote(releaseNote, translationJson.result.get())
      } else if (translationJson.status != 404 && translationJson.executionError.orElse(null) !is S3.Md5FailureException) {
        throw RetryLaterException()
      }
    }

    return null
  }

  private fun resolveMegaphone(remoteMegaphone: RemoteMegaphone): FullRemoteMegaphone? {
    val potentialNoteUrls = "$BASE_RELEASE_NOTE/${remoteMegaphone.uuid}".getLocaleUrls()
    for (potentialUrl: String in potentialNoteUrls) {
      val translationJson: ServiceResponse<TranslatedRemoteMegaphone> = S3.getAndVerifyObject(potentialUrl, TranslatedRemoteMegaphone::class.java)

      if (translationJson.result.isPresent) {
        return FullRemoteMegaphone(remoteMegaphone, translationJson.result.get())
      } else if (translationJson.status != 404 && translationJson.executionError.orElse(null) !is S3.Md5FailureException) {
        throw RetryLaterException()
      }
    }

    return null
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return e is RetryLaterException || e is IOException
  }

  private fun String.getLocaleUrls(): List<String> {
    val localeList: LocaleListCompat = LocaleListCompat.getDefault()

    val potentialNoteUrls = mutableListOf<String>()

    if (SignalStore.settings().language != "zz") {
      potentialNoteUrls += "$this/${SignalStore.settings().language}.json"
    }

    for (index in 0 until localeList.size()) {
      val locale: Locale = localeList.get(index) ?: continue
      if (locale.language.isNotEmpty()) {
        if (locale.country.isNotEmpty()) {
          potentialNoteUrls += "$this/${locale.language}_${locale.country}.json"
        }
        potentialNoteUrls += "$this/${locale.language}.json"
      }
    }

    potentialNoteUrls += "$this/en.json"

    return potentialNoteUrls
  }

  data class FullReleaseNote(val releaseNote: ReleaseNote, val translation: TranslatedReleaseNote)

  data class FullRemoteMegaphone(val remoteMegaphone: RemoteMegaphone, val translation: TranslatedRemoteMegaphone)

  data class ReleaseNotes(@JsonProperty val announcements: List<ReleaseNote>, @JsonProperty val megaphones: List<RemoteMegaphone>?)

  data class ReleaseNote(
    @JsonProperty val uuid: String,
    @JsonProperty val countries: String?,
    @JsonProperty val androidMinVersion: String?,
    @JsonProperty val link: String?,
    @JsonProperty val ctaId: String?,
    @JsonProperty val includeBoostMessage: Boolean?
  )

  data class RemoteMegaphone(
    @JsonProperty val uuid: String,
    @JsonProperty val priority: Long,
    @JsonProperty val countries: String?,
    @JsonProperty val androidMinVersion: String?,
    @JsonProperty val dontShowBeforeEpochSeconds: Long?,
    @JsonProperty val dontShowAfterEpochSeconds: Long?,
    @JsonProperty val showForNumberOfDays: Long?,
    @JsonProperty val conditionalId: String?,
    @JsonProperty val primaryCtaId: String?,
    @JsonProperty val secondaryCtaId: String?,
    @JsonProperty val primaryCtaData: JsonNode?,
    @JsonProperty val secondaryCtaData: JsonNode?
  )

  data class TranslatedReleaseNote(
    @JsonProperty val uuid: String,
    @JsonProperty val image: String?,
    @JsonProperty val imageWidth: String?,
    @JsonProperty val imageHeight: String?,
    @JsonProperty val linkText: String?,
    @JsonProperty val title: String,
    @JsonProperty val body: String,
    @JsonProperty val callToActionText: String?,
  )

  data class TranslatedRemoteMegaphone(
    @JsonProperty val uuid: String,
    @JsonProperty val image: String?,
    @JsonProperty val title: String,
    @JsonProperty val body: String,
    @JsonProperty val primaryCtaText: String?,
    @JsonProperty val secondaryCtaText: String?,
  )

  class Factory : Job.Factory<RetrieveRemoteAnnouncementsJob> {
    override fun create(parameters: Parameters, data: Data): RetrieveRemoteAnnouncementsJob {
      return RetrieveRemoteAnnouncementsJob(data.getBoolean(KEY_FORCE), parameters)
    }
  }
}
