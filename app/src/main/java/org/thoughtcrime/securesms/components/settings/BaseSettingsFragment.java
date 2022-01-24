package org.thoughtcrime.securesms.components.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList;

import java.io.Serializable;
import java.util.Objects;

/**
 * A simple settings screen that takes its configuration via {@link Configuration}.
 */
public class BaseSettingsFragment extends Fragment {

  private static final String CONFIGURATION_ARGUMENT = "current_selection";

  private RecyclerView recycler;

  public static @NonNull BaseSettingsFragment create(@NonNull Configuration configuration) {
    BaseSettingsFragment fragment = new BaseSettingsFragment();

    Bundle arguments = new Bundle();
    arguments.putSerializable(CONFIGURATION_ARGUMENT, configuration);
    fragment.setArguments(arguments);

    return fragment;
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.base_settings_fragment, container, false);

    recycler = view.findViewById(R.id.base_settings_list);
    recycler.setItemAnimator(null);

    return view;
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    BaseSettingsAdapter adapter = new BaseSettingsAdapter();

    recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
    recycler.setAdapter(adapter);

    Configuration configuration = (Configuration) Objects.requireNonNull(requireArguments().getSerializable(CONFIGURATION_ARGUMENT));
    configuration.configure(requireActivity(), adapter);
    configuration.setArguments(getArguments());
    configuration.configureAdapter(adapter);

    adapter.submitList(configuration.getSettings());
  }

  /**
   * A configuration for a settings screen. Utilizes serializable to hide
   * reflection of instantiating from a fragment argument.
   */
  public static abstract class Configuration implements Serializable {
    protected transient FragmentActivity    activity;
    protected transient BaseSettingsAdapter adapter;

    public void configure(@NonNull FragmentActivity activity, @NonNull BaseSettingsAdapter adapter) {
      this.activity = activity;
      this.adapter  = adapter;
    }

    /**
     * Retrieve any runtime information from the fragment's arguments.
     */
    public void setArguments(@Nullable Bundle arguments) {}

    protected void updateSettingsList() {
      adapter.submitList(getSettings());
    }

    public abstract void configureAdapter(@NonNull BaseSettingsAdapter adapter);

    public abstract @NonNull MappingModelList getSettings();
  }
}
