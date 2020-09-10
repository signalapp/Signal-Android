package org.thoughtcrime.securesms.util;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import org.whispersystems.libsignal.util.guava.Function;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A reusable and composable {@link androidx.recyclerview.widget.RecyclerView.Adapter} built on-top of {@link ListAdapter} to
 * provide async item diffing support.
 * <p></p>
 * The adapter makes use of mapping a model class to view holder factory at runtime via one of the {@link #registerFactory(Class, Factory)}
 * methods. The factory creates a view holder specifically designed to handle the paired model type. This allows the view holder concretely
 * deal with the model type it cares about. Due to the enforcement of matching generics during factory registration we can safely ignore or
 * override compiler typing recommendations when binding and diffing.
 * <p></p>
 * General pattern for implementation:
 *  <ol>
 *    <li>Create {@link MappingModel}s for the items in the list. These encapsulate data massaging methods for views to use and the diff logic.</li>
 *    <li>Create {@link MappingViewHolder}s for each item type in the list and their corresponding {@link Factory}.</li>
 *    <li>Create an instance or subclass of {@link MappingAdapter} and register the mapping of model type to view holder factory for that model type.</li>
 *  </ol>
 *  Event listeners, click or otherwise, are handled at the view holder level and should be passed into the appropriate view holder factories. This
 *  pattern mimics how we pass data into view models via factories.
 *  <p></p>
 *  NOTE: There can only be on factory registered per model type. Registering two for the same type will result in the last one being used. However, the
 *  same factory can be registered multiple times for multiple model types (if the model type class hierarchy supports it).
 */
public class MappingAdapter extends ListAdapter<MappingModel<?>, MappingViewHolder<?>> {

  private final Map<Integer, Factory<?>> factories;
  private final Map<Class<?>, Integer>   itemTypes;
  private       int                      typeCount;

  public MappingAdapter() {
    super(new MappingDiffCallback());

    factories = new HashMap<>();
    itemTypes = new HashMap<>();
    typeCount = 0;
  }

  @Override
  public void onViewAttachedToWindow(@NonNull MappingViewHolder<?> holder) {
    super.onViewAttachedToWindow(holder);
    holder.onAttachedToWindow();
  }

  @Override
  public void onViewDetachedFromWindow(@NonNull MappingViewHolder<?> holder) {
    super.onViewDetachedFromWindow(holder);
    holder.onDetachedFromWindow();
  }

  public <T extends MappingModel<T>> void registerFactory(Class<T> clazz, Factory<T> factory) {
    int type = typeCount++;
    factories.put(type, factory);
    itemTypes.put(clazz, type);
  }

  @Override
  public int getItemViewType(int position) {
    Integer type = itemTypes.get(getItem(position).getClass());
    if (type != null) {
      return type;
    }
    throw new AssertionError("No view holder factory for type: " + getItem(position).getClass());
  }

  @Override
  public @NonNull MappingViewHolder<?> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return Objects.requireNonNull(factories.get(viewType)).createViewHolder(parent);
  }

  @Override
  public void onBindViewHolder(@NonNull MappingViewHolder holder, int position) {
    //noinspection unchecked
    holder.bind(getItem(position));
  }

  private static class MappingDiffCallback extends DiffUtil.ItemCallback<MappingModel<?>> {
    @Override
    public boolean areItemsTheSame(@NonNull MappingModel oldItem, @NonNull MappingModel newItem) {
      if (oldItem.getClass() == newItem.getClass()) {
        //noinspection unchecked
        return oldItem.areItemsTheSame(newItem);
      }
      return false;
    }

    @SuppressLint("DiffUtilEquals")
    @Override
    public boolean areContentsTheSame(@NonNull MappingModel oldItem, @NonNull MappingModel newItem) {
      if (oldItem.getClass() == newItem.getClass()) {
        //noinspection unchecked
        return oldItem.areContentsTheSame(newItem);
      }
      return false;
    }
  }

  public interface Factory<T extends MappingModel<T>> {
    @NonNull MappingViewHolder<T> createViewHolder(@NonNull ViewGroup parent);
  }

  public static class LayoutFactory<T extends MappingModel<T>> implements Factory<T> {
    private       Function<View, MappingViewHolder<T>> creator;
    private final int                                  layout;

    public LayoutFactory(@NonNull Function<View, MappingViewHolder<T>> creator, @LayoutRes int layout) {
      this.creator = creator;
      this.layout  = layout;
    }

    @Override
    public @NonNull MappingViewHolder<T> createViewHolder(@NonNull ViewGroup parent) {
      return creator.apply(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }
  }
}
