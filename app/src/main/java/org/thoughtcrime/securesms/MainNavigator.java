package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.thoughtcrime.securesms.components.settings.DSLSettingsActivity;
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity;
import org.thoughtcrime.securesms.conversation.ConversationIntents;
import org.thoughtcrime.securesms.conversationlist.ConversationListArchiveFragment;
import org.thoughtcrime.securesms.conversationlist.ConversationListFragment;
import org.thoughtcrime.securesms.conversationlist.ConversationListItemOptionsFragment;
import org.thoughtcrime.securesms.conversationlist.ConversationListSearchFragment;
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupActivity;
import org.thoughtcrime.securesms.insights.InsightsLauncher;
import org.thoughtcrime.securesms.profiles.edit.EditProfileActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.Set;

public class MainNavigator {

  public static final int REQUEST_CONFIG_CHANGES = 901;

  private final MainActivity activity;
  public static final int PROFILE_EMPTY = 100;

  //for current conversation
  private long preDeleteThreadId;
  private boolean fromSearch;
  private boolean fromArchive;
  private boolean fromOptions;
  private Set<Long> preDeleteSet;

  public MainNavigator(@NonNull MainActivity activity) {
    this.activity = activity;
  }

  public static MainNavigator get(@NonNull Activity activity) {
    if (!(activity instanceof MainActivity)) {
      throw new IllegalArgumentException("Activity must be an instance of MainActivity!");
    }

    return ((MainActivity) activity).getNavigator();
  }

  public void setCurrentConversation(long threadId, Set<Long> set) {
    preDeleteSet = set;
    preDeleteThreadId = threadId;
  }

  public long getPreDeleteThreadId() {
    return  preDeleteThreadId;
  }

  public Set<Long> getPreDeleteSet() {
    return preDeleteSet;
  }

  public boolean getFromSearch() {
    return fromSearch;
  }

  public void setFromSearch(boolean search) {
    fromSearch = search;
  }

  public boolean getFromArchive(){
    return  fromArchive;
  }

  public void setFromArchive(boolean archive){
    fromArchive = archive;
  }

  public boolean getFromOptions(){
    return  fromOptions;
  }

  public void setFromOptions(boolean options){
    fromOptions = options;
  }

  public void onCreate(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState != null) {
      return;
    }

    getFragmentManager().beginTransaction()
                        .add(R.id.fragment_container, ConversationListFragment.newInstance())
                        .commit();

    if(Recipient.self().getProfileName().toString().equals("")){
      Intent intent = new Intent(activity, EditProfileActivity.class);
      intent.putExtra(EditProfileActivity.EXCLUDE_SYSTEM, true);
      //intent.putExtra(EditProfileActivity.DISPLAY_USERNAME, true);
      intent.putExtra(EditProfileActivity.NEXT_BUTTON_TEXT, R.string.save);
      activity.startActivityForResult(intent,1);
    }
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

  public void goToConversation(@NonNull RecipientId recipientId, long threadId, int distributionType, int startingPosition) {
    Intent intent = ConversationIntents.createBuilder(activity, recipientId, threadId)
                                       .withDistributionType(distributionType)
                                       .withStartingPosition(startingPosition)
                                       .build();

    activity.overridePendingTransition(R.anim.slide_from_end, R.anim.fade_scale_out);
    activity.startActivity(intent);
  }

  public void goToAppSettings() {
    activity.startActivityForResult(AppSettingsActivity.home(activity), REQUEST_CONFIG_CHANGES);
  }

  public void goToArchiveList() {
    getFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.slide_from_end, R.anim.slide_to_start, R.anim.slide_from_start, R.anim.slide_to_end)
                        .replace(R.id.fragment_container, ConversationListArchiveFragment.newInstance())
                        .addToBackStack(null)
                        .commit();
  }

  public void goToOptionsList() {
    getFragmentManager().beginTransaction()
            .setCustomAnimations(R.anim.slide_from_end, R.anim.slide_to_start, R.anim.slide_from_start, R.anim.slide_to_end)
            .replace(R.id.fragment_container, ConversationListItemOptionsFragment.newInstance())
            .addToBackStack(null)
            .commit();
  }

  public void goToConversationList() {
    getFragmentManager().beginTransaction()
            .setCustomAnimations(R.anim.slide_from_end, R.anim.slide_to_start, R.anim.slide_from_start, R.anim.slide_to_end)
            .replace(R.id.fragment_container, ConversationListFragment.newInstance())
            .addToBackStack(null)
            .commit();
  }

  public void goToSearch() {
    getFragmentManager().beginTransaction()
            .setCustomAnimations(R.anim.slide_from_end, R.anim.slide_to_start, R.anim.slide_from_start, R.anim.slide_to_end)
            .replace(R.id.fragment_container, ConversationListSearchFragment.newInstance())
            .addToBackStack(null)
            .commit();
  }

  public void goToGroupCreation() {
    activity.startActivity(CreateGroupActivity.newIntent(activity));
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
