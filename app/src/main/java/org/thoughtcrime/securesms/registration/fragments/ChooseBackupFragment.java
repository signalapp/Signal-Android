package org.thoughtcrime.securesms.registration.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.text.HtmlCompat;
import androidx.navigation.Navigation;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.documents.Document;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.BackupUtil;

public class ChooseBackupFragment extends BaseRegistrationFragment {

  private static final String TAG = Log.tag(ChooseBackupFragment.class);

  private static final short OPEN_FILE_REQUEST_CODE = 3862;

  private View     chooseBackupButton;
  private TextView learnMore;

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.fragment_registration_choose_backup, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    if (BackupUtil.isUserSelectionRequired(requireContext())) {
      chooseBackupButton = view.findViewById(R.id.choose_backup_fragment_button);
      chooseBackupButton.setOnClickListener(this::onChooseBackupSelected);

      learnMore = view.findViewById(R.id.choose_backup_fragment_learn_more);
      learnMore.setText(HtmlCompat.fromHtml(String.format("<a href=\"%s\">%s</a>", getString(R.string.backup_support_url), getString(R.string.ChooseBackupFragment__learn_more)), 0));
      learnMore.setMovementMethod(LinkMovementMethod.getInstance());
    } else {
      Log.i(TAG, "User Selection is not required. Skipping.");
      Navigation.findNavController(requireView()).navigate(ChooseBackupFragmentDirections.actionSkip());
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
      ChooseBackupFragmentDirections.ActionRestore restore = ChooseBackupFragmentDirections.actionRestore();

      restore.setUri(data.getData());

      Navigation.findNavController(requireView()).navigate(restore);
    }
  }

  @RequiresApi(21)
  private void onChooseBackupSelected(@NonNull View view) {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);

    intent.setType("application/octet-stream");
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);

    if (Build.VERSION.SDK_INT >= 26) {
      intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, SignalStore.settings().getLatestSignalBackupDirectory());
    }

    startActivityForResult(intent, OPEN_FILE_REQUEST_CODE);
  }
}
