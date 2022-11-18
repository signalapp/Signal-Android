package org.thoughtcrime.securesms.profiles.edit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.profiles.manage.EditProfileNameFragment;
import org.thoughtcrime.securesms.registration.RegistrationUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;

//import static org.thoughtcrime.securesms.groups.v2.GroupDescriptionUtil.MAX_DESCRIPTION_LENGTH;////关联patch未合并，导致文件缺失，临时注释
import static org.thoughtcrime.securesms.profiles.edit.EditProfileActivity.EXCLUDE_SYSTEM;
import static org.thoughtcrime.securesms.profiles.edit.EditProfileActivity.GROUP_ID;
import static org.thoughtcrime.securesms.profiles.edit.EditProfileActivity.NEXT_BUTTON_TEXT;
import static org.thoughtcrime.securesms.profiles.edit.EditProfileActivity.NEXT_INTENT;
import static org.thoughtcrime.securesms.profiles.edit.EditProfileActivity.SHOW_TOOLBAR;

//import com.dd.CircularProgressButton;

public class EditProfileFragment extends LoggingFragment implements View.OnFocusChangeListener {

    private static final String TAG = Log.tag(EditProfileFragment.class);

    private static final int    MAX_DESCRIPTION_GLYPHS     = 480;
    private static final int    MAX_DESCRIPTION_BYTES      = 8192;

    private Intent mNextIntent;
    private EditProfileViewModel viewModel;
    private Controller mController;
    private GroupId mGroupId;

