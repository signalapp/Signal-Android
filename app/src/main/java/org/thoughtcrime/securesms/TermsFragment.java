package org.thoughtcrime.securesms;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.SavedStateViewModelFactory;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.registration.fragments.WelcomeFragment;
import org.thoughtcrime.securesms.registration.fragments.WelcomeFragmentDirections;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.BackupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Arrays;
import java.util.Locale;

public class TermsFragment extends Fragment {
    private static final String TAG = TermsFragment.class.getSimpleName();
    private TextView mTermsTv;
//    private TextView mContinueTv;
//    private TextView mContinuePreTv;
//    private LinearLayout mContinueLl;
//    private boolean showTvContinue;
    private RegistrationViewModel model;

    private static final float WELCOME_OPTIOON_SCALE_FOCUS = 1.5f;
    private static final float WELCOME_OPTIOON_SCALE_NON_FOCUS = 1.0f;
    private static final float WELCOME_OPTIOON_TRANSLATION_X_FOCUS = 12.0f;
    private static final float WELCOME_OPTIOON_TRANSLATION_X_NON_FOCUS = 1.0f;

    private void MP02_Animate(View view, boolean b) {
        float scale = b ? WELCOME_OPTIOON_SCALE_FOCUS : WELCOME_OPTIOON_SCALE_NON_FOCUS;
        float transx = b ? WELCOME_OPTIOON_TRANSLATION_X_FOCUS : WELCOME_OPTIOON_TRANSLATION_X_NON_FOCUS;
        ViewCompat.animate(view)
                .scaleX(scale)
                .scaleY(scale)
                .translationX(transx)
                .start();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_registration_terms, container, false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.model = getRegistrationViewModel(requireActivity());
        mTermsTv = view.findViewById(R.id.terms_tv);

//        mContinueTv = view.findViewById(R.id.tv_continue);
//        mContinuePreTv = view.findViewById(R.id.tv_continue_pre);
//        mContinueLl = view.findViewById(R.id.ll_continue);


        StringBuilder builder = new StringBuilder();
        builder.append(getString(R.string.terms_and_policy_1));
        builder.append(getString(R.string.terms_and_policy_2));
        builder.append(getString(R.string.terms_and_policy_3));
        builder.append(getString(R.string.terms_and_policy_4));
        builder.append(getString(R.string.terms_and_policy_5));
        builder.append(getString(R.string.terms_and_policy_6));
        builder.append(getString(R.string.terms_and_policy_7));
        builder.append(getString(R.string.terms_and_policy_8));
        builder.append(getString(R.string.terms_and_policy_9));
        builder.append(getString(R.string.terms_and_policy_10));
        builder.append(getString(R.string.terms_and_policy_11));
        builder.append(getString(R.string.terms_and_policy_12));
        builder.append(getString(R.string.terms_and_policy_13));
        builder.append(getString(R.string.terms_and_policy_14));
        mTermsTv.setText(builder);

//        mContinueLl.setOnFocusChangeListener((v, hasFocus) -> {
//            MP02_Animate(mContinueTv, hasFocus);
//            Log.d(TAG, "onViewCreated");
//        });
//
//        mContinueLl.setOnClickListener(v ->  continueClicked(mContinueTv));

    }

    /*private void continueClicked(@NonNull View view) {
        Permissions.with(this)
                .request(android.Manifest.permission.WRITE_CONTACTS,
                        android.Manifest.permission.READ_CONTACTS,
                        android.Manifest.permission.RECORD_AUDIO,
                        android.Manifest.permission.READ_SMS,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.READ_PHONE_STATE)
                .ifNecessary()
                .withRationaleDialog(getString(R.string.RegistrationActivity_signal_needs_access_to_your_contacts_and_media_in_order_to_connect_with_friends),
                        R.drawable.ic_contacts_white_48dp, R.drawable.ic_folder_white_48dp)
                .onAnyResult(() -> {
                    gatherInformationAndContinue(mContinueTv);
                })
                .execute();
    }*/

    private void gatherInformationAndContinue(@NonNull View view) {
        view.setClickable(false);

        searchForBackup(backup -> {
            Context context = getContext();
            if (context == null) {
                Log.i(TAG, "No context on fragment, must have navigated away.");
                return;
            }
            TextSecurePreferences.setHasSeenWelcomeScreen(requireContext(), true);
            initializeNumber();
            view.setClickable(true);
            if (backup == null) {
                Navigation.findNavController(view)
                        .navigate(WelcomeFragmentDirections.actionSkipRestore());
            } else {
                Navigation.findNavController(view)
                        .navigate(WelcomeFragmentDirections.actionRestore());
            }
        });
    }

