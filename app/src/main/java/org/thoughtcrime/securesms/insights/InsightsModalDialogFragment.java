package org.thoughtcrime.securesms.insights;

import android.animation.AnimatorSet;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProviders;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ArcProgressBar;
import org.thoughtcrime.securesms.components.AvatarImageView;

public final class InsightsModalDialogFragment extends DialogFragment {

  private ArcProgressBar  progress;
  private TextView        securePercentage;
  private AvatarImageView avatarImageView;
  private AnimatorSet     animatorSet;
  private View            progressContainer;

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);

    requireFragmentManager().beginTransaction()
                            .detach(this)
                            .attach(this)
                            .commit();
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setStyle(STYLE_NO_FRAME, R.style.Theme_Signal_Insights_Modal);
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    Dialog dialog = super.onCreateDialog(savedInstanceState);
    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    return dialog;
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.insights_modal, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    View   close        = view.findViewById(R.id.insights_modal_close);
    Button viewInsights = view.findViewById(R.id.insights_modal_view_insights);

    progress          = view.findViewById(R.id.insights_modal_progress);
    securePercentage  = view.findViewById(R.id.insights_modal_percent_secure);
    avatarImageView   = view.findViewById(R.id.insights_modal_avatar);
    progressContainer = view.findViewById(R.id.insights_modal_percent_container);

    close.setOnClickListener(v -> dismiss());
    viewInsights.setOnClickListener(v -> openInsightsAndDismiss());

    initializeViewModel();
  }

  private void initializeViewModel() {
    final InsightsModalViewModel.Repository repository = new InsightsRepository(requireContext());
    final InsightsModalViewModel.Factory    factory    = new InsightsModalViewModel.Factory(repository);
    final InsightsModalViewModel            viewModel  = ViewModelProviders.of(this, factory).get(InsightsModalViewModel.class);

    viewModel.getState().observe(this, state -> {
      updateInsecurePercent(state.getData());
      updateUserAvatar(state.getUserAvatar());
    });
  }

  private void updateInsecurePercent(@Nullable InsightsData insightsData) {
    if (insightsData == null) return;

    if (animatorSet == null) {
      animatorSet = InsightsAnimatorSetFactory.create(insightsData.getPercentInsecure(), this::setProgressPercentage, null, this::setPercentSecureScale, null);
      animatorSet.start();
    }
  }

  private void setProgressPercentage(float percent) {
    securePercentage.setText(String.valueOf(Math.round(percent * 100)));
    progress.setProgress(percent);
  }

  private void setPercentSecureScale(float scale) {
    progressContainer.setScaleX(scale);
    progressContainer.setScaleY(scale);
  }

  private void updateUserAvatar(@Nullable InsightsUserAvatar userAvatar) {
    if (userAvatar == null) avatarImageView.setImageDrawable(null);
    else                    userAvatar.load(avatarImageView);
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialog) {
    InsightsOptOut.userRequestedOptOut(requireContext());
  }

  private void openInsightsAndDismiss() {
    InsightsLauncher.showInsightsDashboard(requireFragmentManager());
    dismiss();
  }

  @Override
  public void onDestroyView() {
    if (animatorSet != null) {
      animatorSet.cancel();
      animatorSet = null;
    }

    super.onDestroyView();
  }
}
