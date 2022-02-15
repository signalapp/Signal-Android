package org.thoughtcrime.securesms.preferences;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.Fragment;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.backup.BackupDialog;
import org.thoughtcrime.securesms.backup.FullBackupBase;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.LocalBackupJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.util.BackupUtil;
import org.thoughtcrime.securesms.util.StorageUtil;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

public class BackupsPreferenceFragment extends Fragment {

  private static final String TAG = Log.tag(BackupsPreferenceFragment.class);

  private static final short CHOOSE_BACKUPS_LOCATION_REQUEST_CODE = 26212;

  private View        create;
  private View        folder;
  private View        verify;
  private TextView    toggle;
  private TextView    info;
  private TextView    summary;
  private TextView    folderName;
  private ProgressBar progress;
  private TextView    progressSummary;

  private final NumberFormat formatter = NumberFormat.getInstance();

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_backups, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    create          = view.findViewById(R.id.fragment_backup_create);
    folder          = view.findViewById(R.id.fragment_backup_folder);
    verify          = view.findViewById(R.id.fragment_backup_verify);
    toggle          = view.findViewById(R.id.fragment_backup_toggle);
    info            = view.findViewById(R.id.fragment_backup_info);
    summary         = view.findViewById(R.id.fragment_backup_create_summary);
    folderName      = view.findViewById(R.id.fragment_backup_folder_name);
    progress        = view.findViewById(R.id.fragment_backup_progress);
    progressSummary = view.findViewById(R.id.fragment_backup_progress_summary);

    toggle.setOnClickListener(unused -> onToggleClicked());
    create.setOnClickListener(unused -> onCreateClicked());
    verify.setOnClickListener(unused -> BackupDialog.showVerifyBackupPassphraseDialog(requireContext()));

    formatter.setMinimumFractionDigits(1);
    formatter.setMaximumFractionDigits(1);

