package org.thoughtcrime.securesms.registration.fragments;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.registration.viewmodel.NumberViewState;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;

import java.util.List;
import java.util.Locale;

/**
 * TODO : add new Country code list for UI
 */
public class EnterCountryCodeFragment extends BaseRegistrationFragment {

    private TextView mCountryEntry;
    private TextView mCountryInfo;
    private TextView mCountryNext;
    private RecyclerView mCountryList;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_enter_country_code, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mCountryEntry = view.findViewById(R.id.country_code_entry);
        mCountryEntry.requestFocus();
        mCountryEntry.setOnClickListener(this::pickCountry);
        mCountryEntry.setOnFocusChangeListener((view1, b) -> mCountryInfo.setTextColor(b ? getResources().getColor(R.color.white_focus) : getResources().getColor(R.color.white_not_focus)));

        mCountryInfo = view.findViewById(R.id.country_code_info);
        mCountryNext = view.findViewById(R.id.country_code_next);
        mCountryNext.setOnClickListener(view12 -> {
            if (getModel().getNumber().getCountryCode() != 0) {
                enterPhoneNumber(view12);
            } else {
                Toast.makeText(requireActivity(), getString(R.string.RegistrationActivity_you_must_specify_your_country_code), Toast.LENGTH_LONG).show();
            }
        });

        RegistrationViewModel model = getModel();
        NumberViewState number = model.getNumber();
        initCountryCode(number);
    }

    private void initCountryCode(@NonNull NumberViewState numberViewState) {
        int code = numberViewState.getCountryCode();
        String region = numberViewState.getCountryDisplayName();
        if (code != 0 && region != null && !region.equals("")) {
            mCountryInfo.setVisibility(View.VISIBLE);
            mCountryInfo.setText(String.format(Locale.ENGLISH, "%s +%d", region, code));
            mCountryEntry.setTextSize(24);
        }
    }

    private void enterPhoneNumber(@NonNull View view) {
        Navigation.findNavController(view).navigate(R.id.action_enter_phonenumber);
    }

    private void pickCountry(@NonNull View view) {
        Navigation.findNavController(view).navigate(R.id.action_pickCountry);
    }

    //TODO : new Country code list adapter
    private static class CountryListAdapter extends RecyclerView.Adapter<CountryListAdapter.ViewHolder> {

        private static final int COUNTRY_LIST_TYPE_SEARCH = 0x00;
        private static final int COUNTRY_LIST_TYPE_ITEM = 0x01;

        private Context mContext;
        private List<String> mCountryList;

        CountryListAdapter(Context context, List<String> countries) {
            mContext = context;
            mCountryList = countries;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == COUNTRY_LIST_TYPE_SEARCH) {
                View searchView = LayoutInflater.from(mContext).inflate(R.layout.mp02_country_list_search, parent, false);
                return new ViewHolder(searchView);
            } else {
                View itemView = LayoutInflater.from(mContext).inflate(R.layout.mp02_country_list_item, parent, false);
                return new ViewHolder(itemView);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        }

        @Override
        public int getItemCount() {
            if (mCountryList != null) {
                return mCountryList.size() + 1;
            }
            return 1;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return COUNTRY_LIST_TYPE_SEARCH;
            } else {
                return COUNTRY_LIST_TYPE_ITEM;
            }
        }

        static class ViewHolder extends RecyclerView.ViewHolder {

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }

    }
}
