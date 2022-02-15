package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.EmojiSearchData;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.EmojiValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.dynamiclanguage.DynamicLanguageContextWrapper;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Downloads a new emoji search index based on our current version and language, if needed.
 */
public final class EmojiSearchIndexDownloadJob extends BaseJob {

  private static final String TAG = Log.tag(EmojiSearchIndexDownloadJob.class);

  public static final String KEY = "EmojiSearchIndexDownloadJob";

  private static final long INTERVAL_WITHOUT_INDEX = TimeUnit.DAYS.toMillis(1);
  private static final long INTERVAL_WITH_INDEX    = TimeUnit.DAYS.toMillis(7);

  private EmojiSearchIndexDownloadJob() {
    this(new Parameters.Builder()
                       .setQueue("EmojiSearchIndexDownloadJob")
                       .setMaxInstancesForFactory(2)
                       .addConstraint(NetworkConstraint.KEY)
                       .setLifespan(TimeUnit.DAYS.toMillis(1))
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .build());
  }

  private EmojiSearchIndexDownloadJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  public static void scheduleImmediately() {
    ApplicationDependencies.getJobManager().add(new EmojiSearchIndexDownloadJob());
  }

  public static void scheduleIfNecessary() {
    long    timeSinceCheck = System.currentTimeMillis() - SignalStore.emojiValues().getLastSearchIndexCheck();
    boolean needsCheck     = false;

    if (SignalStore.emojiValues().hasSearchIndex()) {
      needsCheck = timeSinceCheck > INTERVAL_WITH_INDEX;
    } else {
      needsCheck = timeSinceCheck > INTERVAL_WITHOUT_INDEX;
    }

    if (needsCheck) {
      Log.i(TAG, "Need to check. It's been " + timeSinceCheck + " ms since the last check.");
      scheduleImmediately();
    } else {
      Log.d(TAG, "Do not need to check. It's been " + timeSinceCheck + " ms since the last check.");
    }
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    OkHttpClient client = ApplicationDependencies.getOkHttpClient();

    Manifest manifest = downloadManifest(client);

    Locale locale         = DynamicLanguageContextWrapper.getUsersSelectedLocale(context);
    String remoteLanguage = findMatchingLanguage(locale, manifest.getLanguages());

    if (manifest.getVersion() == SignalStore.emojiValues().getSearchVersion() &&
        remoteLanguage.equals(SignalStore.emojiValues().getSearchLanguage()))
    {
      Log.i(TAG, "Already using the latest version of " + manifest.getVersion() + " with the correct language " + remoteLanguage);
      SignalStore.emojiValues().setLastSearchIndexCheck(System.currentTimeMillis());
      return;
    }

    Log.i(TAG, "Need to get a new search index. Downloading version: " + manifest.getVersion() + ", language: " + remoteLanguage);

    List<EmojiSearchData> searchIndex = downloadSearchIndex(client, manifest.getVersion(), remoteLanguage);

    SignalDatabase.emojiSearch().setSearchIndex(searchIndex);
    SignalStore.emojiValues().onSearchIndexUpdated(manifest.getVersion(), remoteLanguage);
    SignalStore.emojiValues().setLastSearchIndexCheck(System.currentTimeMillis());

    Log.i(TAG, "Success! Now at version: " + manifest.getVersion() + ", language: " + remoteLanguage);
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof IOException && !(e instanceof NonSuccessfulResponseCodeException);
  }

  @Override
  public void onFailure() {

  }

  private static @NonNull Manifest downloadManifest(@NonNull OkHttpClient client) throws IOException {
    String url  = "https://updates.signal.org/dynamic/android/emoji/search/manifest.json";
    String body = downloadFile(client, url);

    return JsonUtil.fromJson(body, Manifest.class);
  }

  private static @NonNull List<EmojiSearchData> downloadSearchIndex(@NonNull OkHttpClient client, int version, @NonNull String language) throws IOException {
    String url  = "https://updates.signal.org/static/android/emoji/search/" + version + "/" + language + ".json";
    String body = downloadFile(client, url);

    return Arrays.asList(JsonUtil.fromJson(body, EmojiSearchData[].class));
  }

  private static @NonNull String downloadFile(@NonNull OkHttpClient client, @NonNull String url) throws IOException {
    Call     call     = client.newCall(new Request.Builder().url(url).build());
    Response response = call.execute();

    if (response.code() != 200) {
      throw new NonSuccessfulResponseCodeException(response.code());
    }

    if (response.body() == null) {
      throw new NonSuccessfulResponseCodeException(404, "Missing body!");
    }

    return response.body().string();
  }

  private static @NonNull String findMatchingLanguage(@NonNull Locale locale, List<String> languages) {
    String parentLanguage = null;

    for (String language : languages) {
      Locale testLocale = new Locale(language);

      if (locale.getLanguage().equals(testLocale.getLanguage())) {
        if (locale.getVariant().equals(testLocale.getVariant())) {
          Log.d(TAG, "Found an exact match: " + language);
          return language;
        } else if (locale.getVariant().equals("")) {
          Log.d(TAG, "Found the parent language: " + language);
          parentLanguage = language;
        }
      }
    }

    if (parentLanguage != null) {
      Log.i(TAG, "No exact match found. Using parent language: " + parentLanguage);
      return parentLanguage;
    } else if (languages.contains("en")) {
      Log.w(TAG, "No match, so falling back to en locale.");
      return "en";
    } else if (languages.contains("en_US")) {
      Log.w(TAG, "No match, so falling back to en_US locale.");
      return "en_US";
    } else {
      Log.w(TAG, "No match and no english fallback! Must return no language!");
      return EmojiValues.NO_LANGUAGE;
    }
  }

  private static class Manifest {
    @JsonProperty
    private int version;

    @JsonProperty
    private List<String> languages;

    public Manifest() {}

    public int getVersion() {
      return version;
    }

    public @NonNull List<String> getLanguages() {
      return languages != null ? languages : Collections.emptyList();
    }
  }

  public static final class Factory implements Job.Factory<EmojiSearchIndexDownloadJob> {
    @Override
    public @NonNull EmojiSearchIndexDownloadJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new EmojiSearchIndexDownloadJob(parameters);
    }
  }
}
