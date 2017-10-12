/*
 * Copyright (C) 2016 Open Whisper Systems
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
package org.thoughtcrime.securesms.scribbles;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideApp;

public class StickerSelectFragment extends Fragment implements LoaderManager.LoaderCallbacks<String[]> {

  private RecyclerView recyclerView;
  private String assetDirectory;
  private StickerSelectionListener listener;

  public static StickerSelectFragment newInstance(String assetDirectory) {
    StickerSelectFragment fragment = new StickerSelectFragment();

    Bundle args = new Bundle();
    args.putString("assetDirectory", assetDirectory);
    fragment.setArguments(args);

    return fragment;
  }

  @Nullable
  public View onCreateView(LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    View view = inflater.inflate(R.layout.scribble_select_sticker_fragment, container, false);
    this.recyclerView = view.findViewById(R.id.stickers_recycler_view);

    return view;
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);

    this.assetDirectory = getArguments().getString("assetDirectory");

    getLoaderManager().initLoader(0, null, this);
    this.recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 3));
  }

  @Override
  public Loader<String[]> onCreateLoader(int id, Bundle args) {
    return new StickerLoader(getActivity(), assetDirectory);
  }

  @Override
  public void onLoadFinished(Loader<String[]> loader, String[] data) {
    recyclerView.setAdapter(new StickersAdapter(getActivity(), data));
  }

  @Override
  public void onLoaderReset(Loader<String[]> loader) {
    recyclerView.setAdapter(null);
  }

  public void setListener(StickerSelectionListener listener) {
    this.listener = listener;
  }

  class StickersAdapter extends RecyclerView.Adapter<StickersAdapter.StickerViewHolder> {

    private final Context context;
    private final String[]       stickerFiles;
    private final LayoutInflater layoutInflater;

    StickersAdapter(@NonNull Context context, @NonNull String[] stickerFiles) {
      this.context        = context;
      this.stickerFiles   = stickerFiles;
      this.layoutInflater = LayoutInflater.from(context);
    }

    @Override
    public StickerViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      return new StickerViewHolder(layoutInflater.inflate(R.layout.scribble_sticker_item, parent, false));
    }

    @Override
    public void onBindViewHolder(StickerViewHolder holder, int position) {
      holder.fileName = stickerFiles[position];

      GlideApp.with(context)
              .load(Uri.parse("file:///android_asset/" + holder.fileName))
              .diskCacheStrategy(DiskCacheStrategy.NONE)
              .into(holder.image);
    }

    @Override
    public int getItemCount() {
      return stickerFiles.length;
    }

    @Override
    public void onViewRecycled(StickerViewHolder holder) {
      super.onViewRecycled(holder);
      GlideApp.with(context).clear(holder.image);
    }

    private void onStickerSelected(String fileName) {
      if (listener != null) listener.onStickerSelected(fileName);
    }

    class StickerViewHolder extends RecyclerView.ViewHolder {

      private String fileName;
      private ImageView image;

      StickerViewHolder(View itemView) {
        super(itemView);
        image = itemView.findViewById(R.id.sticker_image);
        itemView.setOnClickListener(view -> {
          int pos = getAdapterPosition();
          if (pos >= 0) {
            onStickerSelected(fileName);
          }
        });
      }
    }
  }

  interface StickerSelectionListener {
    void onStickerSelected(String name);
  }


}
