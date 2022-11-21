package org.thoughtcrime.securesms.registration.fragments;

import static org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.showConfirmNumberDialogIfTranslated;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneStateListener;
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

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.registration.VerficationCodeObserver;
import org.thoughtcrime.securesms.registration.VerifyAccountRepository;
import org.thoughtcrime.securesms.registration.viewmodel.BaseRegistrationViewModel;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.LifecycleDisposable;
import org.thoughtcrime.securesms.util.SupportEmailUtil;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.whispersystems.signalservice.internal.push.LockedException;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Base fragment used by registration and change number flow to input an SMS verification code or request a
 * phone code after requesting SMS.
 *
 * @param <ViewModel> - The concrete view model used by the subclasses, for ease of access in said subclass
 */
public abstract class BaseEnterCodeFragment<ViewModel extends BaseRegistrationViewModel> extends LoggingFragment implements SignalStrengthPhoneStateListener.Callback, View.OnFocusChangeListener {

  private static final String TAG = Log.tag(BaseEnterCodeFragment.class);

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
  private BaseEnterCodeFragment.EnterCodeAdapter enterCodeAdapter;
  private ArrayList<String> options;
  private FrameLayout mLoadingLayout;
  private VerficationCodeObserver mObserver;

  private PhoneStateListener signalStrengthListener;
  private RegistrationViewModel viewModel;
  private final LifecycleDisposable disposables = new LifecycleDisposable();

  private static int mFocusHeight;
  private static int mNormalHeight;
  private static int mFocusTextSize;
  private static int mNormalTextSize;
  private static int mFocusPaddingX;
  private static int mNormalPaddingX;
  private static int mMinFinished = -1;
  private static int mSecFinished = -1;

  private BaseEnterCodeFragment.EnterCodeAdapter.OnItemClickListener mlistener = new BaseEnterCodeFragment.EnterCodeAdapter.OnItemClickListener() {
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


  public BaseEnterCodeFragment(@LayoutRes int contentLayoutId) {
    super(contentLayoutId);
  }

  private static class CodeHandler extends Handler {
    private WeakReference<BaseEnterCodeFragment> mRef;

    CodeHandler(BaseEnterCodeFragment fragment) {
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

//  @Override
//  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//    return inflater.inflate(R.layout.fragment_registration_enter_code, container, false);
//  }


  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    BaseEnterCodeFragment.CodeHandler handler = new BaseEnterCodeFragment.CodeHandler(this);
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
    enterCodeAdapter = new BaseEnterCodeFragment.EnterCodeAdapter(requireContext(),mlistener,options);
    recyclerView.setAdapter(enterCodeAdapter);
    mVerificationEntry.setTextSize(24);
    recyclerView.setVisibility(View.GONE);
    mVerificationEntry.setOnFocusChangeListener(this);
    mVerificationInput.setOnFocusChangeListener(this);
    mVerificationNav.setOnFocusChangeListener(this);


    updateUiStatus(BaseEnterCodeFragment.EnterCodeStatus.STATUS_INIT);

    setOnCodeFullyEnteredListener(mVerificationInput);

    mVerificationNav.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        boolean isNext = (boolean) view.getTag();
        if (isNext) {
          handleVerificationCode();
        } else {
          updateUiStatus(BaseEnterCodeFragment.EnterCodeStatus.STATUS_SWITCH_OPTIONS);
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
    disposables.bindTo(getViewLifecycleOwner().getLifecycle());
    viewModel = ViewModelProviders.of(requireActivity()).get(RegistrationViewModel.class);
    viewModel.getSuccessfulCodeRequestAttempts().observe(getViewLifecycleOwner(), (attempts) -> {
    });

    viewModel.onStartEnterCode();
    updateUiStatus(BaseEnterCodeFragment.EnterCodeStatus.STATUS_INIT);
  }

  protected abstract ViewModel getViewModel();

  protected abstract void handleSuccessfulVerify();

  protected abstract void navigateToCaptcha();

  protected abstract void navigateToRegistrationLock(long timeRemaining);

  protected abstract void navigateToKbsAccountLocked();

  private void onWrongNumber() {
    Navigation.findNavController(requireView()).navigateUp();
  }
//  private void onWrongNumber() {
//    Navigation.findNavController(requireView())
//            .navigate(EnterCodeFragmentDirections.actionWrongNumber());
//  }


  private void CallClick(@NonNull View view){
    handlePhoneCallRequest();
  }

  private void handleVerificationCode() {
    String code = mVerificationInput.getText().toString();
    viewModel.onVerificationCodeEntered(code);
    updateUiStatus(BaseEnterCodeFragment.EnterCodeStatus.STATUS_START_VERIFY);

    Disposable verify = viewModel.verifyCodeWithoutRegistrationLock(code)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(processor -> {
              if (processor.hasResult()) {
                handleSuccessfulRegistration();
              } else if (processor.rateLimit()) {
                handleRateLimited();
              } else if (processor.registrationLock() && !processor.isKbsLocked()) {
                LockedException lockedException = processor.getLockedException();
                handleRegistrationLock(lockedException.getTimeRemaining());
              } else if (processor.isKbsLocked()) {
                handleKbsAccountLocked();
              } else if (processor.authorizationFailed()) {
                handleIncorrectCodeError();
              } else {
                Log.w(TAG, "Unable to verify code", processor.getError());
                handleGeneralError();
              }
            });
    disposables.add(verify);
  }

  public void handleSuccessfulRegistration() {
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

      Navigation.findNavController(requireView()).navigate(EnterCodeFragmentDirections.actionSuccessfulRegistration());
    });
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
        updateUiStatus(BaseEnterCodeFragment.EnterCodeStatus.STATUS_DEF);
      }
    });
  }


  public void handleRateLimited() {

    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
    builder.setTitle(R.string.RegistrationActivity_too_many_attempts)
            .setMessage(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
              updateUiStatus(BaseEnterCodeFragment.EnterCodeStatus.STATUS_VERIFY_FAIL);
            })
            .show();

  }


  public void handleRegistrationLock(long timeRemaining) {
    navigateToRegistrationLock(timeRemaining);
  }


  public void handleKbsAccountLocked() {
    Navigation.findNavController(requireView()).navigate(RegistrationLockFragmentDirections.actionAccountLocked());
  }

  public void handleIncorrectCodeError() {
    Toast.makeText(requireContext(), R.string.RegistrationActivity_incorrect_code, Toast.LENGTH_LONG).show();
    updateUiStatus(BaseEnterCodeFragment.EnterCodeStatus.STATUS_VERIFY_FAIL);
  }

  public void handleGeneralError() {
    Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show();
    updateUiStatus(BaseEnterCodeFragment.EnterCodeStatus.STATUS_VERIFY_FAIL);
  }

  public boolean onKeyDown(int keyCode) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      if (mVerificationNav.getVisibility() == View.GONE) {
        updateUiStatus(BaseEnterCodeFragment.EnterCodeStatus.STATUS_SWITCH_NEXT);
        return true;
      }
    }
    return false;
  }
  protected void displaySuccess(@NonNull Runnable runAfterAnimation) {
        runAfterAnimation.run();
  }


