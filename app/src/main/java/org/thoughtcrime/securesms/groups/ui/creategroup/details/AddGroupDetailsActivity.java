package org.thoughtcrime.securesms.groups.ui.creategroup.details;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavGraph;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversation.ConversationIntents;
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupInviteSentDialog;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AddGroupDetailsActivity extends PassphraseRequiredActivity implements AddGroupDetailsFragment.Callback {

  private static final String EXTRA_RECIPIENTS = "recipient_ids";

  private final DynamicTheme theme = new DynamicNoActionBarTheme();

  public static Intent newIntent(@NonNull Context context, @NonNull Collection<RecipientId> recipients) {
    Intent intent = new Intent(context, AddGroupDetailsActivity.class);

    intent.putParcelableArrayListExtra(EXTRA_RECIPIENTS, new ArrayList<>(recipients));

    return intent;
  }

  @Override
  protected void onCreate(@Nullable Bundle bundle, boolean ready) {
    theme.onCreate(this);

    setContentView(R.layout.add_group_details_activity);

    if (bundle == null) {
      ArrayList<RecipientId>      recipientIds = getIntent().getParcelableArrayListExtra(EXTRA_RECIPIENTS);
      AddGroupDetailsFragmentArgs arguments    = new AddGroupDetailsFragmentArgs.Builder(recipientIds.toArray(new RecipientId[0])).build();
      NavGraph                    graph        = Navigation.findNavController(this, R.id.nav_host_fragment).getGraph();

      Navigation.findNavController(this, R.id.nav_host_fragment).setGraph(graph, arguments.toBundle());
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    theme.onResume(this);
  }

  @Override
  public void onGroupCreated(@NonNull RecipientId recipientId,
                             long threadId,
                             @NonNull List<Recipient> invitedMembers)
  {
    Dialog dialog = GroupInviteSentDialog.showInvitesSent(this, invitedMembers);
    if (dialog != null) {
      dialog.setOnDismissListener((d) -> goToConversation(recipientId, threadId));
    } else {
      goToConversation(recipientId, threadId);
    }
  }

  void goToConversation(@NonNull RecipientId recipientId, long threadId) {
    Intent intent = ConversationIntents.createBuilder(this, recipientId, threadId)
                                       .firstTimeInSelfCreatedGroup()
                                       .build();

    startActivity(intent);
    setResult(RESULT_OK);
    finish();
  }

  @Override
  public void onNavigationButtonPressed() {
    setResult(RESULT_CANCELED);
    finish();
  }
}
