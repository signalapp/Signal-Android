package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.recyclerview.widget.RecyclerView;

public abstract class MappingViewHolder<Model extends MappingModel<Model>> extends LifecycleViewHolder implements LifecycleOwner {

  protected final Context context;

  public MappingViewHolder(@NonNull View itemView) {
    super(itemView);
    context = itemView.getContext();
  }

  public <T extends View> T findViewById(@IdRes int id) {
    return itemView.findViewById(id);
  }

  public @NonNull Context getContext() {
    return itemView.getContext();
  }

  public abstract void bind(@NonNull Model model);
}