//  @Override
//  public void onStart() {
//    super.onStart();
//    EventBus.getDefault().register(this);
//  }

  @Override
  public void onStop() {
    super.onStop();
    EventBus.getDefault().unregister(this);
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

  private static List<Integer> convertVerificationCodeToDigits(@Nullable String code) {
    if (code == null || code.length() != 6) {
      return Collections.emptyList();
    }

    List<Integer> result = new ArrayList<>(code.length());

    try {
      for (int i = 0; i < code.length(); i++) {
        result.add(Integer.parseInt(Character.toString(code.charAt(i))));
      }
    } catch (NumberFormatException e) {
      Log.w(TAG, "Failed to convert code into digits.", e);
      return Collections.emptyList();
    }

    return result;
  }


  private void handlePhoneCallRequest() {
    updateUiStatus(BaseEnterCodeFragment.EnterCodeStatus.STATUS_START_VERIFY);

    showConfirmNumberDialogIfTranslated(requireContext(),
            R.string.RegistrationActivity_you_will_receive_a_call_to_verify_this_number,
            viewModel.getNumber().getE164Number(),
            this::handlePhoneCallRequestAfterConfirm,
            this::onWrongNumber);
  }

//  private void handlePhoneCallRequestAfterConfirm() {
//    Disposable request = viewModel.requestVerificationCode(VerifyAccountRepository.Mode.PHONE_CALL)
//                                  .observeOn(AndroidSchedulers.mainThread())
//                                  .subscribe(processor -> {
//                                    if (processor.hasResult()) {
//                                      Toast.makeText(requireContext(), R.string.RegistrationActivity_call_requested, Toast.LENGTH_LONG).show();
//                                    } else if (processor.captchaRequired()) {
//                                      navigateToCaptcha();
//                                    } else if (processor.rateLimit()) {
//                                      Toast.makeText(requireContext(), R.string.RegistrationActivity_rate_limited_to_service, Toast.LENGTH_LONG).show();
//                                    } else {
//                                      Log.w(TAG, "Unable to request phone code", processor.getError());
//                                      Toast.makeText(requireContext(), R.string.RegistrationActivity_unable_to_connect_to_service, Toast.LENGTH_LONG).show();
//                                    }
//                                  });
//
//    disposables.add(request);
//  }

  private void handlePhoneCallRequestAfterConfirm() {
    Disposable request = viewModel.requestVerificationCode(VerifyAccountRepository.Mode.PHONE_CALL)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(processor -> {
              if (processor.hasResult()) {
                Toast.makeText(requireContext(), R.string.RegistrationActivity_call_requested, Toast.LENGTH_LONG).show();
              } else if (processor.captchaRequired()) {
                NavHostFragment.findNavController(this).navigate(EnterCodeFragmentDirections.actionRequestCaptcha());
              } else if (processor.rateLimit()) {
                Toast.makeText(requireContext(), R.string.RegistrationActivity_rate_limited_to_service, Toast.LENGTH_LONG).show();
              } else {
                Log.w(TAG, "Unable to request phone code", processor.getError());
                Toast.makeText(requireContext(), R.string.RegistrationActivity_unable_to_connect_to_service, Toast.LENGTH_LONG).show();
              }
            });

    disposables.add(request);
  }
  private void updateUiStatus(int type) {
    switch (type) {
      case BaseEnterCodeFragment.EnterCodeStatus.STATUS_INIT:
      case BaseEnterCodeFragment.EnterCodeStatus.STATUS_SWITCH_NEXT:
        mLoadingLayout.setVisibility(View.GONE);
        mVerificationEntry.setVisibility(View.VISIBLE);
        mVerificationInput.setVisibility(View.VISIBLE);
        mVerificationInput.setEnabled(true);
        mVerificationNav.setEnabled(true);
        mVerificationNav.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        break;
      case BaseEnterCodeFragment.EnterCodeStatus.STATUS_VERIFY_SUCCESS:
      case BaseEnterCodeFragment.EnterCodeStatus.STATUS_START_VERIFY:
        mLoadingLayout.setVisibility(View.VISIBLE);
        mVerificationEntry.setVisibility(View.VISIBLE);
        mVerificationInput.setVisibility(View.VISIBLE);
        mVerificationInput.setEnabled(false);
        mVerificationNav.setEnabled(false);
        mVerificationNav.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        break;
      case BaseEnterCodeFragment.EnterCodeStatus.STATUS_SWITCH_OPTIONS:
        mLoadingLayout.setVisibility(View.GONE);
        mVerificationEntry.setVisibility(View.GONE);
        mVerificationInput.setVisibility(View.GONE);
        mVerificationInput.setEnabled(false);
        mVerificationNav.setEnabled(false);
        mVerificationNav.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        recyclerView.requestFocus();
        break;
      case BaseEnterCodeFragment.EnterCodeStatus.STATUS_VERIFY_FAIL:
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

  private static class EnterCodeAdapter extends RecyclerView.Adapter<BaseEnterCodeFragment.EnterCodeAdapter.ViewHolder> {

    private Context mContext;
    private BaseEnterCodeFragment.EnterCodeAdapter.OnItemClickListener mListener;
    private ArrayList<String> mList;

    EnterCodeAdapter(Context context, BaseEnterCodeFragment.EnterCodeAdapter.OnItemClickListener listener, ArrayList<String> list) {
      this.mContext = context;
      this.mListener = listener;
      this.mList = list;
      initTimer();
      mCallMeTimer.start();
    }

    @NonNull
    @Override
    public BaseEnterCodeFragment.EnterCodeAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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
      return new BaseEnterCodeFragment.EnterCodeAdapter.ViewHolder(view);
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
    public void onBindViewHolder(@NonNull BaseEnterCodeFragment.EnterCodeAdapter.ViewHolder holder, int position) {
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

  private void sendEmailToSupport() {
    String body = SupportEmailUtil.generateSupportEmailBody(requireContext(),
                                                            R.string.RegistrationActivity_code_support_subject,
                                                            null,
                                                            null);
    CommunicationActions.openEmail(requireContext(),
                                   SupportEmailUtil.getSupportEmailAddress(requireContext()),
                                   getString(R.string.RegistrationActivity_code_support_subject),
                                   body);
  }

  private static void resetTimer() {
    mMinFinished = -1;
    mSecFinished = -1;
  }

  private static void initTimer() {
    mMinFinished = 1;
    mSecFinished = 0;
  }

  @Override
  public void onNoCellSignalPresent() {

  }

  @Override
  public void onCellSignalPresent() {

  }
}
