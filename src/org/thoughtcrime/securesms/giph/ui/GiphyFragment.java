package org.thoughtcrime.securesms.giph.ui;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.giph.model.GiphyImage;
import org.thoughtcrime.securesms.giph.net.GiphyLoader;
import org.thoughtcrime.securesms.giph.util.InfiniteScrollListener;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.LinkedList;
import java.util.List;

public abstract class GiphyFragment extends Fragment implements LoaderManager.LoaderCallbacks<List<GiphyImage>>, GiphyAdapter.OnItemClickListener {

  private static final String TAG = GiphyFragment.class.getSimpleName();

  private GiphyAdapter                     giphyAdapter;
  private RecyclerView                     recyclerView;
  private ProgressBar                      loadingProgress;
  private TextView                         noResultsView;
  private GiphyAdapter.OnItemClickListener listener;

  protected String searchString;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
    ViewGroup container = ViewUtil.inflate(inflater, viewGroup, R.layout.giphy_fragment);
    this.recyclerView    = ViewUtil.findById(container, R.id.giphy_list);
    this.loadingProgress = ViewUtil.findById(container, R.id.loading_progress);
    this.noResultsView   = ViewUtil.findById(container, R.id.no_results);

    return container;
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);

    this.giphyAdapter = new GiphyAdapter(getActivity(), new LinkedList<GiphyImage>());
    this.giphyAdapter.setListener(this);

    this.recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
    this.recyclerView.setItemAnimator(new DefaultItemAnimator());
    this.recyclerView.setAdapter(giphyAdapter);
    this.recyclerView.addOnScrollListener(new GiphyScrollListener());

    getLoaderManager().initLoader(0, null, this);
  }

  @Override
  public void onLoadFinished(Loader<List<GiphyImage>> loader, List<GiphyImage> data) {
    this.loadingProgress.setVisibility(View.GONE);

    if (data == null || data.isEmpty()) noResultsView.setVisibility(View.VISIBLE);
    else                                noResultsView.setVisibility(View.GONE);

    this.giphyAdapter.setImages(data);
  }

  @Override
  public void onLoaderReset(Loader<List<GiphyImage>> loader) {
    noResultsView.setVisibility(View.GONE);
    this.giphyAdapter.setImages(new LinkedList<GiphyImage>());
  }

  public void setLayoutManager(int type) {
    if (type == GiphyActivityToolbar.OnLayoutChangedListener.LAYOUT_GRID) {
      this.recyclerView.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
    } else {
      this.recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
    }
  }

  public void setClickListener(GiphyAdapter.OnItemClickListener listener) {
    this.listener = listener;
  }

  public void setSearchString(@Nullable String searchString) {
    this.searchString = searchString;
    this.noResultsView.setVisibility(View.GONE);
    this.getLoaderManager().restartLoader(0, null, this);
  }

  @Override
  public void onClick(GiphyAdapter.GiphyViewHolder viewHolder) {
    if (listener != null) listener.onClick(viewHolder);
  }

  private class GiphyScrollListener extends InfiniteScrollListener {
    @Override
    public void onLoadMore(final int currentPage) {
      final Loader<List<GiphyImage>> loader = getLoaderManager().getLoader(0);
      if (loader == null) return;

      new AsyncTask<Void, Void, List<GiphyImage>>() {
        @Override
        protected List<GiphyImage> doInBackground(Void... params) {
          return ((GiphyLoader)loader).loadPage(currentPage * GiphyLoader.PAGE_SIZE);
        }

        protected void onPostExecute(List<GiphyImage> images) {
          giphyAdapter.addImages(images);
        }
      }.execute();
    }
  }
}
