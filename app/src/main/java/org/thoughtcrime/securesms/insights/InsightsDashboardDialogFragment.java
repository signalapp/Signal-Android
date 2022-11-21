package org.thoughtcrime.securesms.insights;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;

import org.thoughtcrime.securesms.NewConversationActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ArcProgressBar;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ThemeUtil;

import java.util.List;

public final class InsightsDashboardDialogFragment extends DialogFragment {

  private TextView                          securePercentage;
  private ArcProgressBar                    progress;
  private View                              progressContainer;
  private TextView                          tagline;
  private TextView                          encryptedMessages;
  private TextView                          title;
  private TextView                          description;
  private RecyclerView                      insecureRecipients;
  private TextView                          locallyGenerated;
  private AvatarImageView                   avatarImageView;
  private InsightsInsecureRecipientsAdapter adapter;
  private LottieAnimationView               lottieAnimationView;
  private AnimatorSet                       animatorSet;
  private Button                            startAConversation;
  private Toolbar                           toolbar;
  private InsightsDashboardViewModel        viewModel;

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
    if (ThemeUtil.isDarkTheme(requireActivity())) {
      setStyle(STYLE_NO_FRAME, R.style.TextSecure_DarkTheme);
    } else {
      setStyle(STYLE_NO_FRAME, R.style.TextSecure_LightTheme);
    }
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.insights_dashboard, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    securePercentage    = view.findViewById(R.id.insights_dashboard_percent_secure);
    progress            = view.findViewById(R.id.insights_dashboard_progress);
    progressContainer   = view.findViewById(R.id.insights_dashboard_percent_container);
    encryptedMessages   = view.findViewById(R.id.insights_dashboard_encrypted_messages);
    tagline             = view.findViewById(R.id.insights_dashboard_tagline);
    title               = view.findViewById(R.id.insights_dashboard_make_signal_secure);
    description         = view.findViewById(R.id.insights_dashboard_invite_your_contacts);
    insecureRecipients  = view.findViewById(R.id.insights_dashboard_recycler);
    locallyGenerated    = view.findViewById(R.id.insights_dashboard_this_stat_was_generated_locally);
    avatarImageView     = view.findViewById(R.id.insights_dashboard_avatar);
    startAConversation  = view.findViewById(R.id.insights_dashboard_start_a_conversation);
    lottieAnimationView = view.findViewById(R.id.insights_dashboard_lottie_animation);
    toolbar             = view.findViewById(R.id.insights_dashboard_toolbar);