    EventBus.getDefault().register(this);
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void onResume() {
    super.onResume();

    setBackupStatus();
    setBackupSummary();
    setInfo();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    EventBus.getDefault().unregister(this);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (Build.VERSION.SDK_INT >= 29                         &&
        requestCode == CHOOSE_BACKUPS_LOCATION_REQUEST_CODE &&
        resultCode == Activity.RESULT_OK                    &&
        data != null                                        &&
        data.getData() != null)
    {
      BackupDialog.showEnableBackupDialog(requireContext(),
                                          data,
                                          StorageUtil.getDisplayPath(requireContext(), data.getData()),
                                          this::setBackupsEnabled);
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEvent(FullBackupBase.BackupEvent event) {
    if (event.getType() == FullBackupBase.BackupEvent.Type.PROGRESS) {
      create.setEnabled(false);
      summary.setText(getString(R.string.BackupsPreferenceFragment__in_progress));
      progress.setVisibility(View.VISIBLE);
      progressSummary.setVisibility(event.getCount() > 0 ? View.VISIBLE : View.GONE);

      if (event.getEstimatedTotalCount() == 0) {
        progress.setIndeterminate(true);
        progressSummary.setText(getString(R.string.BackupsPreferenceFragment__d_so_far, event.getCount()));
      } else {
        double completionPercentage = event.getCompletionPercentage();

        progress.setIndeterminate(false);
        progress.setMax(100);
        progress.setProgress((int) completionPercentage);
        progressSummary.setText(getString(R.string.BackupsPreferenceFragment__s_so_far, formatter.format(completionPercentage)));
      }
    } else if (event.getType() == FullBackupBase.BackupEvent.Type.FINISHED) {
      create.setEnabled(true);
      progress.setVisibility(View.GONE);
      progressSummary.setVisibility(View.GONE);
      setBackupSummary();
    }
  }

  private void setBackupStatus() {
    if (SignalStore.settings().isBackupEnabled()) {
      if (BackupUtil.canUserAccessBackupDirectory(requireContext())) {
        setBackupsEnabled();
      } else {
        Log.w(TAG, "Cannot access backup directory. Disabling backups.");

        BackupUtil.disableBackups(requireContext());
        setBackupsDisabled();
      }
    } else {
      setBackupsDisabled();
    }
  }

  private void setBackupSummary() {
    summary.setText(getString(R.string.BackupsPreferenceFragment__last_backup, BackupUtil.getLastBackupTime(requireContext(), Locale.getDefault())));
  }

  private void setBackupFolderName() {
    folder.setVisibility(View.GONE);

    if (BackupUtil.canUserAccessBackupDirectory(requireContext())) {
      if (BackupUtil.isUserSelectionRequired(requireContext()) &&
          BackupUtil.canUserAccessBackupDirectory(requireContext()))
      {
        Uri backupUri = Objects.requireNonNull(SignalStore.settings().getSignalBackupDirectory());

        folder.setVisibility(View.VISIBLE);
        folderName.setText(StorageUtil.getDisplayPath(requireContext(), backupUri));
      } else if (StorageUtil.canWriteInSignalStorageDir()) {
        try {
          folder.setVisibility(View.VISIBLE);
          folderName.setText(StorageUtil.getOrCreateBackupDirectory().getPath());
        } catch (NoExternalStorageException e) {
          Log.w(TAG, "Could not display folder name.", e);
        }
      }
    }
  }

  private void setInfo() {
    String link     = String.format("<a href=\"%s\">%s</a>", getString(R.string.backup_support_url), getString(R.string.BackupsPreferenceFragment__learn_more));
    String infoText = getString(R.string.BackupsPreferenceFragment__to_restore_a_backup, link);

    info.setText(HtmlCompat.fromHtml(infoText, 0));
    info.setMovementMethod(LinkMovementMethod.getInstance());
  }

  private void onToggleClicked() {
    if (BackupUtil.isUserSelectionRequired(requireContext())) {
      onToggleClickedApi29();
    } else {
      onToggleClickedLegacy();
    }
  }

  @RequiresApi(29)
  private void onToggleClickedApi29() {
    if (!SignalStore.settings().isBackupEnabled()) {
      BackupDialog.showChooseBackupLocationDialog(this, CHOOSE_BACKUPS_LOCATION_REQUEST_CODE);
    } else {
      BackupDialog.showDisableBackupDialog(requireContext(), this::setBackupsDisabled);
    }
  }

  private void onToggleClickedLegacy() {
    Permissions.with(this)
               .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
               .ifNecessary()
               .onAllGranted(() -> {
                 if (!SignalStore.settings().isBackupEnabled()) {
                   BackupDialog.showEnableBackupDialog(requireContext(), null, null, this::setBackupsEnabled);
                 } else {
                   BackupDialog.showDisableBackupDialog(requireContext(), this::setBackupsDisabled);
                 }
               })
               .withPermanentDenialDialog(getString(R.string.BackupsPreferenceFragment_signal_requires_external_storage_permission_in_order_to_create_backups))
               .execute();
  }

  private void onCreateClicked() {
    if (BackupUtil.isUserSelectionRequired(requireContext())) {
      onCreateClickedApi29();
    } else {
      onCreateClickedLegacy();
    }
  }

  @RequiresApi(29)
  private void onCreateClickedApi29() {
    Log.i(TAG, "Queing backup...");
    LocalBackupJob.enqueue(true);
  }

  private void onCreateClickedLegacy() {
    Permissions.with(this)
               .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
               .ifNecessary()
               .onAllGranted(() -> {
                 Log.i(TAG, "Queuing backup...");
                 LocalBackupJob.enqueue(true);
               })
               .withPermanentDenialDialog(getString(R.string.BackupsPreferenceFragment_signal_requires_external_storage_permission_in_order_to_create_backups))
               .execute();
  }

  private void setBackupsEnabled() {
    toggle.setText(R.string.BackupsPreferenceFragment__turn_off);
    create.setVisibility(View.VISIBLE);
    verify.setVisibility(View.VISIBLE);
    setBackupFolderName();
  }

  private void setBackupsDisabled() {
    toggle.setText(R.string.BackupsPreferenceFragment__turn_on);
    create.setVisibility(View.GONE);
    folder.setVisibility(View.GONE);
    verify.setVisibility(View.GONE);
    ApplicationDependencies.getJobManager().cancelAllInQueue(LocalBackupJob.QUEUE);
  }
}
