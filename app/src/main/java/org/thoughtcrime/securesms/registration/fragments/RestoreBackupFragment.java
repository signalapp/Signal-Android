package org.thoughtcrime.securesms.registration.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.AppInitialization;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.backup.BackupEvent;
import org.thoughtcrime.securesms.backup.BackupPassphrase;
import org.thoughtcrime.securesms.backup.FullBackupImporter;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.service.LocalBackupListener;
import org.thoughtcrime.securesms.util.BackupUtil;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.Util;
import org.signal.core.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton;

import java.io.IOException;
import java.util.Locale;

import static org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView;

public final class RestoreBackupFragment extends LoggingFragment {

  private static final String TAG                            = Log.tag(RestoreBackupFragment.class);
  private static final short  OPEN_DOCUMENT_TREE_RESULT_CODE = 13782;

  private TextView                       restoreBackupSize;
  private TextView                       restoreBackupTime;
  private TextView                       restoreBackupProgress;
  private CircularProgressMaterialButton restoreButton;
  private View                           skipRestoreButton;
  private RegistrationViewModel          viewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_restore_backup, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    setDebugLogSubmitMultiTapView(view.findViewById(R.id.verify_header));

    Log.i(TAG, "Backup restore.");

    restoreBackupSize     = view.findViewById(R.id.backup_size_text);
    restoreBackupTime     = view.findViewById(R.id.backup_created_text);
    restoreBackupProgress = view.findViewById(R.id.backup_progress_text);
    restoreButton         = view.findViewById(R.id.restore_button);
    skipRestoreButton     = view.findViewById(R.id.skip_restore_button);

    skipRestoreButton.setOnClickListener((v) -> {
                       Log.i(TAG, "User skipped backup restore.");
                       SafeNavigation.safeNavigate(Navigation.findNavController(view),
                                                   RestoreBackupFragmentDirections.actionSkip());
                     });

    viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

    if (viewModel.isReregister()) {
      Log.i(TAG, "Skipping backup restore during re-register.");
      SafeNavigation.safeNavigate(Navigation.findNavController(view),
                                  RestoreBackupFragmentDirections.actionSkipNoReturn());
      return;
    }

    if (viewModel.hasBackupCompleted()) {
      onBackupComplete();
      return;
    }

    if (SignalStore.settings().isBackupEnabled()) {
      Log.i(TAG, "Backups enabled, so a backup must have been previously restored.");
      SafeNavigation.safeNavigate(Navigation.findNavController(view),
                                  RestoreBackupFragmentDirections.actionSkipNoReturn());
      return;
    }

    RestoreBackupFragmentArgs args = RestoreBackupFragmentArgs.fromBundle(requireArguments());
    if ((Build.VERSION.SDK_INT < 29 || BackupUtil.isUserSelectionRequired(requireContext())) && args.getUri() != null) {
      Log.i(TAG, "Restoring backup from passed uri");
      initializeBackupForUri(view, args.getUri());

      return;
    }