    private RecyclerView mProfileRecy;
    private ProfileAdapter mProfileAdapter;
    public static int mFocusHeight;
    public static int mNormalHeight;
    public static int mFocusTextSize;
    public static int mNormalTextSize;
    public static int mFocusPaddingX;
    public static int mNormalPaddingX;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof Controller) {
            mController = (Controller) context;
        } else {
            throw new IllegalStateException("Context must subclass Controller");
        }
    }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.profile_create_fragment, container, false);
  }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mGroupId = GroupId.parseNullableOrThrow(requireArguments().getString(GROUP_ID, null));

        Bundle arguments = requireArguments();
        mNextIntent = arguments.getParcelable(NEXT_INTENT);
        mProfileRecy = view.findViewById(R.id.profile_recy);
        LinearLayoutManager manager = new LinearLayoutManager(requireContext());
        mProfileRecy.setLayoutManager(manager);
        initializeViewModel(requireArguments().getBoolean(EXCLUDE_SYSTEM, false), mGroupId, savedInstanceState != null);
        mProfileAdapter = new ProfileAdapter(this, requireContext(), viewModel);
        mProfileRecy.setAdapter(mProfileAdapter);

        mProfileRecy.setClipToPadding(false);
        mProfileRecy.setClipChildren(false);
        mProfileRecy.setPadding(0, 76, 0, 200);

        Resources res = getActivity().getResources();
        mFocusHeight = res.getDimensionPixelSize(R.dimen.focus_item_height);
        mNormalHeight = res.getDimensionPixelSize(R.dimen.item_height);

        mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize);
        mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize);

        mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
        mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void initializeViewModel(boolean excludeSystem, @Nullable GroupId groupId, boolean hasSavedInstanceState) {
        EditProfileRepository repository;

        if (groupId != null) {
            repository = new EditGroupProfileRepository(requireContext(), groupId);
        } else {
            repository = new EditSelfProfileRepository(requireContext(), excludeSystem);
        }

        EditProfileViewModel.Factory factory = new EditProfileViewModel.Factory(repository, hasSavedInstanceState, groupId);

        viewModel = ViewModelProviders.of(requireActivity(), factory)
                .get(EditProfileViewModel.class);
    }

    private static void updateFieldIfNeeded(@NonNull EditText field, @NonNull String value) {
        String fieldTrimmed = field.getText().toString().trim();
        String valueTrimmed = value.trim();

        if (!fieldTrimmed.equals(valueTrimmed)) {
            boolean setSelectionToEnd = field.getText().length() == 0;

            field.setText(value);

            if (setSelectionToEnd) {
                field.setSelection(field.getText().length());
            }
        }
    }

    private void handleUpload() {
        viewModel.getUploadResult().observe(getViewLifecycleOwner(), uploadResult -> {
            if (uploadResult == EditProfileRepository.UploadResult.SUCCESS) {
                handleFinishedLegacy();
            } else {
                Toast.makeText(requireContext(), R.string.CreateProfileActivity_problem_setting_profile, Toast.LENGTH_LONG).show();
            }
        });
        viewModel.submitProfile();
    }

    @Override
    public void onFocusChange(View view, boolean b) {

    }

    private void handleFinishedLegacy() {
        if (mNextIntent != null) startActivity(mNextIntent);

        mController.onProfileNameUploadCompleted();
    }

    public GroupId getGroupId() {
        return mGroupId;
    }

    public interface Controller {
        void onProfileNameUploadCompleted();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private static class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.ViewHolder> {
        private static final int ITEM_TYPE_TWOLINE = 0x01;
        private static final int ITEM_TYPE_SINGLELINE = 0x02;

        private Context mContext;
        private EditProfileFragment mFragment;
        private EditProfileViewModel mViewModel;

        private ProfileAdapter(EditProfileFragment fragment, Context context, EditProfileViewModel vm) {
            mFragment = fragment;
            mContext = context;
            mViewModel = vm;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == ITEM_TYPE_TWOLINE) {
                View nameView = LayoutInflater.from(mContext).inflate(R.layout.mp02_twoline_item, parent, false);
                TextView nameTitle = nameView.findViewById(R.id.item_twoline_title);
                EditText nameEdit = nameView.findViewById(R.id.item_twoline_edit);
                nameEdit.setVisibility(View.VISIBLE);
                nameView.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        ((ViewGroup) v).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                        nameEdit.setEnabled(true);
                        nameEdit.setFocusable(true);
                        nameEdit.requestFocus();
                    } else {
                        ((ViewGroup) v).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    }

//          updateFocusView(v, nameTitle, nameEdit, hasFocus);
                });
                nameEdit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        updateFocusView((View) v.getParent(), nameTitle, nameEdit, hasFocus);
                    }
                });
                return new ViewHolder(nameView);
            } else {
                View optionView = LayoutInflater.from(mContext).inflate(R.layout.mp02_singleline_item, parent, false);
                TextView optionTitle = optionView.findViewById(R.id.item_singleline_tv);
                optionView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View view, boolean b) {
                        TextView tv = (TextView) view.findViewById(R.id.item_singleline_tv);
                        if (b) {
                            tv.setSelected(true);
                            tv.setEllipsize(TextUtils.TruncateAt.MARQUEE);

                        } else {
                            tv.setEllipsize(TextUtils.TruncateAt.END);
                        }
                        updateFocusView(view, tv, null, b);
                    }

                });
                optionView.setOnClickListener(v -> mFragment.handleUpload());
                return new ViewHolder(optionView);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (position == 0) {
                TextView firstNameTv = holder.itemView.findViewById(R.id.item_twoline_title);
                EditText firstNameEdit = holder.itemView.findViewById(R.id.item_twoline_edit);
                if (mFragment.getGroupId() != null) {
                    firstNameTv.setText(R.string.EditProfileFragment__group_name);
                } else {
                    firstNameTv.setText(R.string.CreateProfileActivity_first_name_required);
                }

                firstNameEdit.setHint(R.string.CreateProfileActivity_required);
                firstNameEdit.addTextChangedListener(new AfterTextChanged(s -> {
                    EditProfileNameFragment.trimFieldToMaxByteLength(s);
                    mViewModel.setGivenName(s.toString());
                }));
                mViewModel.givenName().observe(mFragment, givenName -> updateFieldIfNeeded(firstNameEdit, givenName));
            } else if (position == 1 && mFragment.getGroupId() == null) {
                TextView lastNameTv = holder.itemView.findViewById(R.id.item_twoline_title);
                EditText lastNameEdit = holder.itemView.findViewById(R.id.item_twoline_edit);
                lastNameTv.setText(R.string.CreateProfileActivity_last_name_optional);
                lastNameEdit.setHint(R.string.CreateProfileActivity_optional);
                lastNameEdit.addTextChangedListener(new AfterTextChanged(s -> {
                    EditProfileNameFragment.trimFieldToMaxByteLength(s);
                    mViewModel.setFamilyName(s.toString());
                }));
                mViewModel.familyName().observe(mFragment, familyName -> updateFieldIfNeeded(lastNameEdit, familyName));
            } else if (position == 1 && mFragment.getGroupId() != null) {
              TextView textTv = holder.itemView.findViewById(R.id.item_singleline_tv);
              textTv.setText(R.string.save);
            } else if (position == 2){
                TextView phoneNumber = holder.itemView.findViewById(R.id.item_singleline_tv);
                String number = PhoneNumberFormatter.prettyPrint(TextSecurePreferences.getLocalNumber(mContext));
                phoneNumber.setText(number);
            }/*else if (position == 2) {
                TextView textTv = holder.itemView.findViewById(R.id.item_singleline_tv);
                textTv.setText(R.string.CreateProfileActivity_signal_profiles_are_end_to_end_encrypted);
                textTv.setEnabled(false);

            }*/else if (position == 3) {
                TextView nextTv = holder.itemView.findViewById(R.id.item_singleline_tv);
                nextTv.setText(R.string.save);
                nextTv.setWidth(100);
                mViewModel.profileName().observe(mFragment, profileName -> {
                    holder.itemView.setEnabled(!profileName.isGivenNameEmpty());
                });
            }
        }

        private void updateFocusView(View parent, TextView tv, EditText et, boolean itemFocus) {
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
                    tv.setTextSize(mNormalTextSize);
                    et.setTextSize((int) textsize);
                    et.setTextColor(color);
                    et.getLayoutParams().height = (int) editHeight;
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

        @Override
        public int getItemCount() {
            return mFragment.getGroupId() != null ? 2 : 4;
        }

        @Override
        public int getItemViewType(int position) {
            if (mFragment.getGroupId() != null) {
                return position == 0 ? ITEM_TYPE_TWOLINE : ITEM_TYPE_SINGLELINE;
            } else {
                if (position < 2) {
                    return ITEM_TYPE_TWOLINE;
                }
                return ITEM_TYPE_SINGLELINE;
            }
        }


        static class ViewHolder extends RecyclerView.ViewHolder {
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }

    public void onKeyDown(int code) {
        if (code == KeyEvent.KEYCODE_CALL) {
            handleUpload();
        }
    }
}
