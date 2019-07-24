/*
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.securesms.logsubmit;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.logsubmit.util.Scrubber;
import org.thoughtcrime.securesms.util.BucketInfo;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * A helper {@link Fragment} to preview and submit logcat information to a public pastebin.
 * Activities that contain this fragment must implement the
 * {@link SubmitLogFragment.OnLogSubmittedListener} interface
 * to handle interaction events.
 * Use the {@link SubmitLogFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class SubmitLogFragment extends Fragment {

  private static final String TAG = SubmitLogFragment.class.getSimpleName();

  private static final String API_ENDPOINT = "https://debuglogs.org";

  private static final String HEADER_SYSINFO = "========== SYSINFO ========";
  private static final String HEADER_JOBS    = "=========== JOBS =========";
  private static final String HEADER_POWER   = "========== POWER =========";
  private static final String HEADER_LOGCAT  = "========== LOGCAT ========";
  private static final String HEADER_LOGGER  = "========== LOGGER ========";

  private Button   okButton;
  private Button   cancelButton;
  private View     scrollButton;
  private String   supportEmailAddress;
  private String   supportEmailSubject;
  private String   hackSavedLogUrl;
  private boolean  emailActivityWasStarted = false;


  private RecyclerView           logPreview;
  private LogPreviewAdapter      logPreviewAdapter;
  private OnLogSubmittedListener mListener;

  /**
   * Use this factory method to create a new instance of
   * this fragment using the provided parameters.
   *
   * @return A new instance of fragment SubmitLogFragment.
   */
  public static SubmitLogFragment newInstance(String supportEmailAddress,
                                              String supportEmailSubject)
  {
    SubmitLogFragment fragment = new SubmitLogFragment();

    fragment.supportEmailAddress = supportEmailAddress;
    fragment.supportEmailSubject = supportEmailSubject;

    return fragment;
  }

  public static SubmitLogFragment newInstance()
  {
    return newInstance(null, null);
  }

  public SubmitLogFragment() { }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    // Inflate the layout for this fragment
    return inflater.inflate(R.layout.fragment_submit_log, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    initializeResources();
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    try {
      mListener = (OnLogSubmittedListener) activity;
    } catch (ClassCastException e) {
      throw new ClassCastException(activity.toString() + " must implement OnFragmentInteractionListener");
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    if (emailActivityWasStarted && mListener != null)
      mListener.onSuccess();
  }

  @Override
  public void onDetach() {
    super.onDetach();
    mListener = null;
  }

  private void initializeResources() {
    okButton     = getView().findViewById(R.id.ok);
    cancelButton = getView().findViewById(R.id.cancel);
    logPreview   = getView().findViewById(R.id.log_preview);
    scrollButton = getView().findViewById(R.id.scroll_to_bottom_button);

    okButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        new SubmitToPastebinAsyncTask(logPreviewAdapter.getText()).execute();
      }
    });

    cancelButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (mListener != null) mListener.onCancel();
      }
    });

    scrollButton.setOnClickListener(v -> logPreview.scrollToPosition(logPreviewAdapter.getItemCount() - 1));

    logPreview.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition() < logPreviewAdapter.getItemCount() - 10) {
          scrollButton.setVisibility(View.VISIBLE);
        } else {
          scrollButton.setVisibility(View.GONE);
        }
      }
    });

    logPreviewAdapter = new LogPreviewAdapter();

    logPreview.setLayoutManager(new LinearLayoutManager(getContext()));
    logPreview.setAdapter(logPreviewAdapter);

    new PopulateLogcatAsyncTask(getActivity()).execute();
  }

  private static String grabLogcat() {
    try {
      final Process         process        = Runtime.getRuntime().exec("logcat -d");
      final BufferedReader  bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      final StringBuilder   log            = new StringBuilder();
      final String          separator      = System.getProperty("line.separator");

      String line;
      while ((line = bufferedReader.readLine()) != null) {
        log.append(line);
        log.append(separator);
      }
      return log.toString();
    } catch (IOException ioe) {
      Log.w(TAG, "IOException when trying to read logcat.", ioe);
      return null;
    }
  }

  private Intent getIntentForSupportEmail(String logUrl) {
    Intent emailSendIntent = new Intent(Intent.ACTION_SEND);

    emailSendIntent.putExtra(Intent.EXTRA_EMAIL,   new String[] { supportEmailAddress });
    emailSendIntent.putExtra(Intent.EXTRA_SUBJECT, supportEmailSubject);
    emailSendIntent.putExtra(
        Intent.EXTRA_TEXT,
        getString(R.string.log_submit_activity__please_review_this_log_from_my_app, logUrl)
    );
    emailSendIntent.setType("message/rfc822");

    return emailSendIntent;
  }

  private void handleShowChooserForIntent(final Intent intent, String chooserTitle) {
    final AlertDialog.Builder    builder = new AlertDialog.Builder(getActivity());
    final ShareIntentListAdapter adapter = ShareIntentListAdapter.getAdapterForIntent(getActivity(), intent);

    builder.setTitle(chooserTitle)
           .setAdapter(adapter, new DialogInterface.OnClickListener() {

             @Override
             public void onClick(DialogInterface dialog, int which) {
               ActivityInfo info = adapter.getItem(which).activityInfo;
               intent.setClassName(info.packageName, info.name);
               startActivity(intent);

               emailActivityWasStarted = true;
             }

           })
           .setOnCancelListener(new DialogInterface.OnCancelListener() {

             @Override
             public void onCancel(DialogInterface dialogInterface) {
               if (hackSavedLogUrl != null)
                 handleShowSuccessDialog(hackSavedLogUrl);
             }

           })
           .create().show();
  }

  private TextView handleBuildSuccessTextView(final String logUrl) {
    TextView showText = new TextView(getActivity());

    showText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
    showText.setPadding(15, 30, 15, 30);
    showText.setText(getString(R.string.log_submit_activity__copy_this_url_and_add_it_to_your_issue, logUrl));
    showText.setAutoLinkMask(Activity.RESULT_OK);
    showText.setMovementMethod(LinkMovementMethod.getInstance());
    showText.setOnLongClickListener(new View.OnLongClickListener() {

      @Override
      public boolean onLongClick(View v) {
        @SuppressWarnings("deprecation")
        ClipboardManager manager =
            (ClipboardManager) getActivity().getSystemService(Activity.CLIPBOARD_SERVICE);
        manager.setText(logUrl);
        Toast.makeText(getActivity(),
                       R.string.log_submit_activity__copied_to_clipboard,
                       Toast.LENGTH_SHORT).show();
        return true;
      }
    });

    Linkify.addLinks(showText, Linkify.WEB_URLS);
    return showText;
  }

  private void handleShowSuccessDialog(final String logUrl) {
    TextView            showText = handleBuildSuccessTextView(logUrl);
    AlertDialog.Builder builder  = new AlertDialog.Builder(getActivity());

    builder.setTitle(R.string.log_submit_activity__success)
           .setView(showText)
           .setCancelable(false)
           .setNeutralButton(R.string.log_submit_activity__button_got_it, new DialogInterface.OnClickListener() {
             @Override
             public void onClick(DialogInterface dialogInterface, int i) {
               dialogInterface.dismiss();
               if (mListener != null) mListener.onSuccess();
             }
           });
    if (supportEmailAddress != null) {
      builder.setPositiveButton(R.string.log_submit_activity__button_compose_email, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
          handleShowChooserForIntent(
              getIntentForSupportEmail(logUrl),
              getString(R.string.log_submit_activity__choose_email_app)
          );
        }
      });
    }

    builder.create().show();
    hackSavedLogUrl = logUrl;
  }

  private class PopulateLogcatAsyncTask extends AsyncTask<Void,Void,String> {
    private WeakReference<Context> weakContext;

    public PopulateLogcatAsyncTask(Context context) {
      this.weakContext = new WeakReference<>(context);
    }

    @Override
    protected String doInBackground(Void... voids) {
      Context context = weakContext.get();
      if (context == null) return null;

      Scrubber scrubber = new Scrubber();

      String newLogs;
      try {
        long t1 = System.currentTimeMillis();
        String logs = ApplicationContext.getInstance(context).getPersistentLogger().getLogs().get();
        Log.i(TAG, "Fetch our logs : " + (System.currentTimeMillis() - t1) + " ms");

        long t2 = System.currentTimeMillis();
        newLogs = scrubber.scrub(logs);
        Log.i(TAG, "Scrub our logs: " + (System.currentTimeMillis() - t2) + " ms");
      } catch (InterruptedException | ExecutionException e) {
        Log.w(TAG, "Failed to retrieve new logs.", e);
        newLogs = "Failed to retrieve logs.";
      }

      long t3 = System.currentTimeMillis();
      String logcat = grabLogcat();
      Log.i(TAG, "Fetch logcat: " + (System.currentTimeMillis() - t3) + " ms");

      long t4 = System.currentTimeMillis();
      String scrubbedLogcat = scrubber.scrub(logcat);
      Log.i(TAG, "Scrub logcat: " + (System.currentTimeMillis() - t4) + " ms");


      StringBuilder stringBuilder = new StringBuilder();

      stringBuilder.append(HEADER_SYSINFO)
                   .append("\n\n")
                   .append(buildDescription(context))
                   .append("\n\n\n")
                   .append(HEADER_JOBS)
                   .append("\n\n")
                   .append(scrubber.scrub(ApplicationContext.getInstance(context).getJobManager().getDebugInfo()))
                   .append("\n\n\n");

      if (VERSION.SDK_INT >= 28) {
        stringBuilder.append(HEADER_POWER)
                     .append("\n\n")
                     .append(buildPower(context))
                     .append("\n\n\n");
      }

      stringBuilder.append(HEADER_LOGCAT)
                   .append("\n\n")
                   .append(scrubbedLogcat)
                   .append("\n\n\n")
                   .append(HEADER_LOGGER)
                   .append("\n\n")
                   .append(newLogs);

      return stringBuilder.toString();
    }

    @Override
    protected void onPreExecute() {
      super.onPreExecute();
      logPreviewAdapter.setText(getString(R.string.log_submit_activity__loading_logs));
      okButton.setEnabled(false);
    }

    @Override
    protected void onPostExecute(String logcat) {
      super.onPostExecute(logcat);
      if (TextUtils.isEmpty(logcat)) {
        if (mListener != null) mListener.onFailure();
        return;
      }
      logPreviewAdapter.setText(logcat);
      okButton.setEnabled(true);
    }
  }

  private class SubmitToPastebinAsyncTask extends ProgressDialogAsyncTask<Void,Void,String> {
    private final String         paste;

    public SubmitToPastebinAsyncTask(String paste) {
      super(getActivity(), R.string.log_submit_activity__submitting, R.string.log_submit_activity__uploading_logs);
      this.paste = paste;
    }

    @Override
    protected String doInBackground(Void... voids) {
      try {
        OkHttpClient client   = new OkHttpClient.Builder().build();
        Response     response = client.newCall(new Request.Builder().url(API_ENDPOINT).get().build()).execute();
        ResponseBody body     = response.body();

        if (!response.isSuccessful() || body == null) {
          throw new IOException("Unsuccessful response: " + response);
        }

        JSONObject            json   = new JSONObject(body.string());
        String                url    = json.getString("url");
        JSONObject            fields = json.getJSONObject("fields");
        String                item   = fields.getString("key");
        MultipartBody.Builder post   = new MultipartBody.Builder();
        Iterator<String>      keys   = fields.keys();

        post.addFormDataPart("Content-Type", "text/plain");

        while (keys.hasNext()) {
          String key = keys.next();
          post.addFormDataPart(key, fields.getString(key));
        }

        post.addFormDataPart("file", "file", RequestBody.create(MediaType.parse("text/plain"), paste));

        Response postResponse = client.newCall(new Request.Builder().url(url).post(post.build()).build()).execute();

        if (!postResponse.isSuccessful()) {
          throw new IOException("Bad response: " + postResponse);
        }

        return API_ENDPOINT + "/" + item;
      } catch (IOException | JSONException e) {
        Log.w("ImageActivity", e);
      }
      return null;
    }

    @Override
    protected void onPostExecute(final String response) {
      super.onPostExecute(response);

      if (response != null)
        handleShowSuccessDialog(response);
      else {
        Log.w(TAG, "Response was null from Gist API.");
        Toast.makeText(getActivity(), R.string.log_submit_activity__network_failure, Toast.LENGTH_LONG).show();
      }
    }
  }

  private static long asMegs(long bytes) {
    return bytes / 1048576L;
  }

  public static String getMemoryUsage(Context context) {
    Runtime info        = Runtime.getRuntime();
    long    totalMemory = info.totalMemory();
    return String.format(Locale.ENGLISH, "%dM (%.2f%% free, %dM max)",
                         asMegs(totalMemory),
                         (float)info.freeMemory() / totalMemory * 100f,
                         asMegs(info.maxMemory()));
  }

  @TargetApi(VERSION_CODES.KITKAT)
  public static String getMemoryClass(Context context) {
    ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    String          lowMem          = "";

    if (VERSION.SDK_INT >= VERSION_CODES.KITKAT && activityManager.isLowRamDevice()) {
      lowMem = ", low-mem device";
    }
    return activityManager.getMemoryClass() + lowMem;
  }

  private static CharSequence buildDescription(Context context) {
    final PackageManager pm      = context.getPackageManager();
    final StringBuilder  builder = new StringBuilder();

    builder.append("Time    : ").append(System.currentTimeMillis()).append('\n');
    builder.append("Device  : ")
           .append(Build.MANUFACTURER).append(" ")
           .append(Build.MODEL).append(" (")
           .append(Build.PRODUCT).append(")\n");
    builder.append("Android : ").append(VERSION.RELEASE).append(" (")
                               .append(VERSION.INCREMENTAL).append(", ")
                               .append(Build.DISPLAY).append(")\n");
    builder.append("ABIs    : ").append(TextUtils.join(", ", getSupportedAbis())).append("\n");
    builder.append("Memory  : ").append(getMemoryUsage(context)).append("\n");
    builder.append("Memclass: ").append(getMemoryClass(context)).append("\n");
    builder.append("OS Host : ").append(Build.HOST).append("\n");
    builder.append("App     : ");
    try {
      builder.append(pm.getApplicationLabel(pm.getApplicationInfo(context.getPackageName(), 0)))
             .append(" ")
             .append(pm.getPackageInfo(context.getPackageName(), 0).versionName)
             .append(" (")
             .append(Util.getManifestApkVersion(context))
             .append(")\n");
    } catch (PackageManager.NameNotFoundException nnfe) {
      builder.append("Unknown\n");
    }

    return builder;
  }

  @RequiresApi(28)
  private static CharSequence buildPower(@NonNull Context context) {
    final UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

    if (usageStatsManager == null) {
      return "UsageStatsManager not available";
    }

    BucketInfo info = BucketInfo.getInfo(usageStatsManager, TimeUnit.DAYS.toMillis(3));

    return new StringBuilder().append("Current bucket: ").append(BucketInfo.bucketToString(info.getCurrentBucket())).append('\n')
                              .append("Highest bucket: ").append(BucketInfo.bucketToString(info.getBestBucket())).append('\n')
                              .append("Lowest bucket : ").append(BucketInfo.bucketToString(info.getWorstBucket())).append("\n\n")
                              .append(info.getHistory());
  }

  private static Iterable<String> getSupportedAbis() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return Arrays.asList(Build.SUPPORTED_ABIS);
    } else {
      LinkedList<String> abis = new LinkedList<>();
      abis.add(Build.CPU_ABI);
      if (Build.CPU_ABI2 != null && !"unknown".equals(Build.CPU_ABI2)) {
        abis.add(Build.CPU_ABI2);
      }
      return abis;
    }
  }

  /**
   * This interface must be implemented by activities that contain this
   * fragment to allow an interaction in this fragment to be communicated
   * to the activity and potentially other fragments contained in that
   * activity.
   * <p>
   * See the Android Training lesson <a href=
   * "http://developer.android.com/training/basics/fragments/communicating.html"
   * >Communicating with Other Fragments</a> for more information.
   */
  public interface OnLogSubmittedListener {
    public void onSuccess();
    public void onFailure();
    public void onCancel();
  }

  private static final class LogPreviewAdapter extends RecyclerView.Adapter<LogPreviewViewHolder> {

    private String[] lines = new String[0];

    @Override
    public LogPreviewViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      return new LogPreviewViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log_preview, parent, false));
    }

    @Override
    public void onBindViewHolder(LogPreviewViewHolder holder, int position) {
      holder.bind(lines, position);
    }

    @Override
    public void onViewRecycled(LogPreviewViewHolder holder) {
      holder.unbind();
    }

    @Override
    public int getItemCount() {
      return lines.length;
    }

    void setText(@NonNull String text) {
      lines = text.split("\n");
      notifyDataSetChanged();
    }

    String getText() {
      return Util.join(lines, "\n");
    }
  }

  private static final class LogPreviewViewHolder extends RecyclerView.ViewHolder {

    private EditText text;
    private String[] lines;
    private int      index;

    LogPreviewViewHolder(View itemView) {
      super(itemView);
      text = (EditText) itemView;
    }

    void bind(String[] lines, int index) {
      this.lines = lines;
      this.index = index;

      text.setText(lines[index]);
      text.addTextChangedListener(textWatcher);
    }

    void unbind() {
      text.removeTextChangedListener(textWatcher);
    }

    private final SimpleTextWatcher textWatcher = new SimpleTextWatcher() {
      @Override
      public void onTextChanged(String text) {
        if (lines != null) {
          lines[index] = text;
        }
      }
    };
  }
}
