package com.example.alltimemusic;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EditorLyricsAdapter extends RecyclerView.Adapter<EditorLyricsAdapter.ViewHolder> {

    public interface OnEditorLyricActionListener {
        void onLyricClick(int position, LyricLine line);
        void onDeleteLine(int position);
        void onAddLineAfter(int position);
        void onAddMusicNoteAfter(int position);
        void onEditLine(int position);
    }

    private List<LyricLine> lyrics = new ArrayList<>();
    private int activeIndex = -1;
    private OnEditorLyricActionListener listener;
    private long lastClickTime = 0;
    private static final long DOUBLE_CLICK_TIME_DELTA = 300; // milliseconds

    public void setLyrics(List<LyricLine> lyrics) {
        this.lyrics = lyrics;
        notifyDataSetChanged();
    }

    public void setListener(OnEditorLyricActionListener listener) {
        this.listener = listener;
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
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_editor_lyric_line, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LyricLine line = lyrics.get(position);
        holder.textView.setText(line.getText());

        // Display Timestamp with MS precision (Suggestion #5)
        long time = line.getTimeMs();
        long min = (time / 1000) / 60;
        long sec = (time / 1000) % 60;
        long ms = (time % 1000) / 10; // Two digit ms display
        
        if (time > 0) {
            holder.timestampView.setText(String.format(Locale.US, "%02d:%02d.%02d", min, sec, ms));
            holder.timestampView.setAlpha(1.0f);
        } else {
            holder.timestampView.setText("--:--.--");
            holder.timestampView.setAlpha(0.3f);
        }

        // Highlight active line
        if (position == activeIndex) {
            holder.textView.setTextColor(android.graphics.Color.WHITE);
            holder.textView.setAlpha(1.0f);
        } else {
            holder.textView.setTextColor(android.graphics.Color.parseColor("#80FFFFFF"));
            holder.textView.setAlpha(0.7f);
        }

        holder.itemView.setOnClickListener(v -> {
            long clickTime = System.currentTimeMillis();
            if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                // Double Click: Edit Line
                if (listener != null) listener.onEditLine(position);
            } else {
                // Single Click: Select/Seek
                if (listener != null) listener.onLyricClick(position, line);
            }
            lastClickTime = clickTime;
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onDeleteLine(position);
            return true;
        });

        holder.addNoteBtn.setOnClickListener(v -> {
            if (listener != null) listener.onAddMusicNoteAfter(position);
        });

        holder.addLineBtn.setOnClickListener(v -> {
            if (listener != null) listener.onAddLineAfter(position);
        });
    }

    @Override
    public int getItemCount() {
        return lyrics.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView, timestampView;
        ImageView addNoteBtn, addLineBtn;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.editor_lyric_text);
            timestampView = itemView.findViewById(R.id.editor_lyric_timestamp);
            addNoteBtn = itemView.findViewById(R.id.editor_lyric_add_note);
            addLineBtn = itemView.findViewById(R.id.editor_lyric_add_line);
        }
    }
}
