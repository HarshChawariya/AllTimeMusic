package com.example.alltimemusic;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.imageview.ShapeableImageView;

import android.graphics.drawable.BitmapDrawable;
import java.util.ArrayList;

@UnstableApi
public class musicList_Recycler_Adapter extends RecyclerView.Adapter<musicList_Recycler_Adapter.ViewHolder> {
    private int lastPosition = -1;
    private int selectedPosition = -1;
    private final Context context;
    private final ArrayList<musicList_Structure> musicList;
    public static musicList_Structure currentItem;
    public static ArrayList<musicList_Structure> fullMusicList;
    public static int currentPosition;

    public musicList_Recycler_Adapter(Context context, ArrayList<musicList_Structure> musicList) {
        this.context = context;
        this.musicList = musicList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.music_list_model, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, @SuppressLint("RecyclerView") int position) {
        musicList_Structure item = musicList.get(position);
        holder.songTitles.setText(item.songTitle);
        holder.artistNames.setText(item.getCleanArtist());

        // Use Glide for efficient metadata image loading in list
        updateListProfileImage(holder.profileImg, item);

        // Favorite Indicator (Visual Only)
        if (item.isFavourite) {
            holder.favHeart.setImageResource(R.drawable.fill_heart);
            holder.favHeart.setColorFilter(Color.RED);
        } else {
            holder.favHeart.setImageResource(R.drawable.boder_of_heart);
            holder.favHeart.setColorFilter(Color.parseColor("#C1BDBD"));
        }

        setAnimation(holder.itemView, position);

        // Selection Highlight
        boolean isCurrentPlaying = false;
        if (currentItem != null && item.songPath.equals(currentItem.songPath)) {
            isCurrentPlaying = true;
            selectedPosition = position; // Update internal selection for this list context
        }

        if (isCurrentPlaying) {
            holder.songTitles.setTextColor(Color.RED);
            holder.artistNames.setTextColor(Color.RED);
        } else {
            holder.songTitles.setTextColor(Color.WHITE);
            holder.artistNames.setTextColor(Color.parseColor("#C1BDBD"));
        }

        holder.itemView.setOnClickListener(v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return;

            int previous = selectedPosition;
            selectedPosition = currentPos;
            notifyItemChanged(previous);
            notifyItemChanged(selectedPosition);

            currentItem = musicList.get(currentPos);
            currentPosition = currentPos;
            fullMusicList = musicList; // Sync the global list with the current context

            if (context instanceof MainActivity) {
                ((MainActivity) context).openPlayerLayout();
            } else if (context instanceof LikedSongsActivity) {
                MainActivity.isReturningFromLiked = true; // Set flag to open player in MainActivity
                ((LikedSongsActivity) context).openPlayerLayout();
            }
        });

        // Alphabet Header Logic
        if (context instanceof LikedSongsActivity) {
            holder.alphabet1.setVisibility(View.GONE);
        } else if (position == 0) {
            holder.alphabet1.setVisibility(View.GONE);
        } else if (item.songTitle != null && !item.songTitle.isEmpty()) {
            String firstLetter = String.valueOf(item.songTitle.charAt(0)).toUpperCase();
            String prevTitle = musicList.get(position - 1).songTitle;
            if (prevTitle != null && !prevTitle.isEmpty()) {
                String prevFirstLetter = String.valueOf(prevTitle.charAt(0)).toUpperCase();
                if (!firstLetter.equalsIgnoreCase(prevFirstLetter)) {
                    holder.alphabet1.setVisibility(View.VISIBLE);
                    holder.alphabet1.setText(firstLetter);
                } else {
                    holder.alphabet1.setVisibility(View.GONE);
                }
            } else {
                holder.alphabet1.setVisibility(View.VISIBLE);
                holder.alphabet1.setText(firstLetter);
            }
        } else {
            holder.alphabet1.setVisibility(View.GONE);
        }

