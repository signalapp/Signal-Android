package org.thoughtcrime.securesms.jobs

import androidx.core.os.LocaleListCompat
import com.fasterxml.jackson.annotation.JsonProperty
import org.signal.core.util.ThreadUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.database.MessageDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.addButton
import org.thoughtcrime.securesms.database.model.addLink
import org.thoughtcrime.securesms.database.model.addStyle
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Data
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.releasechannel.ReleaseChannel
import org.thoughtcrime.securesms.s3.S3
import org.thoughtcrime.securesms.transport.RetryLaterException
import org.thoughtcrime.securesms.util.Hex
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
class RetrieveReleaseChannelJob private constructor(private val force: Boolean, parameters: Parameters) : BaseJob(parameters) {
  companion object {
    const val KEY = "RetrieveReleaseChannelJob"
    private const val MANIFEST = "https://updates.signal.org/dynamic/release-notes/release-notes.json"
    private const val BASE_RELEASE_NOTE = "https://updates.signal.org/static/release-notes"
    private const val KEY_FORCE = "force"

    private val TAG = Log.tag(RetrieveReleaseChannelJob::class.java)

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

      val job = RetrieveReleaseChannelJob(
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

  @Suppress("UsePropertyAccessSyntax")
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

    if (Recipient.resolved(values.releaseChannelRecipientId!!).isBlocked) {
      Log.i(TAG, "Release channel is blocked, do not fetch updates")
      values.nextScheduledCheck = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)
      return
    }

    if (!force && System.currentTimeMillis() < values.nextScheduledCheck) {
      Log.i(TAG, "Too soon to check for updated release notes")
      return
    }

    if (values.previousManifestMd5.isEmpty() && (SignalDatabase.threads.getArchivedConversationListCount() + SignalDatabase.threads.getUnarchivedConversationListCount()) < 6) {
      Log.i(TAG, "User does not have enough conversations to show release channel")
      values.nextScheduledCheck = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)
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
      MessageDigest.isEqual(manifestMd5, values.previousManifestMd5) -> {
        Log.i(TAG, "Manifest has not changed since last fetch.")
      }
      else -> updateReleaseNotes(manifestMd5)
    }

    values.previousManifestMd5 = manifestMd5
    values.nextScheduledCheck = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)
  }

  private fun updateReleaseNotes(manifestMd5: ByteArray) {
    Log.i(TAG, "Updating release notes to ${Hex.toStringCondensed(manifestMd5)}")

    val values = SignalStore.releaseChannelValues()
    val allReleaseNotes: ReleaseNotes? = S3.getAndVerifyObject(MANIFEST, ReleaseNotes::class.java, manifestMd5).result.orNull()

    if (allReleaseNotes != null) {
      val resolvedNotes: List<FullReleaseNote?> = allReleaseNotes.announcements.asSequence()
        .filter { it.androidMinVersion != null }
        .filter { it.androidMinVersion!!.toIntOrNull()?.let { minVersion: Int -> minVersion > values.highestVersionNoteReceived && minVersion <= BuildConfig.CANONICAL_VERSION_CODE } ?: false }
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

      resolvedNotes.filterNotNull()
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

          ThreadUtil.sleep(1)
          val insertResult: MessageDatabase.InsertResult? = ReleaseChannel.insertAnnouncement(
            recipientId = values.releaseChannelRecipientId!!,
            body = body,
            threadId = threadId,
            messageRanges = bodyRangeList.build(),
            image = note.translation.image,
            imageWidth = note.translation.imageWidth?.toIntOrNull() ?: 0,
            imageHeight = note.translation.imageHeight?.toIntOrNull() ?: 0
          )

          SignalDatabase.sms.insertBoostRequestMessage(values.releaseChannelRecipientId!!, threadId)

          if (insertResult != null) {
            SignalDatabase.attachments.getAttachmentsForMessage(insertResult.messageId)
              .forEach { ApplicationDependencies.getJobManager().add(AttachmentDownloadJob(insertResult.messageId, it.attachmentId, false)) }

            ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.threadId)
            TrimThreadJob.enqueueAsync(insertResult.threadId)

            highestVersion = max(highestVersion, note.releaseNote.androidMinVersion!!.toInt())
          }
        }

      values.highestVersionNoteReceived = highestVersion
    } else {
      Log.w(TAG, "Unable to retrieve manifest json")
    }
  }

  private fun resolveReleaseNote(releaseNote: ReleaseNote): FullReleaseNote? {
    val urlBase = "$BASE_RELEASE_NOTE/${releaseNote.uuid}"
    val localeList: LocaleListCompat = LocaleListCompat.getDefault()

    val potentialNoteUrls = mutableListOf<String>()

    if (SignalStore.settings().language != "zz") {
      potentialNoteUrls += "$urlBase/${SignalStore.settings().language}.json"
    }

    for (index in 0 until localeList.size()) {
      val locale: Locale = localeList.get(index)
      if (locale.language.isNotEmpty()) {
        if (locale.country.isNotEmpty()) {
          potentialNoteUrls += "$urlBase/${locale.language}_${locale.country}.json"
        }
        potentialNoteUrls += "$urlBase/${locale.language}.json"
      }
    }

    potentialNoteUrls += "$urlBase/en.json"

    for (potentialUrl: String in potentialNoteUrls) {
      val translationJson: ServiceResponse<TranslatedReleaseNote> = S3.getAndVerifyObject(potentialUrl, TranslatedReleaseNote::class.java)

      if (translationJson.result.isPresent) {
        return FullReleaseNote(releaseNote, translationJson.result.get())
      } else if (translationJson.status != 404 && translationJson.executionError.orNull() !is S3.Md5FailureException) {
        throw RetryLaterException()
      }
    }

    return null
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return e is RetryLaterException || e is IOException
  }

  data class FullReleaseNote(val releaseNote: ReleaseNote, val translation: TranslatedReleaseNote)

  data class ReleaseNotes(@JsonProperty val announcements: List<ReleaseNote>)

  data class ReleaseNote(
    @JsonProperty val uuid: String,
    @JsonProperty val countries: String?,
    @JsonProperty val androidMinVersion: String?,
    @JsonProperty val link: String?,
    @JsonProperty val ctaId: String?
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

  class Factory : Job.Factory<RetrieveReleaseChannelJob> {
    override fun create(parameters: Parameters, data: Data): RetrieveReleaseChannelJob {
      return RetrieveReleaseChannelJob(data.getBoolean(KEY_FORCE), parameters)
    }
  }
}
