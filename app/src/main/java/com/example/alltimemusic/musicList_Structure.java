package com.example.alltimemusic;

import java.io.Serializable;
import java.util.regex.Pattern;

public class musicList_Structure implements Serializable {
    public String songTitle;
    public String songPath;
    public String artistName;
    public long albumId; // Store Album ID for high-performance Glide loading
    public boolean isFavourite = false;
    public boolean showAlphabetHeader = false; // Cache header visibility
    public String alphabetHeader = "";        // Cache the letter
    private String cachedCleanArtist = null;   // Cache for scrolling performance

    public void setCachedCleanArtist(String cachedCleanArtist) {
        this.cachedCleanArtist = cachedCleanArtist;
    }

    public musicList_Structure(String songTitle, String songPath, String artistName, long albumId) {
        this.songTitle = songTitle;
        this.songPath = songPath;
        this.artistName = artistName;
        this.albumId = albumId;
    }

    public String getCleanArtist() {
        if (cachedCleanArtist != null) return cachedCleanArtist;
        String artist = artistName;
        if (artist == null || artist.equalsIgnoreCase("<unknown>") || artist.equalsIgnoreCase("unknown") || artist.trim().isEmpty()) {
            String[] delims = {" _ ", " | ", " — ", " - ", " : ", " ~ "};
            for (String d : delims) {
                if (songTitle.contains(d)) {
                    String[] parts = songTitle.split(Pattern.quote(d));
                    if (parts.length >= 2) {
                        artist = parts[parts.length - 1].trim();
                        break;
                    }
                }
            }
        }

        String cleaned = internalClean(artist);
        cachedCleanArtist = cleaned.isEmpty() || cleaned.equalsIgnoreCase("<unknown>") || cleaned.equalsIgnoreCase("unknown") 
                ? "Unknown Artist Name" : cleaned;

        return cachedCleanArtist;
    }

    private String internalClean(String input) {
        if (input == null) return "";
        return input.replaceAll("(?i)\\[.*?\\]", "")
                .replaceAll("(?i)\\(.*?\\)", "")
                .replaceAll("(?i)official audio|official video|full video|full audio|lyrical|audio|video|hd|4k|lyric video", "")
                .replaceAll("(?i)\\.mp3|\\.m4a|\\.wav|\\.flac", "")
                .replaceAll("(?i)\\d{4}", "") // removes years
                .replaceAll("(?i)\\.com|\\.to|\\.org|\\.net|\\.info|\\.me|\\.biz|\\.io", "")
                .replaceAll("(?i)PagalWorld.*?|PaglaSongs|PagalNew|KoshalWorld", "")
                .replaceAll("^[-_ ]+|[-_ ]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