        holder.recyclerThreeDot.setOnClickListener(view -> {
            CustomPopupMenu popupMenu = new CustomPopupMenu(context, view);
            popupMenu.setItemTextColor(MainActivity.lastDynamicColor);
            popupMenu.addMenuItem("Play Next");
            
            // Check if song is already favorite to show correct menu option
            String favoriteOption = item.isFavourite ? "Removed From Favourite" : "Add To Favourite";
            popupMenu.addMenuItem(favoriteOption);

            popupMenu.setOnItemClickListener(title -> {
                if (title.equals("Play Next")) {
                    int currentIdx = holder.getBindingAdapterPosition();
                    if (currentIdx == RecyclerView.NO_POSITION) return;
                    
                    musicList_Structure selectedSong = musicList.get(currentIdx);
                    
                    MainActivity activity = (context instanceof MainActivity) ? (MainActivity) context : null;
                    if (activity != null && activity.musicViewModel != null) {
                        activity.musicViewModel.addToPlayNext(selectedSong);
                    } else {
                        MusicService.arrPlayNext.add(selectedSong);
                        Toast.makeText(context, "Added to Play Next (Legacy)", Toast.LENGTH_SHORT).show();
                    }
                } else if (title.equals("Add To Favourite") || title.equals("Removed From Favourite")) {
                    // Toggle favorite logic
                    FavoritesDatabase db = new FavoritesDatabase(context);
                    if (item.isFavourite) {
                        db.removeFavorite(item.songPath);
                        item.isFavourite = false;
                        Toast.makeText(context, "Removed From Favourite", Toast.LENGTH_SHORT).show();
                    } else {
                        db.addFavorite(item);
                        item.isFavourite = true;
                        Toast.makeText(context, "Added To Favourite", Toast.LENGTH_SHORT).show();
                    }
                    
                    // Update visual heart indicator
                    notifyItemChanged(holder.getBindingAdapterPosition());
                }
            });
            popupMenu.show(view);
        });
    }


    private void setAnimation(View viewToAnimate, int position) {
        if (position > lastPosition) {
            Animation slideIn = AnimationUtils.loadAnimation(context, android.R.anim.slide_in_left);
            viewToAnimate.startAnimation(slideIn);
            lastPosition = position;
        }
    }

    public void updateSelection(int ignored) {
        int previous = selectedPosition;
        selectedPosition = -1;
        
        // Find the new index of currentItem in the current list
        if (currentItem != null) {
            for (int i = 0; i < musicList.size(); i++) {
                if (musicList.get(i).songPath.equals(currentItem.songPath)) {
                    selectedPosition = i;
                    break;
                }
            }
        }
        
        // Safety: We only call notifyItemChanged if the positions are valid for the current list state.
        int count = getItemCount();
        if (previous != -1 && previous < count) {
            notifyItemChanged(previous);
        }
        if (selectedPosition != -1 && selectedPosition < count) {
            notifyItemChanged(selectedPosition);
        }
    }

    private void updateListProfileImage(ImageView imageView, musicList_Structure item) {
        if (imageView == null || item == null) return;

        Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
        Uri uri = ContentUris.withAppendedId(sArtworkUri, item.albumId);

        Glide.with(context)
                .load(uri)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .transform(new CenterCrop())
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        imageView.setImageDrawable(resource);
                        
                        // PIGGYBACK EXTRACTION: Pre-extract color while loading list to eliminate playback lag
                        if (resource instanceof BitmapDrawable) {
                            Bitmap bitmap = ((BitmapDrawable) resource).getBitmap();
                            if (bitmap != null && !MusicViewModel.colorCache.containsKey(item.songPath)) {
                                // Background thread extraction to avoid scroll stutter
                                new Thread(() -> {
                                    int extractedColor = MusicViewModel.extractThemeColor(bitmap);
                                    MusicViewModel.colorCache.put(item.songPath, extractedColor);
                                }).start();
                            }
                        }

                        ViewGroup.LayoutParams params = imageView.getLayoutParams();
                        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        imageView.setLayoutParams(params);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        imageView.setImageDrawable(placeholder);
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        imageView.setImageDrawable(errorDrawable);
                        setDefaultListProfileImage(imageView);
                    }
                });
    }

    private void setDefaultListProfileImage(ImageView imageView) {
        imageView.setImageResource(R.drawable.profile);

        // Set to 60dp for default image in list as requested to maintain original look
        int sizeInPx = (int) (60 * context.getResources().getDisplayMetrics().density);
        ViewGroup.LayoutParams params = imageView.getLayoutParams();
        params.width = sizeInPx;
        params.height = sizeInPx;
        imageView.setLayoutParams(params);
    }

    @Override
    public int getItemCount() {
        return musicList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView songTitles, artistNames, alphabet1;
        ImageView favHeart, recyclerThreeDot;
        ShapeableImageView profileImg;
        public ViewHolder(View itemView) {
            super(itemView);
            songTitles = itemView.findViewById(R.id.songTitle);
            artistNames = itemView.findViewById(R.id.artistNameList);
            alphabet1 = itemView.findViewById(R.id.alphabet1);
            favHeart = itemView.findViewById(R.id.recycler_fav_heart);
            recyclerThreeDot = itemView.findViewById(R.id.recycler_three_dot);
            profileImg = itemView.findViewById(R.id.RL_profile_img);
        }
    }
}