    setupStartAConversation();
    setDashboardDetailsAlpha(0f);
    setNotEnoughDataAlpha(0f);
    setupToolbar();
    setupRecycler();
    initializeViewModel();
  }

  private void setupStartAConversation() {
    startAConversation.setOnClickListener(v -> startActivity(new Intent(requireActivity(), NewConversationActivity.class)));
  }

  private void setDashboardDetailsAlpha(float alpha) {
    tagline.setAlpha(alpha);
    title.setAlpha(alpha);
    description.setAlpha(alpha);
    insecureRecipients.setAlpha(alpha);
    locallyGenerated.setAlpha(alpha);
    encryptedMessages.setAlpha(alpha);
  }

  private void setupToolbar() {
    toolbar.setNavigationOnClickListener(v -> dismiss());
  }

  private void setupRecycler() {
    adapter = new InsightsInsecureRecipientsAdapter(this::handleInviteRecipient);
    insecureRecipients.setAdapter(adapter);
  }

  private void initializeViewModel() {
    final InsightsDashboardViewModel.Repository repository = new InsightsRepository(requireContext());
    final InsightsDashboardViewModel.Factory    factory    = new InsightsDashboardViewModel.Factory(repository);

    viewModel  = ViewModelProviders.of(this, factory).get(InsightsDashboardViewModel.class);

    viewModel.getState().observe(getViewLifecycleOwner(), state -> {
      updateInsecurePercent(state.getData());
      updateInsecureRecipients(state.getInsecureRecipients());
      updateUserAvatar(state.getUserAvatar());
    });
  }

  private void updateInsecurePercent(@Nullable InsightsData insightsData) {
    if (insightsData == null) return;

    if (insightsData.hasEnoughData()) {
      setTitleAndDescriptionText(insightsData.getPercentInsecure());
      animateProgress(insightsData.getPercentInsecure());
    } else {
      setNotEnoughDataText();
      animateNotEnoughData();
    }
  }

  private void animateProgress(int insecurePercent) {
    startAConversation.setVisibility(View.GONE);
    if (animatorSet == null) {
      animatorSet = InsightsAnimatorSetFactory.create(insecurePercent,
                                                      this::setProgressPercentage,
                                                      this::setDashboardDetailsAlpha,
                                                      this::setPercentSecureScale,
                                                      insecurePercent == 0 ? this::setLottieProgress : null);

      if (insecurePercent == 0) {
        animatorSet.addListener(new ToolbarBackgroundColorAnimationListener());
      }

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

  private void setLottieProgress(float progress) {
    lottieAnimationView.setProgress(progress);
  }

  private void setTitleAndDescriptionText(int insecurePercent) {
    startAConversation.setVisibility(View.GONE);
    progressContainer.setVisibility(View.VISIBLE);
    insecureRecipients.setVisibility(View.VISIBLE);
    encryptedMessages.setText(R.string.InsightsDashboardFragment__encrypted_messages);
    tagline.setText(getString(R.string.InsightsDashboardFragment__signal_protocol_automatically_protected, 100 - insecurePercent, InsightsConstants.PERIOD_IN_DAYS));

    if (insecurePercent == 0) {
      lottieAnimationView.setVisibility(View.VISIBLE);
      title.setVisibility(View.GONE);
      description.setVisibility(View.GONE);
    } else {
      lottieAnimationView.setVisibility(View.GONE);
      title.setText(R.string.InsightsDashboardFragment__spread_the_word);
      description.setText(R.string.InsightsDashboardFragment__invite_your_contacts);
      title.setVisibility(View.VISIBLE);
      description.setVisibility(View.VISIBLE);
    }
  }

  private void setNotEnoughDataText() {
    startAConversation.setVisibility(View.VISIBLE);
    progressContainer.setVisibility(View.INVISIBLE);
    insecureRecipients.setVisibility(View.GONE);
    encryptedMessages.setText(R.string.InsightsDashboardFragment__not_enough_data);
    tagline.setText(getString(R.string.InsightsDashboardFragment__your_insights_percentage_is_calculated_based_on, InsightsConstants.PERIOD_IN_DAYS));
  }

  private void animateNotEnoughData() {
    if (animatorSet == null) {
      animatorSet = InsightsAnimatorSetFactory.create(0, null, this::setNotEnoughDataAlpha, null, null);
      animatorSet.start();
    }
  }

  private void setNotEnoughDataAlpha(float alpha) {
    encryptedMessages.setAlpha(alpha);
    tagline.setAlpha(alpha);
    startAConversation.setAlpha(alpha);
  }

  private void updateInsecureRecipients(@NonNull List<Recipient> recipients) {
    adapter.updateData(recipients);
  }

  private void updateUserAvatar(@Nullable InsightsUserAvatar userAvatar) {
    if (userAvatar == null) avatarImageView.setImageDrawable(null);
    else                    userAvatar.load(avatarImageView);
  }

  private void handleInviteRecipient(final @NonNull Recipient recipient) {
    new AlertDialog.Builder(requireContext())
                   .setTitle(getResources().getQuantityString(R.plurals.InviteActivity_send_sms_invites, 1, 1))
                   .setMessage(getString(R.string.InviteActivity_lets_switch_to_signal, getString(R.string.install_url)))
                   .setPositiveButton(R.string.InsightsDashboardFragment__send, (dialog, which) -> viewModel.sendSmsInvite(recipient))
                   .setNegativeButton(R.string.InsightsDashboardFragment__cancel, (dialog, which) -> dialog.dismiss())
                   .show();
  }

  @Override
  public void onDestroyView() {
    if (animatorSet != null) {
      animatorSet.cancel();
      animatorSet = null;
    }

    super.onDestroyView();
  }

  private final class ToolbarBackgroundColorAnimationListener implements Animator.AnimatorListener {

    @Override
    public void onAnimationStart(Animator animation) {
      toolbar.setBackgroundResource(R.color.transparent);
    }

    @Override
    public void onAnimationEnd(Animator animation) {
      toolbar.setBackgroundColor(ThemeUtil.getThemedColor(requireContext(), android.R.attr.windowBackground));
    }

    @Override
    public void onAnimationCancel(Animator animation) {
      toolbar.setBackgroundColor(ThemeUtil.getThemedColor(requireContext(), android.R.attr.windowBackground));
    }

    @Override
    public void onAnimationRepeat(Animator animation) {

    }
  }
}
