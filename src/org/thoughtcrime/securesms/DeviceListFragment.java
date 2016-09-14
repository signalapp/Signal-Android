package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.melnykov.fab.FloatingActionButton;

import org.thoughtcrime.securesms.database.loaders.DeviceListLoader;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

public class DeviceListFragment extends ListFragment
    implements LoaderManager.LoaderCallbacks<List<DeviceInfo>>,
               ListView.OnItemClickListener, InjectableType, Button.OnClickListener
{

  private static final String TAG = DeviceListFragment.class.getSimpleName();

  @Inject
  SignalServiceAccountManager accountManager;

  private Locale                 locale;
  private View                   empty;
  private View                   progressContainer;
  private FloatingActionButton   addDeviceButton;
  private Button.OnClickListener addDeviceButtonListener;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.locale = (Locale) getArguments().getSerializable(PassphraseRequiredActionBarActivity.LOCALE_EXTRA);
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    ApplicationContext.getInstance(activity).injectDependencies(this);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    View view = inflater.inflate(R.layout.device_list_fragment, container, false);

    this.empty             = view.findViewById(R.id.empty);
    this.progressContainer = view.findViewById(R.id.progress_container);
    this.addDeviceButton   = ViewUtil.findById(view, R.id.add_device);
    this.addDeviceButton.setOnClickListener(this);

    return view;
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);
    getLoaderManager().initLoader(0, null, this);
    getListView().setOnItemClickListener(this);
  }

  public void setAddDeviceButtonListener(Button.OnClickListener listener) {
    this.addDeviceButtonListener = listener;
  }

  @Override
  public Loader<List<DeviceInfo>> onCreateLoader(int id, Bundle args) {
    empty.setVisibility(View.GONE);
    progressContainer.setVisibility(View.VISIBLE);

    return new DeviceListLoader(getActivity(), accountManager);
  }

  @Override
  public void onLoadFinished(Loader<List<DeviceInfo>> loader, List<DeviceInfo> data) {
    progressContainer.setVisibility(View.GONE);

    if (data == null) {
      handleLoaderFailed();
      return;
    }

    setListAdapter(new DeviceListAdapter(getActivity(), R.layout.device_list_item_view, data, locale));

    if (data.isEmpty()) {
      empty.setVisibility(View.VISIBLE);
      TextSecurePreferences.setMultiDevice(getActivity(), false);
    } else {
      empty.setVisibility(View.GONE);
    }
  }

  @Override
  public void onLoaderReset(Loader<List<DeviceInfo>> loader) {
    setListAdapter(null);
  }

  @Override
  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    final String deviceName = ((DeviceListItem)view).getDeviceName();
    final long   deviceId   = ((DeviceListItem)view).getDeviceId();

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setTitle(getActivity().getString(R.string.DeviceListActivity_unlink_s, deviceName));
    builder.setMessage(R.string.DeviceListActivity_by_unlinking_this_device_it_will_no_longer_be_able_to_send_or_receive);
    builder.setNegativeButton(android.R.string.cancel, null);
    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        handleDisconnectDevice(deviceId);
      }
    });
    builder.show();
  }

  private void handleLoaderFailed() {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setMessage(R.string.DeviceListActivity_network_connection_failed);
    builder.setPositiveButton(R.string.DeviceListActivity_try_again,
                              new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        getLoaderManager().restartLoader(0, null, DeviceListFragment.this);
      }
    });

    builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        DeviceListFragment.this.getActivity().onBackPressed();
      }
    });
    builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        DeviceListFragment.this.getActivity().onBackPressed();
      }
    });

    builder.show();
  }

  private void handleDisconnectDevice(final long deviceId) {
    new ProgressDialogAsyncTask<Void, Void, Void>(getActivity(),
                                                  R.string.DeviceListActivity_unlinking_device_no_ellipsis,
                                                  R.string.DeviceListActivity_unlinking_device)
    {
      @Override
      protected Void doInBackground(Void... params) {
        try {
          accountManager.removeDevice(deviceId);
        } catch (IOException e) {
          Log.w(TAG, e);
          Toast.makeText(getActivity(), R.string.DeviceListActivity_network_failed, Toast.LENGTH_LONG).show();
        }
        return null;
      }

      @Override
      protected void onPostExecute(Void result) {
        super.onPostExecute(result);
        getLoaderManager().restartLoader(0, null, DeviceListFragment.this);
      }
    }.execute();
  }

  @Override
  public void onClick(View v) {
    if (addDeviceButtonListener != null) addDeviceButtonListener.onClick(v);
  }

  private static class DeviceListAdapter extends ArrayAdapter<DeviceInfo> {

    private final int    resource;
    private final Locale locale;

    public DeviceListAdapter(Context context, int resource, List<DeviceInfo> objects, Locale locale) {
      super(context, resource, objects);
      this.resource = resource;
      this.locale = locale;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      if (convertView == null) {
        convertView = ((Activity)getContext()).getLayoutInflater().inflate(resource, parent, false);
      }

      ((DeviceListItem)convertView).set(getItem(position), locale);

      return convertView;
    }
  }
}
