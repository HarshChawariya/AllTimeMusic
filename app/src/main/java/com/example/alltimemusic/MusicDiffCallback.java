package com.example.alltimemusic;

import androidx.recyclerview.widget.DiffUtil;
import java.util.List;

public class MusicDiffCallback extends DiffUtil.Callback {

    private final List<musicList_Structure> oldList;
    private final List<musicList_Structure> newList;

    public MusicDiffCallback(List<musicList_Structure> oldList, List<musicList_Structure> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList != null ? oldList.size() : 0;
    }

    @Override
    public int getNewListSize() {
        return newList != null ? newList.size() : 0;
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        return oldList.get(oldItemPosition).songPath.equals(newList.get(newItemPosition).songPath);
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        musicList_Structure oldSong = oldList.get(oldItemPosition);
        musicList_Structure newSong = newList.get(newItemPosition);
        return oldSong.songTitle.equals(newSong.songTitle) &&
               oldSong.isFavourite == newSong.isFavourite;
    }
}
