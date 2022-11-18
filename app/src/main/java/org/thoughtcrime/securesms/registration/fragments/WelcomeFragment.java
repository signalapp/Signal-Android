package org.thoughtcrime.securesms.registration.fragments;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.navigation.ActivityNavigator;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.blocked.BlockedUsersViewModel;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.BackupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.ArrayList;

public final class WelcomeFragment extends BaseRegistrationFragment {

  private static final String TAG = Log.tag(WelcomeFragment.class);
  private static final int WELCOME_OPTION_DISCLAIMER = 0;
  private static final int WELCOME_OPTION_TERMS = 1;
  private static final int WELCOME_OPTION_CONTINUE = 2;
//    private TextView mTermsTv;

  public static boolean isBackFromDisclaimerFragment = false;
  public static boolean isBackFromTermsFragment = false;

  private TextView mTitleTv;
  private TextView mContinueTv;
  private RecyclerView mWelcomeRv;
  private WelcomeAdapter mWelcomeAdapter;
  private ArrayList<String> mOptions;

  private static int mFocusHeight;
  private static int mNormalHeight;
  private static int mFocusTextSize;
  private static int mNormalTextSize;
  private static int mFocusPaddingX;
  private static int mNormalPaddingX;

  private WelcomeAdapter.OnItemClickListener mListener = new WelcomeAdapter.OnItemClickListener() {
    @Override
    public void onItemClicked(View view, int position) {
      if (position == WELCOME_OPTION_DISCLAIMER) {
        Navigation.findNavController(view)
                .navigate(WelcomeFragmentDirections.actionReadDisclaimer());
      } else if (position == WELCOME_OPTION_TERMS) {
        Navigation.findNavController(view)
                .navigate(WelcomeFragmentDirections.actionReadTerms());
      } else if (position == WELCOME_OPTION_CONTINUE) {
        continueClicked(view);
      }
    }
  };

  private static final            String[]       PERMISSIONS        = {Manifest.permission.WRITE_CONTACTS,
          Manifest.permission.READ_CONTACTS,
          Manifest.permission.RECORD_AUDIO,
          Manifest.permission.READ_SMS,
          Manifest.permission.WRITE_EXTERNAL_STORAGE,
          Manifest.permission.READ_EXTERNAL_STORAGE,
          Manifest.permission.READ_PHONE_STATE};
  @RequiresApi(26)
  private static final            String[]       PERMISSIONS_API_26 = {Manifest.permission.WRITE_CONTACTS,
          Manifest.permission.READ_CONTACTS,
          Manifest.permission.RECORD_AUDIO,
          Manifest.permission.READ_SMS,
          Manifest.permission.WRITE_EXTERNAL_STORAGE,
          Manifest.permission.READ_EXTERNAL_STORAGE,
          Manifest.permission.READ_PHONE_STATE,
          Manifest.permission.READ_PHONE_NUMBERS};
  @RequiresApi(26)
  private static final            String[]       PERMISSIONS_API_29 = {Manifest.permission.WRITE_CONTACTS,
          Manifest.permission.READ_CONTACTS,
          Manifest.permission.RECORD_AUDIO,
          Manifest.permission.READ_SMS,
          Manifest.permission.READ_PHONE_STATE,
          Manifest.permission.READ_PHONE_NUMBERS};
  private static final @StringRes int            RATIONALE          = R.string.RegistrationActivity_signal_needs_access_to_your_contacts_and_media_in_order_to_connect_with_friends;
  private static final @StringRes int            RATIONALE_API_29   = R.string.RegistrationActivity_signal_needs_access_to_your_contacts_in_order_to_connect_with_friends;
  private static final            int[]          HEADERS            = { R.drawable.ic_contacts_white_48dp, R.drawable.ic_folder_white_48dp };
  private static final            int[]          HEADERS_API_29     = { R.drawable.ic_contacts_white_48dp };

