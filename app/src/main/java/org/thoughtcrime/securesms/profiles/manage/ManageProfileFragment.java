package org.thoughtcrime.securesms.profiles.manage;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiUtil;
import org.thoughtcrime.securesms.mediasend.AvatarSelectionActivity;
import org.thoughtcrime.securesms.mediasend.AvatarSelectionBottomSheetDialogFragment;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.profiles.edit.EditProfileActivity;
import org.thoughtcrime.securesms.profiles.manage.ManageProfileViewModel.AvatarState;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

import static android.app.Activity.RESULT_OK;

public class ManageProfileFragment extends LoggingFragment {

  private static final String TAG                        = Log.tag(ManageProfileFragment.class);
  private static final short  REQUEST_CODE_SELECT_AVATAR = 31726;

  private TextView               profileNameView;
  private TextView               yourNameView;
  private View                   profileNameContainer;
  private TextView               usernameView;
  private TextView               yourUsernameView;
  private View                   usernameContainer;
  private TextView               aboutView;
  private TextView               aboutSelfView;
  private View                   aboutContainer;
  private TextView               learnMoreView;
  private AlertDialog            avatarProgress;
  private ScrollView             scrollView;

  private ManageProfileViewModel viewModel;

  public static int mFocusHeight;
  public static int mNormalHeight;
  public static int mFocusTextSize;
  public static int mNormalTextSize;
  public static int mNormalPaddingX;
  public static int mFocusPaddingX;

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.manage_profile_fragment, container, false);
  }

  @SuppressLint("NewApi")
  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    this.profileNameView       = view.findViewById(R.id.manage_profile_name);
    this.yourNameView          = view.findViewById(R.id.manage_profile_name_subtitle);
    this.profileNameContainer  = view.findViewById(R.id.manage_profile_name_container);
    this.usernameView          = view.findViewById(R.id.manage_profile_username);
    this.yourUsernameView      = view.findViewById(R.id.manage_profile_username_subtitle);
    this.usernameContainer     = view.findViewById(R.id.manage_profile_username_container);
    this.aboutView             = view.findViewById(R.id.manage_profile_about);
    this.aboutSelfView         = view.findViewById(R.id.manage_profile_about_subtitle);
    this.aboutContainer        = view.findViewById(R.id.manage_profile_about_container);
    this.learnMoreView         = view.findViewById(R.id.description_text);
    this.scrollView            = view.findViewById(R.id.scrollview_profile);

    initializeViewModel();

    initView();

    this.profileNameView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        requireActivity().startActivity(EditProfileActivity.getIntentForUserProfileEdit(v.getContext()));
      }
    });

    this.usernameView.setOnClickListener(v -> {
      Navigation.findNavController(v).navigate(ManageProfileFragmentDirections.actionManageUsername());
    });

    this.aboutView.setOnClickListener(v -> {
      Navigation.findNavController(v).navigate(ManageProfileFragmentDirections.actionManageAbout());
    });


    profileNameView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
    profileNameView.setMarqueeRepeatLimit(6);
    profileNameView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
      @Override
      public void onFocusChange(View v, boolean hasFocus) {
        if (hasFocus){
          smoothToMiddleOfScreen(profileNameView);
        }
        updateFocusView(profileNameContainer,profileNameView,yourNameView,hasFocus);
      }
    });

    aboutView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
    aboutView.setMarqueeRepeatLimit(6);
    aboutView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
      @Override
      public void onFocusChange(View v, boolean hasFocus) {
        if (hasFocus){
          smoothToMiddleOfScreen(aboutView);
        }
        updateFocusView(aboutContainer,aboutView,aboutSelfView,hasFocus);
      }
    });

    learnMoreView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
    learnMoreView.setMarqueeRepeatLimit(6);
    learnMoreView.setFocusable(true);
    this.learnMoreView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
      @Override
      public void onFocusChange(View v, boolean hasFocus) {
        updateFocusView(learnMoreView,learnMoreView,null,hasFocus);
        if (hasFocus){
          smoothToMiddleOfScreen(learnMoreView);
        }
      }
    });
    profileNameView.setOnKeyListener(new View.OnKeyListener() {
      @Override
      public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && profileNameView.hasFocus()){
          return true;
        }
        return false;
      }
    });
    learnMoreView.setOnKeyListener(new View.OnKeyListener() {
      @Override
      public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && learnMoreView.hasFocus()){
          return true;
        }
        return false;
      }
    });
  }
  private void initView() {
    Resources res = getActivity().getResources();
    mFocusHeight = res.getDimensionPixelSize(R.dimen.focus_item_height);
    mNormalHeight = res.getDimensionPixelSize(R.dimen.item_height);

    mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize);
    mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize);

    mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
    mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);

    scrollView.setClipToPadding(false);
    scrollView.setClipChildren(false);
    scrollView.setPadding(0, 76, 0, 200);

    profileNameView.setTextSize(mNormalTextSize);
    yourNameView.setTextSize(mNormalTextSize);
    usernameView.setTextSize(mNormalTextSize);
    yourUsernameView.setTextSize(mNormalTextSize);
    aboutView.setTextSize(mNormalTextSize);
    aboutSelfView.setTextSize(mNormalTextSize);
    learnMoreView.setTextSize(mNormalTextSize);
  }

  private void smoothToMiddleOfScreen(View view){
    DisplayMetrics outMetrics = new DisplayMetrics();
    getActivity().getWindowManager().getDefaultDisplay().getRealMetrics(outMetrics);
    int heightPixel = outMetrics.heightPixels;
    int[] ints = new int[2];
    scrollView.postDelayed(new Runnable() {
      @Override
      public void run() {
        view.getLocationOnScreen(ints);
        int viewBottom = Math.round(view.getHeight() + ints[1]);
        int screenCenter = Math.round(heightPixel >> 1);
        int correct = 10;
        int distanceToTheCenter = viewBottom - screenCenter - correct;
        scrollView.smoothScrollBy(0,distanceToTheCenter);
      }
    },300);
  }

  private void updateFocusView(View parent, TextView tv, TextView et, boolean itemFocus) {
      ValueAnimator va;
      if (itemFocus) {
          va = ValueAnimator.ofFloat(0, 1);
      } else {
          va = ValueAnimator.ofFloat(1, 0);
      }

      va.addUpdateListener(valueAnimator -> {
          float scale = (float) valueAnimator.getAnimatedValue();
          float height = ((float) (mFocusHeight - mNormalHeight)) * (scale) + (float) mNormalHeight;
          float editHeight = (float) (mFocusHeight) * (scale);
          float textsize = ((float) (mFocusTextSize - mNormalTextSize)) * (scale) + mNormalTextSize;
          float padding = (float) mNormalPaddingX - ((float) (mNormalPaddingX - mFocusPaddingX)) * (scale);
          int alpha = (int) ((float) 0x81 + (float) ((0xff - 0x81)) * (scale));
          int color = alpha * 0x1000000 + 0xffffff;

          tv.setTextColor(color);
          parent.setPadding((int) padding, parent.getPaddingTop(), parent.getPaddingRight(), parent.getPaddingBottom());
          if (et == null) {
              tv.setTextSize((int) textsize);
              tv.getLayoutParams().height = (int) height;
              parent.getLayoutParams().height = (int) (height);
          } else {
            tv.setTextSize((int) textsize);
            et.getLayoutParams().height = (int) editHeight;
            et.setTextSize(mNormalTextSize);
            et.setTextColor(color);
            parent.getLayoutParams().height = (int) (editHeight + mNormalHeight);
          }
      });

      FastOutLinearInInterpolator FastOutLinearInInterpolator = new FastOutLinearInInterpolator();
      va.setInterpolator(FastOutLinearInInterpolator);
      if (itemFocus) {
          va.setDuration(270);
          va.start();
      } else {
          va.setDuration(270);
          va.start();
      }
  }

  public void backEvent(){
     if (UsernameEditFragment.isIsBackFromUsernamEditFragment()){
      usernameContainer.requestFocus();
      UsernameEditFragment.setIsBackFromUsernamEditFragment(false);
    }else if (EditAboutFragment.isBackFromEditAboutFragment()){
      aboutContainer.requestFocus();
      EditAboutFragment.setIsBackFromEditAboutFragment(false);
    }
  }

    @Override
    public void onResume() {
        super.onResume();
        backEvent();
    }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == REQUEST_CODE_SELECT_AVATAR && resultCode == RESULT_OK) {
      if (data != null && data.getBooleanExtra("delete", false)) {
        viewModel.onAvatarSelected(requireContext(), null);
        return;
      }

      Media result = data.getParcelableExtra(AvatarSelectionActivity.EXTRA_MEDIA);

      viewModel.onAvatarSelected(requireContext(), result);
    }
  }

  private void initializeViewModel() {
    viewModel = ViewModelProviders.of(this, new ManageProfileViewModel.Factory()).get(ManageProfileViewModel.class);

    viewModel.getAvatar().observe(getViewLifecycleOwner(), this::presentAvatar);
    viewModel.getProfileName().observe(getViewLifecycleOwner(), this::presentProfileName);
    viewModel.getEvents().observe(getViewLifecycleOwner(), this::presentEvent);
    viewModel.getAbout().observe(getViewLifecycleOwner(), this::presentAbout);
    viewModel.getAboutEmoji().observe(getViewLifecycleOwner(), this::presentAboutEmoji);

    if (viewModel.shouldShowUsername()) {
      viewModel.getUsername().observe(getViewLifecycleOwner(), this::presentUsername);
    } else {
      usernameContainer.setVisibility(View.GONE);
    }

    if (viewModel.shouldShowUsername()) {
      viewModel.getUsername().observe(getViewLifecycleOwner(), this::presentUsername);
    } else {
      usernameContainer.setVisibility(View.GONE);
    }
  }

  private void presentAvatar(@NonNull AvatarState avatarState) {
    if (avatarProgress == null && avatarState.getLoadingState() == ManageProfileViewModel.LoadingState.LOADING) {
      avatarProgress = SimpleProgressDialog.show(requireContext());
    } else if (avatarProgress != null && avatarState.getLoadingState() == ManageProfileViewModel.LoadingState.LOADED) {
      avatarProgress.dismiss();
    }
  }

  private void presentProfileName(@Nullable ProfileName profileName) {
    if (profileName == null || profileName.isEmpty()) {
      profileNameView.setText(R.string.ManageProfileFragment_profile_name);
    } else {
      profileNameView.setText(profileName.toString());
    }
  }

  private void presentUsername(@Nullable String username) {
    if (username == null || username.isEmpty()) {
      usernameView.setText(R.string.ManageProfileFragment_username);
      usernameView.setTextColor(requireContext().getResources().getColor(R.color.signal_text_secondary));
    } else {
      usernameView.setText(username);
      usernameView.setTextColor(requireContext().getResources().getColor(R.color.signal_text_primary));
    }
  }

  private void presentAbout(@Nullable String about) {
    if (about == null || about.isEmpty()) {
      aboutView.setText(R.string.ManageProfileFragment_about);
      aboutView.setTextColor(requireContext().getResources().getColor(R.color.signal_text_secondary));
    } else {
      aboutView.setText(about);
      aboutView.setTextColor(requireContext().getResources().getColor(R.color.signal_text_primary));
    }
  }

  private void presentAboutEmoji(@NonNull String aboutEmoji) {
    if (aboutEmoji == null || aboutEmoji.isEmpty()) {
//      aboutEmojiView.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_compose_24, null));
    } else {
      Drawable emoji = EmojiUtil.convertToDrawable(requireContext(), aboutEmoji);
    }
  }

  private void presentEvent(@NonNull ManageProfileViewModel.Event event) {
    switch (event) {
      case AVATAR_DISK_FAILURE:
        Toast.makeText(requireContext(), R.string.ManageProfileFragment_failed_to_set_avatar, Toast.LENGTH_LONG).show();
        break;
      case AVATAR_NETWORK_FAILURE:
        Toast.makeText(requireContext(), R.string.EditProfileNameFragment_failed_to_save_due_to_network_issues_try_again_later, Toast.LENGTH_LONG).show();
        break;
    }
  }

  private void onAvatarClicked() {
    AvatarSelectionBottomSheetDialogFragment.create(viewModel.canRemoveAvatar(),
                                                    true,
                                                    REQUEST_CODE_SELECT_AVATAR,
                                                    false)
                                            .show(getChildFragmentManager(), null);
  }
}
