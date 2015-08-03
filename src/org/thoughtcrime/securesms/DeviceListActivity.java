package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.loaders.DeviceListLoader;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.ProgressDialogAsyncTask;
import org.whispersystems.textsecure.api.TextSecureAccountManager;
import org.whispersystems.textsecure.api.messages.multidevice.DeviceInfo;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

public class DeviceListActivity extends PassphraseRequiredActionBarActivity {


  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  @Override
  public void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }


  @Override
  public void onCreate(Bundle bundle, @NonNull MasterSecret masterSecret) {
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    initFragment(android.R.id.content, new DeviceListFragment(), masterSecret);
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home: finish(); return true;
    }

    return false;
  }

  public static class DeviceListFragment extends ListFragment
      implements LoaderManager.LoaderCallbacks<List<DeviceInfo>>, ListView.OnItemClickListener, InjectableType
  {

    private static final String TAG = DeviceListFragment.class.getSimpleName();

    @Inject TextSecureAccountManager accountManager;

    private View empty;
    private View progressContainer;

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

      return view;
    }

    @Override
    public void onActivityCreated(Bundle bundle) {
      super.onActivityCreated(bundle);
      getLoaderManager().initLoader(0, null, this).forceLoad();
      getListView().setOnItemClickListener(this);
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

      setListAdapter(new DeviceListAdapter(getActivity(), R.layout.device_list_item_view, data));

      if (data.isEmpty()) empty.setVisibility(View.VISIBLE);
      else                empty.setVisibility(View.GONE);
    }

    @Override
    public void onLoaderReset(Loader<List<DeviceInfo>> loader) {
      setListAdapter(null);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
      final String deviceName = ((DeviceListItem)view).getDeviceName();
      final long   deviceId   = ((DeviceListItem)view).getDeviceId();

      AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
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
      AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
      builder.setMessage(R.string.DeviceListActivity_network_connection_failed);
      builder.setPositiveButton(R.string.DeviceListActivity_try_again,
                                new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          getLoaderManager().initLoader(0, null, DeviceListFragment.this);
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

    private static class DeviceListAdapter extends ArrayAdapter<DeviceInfo> {

      private final int resource;

      public DeviceListAdapter(Context context, int resource, List<DeviceInfo> objects) {
        super(context, resource, objects);
        this.resource = resource;
      }

      @Override
      public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
          convertView = ((Activity)getContext()).getLayoutInflater().inflate(resource, parent, false);
        }

        ((DeviceListItem)convertView).set(getItem(position));

        return convertView;
      }
    }
  }

}
