package org.thoughtcrime.securesms.components.webrtc;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;

import java.util.List;

final class AudioOutputAdapter extends RecyclerView.Adapter<AudioOutputAdapter.ViewHolder> {

  private final OnAudioOutputChangedListener onAudioOutputChangedListener;
  private final List<WebRtcAudioOutput> audioOutputs;

  private WebRtcAudioOutput selected;

  AudioOutputAdapter(@NonNull OnAudioOutputChangedListener onAudioOutputChangedListener,
                     @NonNull List<WebRtcAudioOutput> audioOutputs) {
    this.audioOutputs                 = audioOutputs;
    this.onAudioOutputChangedListener = onAudioOutputChangedListener;
  }

  public void setSelectedOutput(@NonNull WebRtcAudioOutput selected) {
    this.selected = selected;

    notifyDataSetChanged();
  }

  @Override
  public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.audio_output_adapter_radio_item, parent, false);

    return new ViewHolder(view, this::handlePositionSelected);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    holder.bind(audioOutputs.get(position), selected);
  }

  @Override
  public int getItemCount() {
    return audioOutputs.size();
  }

  private void handlePositionSelected(int position) {
    WebRtcAudioOutput mode = audioOutputs.get(position);

    if (mode != selected) {
      setSelectedOutput(mode);
      onAudioOutputChangedListener.audioOutputChanged(selected);
    }
  }

  static class ViewHolder extends RecyclerView.ViewHolder implements CompoundButton.OnCheckedChangeListener {

    private final RadioButton       radioButton;
    private final Consumer<Integer> onPressed;


    public ViewHolder(@NonNull View itemView, @NonNull Consumer<Integer> onPressed) {
      super(itemView);

      this.radioButton = itemView.findViewById(R.id.radio);
      this.onPressed   = onPressed;
    }

    @CallSuper
    void bind(@NonNull WebRtcAudioOutput audioOutput, @Nullable WebRtcAudioOutput selected) {
      radioButton.setText(audioOutput.getLabelRes());
      radioButton.setCompoundDrawablesRelativeWithIntrinsicBounds(audioOutput.getIconRes(), 0, 0, 0);
      radioButton.setOnCheckedChangeListener(null);
      radioButton.setChecked(audioOutput == selected);
      radioButton.setOnCheckedChangeListener(this);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
      int adapterPosition = getAdapterPosition();
      if (adapterPosition != RecyclerView.NO_POSITION) {
        onPressed.accept(adapterPosition);
      }
    }
  }
}
