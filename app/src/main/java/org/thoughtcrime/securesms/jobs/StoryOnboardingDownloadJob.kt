package org.thoughtcrime.securesms.jobs

import androidx.core.os.LocaleListCompat
import com.fasterxml.jackson.core.JsonParseException
import org.json.JSONArray
import org.json.JSONObject
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.MessageDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Data
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.releasechannel.ReleaseChannel
import org.thoughtcrime.securesms.s3.S3
import org.thoughtcrime.securesms.transport.RetryLaterException
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import java.util.Locale

/**
 * Kicks off the necessary work to download the resources for the onboarding story.
 */
class StoryOnboardingDownloadJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  companion object {

    private const val ONBOARDING_MANIFEST_ENDPOINT = "${S3.DYNAMIC_PATH}/android/stories/onboarding/manifest.json"
    private const val ONBOARDING_IMAGE_PATH = "${S3.STATIC_PATH}/android/stories/onboarding"
    private const val ONBOARDING_EXTENSION = ".jpg"
    private const val ONBOARDING_IMAGE_COUNT = 5
    private const val ONBOARDING_IMAGE_WIDTH = 1125
    private const val ONBOARDING_IMAGE_HEIGHT = 1998

    const val KEY = "StoryOnboardingDownloadJob"

    private val TAG = Log.tag(StoryOnboardingDownloadJob::class.java)

    private fun create(): Job {
      return StoryOnboardingDownloadJob(
        Parameters.Builder()
          .addConstraint(NetworkConstraint.KEY)
          .setQueue("StoryOnboardingDownloadJob")
          .setMaxInstancesForFactory(1)
          .setMaxAttempts(3)
          .build()
      )
    }

    fun enqueueIfNeeded() {
      if (SignalStore.storyValues().hasDownloadedOnboardingStory) {
        return
      }

      Log.d(TAG, "Attempting to enqueue StoryOnboardingDownloadJob...")
      ApplicationDependencies.getJobManager()
        .startChain(CreateReleaseChannelJob.create())
        .then(create())
        .enqueue()
    }
  }

  override fun serialize(): Data = Data.EMPTY
  override fun getFactoryKey(): String = KEY
  override fun onFailure() = Unit

  override fun onRun() {
    if (SignalStore.storyValues().hasDownloadedOnboardingStory) {
      Log.i(TAG, "Already downloaded onboarding story. Exiting.")
      return
    }

    val releaseChannelRecipientId = SignalStore.releaseChannelValues().releaseChannelRecipientId
    if (releaseChannelRecipientId == null) {
      Log.w(TAG, "Cannot create story onboarding without release channel recipient.")
      throw Exception("No release channel recipient.")
    }

    SignalDatabase.mms.getAllStoriesFor(releaseChannelRecipientId, -1).use { reader ->
      reader.forEach { messageRecord ->
        SignalDatabase.mms.deleteMessage(messageRecord.id)
      }
    }

    val manifest: JSONObject = try {
      JSONObject(S3.getString(ONBOARDING_MANIFEST_ENDPOINT))
    } catch (e: JsonParseException) {
      Log.w(TAG, "Returned data could not be parsed into JSON", e)
      throw e
    } catch (e: NonSuccessfulResponseCodeException) {
      Log.w(TAG, "Returned non-successful response code from server.", e)
      throw RetryLaterException()
    }

    if (!manifest.has("languages")) {
      Log.w(TAG, "Could not find languages set in manifest.")
      throw Exception("Could not find languages set in manifest.")
    }

    if (!manifest.has("version")) {
      Log.w(TAG, "Could not find version in manifest")
      throw Exception("Could not find version in manifest.")
    }

    val version = manifest.getString("version")
    Log.i(TAG, "Using images for manifest version $version")

    val languages = manifest.getJSONObject("languages")
    val languageCodeCandidates: List<String> = getLocaleCodes()
    var candidateArray: JSONArray? = null
    for (candidate in languageCodeCandidates) {
      if (languages.has(candidate)) {
        candidateArray = languages.getJSONArray(candidate)
        break
      }
    }

    if (candidateArray == null) {
      Log.w(TAG, "Could not find a candidate for the language set: $languageCodeCandidates")
      throw Exception("Failed to locate onboarding image set.")
    }

    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(releaseChannelRecipientId))

    Log.i(TAG, "Inserting messages...")
    val insertResults: List<MessageDatabase.InsertResult> = (0 until candidateArray.length()).mapNotNull {
      val insertResult: MessageDatabase.InsertResult? = ReleaseChannel.insertReleaseChannelMessage(
        releaseChannelRecipientId,
        "",
        threadId,
        "$ONBOARDING_IMAGE_PATH/$version/${candidateArray.getString(it)}$ONBOARDING_EXTENSION",
        ONBOARDING_IMAGE_WIDTH,
        ONBOARDING_IMAGE_HEIGHT,
        storyType = StoryType.STORY_WITHOUT_REPLIES
      )

      Thread.sleep(5)

      insertResult
    }

    if (insertResults.size != ONBOARDING_IMAGE_COUNT) {
      Log.w(TAG, "Failed to insert some search results. Deleting the ones we added and trying again later.")
      insertResults.forEach {
        SignalDatabase.mms.deleteMessage(it.messageId)
      }

      throw RetryLaterException()
    }

    Log.d(TAG, "Marking onboarding story downloaded.")
    SignalStore.storyValues().hasDownloadedOnboardingStory = true

    Log.i(TAG, "Enqueueing download jobs...")
    insertResults.forEach { insertResult ->
      SignalDatabase.attachments.getAttachmentsForMessage(insertResult.messageId).forEach {
        ApplicationDependencies.getJobManager().add(AttachmentDownloadJob(insertResult.messageId, it.attachmentId, true))
      }
    }
  }

  override fun onShouldRetry(e: Exception): Boolean = e is RetryLaterException

  private fun getLocaleCodes(): List<String> {
    val localeList: LocaleListCompat = LocaleListCompat.getDefault()

    val potentialOnboardingUrlLanguages = mutableListOf<String>()

    if (SignalStore.settings().language != "zz") {
      potentialOnboardingUrlLanguages += SignalStore.settings().language
    }

    for (index in 0 until localeList.size()) {
      val locale: Locale = localeList.get(index) ?: continue
      if (locale.language.isNotEmpty()) {
        if (locale.country.isNotEmpty()) {
          potentialOnboardingUrlLanguages += "${locale.language}_${locale.country}"
        }
        potentialOnboardingUrlLanguages += locale.language
      }
    }

    potentialOnboardingUrlLanguages += "en"

    return potentialOnboardingUrlLanguages
  }

  class Factory : Job.Factory<StoryOnboardingDownloadJob> {
    override fun create(parameters: Parameters, data: Data): StoryOnboardingDownloadJob {
      return StoryOnboardingDownloadJob(parameters)
    }
  }
}
