package org.thoughtcrime.securesms.components.webrtc;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;

import java.util.List;

final class AudioOutputAdapter extends RecyclerView.Adapter<AudioOutputAdapter.AudioOutputViewHolder> {

  private final Consumer<WebRtcAudioOutput> consumer;
  private final List<WebRtcAudioOutput>     audioOutputs;

  AudioOutputAdapter(@NonNull Consumer<WebRtcAudioOutput> consumer, @NonNull List<WebRtcAudioOutput> audioOutputs) {
    this.audioOutputs = audioOutputs;
    this.consumer     = consumer;
  }

  @Override
  public @NonNull AudioOutputViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new AudioOutputViewHolder((TextView) LayoutInflater.from(parent.getContext()).inflate(R.layout.audio_output_adapter_item, parent, false), consumer);
  }

  @Override
  public void onBindViewHolder(@NonNull AudioOutputViewHolder holder, int position) {
    WebRtcAudioOutput audioOutput = audioOutputs.get(position);
    holder.view.setText(audioOutput.getLabelRes());
    holder.view.setCompoundDrawablesRelativeWithIntrinsicBounds(audioOutput.getIconRes(), 0, 0, 0);
  }

  @Override
  public int getItemCount() {
    return audioOutputs.size();
  }

  final static class AudioOutputViewHolder extends RecyclerView.ViewHolder {

    private final TextView view;

    AudioOutputViewHolder(@NonNull TextView itemView, @NonNull Consumer<WebRtcAudioOutput> consumer) {
      super(itemView);

      view = itemView;

      itemView.setOnClickListener(v -> {
        if (getAdapterPosition() != RecyclerView.NO_POSITION) {
          consumer.accept(WebRtcAudioOutput.values()[getAdapterPosition()]);
        }
      });
    }
  }

}
