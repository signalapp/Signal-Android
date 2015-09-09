///*
// * Copyright (C) 2011 Whisper Systems
// *
// * This program is free software: you can redistribute it and/or modify
// * it under the terms of the GNU General Public License as published by
// * the Free Software Foundation, either version 3 of the License, or
// * (at your option) any later version.
// *
// * This program is distributed in the hope that it will be useful,
// * but WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// * GNU General Public License for more details.
// *
// * You should have received a copy of the GNU General Public License
// * along with this program.  If not, see <http://www.gnu.org/licenses/>.
// */
//
//package org.thoughtcrime.redphone.ui;
//
//import android.app.Activity;
//import android.app.NotificationManager;
//import android.content.Context;
//import android.content.Intent;
//import android.database.Cursor;
//import android.graphics.drawable.Drawable;
//import android.os.Bundle;
//import android.provider.CallLog.Calls;
//import android.support.v4.app.LoaderManager;
//import android.support.v4.content.Loader;
//import android.text.format.DateUtils;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.CursorAdapter;
//import android.widget.ImageView;
//import android.widget.ListView;
//import android.widget.RelativeLayout;
//import android.widget.TextView;
//
//import com.actionbarsherlock.app.SherlockListFragment;
//
//import org.thoughtcrime.redphone.Constants;
//import org.thoughtcrime.redphone.R;
//import org.thoughtcrime.redphone.RedPhone;
//import org.thoughtcrime.redphone.RedPhoneService;
//import org.thoughtcrime.redphone.contacts.ContactAccessor;
//import org.thoughtcrime.redphone.contacts.RegisteredUserCursorLoader;
//import org.thoughtcrime.redphone.util.BitmapUtil;
//import org.thoughtcrime.redphone.util.Util;
//
///**
// * A tab for the dialer activity which displays recent call history.
// *
// * @author Moxie Marlinspike
// *
// */
//public class RecentCallListActivity extends SherlockListFragment
//                                    implements LoaderManager.LoaderCallbacks<Cursor>
//{
//
//  @Override
//  public void onActivityCreated(Bundle icicle) {
//    super.onCreate(icicle);
//    displayCalls();
//  }
//
//  @Override
//  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//    return inflater.inflate(R.layout.recent_calls, container, false);
//  }
//
//  @Override
//  public void onResume() {
//    super.onResume();
//    NotificationManager notificationManager =
//         (NotificationManager)getActivity().getSystemService(Activity.NOTIFICATION_SERVICE);
//    notificationManager.cancel(DialerActivity.MISSED_CALL);
//  }
//
//  private void displayCalls() {
//    setListAdapter(new CallListAdapter(getActivity(), null));
//    this.getLoaderManager().initLoader(0, null, this);
//  }
//
//  private class CallListAdapter extends CursorAdapter {
//
//    public CallListAdapter(Context context, Cursor c) {
//      super(context, c, false);
//    }
//
//    @Override
//    public View newView(Context context, Cursor cursor, ViewGroup parent) {
//      CallItemView view = new CallItemView(context);
//      bindView(view, context, cursor);
//
//      return view;
//    }
//
//    @Override
//    public void bindView(View view, Context context, Cursor cursor) {
//      String name   = cursor.getString(cursor.getColumnIndexOrThrow(Calls.CACHED_NAME));
//      String number = cursor.getString(cursor.getColumnIndexOrThrow(Calls.NUMBER));
//      int    type   = cursor.getInt(cursor.getColumnIndexOrThrow(Calls.TYPE));
//      long   date   = cursor.getLong(cursor.getColumnIndexOrThrow(Calls.DATE));
//
//      ((CallItemView)view).set(name, number, type, date);
//    }
//  }
//
//  private class CallItemView extends RelativeLayout {
//    private Context context;
//    private ImageView avatar;
//    private ImageView callTypeIcon;
//    private TextView date;
//    private TextView name;
////    private TextView label;
//    private TextView number;
////    private TextView line1;
//
//    private Drawable incomingCallIcon;
//    private Drawable outgoingCallIcon;
//    private Drawable missedCallIcon;
//
//    public CallItemView(Context context) {
//      super(context.getApplicationContext());
//
//      LayoutInflater li = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//      li.inflate(R.layout.recent_call_item, this, true);
//
//      this.context          = context.getApplicationContext();
//      this.callTypeIcon     = (ImageView) findViewById(R.id.call_type_icon);
//      this.date             = (TextView)  findViewById(R.id.date);
//      this.avatar           = (ImageView) findViewById(R.id.contact_photo_image);
//      this.number           = (TextView)  findViewById(R.id.number);
//      this.name             = (TextView) findViewById(R.id.name);
//      this.incomingCallIcon = getResources().getDrawable(R.drawable.ic_call_log_list_incoming_call);
//      this.outgoingCallIcon = getResources().getDrawable(R.drawable.ic_call_log_list_outgoing_call);
//      this.missedCallIcon   = getResources().getDrawable(R.drawable.ic_call_log_list_missed_call);
//    }
//
//    public void set(String name, String number, int type, long date) {
//      this.name.setText(Util.isEmpty(name) ? number : name);
//      this.number.setText(Util.isEmpty(name) ? "" : number);
//      this.date.setText(DateUtils.getRelativeDateTimeString(context, date,
//                                                            System.currentTimeMillis(),
//                                                            DateUtils.MINUTE_IN_MILLIS,
//                                                            DateUtils.FORMAT_ABBREV_RELATIVE));
//
//      this.avatar.setImageBitmap(BitmapUtil.getCircleCroppedBitmap(ContactAccessor.getInstance().getDefaultContactPhoto(context)));
//
//      if (type == Calls.INCOMING_TYPE) {
//        callTypeIcon.setImageDrawable(incomingCallIcon);
//      } else if (type == Calls.OUTGOING_TYPE) {
//        callTypeIcon.setImageDrawable(outgoingCallIcon);
//      } else if (type == Calls.MISSED_TYPE) {
//        callTypeIcon.setImageDrawable(missedCallIcon);
//      }
//    }
//
//    public String getNumber() {
//      if (this.number.getText().toString().equals(""))
//        return this.name.getText().toString();
//
//      return this.number.getText().toString();
//    }
//  }
//
//  @Override
//  public void onListItemClick(ListView l, View v, int position, long id) {
//    Intent intent = new Intent(getActivity(), RedPhoneService.class);
//    intent.setAction(RedPhoneService.ACTION_OUTGOING_CALL);
//    intent.putExtra(Constants.REMOTE_NUMBER, ((CallItemView)v).getNumber());
//    getActivity().startService(intent);
//
//    Intent activityIntent = new Intent(getActivity(), RedPhone.class);
//    activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//    startActivity(activityIntent);
//
//    getActivity().finish();
//  }
//
//  @Override
//  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
//    // NOTE: To fix a weird bug:
//    // http://stackoverflow.com/questions/11810547/runtimeexception-at-calllog-calls
//    String[] projection = {
//        Calls._ID,
//        Calls.CACHED_NAME,
//        Calls.CACHED_NUMBER_LABEL,
//        Calls.NUMBER,
//        Calls.TYPE,
//        Calls.DATE
//    };
//
//    ((TextView)getListView().getEmptyView()).setText(R.string.RecentCallListActivity_loading);
//    return new RegisteredUserCursorLoader(getActivity(), Calls.CONTENT_URI, projection, null,
//                                          Calls.DEFAULT_SORT_ORDER, Calls.NUMBER, false);
//  }
//
//  @Override
//  public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
//    ((TextView)getListView().getEmptyView()).setText(R.string.RecentCallListActivity_empty_call_log);
//    ((CursorAdapter)getListAdapter()).changeCursor(cursor);
//  }
//
//  @Override
//  public void onLoaderReset(Loader<Cursor> arg0) {
//    ((CursorAdapter)getListAdapter()).changeCursor(null);
//  }
//
//}
