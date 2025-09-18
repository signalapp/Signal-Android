package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import org.signal.core.util.concurrent.LifecycleDisposable;
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity;
import org.thoughtcrime.securesms.conversation.ConversationIntents;
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupActivity;
import org.thoughtcrime.securesms.main.MainNavigationDetailLocation;
import org.thoughtcrime.securesms.main.MainNavigationViewModel;
import org.thoughtcrime.securesms.recipients.RecipientId;

import io.reactivex.rxjava3.disposables.Disposable;

public class MainNavigator {

  public static final int REQUEST_CONFIG_CHANGES = 901;

  private final AppCompatActivity       activity;
  private final LifecycleDisposable     lifecycleDisposable;
  private final MainNavigationViewModel viewModel;

  public MainNavigator(@NonNull AppCompatActivity activity, @NonNull MainNavigationViewModel viewModel) {
    this.activity            = activity;
    this.lifecycleDisposable = new LifecycleDisposable();
    this.viewModel           = viewModel;

    lifecycleDisposable.bindTo(activity);
  }

  public static MainNavigator get(@NonNull Activity activity) {
    if (!(activity instanceof MainActivity)) {
      throw new IllegalArgumentException("Activity must be an instance of MainActivity!");
    }

    return ((NavigatorProvider) activity).getNavigator();
  }

  public void goToConversation(@NonNull RecipientId recipientId, long threadId, int distributionType, int startingPosition) {
    Disposable disposable = ConversationIntents.createBuilder(activity, recipientId, threadId)
                                               .map(builder -> builder.withDistributionType(distributionType)
                                                                      .withStartingPosition(startingPosition)
                                                                      .toConversationArgs())
                                               .subscribe(args -> viewModel.goTo(new MainNavigationDetailLocation.Chats.Conversation(args)));

    lifecycleDisposable.add(disposable);
  }

  public void goToAppSettings() {
    activity.startActivityForResult(AppSettingsActivity.home(activity), REQUEST_CONFIG_CHANGES);
  }

  public void goToGroupCreation() {
    activity.startActivity(CreateGroupActivity.newIntent(activity));
  }

  private @NonNull FragmentManager getFragmentManager() {
    return activity.getSupportFragmentManager();
  }

  public interface BackHandler {
    /**
     * @return True if the back pressed was handled in our own custom way, false if it should be given
     * to the system to do the default behavior.
     */
    boolean onBackPressed();
  }

  public interface NavigatorProvider {
    @NonNull MainNavigator getNavigator();
    void onFirstRender();
  }
}
