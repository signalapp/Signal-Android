package org.thoughtcrime.securesms.components.reminder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;

import java.util.Collections;
import java.util.List;

final class ReminderActionsAdapter extends RecyclerView.Adapter<ReminderActionsAdapter.ActionViewHolder> {

  private final List<Reminder.Action>              actions;
  private final ReminderView.OnActionClickListener actionClickListener;

  ReminderActionsAdapter(List<Reminder.Action> actions, ReminderView.OnActionClickListener actionClickListener) {
    this.actions             = Collections.unmodifiableList(actions);
    this.actionClickListener = actionClickListener;
  }

  @NonNull
  @Override
  public ActionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ActionViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.reminder_action_button, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull ActionViewHolder holder, int position) {
    final Reminder.Action action = actions.get(position);

    ((Button) holder.itemView).setText(action.getTitle());
    holder.itemView.setOnClickListener(v -> {
      if (holder.getAdapterPosition() == RecyclerView.NO_POSITION) return;

      actionClickListener.onActionClick(action.getActionId());
    });
  }

  @Override
  public int getItemCount() {
    return actions.size();
  }

  final class ActionViewHolder extends RecyclerView.ViewHolder {
    ActionViewHolder(@NonNull View itemView) {
      super(itemView);
    }
  }
}
