package org.signal.pagingtest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
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

public class MainActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    MyAdapter           adapter       = new MyAdapter();
    RecyclerView        list          = findViewById(R.id.list);
    LinearLayoutManager layoutManager = new LinearLayoutManager(this);

    list.setAdapter(adapter);
    list.setLayoutManager(layoutManager);

    MainViewModel viewModel = new ViewModelProvider(this, new ViewModelProvider.NewInstanceFactory()).get(MainViewModel.class);
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

    findViewById(R.id.append_btn).setOnClickListener(v -> {
      viewModel.appendItems();
    });
  }

  static class MyAdapter extends RecyclerView.Adapter<MyViewHolder> {

    private final static int TYPE_NORMAL      = 1;
    private final static int TYPE_PLACEHOLDER = -1;

    private PagingController controller;

    private final List<String> data = new ArrayList<>();

    public MyAdapter() {
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
      return position;
    }

    @Override
    public @NonNull MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      switch (viewType) {
        case TYPE_NORMAL:
          return new MyViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item, parent, false));
        case TYPE_PLACEHOLDER:
          return new MyViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item, parent, false));
        default:
          throw new AssertionError();
      }
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
      holder.bind(getItem(position));
    }

    private String getItem(int index) {
      if (controller != null) {
        controller.onDataNeededAroundIndex(index);
      }
      return data.get(index);
    }

    void setPagingController(PagingController pagingController) {
      this.controller = pagingController;
    }

    void submitList(List<String> list) {
      data.clear();
      data.addAll(list);
      notifyDataSetChanged();
    }
  }

  static class MyViewHolder extends RecyclerView.ViewHolder {

    TextView textView;

    public MyViewHolder(@NonNull View itemView) {
      super(itemView);
      textView = itemView.findViewById(R.id.text);
    }

    void bind(@NonNull String s) {
      textView.setText(s == null ? "PLACEHOLDER" : s);
    }
  }
}