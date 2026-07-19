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
            // Main lyric: Full White
            holder.textView.setTextColor(android.graphics.Color.WHITE);
            holder.textView.setAlpha(1.0f);

            // Animation (Ensure it scales if needed)
            android.view.animation.Animation slideUp = android.view.animation.AnimationUtils.loadAnimation(holder.itemView.getContext(), R.anim.slide_up_scale);
            holder.textView.startAnimation(slideUp);
        } else {
            // Other lyrics: Using a specific low-opacity white hex (#33FFFFFF = 20% Alpha)
            // This ensures they look dimmed even if the background is dark
            holder.textView.setTextColor(android.graphics.Color.parseColor("#80FFFFFF"));
            holder.textView.setAlpha(1.0f);
            holder.textView.clearAnimation();
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
