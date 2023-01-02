package org.thoughtcrime.securesms.conversation;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.ParcelableGroupId;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.BottomSheetUtil;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.LifecycleDisposable;

import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Renders a list of admins for a specified groupId. Tapping on one will allow you to send them a message.
 */
public final class ShowAdminsBottomSheetDialog extends BottomSheetDialogFragment {

  private static final String KEY_GROUP_ID = "group_id";

  private final LifecycleDisposable disposables = new LifecycleDisposable();

  public static void show(@NonNull FragmentManager manager, @NonNull GroupId.V2 groupId) {
    ShowAdminsBottomSheetDialog fragment = new ShowAdminsBottomSheetDialog();

    Bundle args = new Bundle();
    args.putParcelable(KEY_GROUP_ID, ParcelableGroupId.from(groupId));
    fragment.setArguments(args);

    fragment.show(manager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    setStyle(DialogFragment.STYLE_NORMAL, R.style.Signal_DayNight_BottomSheet_Rounded);
    super.onCreate(savedInstanceState);
  }

  @Override
  public @NonNull View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.show_admin_bottom_sheet, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    disposables.bindTo(getViewLifecycleOwner().getLifecycle());

    GroupMemberListView list = view.findViewById(R.id.show_admin_list);
    list.initializeAdapter(getViewLifecycleOwner());
    list.setDisplayOnlyMembers(Collections.emptyList());

    list.setRecipientClickListener(recipient -> {
      CommunicationActions.startConversation(requireContext(), recipient, null);
      dismissAllowingStateLoss();
    });

    disposables.add(Single.fromCallable(() -> getAdmins(requireContext().getApplicationContext(), getGroupId()))
                          .subscribeOn(Schedulers.io())
                          .observeOn(AndroidSchedulers.mainThread())
                          .subscribe(list::setDisplayOnlyMembers));
  }

  @Override
  public void show(@NonNull FragmentManager manager, @Nullable String tag) {
    BottomSheetUtil.show(manager, tag, this);
  }

  private GroupId getGroupId() {
    return ParcelableGroupId.get(requireArguments().getParcelable(KEY_GROUP_ID));
  }

  @WorkerThread
  private static @NonNull List<Recipient> getAdmins(@NonNull Context context, @NonNull GroupId groupId) {
    return SignalDatabase.groups()
                         .getGroup(groupId)
                         .map(GroupRecord::getAdmins)
                         .orElse(Collections.emptyList());
  }
}
