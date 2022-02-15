package org.thoughtcrime.securesms.groups.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.List;

public final class GroupMemberListView extends RecyclerView {

  private GroupMemberListAdapter membersAdapter;
  private int                    maxHeight;
  private boolean                selectable;

  public GroupMemberListView(@NonNull Context context) {
    super(context);
    initialize(context, null);
  }

  public GroupMemberListView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize(context, attrs);
  }

  public GroupMemberListView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize(context, attrs);
  }

  private void initialize(@NonNull Context context, @Nullable AttributeSet attrs) {
    if (maxHeight > 0) {
      setHasFixedSize(true);
    }

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.GroupMemberListView, 0, 0);
      try {
        maxHeight = typedArray.getDimensionPixelSize(R.styleable.GroupMemberListView_maxHeight, 0);
        selectable = typedArray.getBoolean(R.styleable.GroupMemberListView_selectable, false);
      } finally {
        typedArray.recycle();
      }
    }
  }

  public void initializeAdapter(@NonNull LifecycleOwner lifecycleOwner) {
    membersAdapter = new GroupMemberListAdapter(selectable, lifecycleOwner);
    setLayoutManager(new LinearLayoutManager(getContext()));
    setAdapter(membersAdapter);
  }

  public void setAdminActionsListener(@Nullable AdminActionsListener adminActionsListener) {
    membersAdapter.setAdminActionsListener(adminActionsListener);
  }

  public void setRecipientClickListener(@Nullable RecipientClickListener listener) {
    membersAdapter.setRecipientClickListener(listener);
  }

  public void setRecipientLongClickListener(@Nullable RecipientLongClickListener listener) {
    membersAdapter.setRecipientLongClickListener(listener);
  }

  public void setRecipientSelectionChangeListener(@Nullable RecipientSelectionChangeListener listener) {
    membersAdapter.setRecipientSelectionChangeListener(listener);
  }

  public void setMembers(@NonNull List<? extends GroupMemberEntry> recipients) {
    membersAdapter.updateData(recipients);
  }

  public void setDisplayOnlyMembers(@NonNull List<Recipient> recipients) {
    membersAdapter.updateData(Stream.of(recipients).map(r -> new GroupMemberEntry.FullMember(r, false)).toList());
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    if (maxHeight > 0) {
      heightSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
    }
    super.onMeasure(widthSpec, heightSpec);
  }
}
