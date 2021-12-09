package org.thoughtcrime.securesms.components.reminder;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;

import java.util.Collections;
import java.util.List;

final class ReminderActionsAdapter extends RecyclerView.Adapter<ReminderActionsAdapter.ActionViewHolder> {

  private final Reminder.Importance                importance;
  private final List<Reminder.Action>              actions;
  private final ReminderView.OnActionClickListener actionClickListener;

  ReminderActionsAdapter(Reminder.Importance importance, List<Reminder.Action> actions, ReminderView.OnActionClickListener actionClickListener) {
    this.importance          = importance;
    this.actions             = Collections.unmodifiableList(actions);
    this.actionClickListener = actionClickListener;
  }

  @NonNull
  @Override
  public ActionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    Context  context = parent.getContext();
    TextView button  = ((TextView) LayoutInflater.from(context).inflate(R.layout.reminder_action_button, parent, false));

    if (importance == Reminder.Importance.NORMAL) {
      button.setTextColor(ContextCompat.getColor(context, R.color.signal_accent_primary));
    }

    return new ActionViewHolder(button);
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
