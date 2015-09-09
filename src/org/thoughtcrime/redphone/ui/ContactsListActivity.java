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
//import android.annotation.SuppressLint;
//import android.content.Context;
//import android.content.Intent;
//import android.database.Cursor;
//import android.graphics.Bitmap;
//import android.graphics.Typeface;
//import android.os.Build;
//import android.os.Bundle;
//import android.provider.ContactsContract.CommonDataKinds.Phone;
//import android.support.v4.app.LoaderManager;
//import android.support.v4.content.Loader;
//import android.support.v7.app.AppCompatActivity;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.CursorAdapter;
//import android.widget.ImageView;
//import android.widget.ListView;
//import android.widget.RelativeLayout;
//import android.widget.SectionIndexer;
//import android.widget.TextView;
//
//import org.thoughtcrime.redphone.Constants;
//import org.thoughtcrime.redphone.RedPhone;
//import org.thoughtcrime.redphone.RedPhoneService;
//import org.thoughtcrime.redphone.contacts.ContactAccessor;
//import org.thoughtcrime.redphone.contacts.ContactsSectionIndexer;
//import org.thoughtcrime.redphone.util.BitmapUtil;
//
//import java.lang.ref.SoftReference;
//import java.util.HashMap;
//
///**
// * Activity that displays a listview of contacts for the RedPhone "dialer."
// * Both for straight contacts and "frequently contacted" tabs.
// *
// * @author Moxie Marlinspike
// *
// */
//
//public class ContactsListActivity extends AppCompatActivity
//                                  implements LoaderManager.LoaderCallbacks<Cursor>
//{
//
//  private HashMap<Long, SoftReference<Bitmap>> photoCache
//                                                  = new HashMap<Long, SoftReference<Bitmap>>();
//
//  private boolean showSectionHeaders = true;
//  private String queryFilter         = "";
//
//  @Override
//  public boolean onOptionsItemSelected(MenuItem item) {
//    super.onOptionsItemSelected(item);
//
//    switch (item.getItemId()) {
//    case R.id.searchItem:
//      this.getSherlockActivity().getSupportActionBar().setIcon(R.drawable.ic_tab_contacts);
//      return true;
//    }
//
//    return false;
//  }
//
//  @Override
//  public void onActivityCreated(Bundle icicle) {
//    super.onActivityCreated(icicle);
//    displayContacts();
//
//    if (!isFavoritesFragment() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
//      setHasOptionsMenu(true);
//    }
//  }
//
//  @Override
//  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//    return inflater.inflate(R.layout.contacts_list_content, container, false);
//  }
//
//  @Override
//  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
//    inflater.inflate(R.menu.contact_list_options_menu, menu);
//    initializeSearch((android.widget.SearchView)menu.findItem(R.id.searchItem).getActionView());
//  }
//
//  @SuppressLint("NewApi")
//  private void initializeSearch(android.widget.SearchView searchView) {
//    searchView.setOnQueryTextListener(new android.widget.SearchView.OnQueryTextListener() {
//      @Override
//      public boolean onQueryTextSubmit(String query) {
//        ContactsListActivity.this.queryFilter = query;
//        ContactsListActivity.this.getLoaderManager().restartLoader(0, null, ContactsListActivity.this);
//        return true;
//      }
//      @Override
//      public boolean onQueryTextChange(String newText) {
//        return onQueryTextSubmit(newText);
//      }
//    });
//  }
//
//  @Override
//  public void onListItemClick(ListView l, View v, int position, long id) {
//    ContactItemView contactItemView = (ContactItemView)v;
//    String number                   = contactItemView.getNumber();
//    Intent intent                   = new Intent(getActivity(), RedPhoneService.class);
//
//    intent.setAction(RedPhoneService.ACTION_OUTGOING_CALL);
//    intent.putExtra(Constants.REMOTE_NUMBER, number );
//    getActivity().startService(intent);
//
//    Intent activityIntent = new Intent(getActivity(), RedPhone.class);
//    activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//    startActivity(activityIntent);
//
//    getActivity().finish();
//  }
//
//  private void displayContacts() {
//    this.showSectionHeaders = !isFavoritesFragment();
//    setListAdapter(new ContactsListAdapter(getActivity(), null));
//    this.getLoaderManager().initLoader(0, null, this);
//    getListView().setDivider(null);
//  }
//
//
//  private class ContactsListAdapter extends CursorAdapter implements SectionIndexer {
//    private ContactsSectionIndexer indexer;
//    private HashMap<String, Integer> groupingTable;
//
//    public ContactsListAdapter(Context context, Cursor c) {
//      super(context, c, false);
//      this.indexer       = new ContactsSectionIndexer(c, Phone.DISPLAY_NAME);
//      this.groupingTable = new HashMap<String, Integer>();
//    }
//
//    @Override
//    public void changeCursor(Cursor cursor) {
//      this.groupingTable = new HashMap<String, Integer>();
//      this.indexer       = new ContactsSectionIndexer(cursor, Phone.DISPLAY_NAME);
//      super.changeCursor(cursor);
//    }
//
////    @Override
////    public Cursor swapCursor(Cursor cursor) {
////      this.groupingTable = new HashMap<String, Integer>();
////      this.indexer       = new ContactsSectionIndexer(cursor, Phone.DISPLAY_NAME);
////      return super.swapCursor(cursor);
////    }
//
//    public int getPositionForSection(int section) {
//      return indexer.getPositionForSection(section);
//    }
//
//    public int getSectionForPosition(int position) {
//      return indexer.getSectionForPosition(position);
//    }
//
//    public Object[] getSections() {
//      return indexer.getSections();
//    }
//
//    @Override
//    public View newView(Context context, Cursor cursor, ViewGroup parent) {
//      ContactItemView view = new ContactItemView(context);
//      bindView(view, context, cursor);
//
//      return view;
//    }
//
//    @Override
//    public void bindView(View view, Context context, Cursor cursor) {
//      String contactName = cursor.getString(cursor.getColumnIndex(Phone.DISPLAY_NAME));
//      int personId       = cursor.getInt(cursor.getColumnIndex(Phone.CONTACT_ID));
//      String number      = cursor.getString(cursor.getColumnIndex(Phone.NUMBER));
//      int type           = cursor.getInt(cursor.getColumnIndex(Phone.TYPE));
//      int rowId          = cursor.getInt(cursor.getColumnIndex(Phone._ID));
//
//      int section = indexer.getSectionForPosition(cursor.getPosition());
//
//      if (showSectionHeaders && (cursor.getPosition() == indexer.getPositionForSection(section))) {
//        ((ContactItemView)view).setSectionLabel((String)indexer.getSections()[section]);
//      } else {
//        ((ContactItemView)view).disableSectionLabel();
//      }
//
//      if (!groupingTable.containsKey(contactName) ||
//          (groupingTable.containsKey(contactName) &&
//           groupingTable.get(contactName) == rowId))
//      {
//        groupingTable.put(contactName, rowId);
//        ((ContactItemView)view).set(contactName, personId, number, type);
//      } else {
//        ((ContactItemView)view).setGrouped(contactName, personId, number, type);
//      }
//
//    }
//  }
//
//  private class ContactItemView extends RelativeLayout {
//    private ImageView divider;
//    private View sectionDivider;
//    private TextView sectionLabel;
//    private TextView name;
//    private TextView number;
//    private TextView type;
//    private ImageView avatar;
//
//    public ContactItemView(Context context) {
//      super(context.getApplicationContext());
//
//      LayoutInflater li = (LayoutInflater) context
//          .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//      li.inflate(R.layout.contact_list_item, this, true);
//
//      this.name           = (TextView)findViewById(R.id.name);
//      this.number         = (TextView)findViewById(R.id.number);
//      this.type           = (TextView)findViewById(R.id.type);
//      this.sectionLabel   = (TextView)findViewById(R.id.section_label);
//      this.divider        = (ImageView)findViewById(R.id.divider);
//      this.sectionDivider = findViewById(R.id.section_divider);
//      this.avatar         = (ImageView)findViewById(R.id.contact_photo_image);
//    }
//
//    public void setSectionLabel(String label) {
//      this.sectionLabel.setText(label);
//      this.sectionLabel.setTypeface(sectionLabel.getTypeface(), Typeface.BOLD);
//      this.sectionLabel.setVisibility(View.VISIBLE);
//      this.divider.setVisibility(View.GONE);
//      this.sectionDivider.setVisibility(View.VISIBLE);
//    }
//
//    public void disableSectionLabel() {
//      this.sectionLabel.setVisibility(View.GONE);
//      this.divider.setVisibility(View.VISIBLE);
//      this.sectionDivider.setVisibility(View.GONE);
//    }
//
//    public void set(String name, int personId, String number, int type) {
//      this.avatar.setImageBitmap(loadContactPhoto(personId));
//      this.name.setText(name);
//      this.number.setText(number);
//      this.type.setText(Phone.getTypeLabel(ContactsListActivity.this.getResources(), type, "").toString().toUpperCase());
//      this.avatar.setVisibility(View.VISIBLE);
//      this.name.setVisibility(View.VISIBLE);
//      this.divider.setVisibility(View.VISIBLE);
//    }
//
//    public void setGrouped(String name, int personId, String number, int type) {
//      set(name, personId, number, type);
//      this.name.setVisibility(View.INVISIBLE);
//      this.divider.setVisibility(View.GONE);
//      this.avatar.setVisibility(View.INVISIBLE);
//    }
//
//    public String getNumber() {
//      return this.number.getText().toString();
//    }
//
//    private Bitmap getCachedBitmapIfAvailable(long id) {
//      SoftReference<Bitmap> bitmapReference = photoCache.get(id);
//      if( bitmapReference != null ) {
//        Bitmap cachedBitmap = bitmapReference.get();
//        if( cachedBitmap != null ) {
//          return cachedBitmap;
//        } else {
//          photoCache.remove(id);
//        }
//      }
//
//      return null;
//    }
//
//    private Bitmap constructNewBitmap(long id) {
//      Bitmap newBitmap            = ContactAccessor.getInstance()
//                                    .getPhoto(ContactsListActivity.this.getActivity(), id);
//      Bitmap croppedBitmap        = BitmapUtil.getCircleCroppedBitmap(newBitmap);
//      SoftReference<Bitmap> newSR = new SoftReference<Bitmap>(croppedBitmap);
//      photoCache.put(id,newSR);
//      return croppedBitmap;
//    }
//
//    private Bitmap loadContactPhoto(long id) {
//      Bitmap contactBitmap = getCachedBitmapIfAvailable(id);
//
//      if (contactBitmap != null) return contactBitmap;
//      else                       return constructNewBitmap(id);
//    }
//  }
//
//  private boolean isFavoritesFragment() {
//    return getArguments() != null && getArguments().getBoolean("favorites", false);
//  }
//
//  @Override
//  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
//    ((TextView)getListView().getEmptyView()).setText(R.string.ContactsListActivity_loading);
//
//    if (isFavoritesFragment()) {
//      if (this.queryFilter == null || this.queryFilter.trim().length() == 0) {
//        return ContactAccessor.getInstance().getRegisteredFavoritesCursor(getActivity());
//      } else {
//        return ContactAccessor.getInstance().getRegisteredFavoritesCursor(getActivity(), queryFilter);
//      }
//    } else {
//      if (this.queryFilter == null || this.queryFilter.trim().length() == 0) {
//        return ContactAccessor.getInstance().getRegisteredContactsCursor(getActivity());
//      } else {
//        return ContactAccessor.getInstance().getRegisteredContactsCursor(getActivity(), queryFilter);
//      }
//    }
//  }
//
//  @Override
//  public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
//    ((TextView)getListView().getEmptyView()).setText(R.string.ContactsListActivity_no_contacts_found);
//    ((CursorAdapter)getListAdapter()).changeCursor(cursor);
//  }
//
//  @Override
//  public void onLoaderReset(Loader<Cursor> arg0) {
//    ((CursorAdapter)getListAdapter()).changeCursor(null);
//  }
//
//}
