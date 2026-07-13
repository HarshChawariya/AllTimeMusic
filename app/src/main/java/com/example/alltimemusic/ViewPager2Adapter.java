package com.example.alltimemusic;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ViewPager2Adapter extends FragmentStateAdapter {

    public ViewPager2Adapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        musicList_Structure item = musicList_Recycler_Adapter.currentItem;
        
        String title;
        if (item != null) {
            title = item.songTitle;
        } else {
            title = "Unknown Title";
        }
        
        String artist;
        if (item != null) {
            artist = item.getCleanArtist();
        } else {
            artist = "Unknown Artist Name";
        }

        if (position == 0) {
            return PlayList_Fragment.newInstance(title, artist);
        } else {
            return Lyrics_Fragment.newInstance(title, artist, "Lyrics will be appearing....");
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}

