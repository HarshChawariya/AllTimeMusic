package com.example.alltimemusic;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class LyricsAdapter extends RecyclerView.Adapter<LyricsAdapter.ViewHolder> {

    private List<LyricLine> lyrics = new ArrayList<>();
    private int activeIndex = -1;

    public void setLyrics(List<LyricLine> lyrics) {
        this.lyrics = lyrics;
        this.activeIndex = -1;
        notifyDataSetChanged();
    }

    public void setActiveIndex(int index) {
        if (this.activeIndex != index) {
            int oldIndex = this.activeIndex;
            this.activeIndex = index;
            if (oldIndex != -1) notifyItemChanged(oldIndex);
            if (activeIndex != -1) notifyItemChanged(activeIndex);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lyric_line, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LyricLine line = lyrics.get(position);
        holder.textView.setText(line.getText());

        if (position == activeIndex) {
            holder.textView.setAlpha(1.0f);
            holder.textView.setScaleX(1.12f);
            holder.textView.setScaleY(1.12f);
        } else {
            holder.textView.setAlpha(0.4f);
            holder.textView.setScaleX(0.8f);
            holder.textView.setScaleY(0.8f);
        }
    }

    @Override
    public int getItemCount() {
        return lyrics.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.lyric_line_text);
        }
    }
}
