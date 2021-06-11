package org.thoughtcrime.securesms.export;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.thoughtcrime.securesms.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class ChatExportTimePickerFragment extends Fragment {

    private static final String TAG = ChatExportTimePickerFragment.class.getSimpleName ();

    private ChipGroup chipGroup;
    private Chip all;
    private Chip thirtyDays;
    private Chip sevenDays;
    private Chip lastThreeMonths;
    private Chip lastSixMonths;
    private TextView fromDateView;
    private TextView untilDateView;
    private Button                          selectTimePeriodButton;
    private DatePickerDialog                datePickerDialog;

    private Date fromDate = new Date(0L);
    private Date untilDate = new Date();

    int startYear, startMonth, startDay, endYear, endMonth, endDay;

    private ChatExportViewModel viewModel;
    private Calendar            cFrom;
    private              Calendar         cUntil;
    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy");


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_chat_export_time_picker, container, false);

        viewModel = ViewModelProviders.of(getActivity ()).get(ChatExportViewModel.class);
        chipGroup = view.findViewById (R.id.chat_export_chip_group);

        all = view.findViewById (R.id.chip_all);
        thirtyDays = view.findViewById (R.id.chip_30_days);
        sevenDays = view.findViewById (R.id.chip_7_days);
        lastThreeMonths = view.findViewById (R.id.chip_3_months);
        lastSixMonths = view.findViewById (R.id.chip_6_months);
        fromDateView = view.findViewById (R.id.export_from_date);
        untilDateView = view.findViewById (R.id.export_to_date);
        selectTimePeriodButton = view.findViewById (R.id.button_set_time_period);
        viewModel.getDateFrom ().observe (getViewLifecycleOwner(), from -> fromDate = from.get ());
        viewModel.getDateUntil ().observe (getViewLifecycleOwner(), until -> untilDate = until.get ());
        cUntil = Calendar.getInstance ();
        cUntil.setTime (untilDate);
        cFrom = Calendar.getInstance ();
        cFrom.setTime (fromDate);
        fromDateView.setText (dateFormatter.format (fromDate));
        untilDateView.setText (dateFormatter.format (untilDate));
        return view;
    }

    @RequiresApi(api = 24)
    @SuppressLint({"ResourceType", "LogTagInlined", "ClickableViewAccessibility", "SetTextI18n"})
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        all.setOnClickListener(v -> {
            chipGroup.clearCheck ();
            chipGroup.setSingleSelection (all.getId ());
                fromDate = new Date(0L);
                untilDate = new Date();
                setPeriodTime(untilDate, fromDate);

        });
        sevenDays.setOnClickListener(v -> {
            chipGroup.clearCheck ();
            chipGroup.setSingleSelection (sevenDays.getId ());
            cUntil = Calendar.getInstance();
            cFrom = Calendar.getInstance();
            untilDate = cUntil.getTime ();
            cFrom.add(Calendar.DAY_OF_MONTH, -7);
                fromDate = cFrom.getTime();
                setPeriodTime(untilDate, fromDate);
        });
        thirtyDays.setOnClickListener(v -> {
            chipGroup.clearCheck ();
            chipGroup.setSingleSelection (thirtyDays.getId ());
            cUntil = Calendar.getInstance();
            cFrom = Calendar.getInstance();
            untilDate = cUntil.getTime ();
            cFrom.add(Calendar.MONTH, -1);
            fromDate = cFrom.getTime();
            setPeriodTime(untilDate, fromDate);
        });
        lastThreeMonths.setOnClickListener(v -> {
            chipGroup.clearCheck ();
            chipGroup.setSingleSelection (lastThreeMonths.getId ());
            cUntil = Calendar.getInstance();
            cFrom = Calendar.getInstance();
            untilDate = cUntil.getTime ();
            cFrom.add (Calendar.MONTH,-3);
            fromDate = cFrom.getTime();
            setPeriodTime(untilDate, fromDate);
        });
        lastSixMonths.setOnClickListener(v -> {
            chipGroup.clearCheck ();

            chipGroup.setSingleSelection (lastSixMonths.getId ());
            cUntil = Calendar.getInstance();
            cFrom = Calendar.getInstance();
            untilDate = cUntil.getTime ();
            cFrom.add(Calendar.MONTH, -6);
            fromDate = cFrom.getTime();
            setPeriodTime(untilDate, fromDate);
        });

        fromDateView.setOnClickListener (v -> {
            chipGroup.clearCheck ();
            // calender class's instance and get current date , month and year from calender
            startYear = cFrom.get (Calendar.YEAR); // current year
            startMonth = cFrom.get (Calendar.MONTH); // current month
            startDay = cFrom.get (Calendar.DAY_OF_MONTH); // current day
            // date picker dialog
            datePickerDialog = new DatePickerDialog (getContext (),
                    (vi, year, monthOfYear, dayOfMonth) -> {
                        // set day of month , month and year value in the edit text
                        fromDateView.setText (dayOfMonth + "/"
                                + (monthOfYear + 1) + "/" + year);
                        cFrom.set (year, monthOfYear, dayOfMonth);
                        fromDate = cFrom.getTime ();

                    }, startYear, startMonth, startDay);
            setPeriodTime (cUntil.getTime (), cFrom.getTime ());
            datePickerDialog.show ();
        });

        untilDateView.setOnClickListener (v -> {
            chipGroup.clearCheck ();
            // calender class's instance and get current date , month and year from calender
            endYear = cUntil.get (Calendar.YEAR); // current year
            endMonth = cUntil.get (Calendar.MONTH); // current month
            endDay = cUntil.get (Calendar.DAY_OF_MONTH); // current day
            // date picker dialog
            datePickerDialog = new DatePickerDialog (getContext (),
                    (vi, year, monthOfYear, dayOfMonth) -> {
                        // set day of month , month and year value in the edit text
                        untilDateView.setText (dayOfMonth + "/"
                                + (monthOfYear + 1) + "/" + year);
                        cUntil.set (year, monthOfYear, dayOfMonth);
                        untilDate = cUntil.getTime ();

                    }, endYear, endMonth, endDay);
            setPeriodTime (cUntil.getTime (), cFrom.getTime ());
            datePickerDialog.show ();
        });

        selectTimePeriodButton.setOnClickListener (
                unused -> {
                    setPeriodTime (cUntil.getTime (), cFrom.getTime ());
                    changePeriodTime (cUntil.getTime (), cFrom.getTime ());
                    if(isValidPeriod())
                        Navigation.findNavController(requireView()).popBackStack();
                    else
                        Toast.makeText(requireContext (), "Time Period is not valid", Toast.LENGTH_SHORT).show();
                });

        }

    private boolean isValidPeriod () {
        return untilDate.getTime () >= fromDate.getTime ();
    }

    private void setPeriodTime (Date untilDate, Date fromDate) {
        fromDateView.setText (dateFormatter.format (fromDate));
        untilDateView.setText (dateFormatter.format (untilDate));
    }

    private void changePeriodTime  (Date untilDate, Date fromDate){

        viewModel.setDateUntil (untilDate);
        viewModel.setDateFrom (fromDate);
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }


}
