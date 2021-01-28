package org.thoughtcrime.securesms.megaphone;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.InviteActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversationlist.ConversationListFragment;
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupActivity;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.SmsUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the a fun rail of cards that educate the user about some actions they can take right after
 * they install the app.
 */
public class OnboardingMegaphoneView extends FrameLayout {

  private static final String TAG = Log.tag(OnboardingMegaphoneView.class);

  private RecyclerView cardList;

  public OnboardingMegaphoneView(Context context) {
    super(context);
    initialize(context);
  }

  public OnboardingMegaphoneView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize(context);
  }

  private void initialize(@NonNull Context context) {
    inflate(context, R.layout.onboarding_megaphone, this);

    this.cardList = findViewById(R.id.onboarding_megaphone_list);
  }

  public void present(@NonNull Megaphone megaphone, @NonNull MegaphoneActionController listener) {
    this.cardList.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
    this.cardList.setAdapter(new CardAdapter(getContext(), listener));
  }

  private static class CardAdapter extends RecyclerView.Adapter<CardViewHolder> implements ActionClickListener {

    private static final int TYPE_GROUP  = 0;
    private static final int TYPE_INVITE = 1;
    private static final int TYPE_SMS    = 2;

    private final Context                   context;
    private final MegaphoneActionController controller;
    private final List<Integer>             data;

    CardAdapter(@NonNull Context context, @NonNull MegaphoneActionController controller) {
      this.context    = context;
      this.controller = controller;
      this.data       = buildData(context);

      if (data.isEmpty()) {
        Log.i(TAG, "Nothing to show (constructor)! Considering megaphone completed.");
        controller.onMegaphoneCompleted(Megaphones.Event.ONBOARDING);
      }

      setHasStableIds(true);
    }

    @Override
    public int getItemViewType(int position) {
      return data.get(position);
    }

    @Override
    public long getItemId(int position) {
      return data.get(position);
    }

    @Override
    public @NonNull CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.onboarding_megaphone_list_item, parent, false);
      switch (viewType) {
        case TYPE_GROUP:  return new GroupCardViewHolder(view);
        case TYPE_INVITE: return new InviteCardViewHolder(view);
        case TYPE_SMS:    return new SmsCardViewHolder(view);
        default:          throw new IllegalStateException("Invalid viewType! " + viewType);
      }
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
      holder.bind(this, controller);
    }

    @Override
    public int getItemCount() {
      return data.size();
    }

    @Override
    public void onClick() {
      data.clear();
      data.addAll(buildData(context));
      if (data.isEmpty()) {
        Log.i(TAG, "Nothing to show! Considering megaphone completed.");
        controller.onMegaphoneCompleted(Megaphones.Event.ONBOARDING);
      }
      notifyDataSetChanged();
    }

    private static List<Integer> buildData(@NonNull Context context) {
      List<Integer> data = new ArrayList<>();

      if (SignalStore.onboarding().shouldShowNewGroup()) {
        data.add(TYPE_GROUP);
      }

      if (SignalStore.onboarding().shouldShowInviteFriends()) {
        data.add(TYPE_INVITE);
      }

      if (SignalStore.onboarding().shouldShowSms(context)) {
        data.add(TYPE_SMS);
      }

      return data;
    }
  }

  private interface ActionClickListener {
    void onClick();
  }

  private static abstract class CardViewHolder extends RecyclerView.ViewHolder {
    private final ImageView image;
    private final TextView  actionButton;
    private final View      closeButton;

    public CardViewHolder(@NonNull View itemView) {
      super(itemView);
      this.image        = itemView.findViewById(R.id.onboarding_megaphone_item_image);
      this.actionButton = itemView.findViewById(R.id.onboarding_megaphone_item_button);
      this.closeButton  = itemView.findViewById(R.id.onboarding_megaphone_item_close);
    }

    public void bind(@NonNull ActionClickListener listener, @NonNull MegaphoneActionController controller) {
      image.setImageResource(getImageRes());
      actionButton.setText(getButtonStringRes());
      actionButton.setOnClickListener(v -> {
        onActionClicked(controller);
        listener.onClick();
      });
      closeButton.setOnClickListener(v -> {
        onCloseClicked();
        listener.onClick();
      });
    }

    abstract @StringRes int getButtonStringRes();
    abstract @DrawableRes int getImageRes();
    abstract void onActionClicked(@NonNull MegaphoneActionController controller);
    abstract void onCloseClicked();
  }

  private static class GroupCardViewHolder extends CardViewHolder {

    public GroupCardViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    @Override
    int getButtonStringRes() {
      return R.string.Megaphones_new_group;
    }

    @Override
    int getImageRes() {
      return R.drawable.ic_megaphone_start_group;
    }

    @Override
    void onActionClicked(@NonNull MegaphoneActionController controller) {
      controller.onMegaphoneNavigationRequested(CreateGroupActivity.newIntent(controller.getMegaphoneActivity()));
    }

    @Override
    void onCloseClicked() {
      SignalStore.onboarding().setShowNewGroup(false);
    }
  }

  private static class InviteCardViewHolder extends CardViewHolder {

    public InviteCardViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    @Override
    int getButtonStringRes() {
      return R.string.Megaphones_invite_friends;
    }

    @Override
    int getImageRes() {
      return R.drawable.ic_megaphone_invite_friends;
    }

    @Override
    void onActionClicked(@NonNull MegaphoneActionController controller) {
      controller.onMegaphoneNavigationRequested(new Intent(controller.getMegaphoneActivity(), InviteActivity.class));
    }

    @Override
    void onCloseClicked() {
      SignalStore.onboarding().setShowInviteFriends(false);
    }
  }

  private static class SmsCardViewHolder extends CardViewHolder {

    public SmsCardViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    @Override
    int getButtonStringRes() {
      return R.string.Megaphones_use_sms;
    }

    @Override
    int getImageRes() {
      return R.drawable.ic_megaphone_use_sms;
    }

    @Override
    void onActionClicked(@NonNull MegaphoneActionController controller) {
      Intent intent = SmsUtil.getSmsRoleIntent(controller.getMegaphoneActivity());
      controller.onMegaphoneNavigationRequested(intent, ConversationListFragment.SMS_ROLE_REQUEST_CODE);
      SignalStore.onboarding().setShowSms(false);
    }

    @Override
    void onCloseClicked() {
      SignalStore.onboarding().setShowSms(false);
    }
  }
}
