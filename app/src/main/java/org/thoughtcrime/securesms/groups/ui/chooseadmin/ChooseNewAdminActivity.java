package org.thoughtcrime.securesms.groups.ui.chooseadmin;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProviders;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.dd.CircularProgressButton;

import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.BadGroupIdException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.ui.GroupChangeResult;
import org.thoughtcrime.securesms.groups.ui.GroupErrors;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;

import java.util.Objects;

public final class ChooseNewAdminActivity extends PassphraseRequiredActivity {

  private static final String EXTRA_GROUP_ID = "group_id";

  private ChooseNewAdminViewModel viewModel;
  private GroupMemberListView     groupList;
  private CircularProgressButton  done;
  private GroupId.V2              groupId;

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  public static Intent createIntent(@NonNull Context context, @NonNull GroupId.V2 groupId) {
    Intent intent = new Intent(context, ChooseNewAdminActivity.class);
    intent.putExtra(EXTRA_GROUP_ID, groupId.toString());
    return intent;
  }

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);
    setContentView(R.layout.choose_new_admin_activity);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    //noinspection ConstantConditions
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    try {
      groupId = GroupId.parse(Objects.requireNonNull(getIntent().getStringExtra(EXTRA_GROUP_ID))).requireV2();
    } catch (BadGroupIdException e) {
      throw new AssertionError(e);
    }

    groupList = findViewById(R.id.choose_new_admin_group_list);
    done      = findViewById(R.id.choose_new_admin_done);
    done.setIndeterminateProgressMode(true);

    initializeViewModel();

    groupList.setRecipientSelectionChangeListener(selection -> viewModel.setSelection(Stream.of(selection)
                                                                                            .select(GroupMemberEntry.FullMember.class)
                                                                                            .collect(Collectors.toSet())));

    done.setOnClickListener(v -> {
      done.setClickable(false);
      done.setProgress(50);
      viewModel.updateAdminsAndLeave(this::handleUpdateAndLeaveResult);
    });
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  private void initializeViewModel() {
    viewModel = ViewModelProviders.of(this, new ChooseNewAdminViewModel.Factory(groupId)).get(ChooseNewAdminViewModel.class);

    viewModel.getNonAdminFullMembers().observe(this, groupList::setMembers);
    viewModel.getSelection().observe(this, selection -> done.setVisibility(selection.isEmpty() ? View.GONE : View.VISIBLE));
  }

  private void handleUpdateAndLeaveResult(@NonNull GroupChangeResult updateResult) {
    if (updateResult.isSuccess()) {
      String title = Recipient.externalGroupExact(this, groupId).getDisplayName(this);
      Toast.makeText(this, getString(R.string.ChooseNewAdminActivity_you_left, title), Toast.LENGTH_LONG).show();
      startActivity(MainActivity.clearTop(this));
      finish();
    } else {
      done.setClickable(true);
      done.setProgress(0);
      //noinspection ConstantConditions
      Toast.makeText(this, GroupErrors.getUserDisplayMessage(updateResult.getFailureReason()), Toast.LENGTH_LONG).show();
    }
  }
}
