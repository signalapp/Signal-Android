package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.conversationlist.ConversationListArchiveFragment;
import org.thoughtcrime.securesms.conversationlist.ConversationListFragment;
import org.thoughtcrime.securesms.insights.InsightsLauncher;
import org.thoughtcrime.securesms.recipients.RecipientId;

public class MainNavigator {

  private final MainActivity activity;

  public MainNavigator(@NonNull MainActivity activity) {
    this.activity = activity;
  }

  public static MainNavigator get(@NonNull Activity activity) {
    if (!(activity instanceof MainActivity)) {
      throw new IllegalArgumentException("Activity must be an instance of MainActivity!");
    }

    return ((MainActivity) activity).getNavigator();
  }

  public void onCreate(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState != null) {
      return;
    }

    getFragmentManager().beginTransaction()
                        .add(R.id.fragment_container, ConversationListFragment.newInstance())
                        .commit();
  }

  /**
   * @return True if the back pressed was handled in our own custom way, false if it should be given
   *         to the system to do the default behavior.
   */
  public boolean onBackPressed() {
    Fragment fragment = getFragmentManager().findFragmentById(R.id.fragment_container);

    if (fragment instanceof BackHandler) {
      return ((BackHandler) fragment).onBackPressed();
    }

    return false;
  }

  public void goToConversation(@NonNull RecipientId recipientId, long threadId, int distributionType, long lastSeen, int startingPosition) {
    Intent intent = ConversationActivity.buildIntent(activity, recipientId, threadId, distributionType, lastSeen, startingPosition);

    activity.startActivity(intent);
    activity.overridePendingTransition(R.anim.slide_from_end, R.anim.fade_scale_out);
  }

  public void goToAppSettings() {
    Intent intent = new Intent(activity, ApplicationPreferencesActivity.class);
    activity.startActivity(intent);
  }


  public void goToArchiveList() {
    getFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.slide_from_end, R.anim.slide_to_start, R.anim.slide_from_start, R.anim.slide_to_end)
                        .replace(R.id.fragment_container, ConversationListArchiveFragment.newInstance())
                        .addToBackStack(null)
                        .commit();
  }

  public void goToGroupCreation() {
    Intent intent = new Intent(activity, GroupCreateActivity.class);
    activity.startActivity(intent);
  }

  public void goToInvite() {
    Intent intent = new Intent(activity, InviteActivity.class);
    activity.startActivity(intent);
  }

  public void goToInsights() {
    InsightsLauncher.showInsightsDashboard(activity.getSupportFragmentManager());
  }

  private @NonNull FragmentManager getFragmentManager() {
    return activity.getSupportFragmentManager();
  }

  public interface BackHandler {
    /**
     * @return True if the back pressed was handled in our own custom way, false if it should be given
     *         to the system to do the default behavior.
     */
    boolean onBackPressed();
  }
}