    static void searchForBackup(@NonNull OnBackupSearchResultListener listener) {
        new AsyncTask<Void, Void, BackupUtil.BackupInfo>() {
            @Override
            protected @Nullable
            BackupUtil.BackupInfo doInBackground(Void... voids) {
                try {
                    return BackupUtil.getLatestBackup();
                } catch (NoExternalStorageException e) {
                    Log.w(TAG, e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(@Nullable BackupUtil.BackupInfo backup) {
                listener.run(backup);
            }
        }.execute();
    }

    interface OnBackupSearchResultListener {

        @MainThread
        void run(@Nullable BackupUtil.BackupInfo backup);
    }


    @SuppressLint("MissingPermission")
    private void initializeNumber() {
        Optional<Phonenumber.PhoneNumber> localNumber = Optional.absent();

        if (Permissions.hasAll(requireContext(), Manifest.permission.READ_PHONE_STATE)) {
            localNumber = Util.getDeviceNumber(requireContext());
        }

        if (localNumber.isPresent()) {
            Log.i(TAG, "Phone number detected");
            Phonenumber.PhoneNumber phoneNumber    = localNumber.get();
            String                  nationalNumber = String.valueOf(phoneNumber.getNationalNumber());

            if (phoneNumber.getNumberOfLeadingZeros() != 0) {
                char[] value = new char[phoneNumber.getNumberOfLeadingZeros()];
                Arrays.fill(value, '0');
                nationalNumber = new String(value) + nationalNumber;
                Log.i(TAG, String.format(Locale.US, "Padded national number with %d zeros", phoneNumber.getNumberOfLeadingZeros()));
            }

            getModel().onNumberDetected(phoneNumber.getCountryCode(), nationalNumber);
        } else {
            Optional<String> simCountryIso = Util.getSimCountryIso(requireContext());

            if (simCountryIso.isPresent() && !TextUtils.isEmpty(simCountryIso.get())) {
                getModel().onNumberDetected(PhoneNumberUtil.getInstance().getCountryCodeForRegion(simCountryIso.get()), "0");
            }
        }
    }

    protected static RegistrationViewModel getRegistrationViewModel(@NonNull FragmentActivity activity) {

        SavedStateViewModelFactory savedStateViewModelFactory = new SavedStateViewModelFactory(activity.getApplication(), activity);

        return ViewModelProviders.of(activity, savedStateViewModelFactory).get(RegistrationViewModel.class);
    }

    protected @NonNull RegistrationViewModel getModel() {
        return model;
    }

    protected boolean isCover(View view) {
        boolean cover = false;
        Rect rect = new Rect();
        cover = view.getGlobalVisibleRect(rect);
        if (cover) {
            if (rect.width() >= view.getMeasuredWidth() && rect.height() >= view.getMeasuredHeight()) {
                return !cover;
            }
        }
        return true;
    }

    public void onKeyDown() {
        /*if (!showTvContinue) {
            Log.d(TAG, "onKeyDown showTvContinue=false");
            showTvContinue = !isCover(mContinuePreTv);
            if (!isCover(mContinuePreTv)) {
                mContinueLl.setVisibility(View.VISIBLE);
                mContinuePreTv.setVisibility(View.GONE);
                mContinueLl.requestFocus();
            } else {
                mContinueLl.setVisibility(View.GONE);
                mContinuePreTv.setVisibility(View.VISIBLE);
            }
        } else Log.d(TAG, "onKeyDown showTvContinue=true");*/
    }

    public void onKeyUp() {
        /*if (mContinueLl.getVisibility() == View.VISIBLE) {
            mContinueLl.setVisibility(View.GONE);
            mContinuePreTv.setVisibility(View.VISIBLE);
            showTvContinue = false;
        }*/
    }

    public boolean onKeyDown(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            WelcomeFragment.isBackFromTermsFragment = true;
            WelcomeFragment.isBackFromDisclaimerFragment = false;
            Navigation.findNavController(getView())
                    .navigate(TermsFragmentDirections.actionTermsFragmentToWelcomeFragment3());
            return true;
        }
        return false;
    }
}