    if (BackupUtil.canUserAccessBackupDirectory(requireContext())) {
      initializeBackupDetection(view);
    } else {
      Log.i(TAG, "Skipping backup detection. We don't have the permission.");
      SafeNavigation.safeNavigate(Navigation.findNavController(view),
                                  RestoreBackupFragmentDirections.actionSkipNoReturn());
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == OPEN_DOCUMENT_TREE_RESULT_CODE && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
      Uri backupDirectoryUri = data.getData();
      int takeFlags          = Intent.FLAG_GRANT_READ_URI_PERMISSION |
                               Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

      SignalStore.settings().setSignalBackupDirectory(backupDirectoryUri);
      requireContext().getContentResolver()
                      .takePersistableUriPermission(backupDirectoryUri, takeFlags);

      enableBackups(requireContext());

      SafeNavigation.safeNavigate(Navigation.findNavController(requireView()),
                                  RestoreBackupFragmentDirections.actionBackupRestored());
    }
  }

  private void initializeBackupForUri(@NonNull View view, @NonNull Uri uri) {
    getFromUri(requireContext(), uri, backup -> handleBackupInfo(view, backup));
  }

  @SuppressLint("StaticFieldLeak")
  private void initializeBackupDetection(@NonNull View view) {
    searchForBackup(backup -> handleBackupInfo(view, backup));
  }

  private void handleBackupInfo(@NonNull View view, @Nullable BackupUtil.BackupInfo backup) {
    Context context = getContext();
    if (context == null) {
      Log.i(TAG, "No context on fragment, must have navigated away.");
      return;
    }

    if (backup == null) {
      Log.i(TAG, "Skipping backup detection. No backup found, or permission revoked since.");
      SafeNavigation.safeNavigate(Navigation.findNavController(view),
                                  RestoreBackupFragmentDirections.actionNoBackupFound());
    } else {
      restoreBackupSize.setText(getString(R.string.RegistrationActivity_backup_size_s, Util.getPrettyFileSize(backup.getSize())));
      restoreBackupTime.setText(getString(R.string.RegistrationActivity_backup_timestamp_s, DateUtils.getExtendedRelativeTimeSpanString(requireContext(), Locale.getDefault(), backup.getTimestamp())));

      restoreButton.setOnClickListener((v) -> handleRestore(v.getContext(), backup));
    }
  }

  interface OnBackupSearchResultListener {

    @MainThread
    void run(@Nullable BackupUtil.BackupInfo backup);
  }

  static void searchForBackup(@NonNull OnBackupSearchResultListener listener) {
    new AsyncTask<Void, Void, BackupUtil.BackupInfo>() {
      @Override
      protected @Nullable
      BackupUtil.BackupInfo doInBackground(Void... voids) {
        try {
          return BackupUtil.getLatestBackup();
        } catch (NoExternalStorageException e) {
          Log.w(TAG, e);
          return null;
        }
      }

      @Override
      protected void onPostExecute(@Nullable BackupUtil.BackupInfo backup) {
        listener.run(backup);
      }
    }.execute();
  }

  static void getFromUri(@NonNull Context context,
                         @NonNull Uri backupUri,
                         @NonNull OnBackupSearchResultListener listener)
  {
    SimpleTask.run(() -> {
                     try {
                       return BackupUtil.getBackupInfoFromSingleUri(context, backupUri);
                     } catch (BackupUtil.BackupFileException e) {
                       Log.w(TAG, "Could not restore backup.", e);
                       postToastForBackupRestorationFailure(context, e);
                       return null;
                     }
                   },
                   listener::run);
  }

  private static void postToastForBackupRestorationFailure(@NonNull Context context, @NonNull BackupUtil.BackupFileException exception) {
    final @StringRes int errorResId;
    switch (exception.getState()) {
      case READABLE:
        throw new AssertionError("Unexpected error state.");
      case NOT_FOUND:
        errorResId = R.string.RestoreBackupFragment__backup_not_found;
        break;
      case UNSUPPORTED_FILE_EXTENSION:
        errorResId = R.string.RestoreBackupFragment__backup_has_a_bad_extension;
        break;
      default:
        errorResId = R.string.RestoreBackupFragment__backup_could_not_be_read;
    }

    ThreadUtil.postToMain(() -> Toast.makeText(context, errorResId, Toast.LENGTH_LONG).show());
  }

  private void handleRestore(@NonNull Context context, @NonNull BackupUtil.BackupInfo backup) {
    View     view   = LayoutInflater.from(context).inflate(R.layout.enter_backup_passphrase_dialog, null);
    EditText prompt = view.findViewById(R.id.restore_passphrase_input);

    prompt.addTextChangedListener(new PassphraseAsYouTypeFormatter());

    new AlertDialog.Builder(context)
                   .setTitle(R.string.RegistrationActivity_enter_backup_passphrase)
                   .setView(view)
                   .setPositiveButton(R.string.RegistrationActivity_restore, (dialog, which) -> {
                     InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                     inputMethodManager.hideSoftInputFromWindow(prompt.getWindowToken(), 0);

                     restoreButton.setSpinning();
                     skipRestoreButton.setVisibility(View.INVISIBLE);

                     String passphrase = prompt.getText().toString();

                     restoreAsynchronously(context, backup, passphrase);
                   })
                   .setNegativeButton(android.R.string.cancel, null)
                   .show();

    Log.i(TAG, "Prompt for backup passphrase shown to user.");
  }

  @SuppressLint("StaticFieldLeak")
  private void restoreAsynchronously(@NonNull Context context,
                                     @NonNull BackupUtil.BackupInfo backup,
                                     @NonNull String passphrase)
  {
    new AsyncTask<Void, Void, BackupImportResult>() {
      @Override
      protected BackupImportResult doInBackground(Void... voids) {
        try {
          Log.i(TAG, "Starting backup restore.");

          SQLiteDatabase database = SignalDatabase.getBackupDatabase();

          BackupPassphrase.set(context, passphrase);
          FullBackupImporter.importFile(context,
                                        AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret(),
                                        database,
                                        backup.getUri(),
                                        passphrase);

          SignalDatabase.upgradeRestored(database);
          NotificationChannels.getInstance().restoreContactNotificationChannels();

          enableBackups(context);

          AppInitialization.onPostBackupRestore(context);

          Log.i(TAG, "Backup restore complete.");
          return BackupImportResult.SUCCESS;
        } catch (FullBackupImporter.DatabaseDowngradeException e) {
          Log.w(TAG, "Failed due to the backup being from a newer version of Signal.", e);
          return BackupImportResult.FAILURE_VERSION_DOWNGRADE;
        } catch (IOException e) {
          Log.w(TAG, e);
          return BackupImportResult.FAILURE_UNKNOWN;
        }
      }

      @Override
      protected void onPostExecute(@NonNull BackupImportResult result) {
        viewModel.markBackupCompleted();
        restoreButton.cancelSpinning();
        skipRestoreButton.setVisibility(View.VISIBLE);

        restoreBackupProgress.setText("");

        switch (result) {
          case SUCCESS:
            Log.i(TAG, "Successful backup restore.");
            break;
          case FAILURE_VERSION_DOWNGRADE:
            Toast.makeText(context, R.string.RegistrationActivity_backup_failure_downgrade, Toast.LENGTH_LONG).show();
            break;
          case FAILURE_UNKNOWN:
            Toast.makeText(context, R.string.RegistrationActivity_incorrect_backup_passphrase, Toast.LENGTH_LONG).show();
            break;
        }
      }
    }.execute();
  }

  @Override
  public void onStart() {
    super.onStart();
    EventBus.getDefault().register(this);
  }

  @Override
  public void onResume() {
    super.onResume();
    if (viewModel != null && viewModel.hasBackupCompleted()) {
      onBackupComplete();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    EventBus.getDefault().unregister(this);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEvent(@NonNull BackupEvent event) {
    long count = event.getCount();

    if (count == 0) {
      restoreBackupProgress.setText(R.string.RegistrationActivity_checking);
    } else {
      restoreBackupProgress.setText(getString(R.string.RegistrationActivity_d_messages_so_far, count));
    }

    restoreButton.setSpinning();
    skipRestoreButton.setVisibility(View.INVISIBLE);

    if (event.getType() == BackupEvent.Type.FINISHED) {
      onBackupComplete();
    }
  }

  private void onBackupComplete() {
    if (BackupUtil.isUserSelectionRequired(requireContext()) && !BackupUtil.canUserAccessBackupDirectory(requireContext())) {
      displayConfirmationDialog(requireContext());
    } else {
      SafeNavigation.safeNavigate(Navigation.findNavController(requireView()),
                                  RestoreBackupFragmentDirections.actionBackupRestored());
    }
  }

  private void enableBackups(@NonNull Context context) {
    if (BackupUtil.canUserAccessBackupDirectory(context)) {
      LocalBackupListener.setNextBackupTimeToIntervalFromNow(context);
      SignalStore.settings().setBackupEnabled(true);
      LocalBackupListener.schedule(context);
    }
  }

  @RequiresApi(29)
  private void displayConfirmationDialog(@NonNull Context context) {
    new AlertDialog.Builder(context)
                   .setTitle(R.string.RestoreBackupFragment__restore_complete)
                   .setMessage(R.string.RestoreBackupFragment__to_continue_using_backups_please_choose_a_folder)
                   .setPositiveButton(R.string.RestoreBackupFragment__choose_folder, (dialog, which) -> {
                     Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

                     intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                                     Intent.FLAG_GRANT_WRITE_URI_PERMISSION       |
                                     Intent.FLAG_GRANT_READ_URI_PERMISSION);

                     startActivityForResult(intent, OPEN_DOCUMENT_TREE_RESULT_CODE);
                   })
                   .setNegativeButton(R.string.RestoreBackupFragment__not_now, (dialog, which) -> {
                     BackupPassphrase.set(context, null);
                     dialog.dismiss();

                     SafeNavigation.safeNavigate(Navigation.findNavController(requireView()),
                                                 RestoreBackupFragmentDirections.actionBackupRestored());
                   })
                   .setCancelable(false)
                   .show();
  }

  private enum BackupImportResult {
    SUCCESS,
    FAILURE_VERSION_DOWNGRADE,
    FAILURE_UNKNOWN
  }

  public static class PassphraseAsYouTypeFormatter implements TextWatcher {

    private static final int GROUP_SIZE = 5;

    @Override
    public void afterTextChanged(Editable editable) {
      removeSpans(editable);

      addSpans(editable);
    }

    private static void removeSpans(Editable editable) {
      SpaceSpan[] paddingSpans = editable.getSpans(0, editable.length(), SpaceSpan.class);

      for (SpaceSpan span : paddingSpans) {
        editable.removeSpan(span);
      }
    }

    private static void addSpans(Editable editable) {
      final int length = editable.length();

      for (int i = GROUP_SIZE; i < length; i += GROUP_SIZE) {
        editable.setSpan(new SpaceSpan(), i - 1, i, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }

      if (editable.length() > BackupUtil.PASSPHRASE_LENGTH) {
        editable.delete(BackupUtil.PASSPHRASE_LENGTH, editable.length());
      }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }
  }

  /**
   * A {@link ReplacementSpan} adds a small space after a single character.
   * Based on https://stackoverflow.com/a/51949578
   */
  private static class SpaceSpan extends ReplacementSpan {

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
      return (int) (paint.measureText(text, start, end) * 1.7f);
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
      canvas.drawText(text.subSequence(start, end).toString(), x, y, paint);
    }
  }
}
