package org.thoughtcrime.securesms.groups.ui.invitesandrequests;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.invited.PendingMemberInvitesFragment;
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.requesting.RequestingMembersFragment;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;

public class ManagePendingAndRequestingMembersActivity extends PassphraseRequiredActivity {

  private static final String GROUP_ID = "GROUP_ID";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  public static Intent newIntent(@NonNull Context context, @NonNull GroupId.V2 groupId) {
    Intent intent = new Intent(context, ManagePendingAndRequestingMembersActivity.class);
    intent.putExtra(GROUP_ID, groupId.toString());
    return intent;
  }

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);
    setContentView(R.layout.group_pending_and_requesting_member_activity);

    if (savedInstanceState == null) {
      GroupId.V2 groupId = GroupId.parseOrThrow(getIntent().getStringExtra(GROUP_ID)).requireV2();

      ViewPager2 viewPager = findViewById(R.id.pending_and_requesting_pager);
      TabLayout  tabLayout = findViewById(R.id.pending_and_requesting_tabs);

      viewPager.setAdapter(new ViewPagerAdapter(this, groupId));

      new TabLayoutMediator(tabLayout, viewPager,
        (tab, position) -> {
          switch (position) {
            case 0 : tab.setText(R.string.PendingMembersActivity_requests); break;
            case 1 : tab.setText(R.string.PendingMembersActivity_invites); break;
            default: throw new AssertionError();
          }
        }
      ).attach();
    }

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    requireSupportActionBar().setDisplayHomeAsUpEnabled(true);
  }

  private static class ViewPagerAdapter extends FragmentStateAdapter {

    private final GroupId.V2 groupId;

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity,
                            @NonNull GroupId.V2 groupId)
    {
      super(fragmentActivity);
      this.groupId = groupId;
    }

    @Override
    public @NonNull Fragment createFragment(int position) {
      switch (position) {
        case 0 : return RequestingMembersFragment.newInstance(groupId);
        case 1 : return PendingMemberInvitesFragment.newInstance(groupId);
        default: throw new AssertionError();
      }
    }

    @Override
    public int getItemCount() {
      return 2;
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public boolean onSupportNavigateUp() {
    onBackPressed();
    return true;
  }
}
