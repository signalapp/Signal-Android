package org.thoughtcrime.securesms.groups.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;

import java.util.Collection;

public final class GroupMemberListView extends RecyclerView {

  private final GroupMemberListAdapter membersAdapter = new GroupMemberListAdapter();
  private       int                    maxHeight;

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
    setHasFixedSize(true);
    setLayoutManager(new LinearLayoutManager(context));
    setAdapter(membersAdapter);

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.GroupMemberListView, 0, 0);
      try {
        maxHeight = typedArray.getDimensionPixelSize(R.styleable.GroupMemberListView_maxHeight, 0);
      } finally {
        typedArray.recycle();
      }
    }
  }

  public void setAdminActionsListener(@Nullable AdminActionsListener adminActionsListener) {
    membersAdapter.setAdminActionsListener(adminActionsListener);
  }

  public void setMembers(@NonNull Collection<? extends GroupMemberEntry> recipients) {
    membersAdapter.updateData(recipients);
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    if (maxHeight > 0) {
      heightSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
    }
    super.onMeasure(widthSpec, heightSpec);
  }
}