  private View                   restoreFromBackup;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    return inflater.inflate(isReregister() ? R.layout.fragment_registration_blank
                    : R.layout.fragment_registration_welcome,
            container,
            false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    if (isReregister()) {
      RegistrationViewModel model = getModel();

      if (model.hasRestoreFlowBeenShown()) {
        Log.i(TAG, "We've come back to the home fragment on a restore, user must be backing out");
        if (!Navigation.findNavController(view).popBackStack()) {
          FragmentActivity activity = requireActivity();
          activity.finish();
          ActivityNavigator.applyPopAnimationsToPendingTransition(activity);
        }
        return;
      }

      initializeNumber();

      Log.i(TAG, "Skipping restore because this is a reregistration.");
      model.setWelcomeSkippedOnRestore();
      Navigation.findNavController(view)
              .navigate(WelcomeFragmentDirections.actionSkipRestore());

    } else {
      //init res
      Resources res = requireActivity().getResources();
      mFocusHeight = res.getDimensionPixelSize(R.dimen.focus_item_height);
      mNormalHeight = res.getDimensionPixelSize(R.dimen.item_height);
      mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize);
      mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize);
      mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
      mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);
      //init options
      mWelcomeRv = view.findViewById(R.id.rv_welcome_option);
      LinearLayoutManager manager = new LinearLayoutManager(requireContext());
      mWelcomeRv.setLayoutManager(manager);
      mWelcomeRv.setClipToPadding(false);
      mWelcomeRv.setClipChildren(false);
      mWelcomeRv.setPadding(0, 76, 0, 200);
      mOptions = new ArrayList<>();
      mOptions.add(getString(R.string.RegistrationActivity_disclaimer));
      mOptions.add(getString(R.string.RegistrationActivity_terms_and_privacy));
      mOptions.add(getString(R.string.RegistrationActivity_continue));
      mWelcomeAdapter = new WelcomeAdapter(requireContext(), mListener, mOptions);
      mWelcomeRv.setAdapter(mWelcomeAdapter);
      mWelcomeRv.setVisibility(View.GONE);
      //init welcome screen
      mContinueTv = view.findViewById(R.id.tv_welcome_continue);
      mTitleTv = view.findViewById(R.id.tv_welcome_title);
      mTitleTv.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          mTitleTv.setVisibility(View.GONE);
          mContinueTv.setVisibility(View.GONE);
          mWelcomeRv.setVisibility(View.VISIBLE);
          mWelcomeRv.requestFocus();
        }
      });

      mTitleTv.setOnKeyListener(new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
          if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN){
            mTitleTv.setVisibility(View.GONE);
            mContinueTv.setVisibility(View.GONE);
            mWelcomeRv.setVisibility(View.VISIBLE);
            mWelcomeRv.requestFocus();
            return true;
          }
          return false;
        }
      });

      if (isBackFromDisclaimerFragment) {
        mTitleTv.setVisibility(View.GONE);
        mContinueTv.setVisibility(View.GONE);
        mWelcomeRv.setVisibility(View.VISIBLE);
        mWelcomeRv.requestFocus();
      }

      if (isBackFromTermsFragment){
        mTitleTv.setVisibility(View.GONE);
        mContinueTv.setVisibility(View.GONE);
        mWelcomeRv.setVisibility(View.VISIBLE);
        mWelcomeRv.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
          @Override
          public void onGlobalLayout() {
            View viewByPosition = mWelcomeRv.getLayoutManager().findViewByPosition(1);
            if (viewByPosition != null){
              viewByPosition.requestFocus();
            }
            mWelcomeRv.getViewTreeObserver().removeOnGlobalLayoutListener(this);
          }
        });
      }
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  private void continueClicked(@NonNull View view) {
    boolean isUserSelectionRequired = BackupUtil.isUserSelectionRequired(requireContext());

    Permissions.with(this)
               .request(getContinuePermissions(isUserSelectionRequired))
               .ifNecessary()
               .withRationaleDialog(getString(getContinueRationale(isUserSelectionRequired)), getContinueHeaders(isUserSelectionRequired))
               .onAnyResult(() -> gatherInformationAndContinue(mContinueTv))
               .execute();
  }

  private void gatherInformationAndContinue(@NonNull View view) {
    view.setClickable(false);

    RestoreBackupFragment.searchForBackup(backup -> {
      Context context = getContext();
      if (context == null) {
        Log.i(TAG, "No context on fragment, must have navigated away.");
        return;
      }
      TextSecurePreferences.setHasSeenWelcomeScreen(requireContext(), true);
      initializeNumber();
      view.setClickable(true);
      if (backup == null) {
        Log.i(TAG, "Skipping backup. No backup found, or no permission to look.");
        Navigation.findNavController(view)
                .navigate(WelcomeFragmentDirections.actionSkipRestore());
      } else {
        Navigation.findNavController(view)
                .navigate(WelcomeFragmentDirections.actionRestore());
      }
    });
  }

  @SuppressLint("MissingPermission")
  private void initializeNumber() {
    Optional<Phonenumber.PhoneNumber> localNumber = Optional.absent();

    if (Permissions.hasAll(requireContext(), Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)) {
      localNumber = Util.getDeviceNumber(requireContext());
    } else {
      Log.i(TAG, "No phone permission");
    }

    if (localNumber.isPresent()) {
      Log.i(TAG, "Phone number detected");
      Phonenumber.PhoneNumber phoneNumber    = localNumber.get();
      String                  nationalNumber = PhoneNumberUtil.getInstance().format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL);

      getModel().onNumberDetected(phoneNumber.getCountryCode(), nationalNumber);
    } else {
      Log.i(TAG, "No number detected");
      Optional<String> simCountryIso = Util.getSimCountryIso(requireContext());

      if (simCountryIso.isPresent() && !TextUtils.isEmpty(simCountryIso.get())) {
        getModel().onNumberDetected(PhoneNumberUtil.getInstance().getCountryCodeForRegion(simCountryIso.get()), "");
      }
    }
  }

  @SuppressLint("NewApi")
  private static String[] getContinuePermissions(boolean isUserSelectionRequired) {
    if (isUserSelectionRequired) {
      return PERMISSIONS_API_29;
    } else if (Build.VERSION.SDK_INT >= 26) {
      return PERMISSIONS_API_26;
    } else {
      return PERMISSIONS;
    }
  }

  private static @StringRes int getContinueRationale(boolean isUserSelectionRequired) {
    return isUserSelectionRequired ? RATIONALE_API_29 : RATIONALE;
  }

  private static int[] getContinueHeaders(boolean isUserSelectionRequired) {
    return isUserSelectionRequired ? HEADERS_API_29 : HEADERS;
  }

  public boolean onKeyDown(int keyCode) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      if (mTitleTv.getVisibility() == View.GONE) {
        mWelcomeRv.setVisibility(View.GONE);
        mTitleTv.setVisibility(View.VISIBLE);
        mContinueTv.setVisibility(View.VISIBLE);
        isBackFromDisclaimerFragment = false;
        isBackFromTermsFragment = false;
        return true;
      }
    }
    return false;
  }

  private static class WelcomeAdapter extends RecyclerView.Adapter<WelcomeAdapter.ViewHolder> {

    private Context mContext;
    private OnItemClickListener mListener;
    private ArrayList<String> mList;

    WelcomeAdapter(Context context, OnItemClickListener listener, ArrayList<String> list) {
      this.mContext = context;
      this.mListener = listener;
      this.mList = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      View view = LayoutInflater.from(mContext).inflate(R.layout.mp02_singleline_item, parent, false);
      view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View view, boolean b) {
          TextView tv = view.findViewById(R.id.item_singleline_tv);
          if (b) {
            tv.setSelected(true);
            tv.setEllipsize(TextUtils.TruncateAt.MARQUEE);
          } else {
            tv.setEllipsize(TextUtils.TruncateAt.END);
          }
          updateFocusView(view, b);
        }
      });
      return new ViewHolder(view);
    }

    private void updateFocusView(View parent, boolean hasFocus) {
      TextView tv = parent.findViewById(R.id.item_singleline_tv);
      ValueAnimator va;
      if (hasFocus) {
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        va = ValueAnimator.ofFloat(0, 1);
      } else {
        va = ValueAnimator.ofFloat(1, 0);
      }
      va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
          float scale = (float) valueAnimator.getAnimatedValue();
          float height = ((float) (mFocusHeight - mNormalHeight)) * (scale) + (float) mNormalHeight;
          float textsize = ((float) (mFocusTextSize - mNormalTextSize)) * (scale) + mNormalTextSize;
          float padding = (float) mNormalPaddingX - ((float) (mNormalPaddingX - mFocusPaddingX)) * (scale);
          int alpha = (int) ((float) 0x81 + (float) ((0xff - 0x81)) * (scale));
          int color = alpha * 0x1000000 + 0xffffff;

          tv.setTextColor(color);
          tv.setTextSize(textsize);
          parent.getLayoutParams().height = (int) (height);
          parent.setPadding((int) padding, parent.getPaddingTop(), parent.getPaddingRight(), parent.getPaddingBottom());
        }
      });
      FastOutLinearInInterpolator FastOutLinearInInterpolator = new FastOutLinearInInterpolator();
      va.setInterpolator(FastOutLinearInInterpolator);
      if (hasFocus) {
        va.setDuration(270);
        va.start();
      } else {
        va.setDuration(270);
        va.start();
      }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
      TextView tv = holder.itemView.findViewById(R.id.item_singleline_tv);
      tv.setText(mList.get(position));
      holder.itemView.setOnClickListener(view -> mListener.onItemClicked(view, position));
    }

    @Override
    public int getItemCount() {
      return mList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
      public ViewHolder(@NonNull View itemView) {
        super(itemView);
      }
    }

    interface OnItemClickListener {
      void onItemClicked(View view, int position);
    }

    public View getViewByPosition(int position){
      View view = getViewByPosition(position);
      return view;
    }
  }
}
