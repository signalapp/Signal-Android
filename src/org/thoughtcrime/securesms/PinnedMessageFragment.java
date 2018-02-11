package org.thoughtcrime.securesms;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.Loader;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.database.Cursor;
import android.support.v4.app.LoaderManager;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.ViewUtil;

public class PinnedMessageFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String ARG_THREADID = "threadId";

    private long mThreadId;

    private RecyclerView recyclerView;
    private PinnedMessageAdapter adapter;
    private MasterSecret masterSecret;

    private OnFragmentInteractionListener mListener;

    public PinnedMessageFragment() {
    }

    public static PinnedMessageFragment newInstance(long threadId) {
        PinnedMessageFragment fragment = new PinnedMessageFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_THREADID, threadId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mThreadId = getArguments().getLong(ARG_THREADID);
        }

        this.mThreadId = getActivity().getIntent().getLongExtra("THREADID",-1);
        this.masterSecret = getArguments().getParcelable("master_secret");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.pinned_message_fragment, container, false);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, true);
        recyclerView = ViewUtil.findById(view, android.R.id.list);
        recyclerView.setHasFixedSize(false);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(null);

        adapter = new PinnedMessageAdapter(getActivity(), null, masterSecret);
        recyclerView.setAdapter(adapter);

        getLoaderManager().initLoader(1, null, this);

        return view;
    }

    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Log.v("pinFragment", "on create loader");
        return new PinMessagesLoader(getActivity(), this.mThreadId, 10);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        Log.v("pinFragment", "on finished loader");
        adapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public void setThreadId(long mThreadId){
        this.mThreadId = mThreadId;
    }

    public interface OnFragmentInteractionListener {
        void onFragmentInteraction(Uri uri);
    }

    public interface PinnedMessageFragmentListener {
        void setThreadId(long threadId);
    }
}
