package org.thoughtcrime.securesms.registration.fragments;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.pin.PinRestoreRepository;
import org.thoughtcrime.securesms.registration.VerficationCodeObserver;
import org.thoughtcrime.securesms.registration.service.CodeVerificationRequest;
import org.thoughtcrime.securesms.registration.service.RegistrationCodeRequest;
import org.thoughtcrime.securesms.registration.service.RegistrationService;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;

public final class EnterCodeFragment extends BaseRegistrationFragment
                                     implements SignalStrengthPhoneStateListener.Callback, View.OnFocusChangeListener
{

  private static final String TAG = Log.tag(EnterCodeFragment.class);

  private static final class EnterCodeStatus {
    private static final int STATUS_DEF = 0;
    private static final int STATUS_INIT = 1;
    private static final int STATUS_START_VERIFY = 2;
    private static final int STATUS_SWITCH_NEXT = 3;
    private static final int STATUS_SWITCH_OPTIONS = 4;
    private static final int STATUS_VERIFY_FAIL = 5;
    private static final int STATUS_VERIFY_SUCCESS = 6;
    private static final int STATUS_ENTER_CODE_DONE = 7;
    private static final int STATUS_DESTROY = -1;
  }

  private static final int MSG_RECEIVED_CODE = 1001;

  private TextView mVerificationEntry;
  private EditText mVerificationInput;
  private TextView mVerificationNav;

  private RecyclerView recyclerView;
  private EnterCodeAdapter enterCodeAdapter;
  private ArrayList<String> options;
  private FrameLayout mLoadingLayout;
  private VerficationCodeObserver mObserver;

  private static int mFocusHeight;
  private static int mNormalHeight;
  private static int mFocusTextSize;
  private static int mNormalTextSize;
  private static int mFocusPaddingX;
  private static int mNormalPaddingX;
  private static int mMinFinished = -1;
  private static int mSecFinished = -1;

  private EnterCodeAdapter.OnItemClickListener mlistener = new EnterCodeAdapter.OnItemClickListener() {
    @Override
    public void onItemClicked(View view, int position) {
      if(position == 0 && mMinFinished == 0 && mSecFinished == 0){

        CallClick(view);
        Navigation.findNavController(view)
                .navigate(EnterCodeFragmentDirections.actionRequestCaptcha());
      }else if (position == 2){
        Navigation.findNavController(view).navigate(EnterCodeFragmentDirections.actionWrongNumber());
      }
    }
  };


  private static class CodeHandler extends Handler {
    private WeakReference<EnterCodeFragment> mRef;

    CodeHandler(EnterCodeFragment fragment) {
      mRef = new WeakReference<>(fragment);
    }

    @Override
    public void handleMessage(Message msg) {
      super.handleMessage(msg);
      if (msg.what == MSG_RECEIVED_CODE) {
        String code = (String) msg.obj;
        EditText view = mRef.get().mVerificationInput;
        view.setText(code);
        view.setSelection(code.length());
      }
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_enter_code, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    CodeHandler handler = new CodeHandler(this);
    mObserver = new VerficationCodeObserver(requireContext(), handler, MSG_RECEIVED_CODE);
    Uri uri = Uri.parse("content://sms");
    requireContext().getContentResolver().registerContentObserver(uri, true, mObserver);
    Resources res = requireActivity().getResources();
    mFocusHeight = res.getDimensionPixelSize(R.dimen.focus_item_height);
    mNormalHeight = res.getDimensionPixelSize(R.dimen.item_height);
    mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize);
    mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize);
    mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
    mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);

    mVerificationEntry = view.findViewById(R.id.verification_entry);
    mVerificationInput = view.findViewById(R.id.verification_input);
    mVerificationNav = view.findViewById(R.id.verification_nav);
    mLoadingLayout = view.findViewById(R.id.verification_loading);
    recyclerView = view.findViewById(R.id.enter_recycler_view);
    LinearLayoutManager manager = new LinearLayoutManager(requireContext());
    recyclerView.setLayoutManager(manager);
    recyclerView.setClipToPadding(false);
    recyclerView.setClipChildren(false);
    recyclerView.setPadding(0, 76, 0, 200);
    options = new ArrayList<>();
    String[] callMeStrArray = getString(
            R.string.RegistrationActivity_call_me_instead_available_in).split("\\n");
    options.addAll(Arrays.asList(callMeStrArray));
    options.add(getString(R.string.RegistrationActivity_wrong_number));
    enterCodeAdapter = new EnterCodeAdapter(requireContext(),mlistener,options);
    recyclerView.setAdapter(enterCodeAdapter);
    mVerificationEntry.setTextSize(24);
    recyclerView.setVisibility(View.GONE);
    mVerificationEntry.setOnFocusChangeListener(this);
    mVerificationInput.setOnFocusChangeListener(this);
    mVerificationNav.setOnFocusChangeListener(this);


    updateUiStatus(EnterCodeStatus.STATUS_INIT);

    setOnCodeFullyEnteredListener(mVerificationInput);

    mVerificationNav.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        boolean isNext = (boolean) view.getTag();
        if (isNext) {
          handleVerificationCode();
        } else {
          updateUiStatus(EnterCodeStatus.STATUS_SWITCH_OPTIONS);
        }
      }
    });

    mVerificationInput.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        mVerificationNav.requestFocus();
      }
    });

    //TODO : no code situation
    RegistrationViewModel model = getModel();
    model.getSuccessfulCodeRequestAttempts().observe(getViewLifecycleOwner(), (attempts) -> {
    });

    model.onStartEnterCode();
    updateUiStatus(EnterCodeStatus.STATUS_INIT);
  }

  @Override
  public void onNoCellSignalPresent() {
  }

  @Override
  public void onCellSignalPresent() {
  }


  private void onWrongNumber() {
    Navigation.findNavController(requireView())
              .navigate(EnterCodeFragmentDirections.actionWrongNumber());
  }

  private void setOnCodeFullyEnteredListener(EditText verifyCodeEdit) {
    verifyCodeEdit.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

      }

      @Override
      public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

      }

      @Override
      public void afterTextChanged(Editable s) {
        updateUiStatus(EnterCodeStatus.STATUS_DEF);
      }
    });
  }

  private void CallClick(@NonNull View view){
    handlePhoneCallRequest();
  }

  private void handleVerificationCode() {
    String code = mVerificationInput.getText().toString();
    RegistrationViewModel model = getModel();
    model.onVerificationCodeEntered(code);
    updateUiStatus(EnterCodeStatus.STATUS_START_VERIFY);

    RegistrationService registrationService = RegistrationService.getInstance(model.getNumber().getE164Number(), model.getRegistrationSecret());
    registrationService.verifyAccount(requireActivity(), model.getFcmToken(), code, null, null,
        new CodeVerificationRequest.VerifyCallback() {

          @Override
          public void onSuccessfulRegistration() {
            updateUiStatus(EnterCodeStatus.STATUS_VERIFY_SUCCESS);

            SimpleTask.run(() -> {
              long startTime = System.currentTimeMillis();
              try {
                FeatureFlags.refreshSync();
                Log.i(TAG, "Took " + (System.currentTimeMillis() - startTime) + " ms to get feature flags.");
              } catch (IOException e) {
                Log.w(TAG, "Failed to refresh flags after " + (System.currentTimeMillis() - startTime) + " ms.", e);
              }
                return null;
              }, none -> {
                handleSuccessfulRegistration();
            });
          }

              //TODO : onV1RegistrationLockPinRequiredOrIncorrect : NEW callbacks
              @Override
              public void onV1RegistrationLockPinRequiredOrIncorrect(long timeRemaining) {
                model.setLockedTimeRemaining(timeRemaining);
                Navigation.findNavController(requireView())
                        .navigate(EnterCodeFragmentDirections.actionRequireKbsLockPin(timeRemaining, true));
              }

              //TODO : onKbsRegistrationLockPinRequired : NEW callbacks
              @Override
              public void onKbsRegistrationLockPinRequired(long timeRemaining, @NonNull PinRestoreRepository.TokenData tokenData, @NonNull String kbsStorageCredentials) {
                model.setLockedTimeRemaining(timeRemaining);
                model.setKeyBackupTokenData(tokenData);
                Navigation.findNavController(requireView())
                        .navigate(EnterCodeFragmentDirections.actionRequireKbsLockPin(timeRemaining, false));
              }

              @Override
              public void onIncorrectKbsRegistrationLockPin(@NonNull PinRestoreRepository.TokenData tokenData) {
                Log.e(TAG, "onIncorrectKbsRegistrationLockPin!");
                throw new AssertionError("Unexpected, user has made no pin guesses");
              }

          @Override
          public void onRateLimited() {
            new AlertDialog.Builder(requireContext())
                .setTitle(R.string.RegistrationActivity_too_many_attempts)
                .setMessage(R.string.RegistrationActivity_you_have_made_too_many_incorrect_registration_lock_pin_attempts_please_try_again_in_a_day)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                  updateUiStatus(EnterCodeStatus.STATUS_VERIFY_FAIL);
                })
                .show();
          }

              //TODO : onKbsAccountLocked : NEW callbacks
              @Override
              public void onKbsAccountLocked(@Nullable Long timeRemaining) {
                if (timeRemaining != null) {
                  model.setLockedTimeRemaining(timeRemaining);
                }
                Navigation.findNavController(requireView()).navigate(RegistrationLockFragmentDirections.actionAccountLocked());
              }

          @Override
          public void onError() {
            Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show();
            updateUiStatus(EnterCodeStatus.STATUS_VERIFY_FAIL);
          }
        });
  }

  private void handleSuccessfulRegistration() {
    Navigation.findNavController(requireView()).navigate(EnterCodeFragmentDirections.actionSuccessfulRegistration());
  }

  @Override
  public void onDestroy() {
    if (mObserver != null) {
      requireContext().getContentResolver().unregisterContentObserver(mObserver);
      mObserver = null;
    }
    resetTimer();
    super.onDestroy();
  }

  public boolean onKeyDown(int keyCode) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      if (mVerificationNav.getVisibility() == View.GONE) {
        updateUiStatus(EnterCodeStatus.STATUS_SWITCH_NEXT);
        return true;
      }
    }
    return false;
  }

  @Override
  public void onStop() {
    super.onStop();
    updateUiStatus(EnterCodeStatus.STATUS_DESTROY);
  }

  private void handlePhoneCallRequest() {
    updateUiStatus(EnterCodeStatus.STATUS_START_VERIFY);

    RegistrationViewModel model = getModel();
    String captcha = model.getCaptchaToken();
    model.clearCaptchaResponse();

    RegistrationService registrationService = RegistrationService.getInstance(model.getNumber().getE164Number(), model.getRegistrationSecret());
    registrationService.requestVerificationCode(requireActivity(), RegistrationCodeRequest.Mode.PHONE_CALL, captcha,
        new RegistrationCodeRequest.SmsVerificationCodeCallback() {

          @Override
          public void onNeedCaptcha() {
          }

          @Override
          public void requestSent(@Nullable String fcmToken) {
            updateUiStatus(EnterCodeStatus.STATUS_SWITCH_NEXT);
            model.setFcmToken(fcmToken);
            model.markASuccessfulAttempt();
          }

              @Override
              public void onRateLimited() {
              }

          @Override
          public void onError() {
            Toast.makeText(requireContext(), R.string.RegistrationActivity_unable_to_connect_to_service, Toast.LENGTH_LONG).show();
            updateUiStatus(EnterCodeStatus.STATUS_VERIFY_FAIL);
          }
        });
  }

    private void updateUiStatus(int type) {
      switch (type) {
        case EnterCodeStatus.STATUS_INIT:
        case EnterCodeStatus.STATUS_SWITCH_NEXT:
          mLoadingLayout.setVisibility(View.GONE);
          mVerificationEntry.setVisibility(View.VISIBLE);
          mVerificationInput.setVisibility(View.VISIBLE);
          mVerificationInput.setEnabled(true);
          mVerificationNav.setEnabled(true);
          mVerificationNav.setVisibility(View.VISIBLE);
          recyclerView.setVisibility(View.GONE);
          break;
        case EnterCodeStatus.STATUS_VERIFY_SUCCESS:
        case EnterCodeStatus.STATUS_START_VERIFY:
          mLoadingLayout.setVisibility(View.VISIBLE);
          mVerificationEntry.setVisibility(View.VISIBLE);
          mVerificationInput.setVisibility(View.VISIBLE);
          mVerificationInput.setEnabled(false);
          mVerificationNav.setEnabled(false);
          mVerificationNav.setVisibility(View.VISIBLE);
          recyclerView.setVisibility(View.GONE);
          break;
        case EnterCodeStatus.STATUS_SWITCH_OPTIONS:
          mLoadingLayout.setVisibility(View.GONE);
          mVerificationEntry.setVisibility(View.GONE);
          mVerificationInput.setVisibility(View.GONE);
          mVerificationInput.setEnabled(false);
          mVerificationNav.setEnabled(false);
          mVerificationNav.setVisibility(View.GONE);
          recyclerView.setVisibility(View.VISIBLE);
          recyclerView.requestFocus();
          break;
        case EnterCodeStatus.STATUS_VERIFY_FAIL:
          mLoadingLayout.setVisibility(View.GONE);
          mVerificationEntry.setVisibility(View.VISIBLE);
          mVerificationInput.setVisibility(View.VISIBLE);
          mVerificationInput.setEnabled(true);
          mVerificationNav.setEnabled(true);
          mVerificationInput.setText("");
          mVerificationNav.setVisibility(View.VISIBLE);
          recyclerView.setVisibility(View.GONE);
          break;
      }
      if (isAdded()) {
        Editable s = mVerificationInput.getText();
        if (!TextUtils.isEmpty(s) && s.toString().length() == 6) {
          mVerificationNav.setTag(true);
          mVerificationNav.setText(getString(R.string.RegistrationActivity_next));
        } else {
          mVerificationNav.setText("Options");
          mVerificationNav.setTag(false);
        }
      }
    }

    @Override
    public void onFocusChange(View view, boolean b) {
      int id = view.getId();
      switch (id) {
        case R.id.verification_entry:
        case R.id.verification_input:
          mVerificationEntry.setTextColor(b ? getResources().getColor(R.color.white_focus) : getResources().getColor(R.color.white_not_focus));
          mVerificationInput.setTextColor(b ? getResources().getColor(R.color.white_focus) : getResources().getColor(R.color.white_not_focus));
          break;
        case R.id.enter_recycler_view:
          recyclerView.setVisibility(View.VISIBLE);
          break;
      }
    }

  private static class EnterCodeAdapter extends RecyclerView.Adapter<EnterCodeFragment.EnterCodeAdapter.ViewHolder> {

    private Context mContext;
    private EnterCodeFragment.EnterCodeAdapter.OnItemClickListener mListener;
    private ArrayList<String> mList;

    EnterCodeAdapter(Context context, EnterCodeFragment.EnterCodeAdapter.OnItemClickListener listener, ArrayList<String> list) {
      this.mContext = context;
      this.mListener = listener;
      this.mList = list;
      initTimer();
      mCallMeTimer.start();
    }

    @NonNull
    @Override
    public EnterCodeFragment.EnterCodeAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      View view = LayoutInflater.from(mContext).inflate(R.layout.mp02_singleline_item, parent, false);
      view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View view, boolean b) {
          TextView tv = (TextView) view.findViewById(R.id.item_singleline_tv);
          if (b) {
            tv.setSelected(true);
            tv.setEllipsize(TextUtils.TruncateAt.MARQUEE);
          } else {
            tv.setEllipsize(TextUtils.TruncateAt.END);
          }
          updateFocusView(view, b, tv);
        }
      });
      return new EnterCodeFragment.EnterCodeAdapter.ViewHolder(view);
    }

    private void updateFocusView(View parent, boolean hasFocus, TextView tv) {
      ValueAnimator va;
      if (hasFocus) {
        va = ValueAnimator.ofFloat(0, 1);
      } else {
        va = ValueAnimator.ofFloat(1, 0);
      }
      va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
          float scale = (float) valueAnimator.getAnimatedValue();
//          float height = ((float) (mFocusHeight - mNormalHeight)) * (scale) + (float) mNormalHeight;
          float textsize = ((float) (mFocusTextSize - mNormalTextSize)) * (scale) + mNormalTextSize;
          float padding = (float) mNormalPaddingX - ((float) (mNormalPaddingX - mFocusPaddingX)) * (scale);
          int alpha = (int) ((float) 0x81 + (float) ((0xff - 0x81)) * (scale));
          int color = alpha * 0x1000000 + 0xffffff;

          tv.setTextColor(color);
          tv.setTextSize(textsize);
//          parent.getLayoutParams().height = (int) (height);
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
    public void onBindViewHolder(@NonNull EnterCodeFragment.EnterCodeAdapter.ViewHolder holder, int position) {
      TextView tv = holder.itemView.findViewById(R.id.item_singleline_tv);
      tv.setText(mList.get(position));
      if (position == 1) {
        tv.setClickable(false);
        tv.setFocusable(false);
        holder.itemView.setFocusable(false);
        String str = mContext.getString(R.string.RegistrationActivity_call_me_instead_available_in,
                mMinFinished, mSecFinished);
        String[] callMeStrArray = str.split("\\n");
        if (callMeStrArray[1] != null) {
          tv.setText(callMeStrArray[1].trim());
        }
      } else {
        holder.itemView.setOnClickListener(view -> mListener.onItemClicked(view, position));
      }
    }

    private final CountDownTimer mCallMeTimer = new CountDownTimer(RegistrationConstants.FIRST_CALL_AVAILABLE_AFTER * 1000, 1000) {
      @Override
      public void onTick(long l) {
        if (l / 1000 % 15 == 0) {
          mMinFinished = (int) (l / 1000 / 60);
          mSecFinished = (int) (l / 1000 % 60);
          notifyItemChanged(1);
        }
      }

      @Override
      public void onFinish() {
      }
    };


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
  }

  private static void resetTimer() {
    mMinFinished = -1;
    mSecFinished = -1;
  }

  private static void initTimer() {
    mMinFinished = 1;
    mSecFinished = 0;
  }
}
