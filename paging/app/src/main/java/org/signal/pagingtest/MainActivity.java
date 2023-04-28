package org.signal.pagingtest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.signal.paging.PagingController;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MainActivity extends AppCompatActivity implements EventListener {

  private MainViewModel viewModel;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    MyAdapter           adapter       = new MyAdapter(this);
    RecyclerView        list          = findViewById(R.id.list);
    LinearLayoutManager layoutManager = new LinearLayoutManager(this);

    list.setAdapter(adapter);
    list.setLayoutManager(layoutManager);

    viewModel = new ViewModelProvider(this, new ViewModelProvider.NewInstanceFactory()).get(MainViewModel.class);
    adapter.setPagingController(viewModel.getPagingController());
    viewModel.getList().observe(this, newList -> {
      adapter.submitList(newList);
    });

    findViewById(R.id.invalidate_btn).setOnClickListener(v -> {
      viewModel.getPagingController().onDataInvalidated();
    });

    findViewById(R.id.down250_btn).setOnClickListener(v -> {
      int target = Math.min(adapter.getItemCount() - 1, layoutManager.findFirstVisibleItemPosition() + 250);
      layoutManager.scrollToPosition(target);
    });

    findViewById(R.id.up250_btn).setOnClickListener(v -> {
      int target = Math.max(0, layoutManager.findFirstVisibleItemPosition() - 250);
      layoutManager.scrollToPosition(target);
    });

    findViewById(R.id.prepend_btn).setOnClickListener(v -> {
      viewModel.prependItems();
    });
  }

  @Override
  public void onItemClicked(String key) {
    viewModel.onItemClicked(key);
  }

  static class MyAdapter extends RecyclerView.Adapter<MyViewHolder> {

    private final static int TYPE_NORMAL      = 1;
    private final static int TYPE_PLACEHOLDER = -1;

    private final EventListener listener;
    private final List<Item>    data;

    private PagingController<String> controller;

    public MyAdapter(@NonNull EventListener listener) {
      this.listener = listener;
      this.data     = new ArrayList<>();

      setHasStableIds(true);
    }

    @Override
    public int getItemViewType(int position) {
      return getItem(position) == null ? TYPE_PLACEHOLDER : TYPE_NORMAL;
    }

    @Override
    public int getItemCount() {
      return data.size();
    }

    @Override
    public long getItemId(int position) {
      Item item = getItem(position);
      if (item != null) {
        return item.key.hashCode();
      } else {
        return 0;
      }
    }

    @Override
    public @NonNull MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      switch (viewType) {
        case TYPE_NORMAL:
        case TYPE_PLACEHOLDER:
          return new MyViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item, parent, false));
        default:
          throw new AssertionError();
      }
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
      holder.bind(getItem(position), position, listener);
    }

    private Item getItem(int index) {
      if (controller != null) {
        controller.onDataNeededAroundIndex(index);
      }
      return data.get(index);
    }

    void setPagingController(PagingController<String> pagingController) {
      this.controller = pagingController;
    }

    void submitList(List<Item> list) {
      DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
        @Override
        public int getOldListSize() {
          return data.size();
        }

        @Override
        public int getNewListSize() {
          return list.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
          String oldKey = Optional.ofNullable(data.get(oldItemPosition)).map(item -> item.key).orElse(null);
          String newKey = Optional.ofNullable(list.get(newItemPosition)).map(item -> item.key).orElse(null);

          return Objects.equals(oldKey, newKey);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
          Long oldKey = Optional.ofNullable(data.get(oldItemPosition)).map(item -> item.timestamp).orElse(null);
          Long newKey = Optional.ofNullable(list.get(newItemPosition)).map(item -> item.timestamp).orElse(null);

          return Objects.equals(oldKey, newKey);
        }
      }, false);

      result.dispatchUpdatesTo(this);

      data.clear();
      data.addAll(list);
    }
  }

  static class MyViewHolder extends RecyclerView.ViewHolder {

    TextView textView;

    public MyViewHolder(@NonNull View itemView) {
      super(itemView);
      textView = itemView.findViewById(R.id.text);
    }

    void bind(@Nullable Item item, int position, @NonNull EventListener listener) {
      if (item != null) {
        textView.setText(position + " | " + item.key.substring(0, 13) + " | " + System.currentTimeMillis());
        textView.setOnClickListener(v -> {
          listener.onItemClicked(item.key);
        });
      } else {
        textView.setText(position + " | PLACEHOLDER");
        textView.setOnClickListener(null);
      }
    }
  }
}
