package org.thoughtcrime.securesms.search;

import android.annotation.SuppressLint;
import androidx.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.search.model.MessageResult;
import org.thoughtcrime.securesms.search.model.SearchResult;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

import java.util.Locale;

/**
 * A fragment that is displayed to do full-text search of messages, groups, and contacts.
 */
public class SearchFragment extends Fragment implements SearchListAdapter.EventListener {

  public static final String TAG          = "SearchFragment";
  public static final String EXTRA_LOCALE = "locale";

  private TextView               noResultsView;
  private RecyclerView           listView;
  private StickyHeaderDecoration listDecoration;

  private SearchViewModel   viewModel;
  private SearchListAdapter listAdapter;
  private String            pendingQuery;
  private Locale            locale;

  public static SearchFragment newInstance(@NonNull Locale locale) {
    Bundle args = new Bundle();
    args.putSerializable(EXTRA_LOCALE, locale);

    SearchFragment fragment = new SearchFragment();
    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    this.locale = (Locale) getArguments().getSerializable(EXTRA_LOCALE);

    SearchRepository searchRepository = new SearchRepository(getContext(),
                                                             DatabaseFactory.getSearchDatabase(getContext()),
                                                             DatabaseFactory.getContactsDatabase(getContext()),
                                                             DatabaseFactory.getThreadDatabase(getContext()),
                                                             ContactAccessor.getInstance(),
                                                             SignalExecutors.SERIAL);
    viewModel = ViewModelProviders.of(this, new SearchViewModel.Factory(searchRepository)).get(SearchViewModel.class);

    if (pendingQuery != null) {
      viewModel.updateQuery(pendingQuery);
      pendingQuery = null;
    }
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_search, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    noResultsView = view.findViewById(R.id.search_no_results);
    listView      = view.findViewById(R.id.search_list);

    listAdapter    = new SearchListAdapter(GlideApp.with(this), this, locale);
    listDecoration = new StickyHeaderDecoration(listAdapter, false, false);

    listView.setAdapter(listAdapter);
    listView.addItemDecoration(listDecoration);
    listView.setLayoutManager(new LinearLayoutManager(getContext()));
  }

  @Override
  public void onStart() {
    super.onStart();
    viewModel.getSearchResult().observe(this, result -> {
      result = result != null ? result : SearchResult.EMPTY;

      listAdapter.updateResults(result);

      if (result.isEmpty()) {
        if (TextUtils.isEmpty(viewModel.getLastQuery().trim())) {
          noResultsView.setVisibility(View.GONE);
        } else {
          noResultsView.setVisibility(View.VISIBLE);
          noResultsView.setText(getString(R.string.SearchFragment_no_results, viewModel.getLastQuery()));
        }
      } else {
        noResultsView.setVisibility(View.VISIBLE);
        noResultsView.setText("");
      }
    });
  }

  @Override
  public void onConversationClicked(@NonNull ThreadRecord threadRecord) {
    ConversationListActivity conversationList = (ConversationListActivity) getActivity();

    if (conversationList != null) {
      conversationList.onCreateConversation(threadRecord.getThreadId(),
                                            threadRecord.getRecipient(),
                                            threadRecord.getDistributionType(),
                                            threadRecord.getLastSeen());
    }
  }

  @Override
  public void onContactClicked(@NonNull Recipient contact) {
    Intent intent = new Intent(getContext(), ConversationActivity.class);
    intent.putExtra(ConversationActivity.ADDRESS_EXTRA, contact.getAddress());

    long existingThread = DatabaseFactory.getThreadDatabase(getContext()).getThreadIdIfExistsFor(contact);

    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, existingThread);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
    startActivity(intent);
  }

  @SuppressLint("StaticFieldLeak")
  @Override
  public void onMessageClicked(@NonNull MessageResult message) {
    new AsyncTask<Void, Void, Integer>() {
      @Override
      protected Integer doInBackground(Void... voids) {
        int  startingPosition = DatabaseFactory.getMmsSmsDatabase(getContext()).getMessagePositionInConversation(message.threadId, message.receivedTimestampMs);
        startingPosition = Math.max(0, startingPosition);

        return startingPosition;
      }

      @Override
      protected void onPostExecute(Integer startingPosition) {
        ConversationListActivity conversationList = (ConversationListActivity) getActivity();
        if (conversationList != null) {
          conversationList.openConversation(message.threadId,
                                            message.conversationRecipient,
                                            ThreadDatabase.DistributionTypes.DEFAULT,
                                            -1,
                                            startingPosition);
        }
      }
    }.execute();
  }

  public void updateSearchQuery(@NonNull String query) {
    if (viewModel != null) {
      viewModel.updateQuery(query);
    } else {
      pendingQuery = query;
    }
  }
}
