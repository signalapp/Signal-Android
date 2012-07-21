/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListFragment;

import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;
import org.thoughtcrime.securesms.contacts.ContactAccessor.GroupData;
import org.thoughtcrime.securesms.contacts.ContactAccessor.NumberData;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * An activity for selecting a list of "contact groups."  Displayed
 * by ContactSelectionActivity in a tabbed frame, and ultimately called
 * by ComposeMessageActivity for selecting a list of recipients.
 *
 * @author Moxie Marlinspike
 *
 */
public class ContactSelectionGroupsFragment extends SherlockListFragment
    implements LoaderManager.LoaderCallbacks<Cursor>
{

  private final HashMap<Long, GroupData> selectedGroups = new HashMap<Long, GroupData>();

  @Override
  public void onActivityCreated(Bundle icicle) {
    super.onActivityCreated(icicle);

    initializeResources();
    initializeCursor();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.contact_selection_group_activity, container, false);
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {
    ((GroupItemView)v).selected();
  }

  private void initializeCursor() {
    setListAdapter(new GroupSelectionListAdapter(getActivity(), null));
    this.getLoaderManager().initLoader(0, null, this);
  }

  private void initializeResources() {
    this.getListView().setFocusable(true);
  }

  public Recipients getSelectedContacts() {
    List<Recipient> recipientList = new LinkedList<Recipient>();

    for (GroupData groupData : selectedGroups.values()) {
      List<ContactData> contactDataList = ContactAccessor.getInstance()
          .getGroupMembership(getActivity(), groupData.id);

      Log.w("GroupSelectionListActivity", "Got contacts in group: " + contactDataList.size());

      for (ContactData contactData : contactDataList) {
        for (NumberData numberData : contactData.numbers) {
          recipientList.add(new Recipient(contactData.name, numberData.number, null));
        }
      }
    }

    return new Recipients(recipientList);
  }

  private void addGroup(GroupData groupData) {
    selectedGroups.put(groupData.id, groupData);
  }

  private void removeGroup(GroupData groupData) {
    selectedGroups.remove(groupData.id);
  }

  private class GroupSelectionListAdapter extends CursorAdapter {

    public GroupSelectionListAdapter(Context context, Cursor cursor) {
      super(context, cursor);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
      GroupItemView view = new GroupItemView(context);
      bindView(view, context, cursor);
      return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
      GroupData groupData = ContactAccessor.getInstance().getGroupData(getActivity(), cursor);
      ((GroupItemView)view).set(groupData);
    }
  }

  private class GroupItemView extends LinearLayout {
    private GroupData groupData;
    private CheckedTextView name;

    public GroupItemView(Context context) {
      super(context);

      LayoutInflater li = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      li.inflate(R.layout.contact_selection_group_item, this, true);

      this.name   = (CheckedTextView)findViewById(R.id.name);
    }

    public void selected() {
      name.toggle();

      if (name.isChecked()) {
        addGroup(groupData);
      } else {
        removeGroup(groupData);
      }
    }

    public void set(GroupData groupData) {
      this.groupData = groupData;

      if (selectedGroups.containsKey(groupData.id))
        this.name.setChecked(true);
      else
        this.name.setChecked(false);

      this.name.setText(groupData.name);
    }
  }

//  private class GroupAggregationHandler extends Handler implements Runnable {
//    private List<Recipient> recipientList = new LinkedList<Recipient>();
//    private ProgressDialog progressDialog;
//    private final Context context;
//
//    public GroupAggregationHandler(Context context) {
//      this.context = context;
//    }
//
//    public void run() {
//      recipientList.clear();
//
//      for (GroupData groupData : selectedGroups.values()) {
//        List<ContactData> contactDataList = ContactAccessor.getInstance()
//            .getGroupMembership(getActivity(), groupData.id);
//
//          Log.w("GroupSelectionListActivity", "Got contacts in group: " + contactDataList.size());
//
//          for (ContactData contactData : contactDataList) {
//            for (NumberData numberData : contactData.numbers) {
//              recipientList.add(new Recipient(contactData.name, numberData.number, null));
//            }
//          }
//      }
//
//      this.obtainMessage().sendToTarget();
//    }
//
//    public void aggregateContacts() {
//      progressDialog = new ProgressDialog(context);
//      progressDialog.setTitle("Aggregating Contacts");
//      progressDialog.setMessage("Aggregating group contacts...");
//      progressDialog.setCancelable(false);
//      progressDialog.setIndeterminate(true);
//      progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
//      progressDialog.show();
//      Log.w("GroupSelectionListActivity", "Showing group spinner...");
//      new Thread(this).start();
//    }
//
//    @Override
//    public void handleMessage(Message message) {
//      progressDialog.dismiss();
//
//      listener.groupAggregationComplete(new Recipients(recipientList));
//    }
//  }

  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    return ContactAccessor.getInstance().getCursorLoaderForContactGroups(getActivity());
  }

  @Override
  public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
    ((CursorAdapter)getListAdapter()).changeCursor(cursor);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> arg0) {
    ((CursorAdapter)getListAdapter()).changeCursor(null);
  }
}
