package org.thoughtcrime.securesms.util.adapter.mapping;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.util.NoCrossfadeChangeDefaultAnimator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function1;

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
 * <ol>
 *   <li>Create {@link MappingModel}s for the items in the list. These encapsulate data massaging methods for views to use and the diff logic.</li>
 *   <li>Create {@link MappingViewHolder}s for each item type in the list and their corresponding {@link Factory}.</li>
 *   <li>Create an instance or subclass of {@link MappingAdapter} and register the mapping of model type to view holder factory for that model type.</li>
 * </ol>
 * Event listeners, click or otherwise, are handled at the view holder level and should be passed into the appropriate view holder factories. This
 * pattern mimics how we pass data into view models via factories.
 * <p></p>
 * NOTE: There can only be on factory registered per model type. Registering two for the same type will result in the last one being used. However, the
 * same factory can be registered multiple times for multiple model types (if the model type class hierarchy supports it).
 */
public class MappingAdapter extends ListAdapter<MappingModel<?>, MappingViewHolder<?>> {

  final Map<Integer, Factory<?>> factories  = new HashMap<>();
  final Map<Class<?>, Integer>   itemTypes  = new HashMap<>();
        int                      typeCount  = 0;
  final boolean                  useNoCrossfadeAnimator;

  public MappingAdapter() {
    this(true);
  }

  public MappingAdapter(boolean useNoCrossfadeAnimator) {
    super(new MappingDiffCallback());

    this.useNoCrossfadeAnimator = useNoCrossfadeAnimator;
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

  @Override
  public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
    super.onAttachedToRecyclerView(recyclerView);
    if (useNoCrossfadeAnimator && recyclerView.getItemAnimator() != null && recyclerView.getItemAnimator().getClass() == DefaultItemAnimator.class) {
      recyclerView.setItemAnimator(new NoCrossfadeChangeDefaultAnimator());
    }
  }

  public <T extends MappingModel<T>> void registerFactory(Class<T> clazz, Factory<T> factory) {
    int type = typeCount++;
    factories.put(type, factory);
    itemTypes.put(clazz, type);
  }

  public <T extends MappingModel<T>> void registerFactory(@NonNull Class<T> clazz, @NonNull Function<View, MappingViewHolder<T>> creator, @LayoutRes int layout) {
    registerFactory(clazz, new LayoutFactory<>(creator, layout));
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
  public void onBindViewHolder(@NonNull MappingViewHolder<?> holder, int position, @NonNull List<Object> payloads) {
    holder.setPayload(payloads);
    onBindViewHolder(holder, position);
  }

  @Override
  public void onBindViewHolder(@NonNull MappingViewHolder holder, int position) {
    //noinspection unchecked
    holder.bind(getItem(position));
  }

  public <T> int indexOfFirst(@NonNull Class<T> clazz, @NonNull Function1<T, Boolean> predicate) {
    return CollectionsKt.indexOfFirst(getCurrentList(), m -> {
      //noinspection unchecked
      return clazz.isAssignableFrom(m.getClass()) && predicate.invoke((T) m);
    });
  }

  public @NonNull Optional<MappingModel<?>> getModel(int index) {
    List<MappingModel<?>> currentList = getCurrentList();
    if (index >= 0 && index < currentList.size()) {
      return Optional.ofNullable(currentList.get(index));
    }
    return Optional.empty();
  }
}
