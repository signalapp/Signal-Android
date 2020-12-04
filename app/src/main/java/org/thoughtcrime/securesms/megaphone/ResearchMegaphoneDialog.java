package org.thoughtcrime.securesms.megaphone;

import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.FullScreenDialogFragment;
import org.thoughtcrime.securesms.util.CommunicationActions;

public class ResearchMegaphoneDialog extends FullScreenDialogFragment {

  private static final String SURVEY_URL = "https://surveys.signalusers.org/s3";

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    TextView content = view.findViewById(R.id.research_megaphone_content);
    content.setText(Html.fromHtml(requireContext().getString(R.string.ResearchMegaphoneDialog_we_believe_in_privacy)));

    view.findViewById(R.id.research_megaphone_dialog_take_the_survey)
        .setOnClickListener(v -> CommunicationActions.openBrowserLink(requireContext(), SURVEY_URL));

    view.findViewById(R.id.research_megaphone_dialog_no_thanks)
        .setOnClickListener(v -> dismissAllowingStateLoss());
  }

  @Override
  protected @StringRes int getTitle() {
    return R.string.ResearchMegaphoneDialog_signal_research;
  }

  @Override
  protected int getDialogLayoutResource() {
    return R.layout.research_megaphone_dialog;
  }
}
