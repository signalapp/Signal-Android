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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;

public class StickerSelectFragment extends Fragment implements LoaderManager.LoaderCallbacks<String[]> {

  private RecyclerView             recyclerView;
  private GlideRequests            glideRequests;
  private String                   assetDirectory;
  private StickerSelectionListener listener;

  public static StickerSelectFragment newInstance(String assetDirectory) {
    StickerSelectFragment fragment = new StickerSelectFragment();

    Bundle args = new Bundle();
    args.putString("assetDirectory", assetDirectory);
    fragment.setArguments(args);

    return fragment;
  }

  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
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

    this.glideRequests  = GlideApp.with(this);
    this.assetDirectory = getArguments().getString("assetDirectory");

    getLoaderManager().initLoader(0, null, this);
    this.recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 3));
  }

  @Override
  public @NonNull Loader<String[]> onCreateLoader(int id, Bundle args) {
    return new StickerLoader(getActivity(), assetDirectory);
  }

  @Override
  public void onLoadFinished(@NonNull Loader<String[]> loader, String[] data) {
    recyclerView.setAdapter(new StickersAdapter(getActivity(), glideRequests, data));
  }

  @Override
  public void onLoaderReset(@NonNull Loader<String[]> loader) {
    recyclerView.setAdapter(null);
  }

  public void setListener(StickerSelectionListener listener) {
    this.listener = listener;
  }

  class StickersAdapter extends RecyclerView.Adapter<StickersAdapter.StickerViewHolder> {

    private final GlideRequests  glideRequests;
    private final String[]       stickerFiles;
    private final LayoutInflater layoutInflater;

    StickersAdapter(@NonNull Context context, @NonNull GlideRequests glideRequests, @NonNull String[] stickerFiles) {
      this.glideRequests  = glideRequests;
      this.stickerFiles   = stickerFiles;
      this.layoutInflater = LayoutInflater.from(context);
    }

    @Override
    public @NonNull StickerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      return new StickerViewHolder(layoutInflater.inflate(R.layout.scribble_sticker_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull StickerViewHolder holder, int position) {
      holder.fileName = stickerFiles[position];

      glideRequests.load(Uri.parse("file:///android_asset/" + holder.fileName))
                   .diskCacheStrategy(DiskCacheStrategy.NONE)
                   .into(holder.image);
    }

    @Override
    public int getItemCount() {
      return stickerFiles.length;
    }

    @Override
    public void onViewRecycled(@NonNull StickerViewHolder holder) {
      super.onViewRecycled(holder);
      glideRequests.clear(holder.image);
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
