package org.thoughtcrime.securesms.megaphone;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.InviteActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.databinding.OnboardingMegaphoneCardBinding;
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupActivity;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.profiles.manage.ManageProfileActivity;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperActivity;

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
    this.cardList.setAdapter(new CardAdapter(getContext(), listener));
  }

  private static class CardAdapter extends RecyclerView.Adapter<CardViewHolder> implements ActionClickListener {

    private static final int TYPE_GROUP      = 0;
    private static final int TYPE_INVITE     = 1;
    private static final int TYPE_APPEARANCE = 2;
    private static final int TYPE_ADD_PHOTO  = 3;

    private final Context                   context;
    private final MegaphoneActionController controller;
    private final List<Integer>             data;

    CardAdapter(@NonNull Context context, @NonNull MegaphoneActionController controller) {
      this.context    = context;
      this.controller = controller;
      this.data       = buildData();

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
      View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.onboarding_megaphone_card, parent, false);
      switch (viewType) {
        case TYPE_GROUP:      return new GroupCardViewHolder(view);
        case TYPE_INVITE:     return new InviteCardViewHolder(view);
        case TYPE_APPEARANCE: return new AppearanceCardViewHolder(view);
        case TYPE_ADD_PHOTO:  return new AddPhotoCardViewHolder(view);
        default:              throw new IllegalStateException("Invalid viewType! " + viewType);
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
      data.addAll(buildData());
      if (data.isEmpty()) {
        Log.i(TAG, "Nothing to show! Considering megaphone completed.");
        controller.onMegaphoneCompleted(Megaphones.Event.ONBOARDING);
      }
      notifyDataSetChanged();
    }

    private static List<Integer> buildData() {
      List<Integer> data = new ArrayList<>();

      if (SignalStore.onboarding().shouldShowNewGroup()) {
        data.add(TYPE_GROUP);
      }

      if (SignalStore.onboarding().shouldShowInviteFriends()) {
        data.add(TYPE_INVITE);
      }

      if (SignalStore.onboarding().shouldShowAddPhoto() && !SignalStore.misc().hasEverHadAnAvatar()) {
        data.add(TYPE_ADD_PHOTO);
      }

      if (SignalStore.onboarding().shouldShowAppearance()) {
        data.add(TYPE_APPEARANCE);
      }

      return data;
    }
  }

  private interface ActionClickListener {
    void onClick();
  }

  private static abstract class CardViewHolder extends RecyclerView.ViewHolder {
    private final OnboardingMegaphoneCardBinding binding;

    public CardViewHolder(@NonNull View itemView) {
      super(itemView);
      binding = OnboardingMegaphoneCardBinding.bind(itemView);
    }

    public void bind(@NonNull ActionClickListener listener, @NonNull MegaphoneActionController controller) {
      binding.getRoot().setCardBackgroundColor(ContextCompat.getColor(binding.getRoot().getContext(), getBackgroundColor()));
      binding.icon.setImageResource(getImageRes());
      binding.text.setText(getButtonStringRes());
      binding.getRoot().setOnClickListener(v -> {
        onActionClicked(controller);
        listener.onClick();
      });
      binding.close.setOnClickListener(v -> {
        onCloseClicked();
        listener.onClick();
      });
    }

    abstract @StringRes int getButtonStringRes();
    abstract @DrawableRes int getImageRes();
    abstract @ColorRes int getBackgroundColor();
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
      return R.drawable.symbol_group_24;
    }

    @Override
    int getBackgroundColor() {
      return R.color.onboarding_background_1;
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
      return R.drawable.symbol_invite_24;
    }

    @Override
    int getBackgroundColor() {
      return R.color.onboarding_background_2;
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

  private static class AppearanceCardViewHolder extends CardViewHolder {

    public AppearanceCardViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    @Override
    int getButtonStringRes() {
      return R.string.Megaphones_chat_colors;
    }

    @Override
    int getImageRes() {
      return R.drawable.ic_color_24;
    }

    @Override
    int getBackgroundColor() {
      return R.color.onboarding_background_3;
    }

    @Override
    void onActionClicked(@NonNull MegaphoneActionController controller) {
      controller.onMegaphoneNavigationRequested(ChatWallpaperActivity.createIntent(controller.getMegaphoneActivity()));
      SignalStore.onboarding().setShowAppearance(false);
    }

    @Override
    void onCloseClicked() {
      SignalStore.onboarding().setShowAppearance(false);
    }
  }

  private static class AddPhotoCardViewHolder extends CardViewHolder {

    public AddPhotoCardViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    @Override
    int getButtonStringRes() {
      return R.string.Megaphones_add_a_profile_photo;
    }

    @Override
    int getImageRes() {
      return R.drawable.symbol_person_circle_24;
    }

    @Override
    int getBackgroundColor() {
      return R.color.onboarding_background_4;
    }

    @Override
    void onActionClicked(@NonNull MegaphoneActionController controller) {
      controller.onMegaphoneNavigationRequested(ManageProfileActivity.getIntentForAvatarEdit(controller.getMegaphoneActivity()));
      SignalStore.onboarding().setShowAddPhoto(false);
    }

    @Override
    void onCloseClicked() {
      SignalStore.onboarding().setShowAddPhoto(false);
    }
  }
}
