package mz.ac.ucm.feng.ti.vibrasound;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Ponto unico de acesso aos dados:
 *  - le a biblioteca de audio do dispositivo atraves do MediaStore;
 *  - guarda/le favoritos, listas e historico na base de dados SQLite;
 *  - guarda preferencias simples em SharedPreferences.
 */
public class MusicRepository {

    public static final String PREFS = "vibrasound_prefs";
    public static final String KEY_DARK_THEME = "dark_theme";
    public static final String KEY_AUTOPLAY = "autoplay";
    public static final String KEY_LAST_MUSIC_ID = "last_music_id";
    public static final String KEY_SHUFFLE = "shuffle";
    public static final String KEY_REPEAT = "repeat_mode";

    private final Context context;
    private final MusicDatabaseHelper db;
    private final SharedPreferences prefs;

    public MusicRepository(Context context) {
        this.context = context.getApplicationContext();
        this.db = new MusicDatabaseHelper(this.context);
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------
    // Biblioteca do dispositivo (MediaStore)
    // ------------------------------------------------------------------

    /**
     * Le todas as musicas do dispositivo.
     * Devolve uma lista vazia se nao houver permissao ou se nao existirem
     * ficheiros de audio. A chamada deve ser feita fora da thread principal.
     */
    public List<Music> queryDeviceMusic() {
        List<Music> result = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID
        };
        // Apenas ficheiros marcados como musica.
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";

        Cursor cursor = null;
        try {
            cursor = resolver.query(collection, projection, selection, null, null);
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    Uri contentUri = ContentUris.withAppendedId(collection, id);
                    Music music = new Music(
                            id,
                            cursor.getLong(albumIdCol),
                            cursor.getString(titleCol),
                            cursor.getString(artistCol),
                            cursor.getString(albumCol),
                            cursor.getLong(durationCol),
                            contentUri.toString());
                    result.add(music);
                }
            }
        } catch (Exception e) {
            // Qualquer falha de leitura devolve lista vazia em vez de crashar.
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
        }
        sortByName(result);
        return result;
    }

    // ------------------------------------------------------------------
    // Ordenacao e pesquisa
    // ------------------------------------------------------------------

    public static void sortByName(List<Music> list) {
        final Collator collator = Collator.getInstance(Locale.getDefault());
        collator.setStrength(Collator.PRIMARY);
        Collections.sort(list, new Comparator<Music>() {
            @Override
            public int compare(Music a, Music b) {
                return collator.compare(a.getDisplayTitle(), b.getDisplayTitle());
            }
        });
    }

    public static void sortByArtist(List<Music> list) {
        final Collator collator = Collator.getInstance(Locale.getDefault());
        collator.setStrength(Collator.PRIMARY);
        Collections.sort(list, new Comparator<Music>() {
            @Override
            public int compare(Music a, Music b) {
                int r = collator.compare(a.getDisplayArtist(), b.getDisplayArtist());
                if (r == 0) r = collator.compare(a.getDisplayTitle(), b.getDisplayTitle());
                return r;
            }
        });
    }

    public static void sortByDuration(List<Music> list) {
        Collections.sort(list, new Comparator<Music>() {
            @Override
            public int compare(Music a, Music b) {
                return Long.compare(a.getDuration(), b.getDuration());
            }
        });
    }

    /** Filtra por titulo, artista ou album (ignora maiusculas). */
    public static List<Music> filter(List<Music> source, String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(source);
        }
        String q = query.trim().toLowerCase(Locale.getDefault());
        List<Music> out = new ArrayList<>();
        for (Music m : source) {
            if (m.getDisplayTitle().toLowerCase(Locale.getDefault()).contains(q)
                    || m.getDisplayArtist().toLowerCase(Locale.getDefault()).contains(q)
                    || m.getDisplayAlbum().toLowerCase(Locale.getDefault()).contains(q)) {
                out.add(m);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Favoritos
    // ------------------------------------------------------------------

    public void addFavorite(Music m) {
        db.addFavorite(m);
    }

    public void removeFavorite(long musicId) {
        db.removeFavorite(musicId);
    }

    public boolean isFavorite(long musicId) {
        return db.isFavorite(musicId);
    }

    public List<Music> getFavorites() {
        return db.getFavorites();
    }

    // ------------------------------------------------------------------
    // Listas de reproducao
    // ------------------------------------------------------------------

    public long createPlaylist(String name) {
        return db.createPlaylist(name);
    }

    public boolean playlistNameExists(String name) {
        return db.playlistNameExists(name);
    }

    public List<Playlist> getPlaylists() {
        return db.getPlaylists();
    }

    public void deletePlaylist(long playlistId) {
        db.deletePlaylist(playlistId);
    }

    public boolean addMusicToPlaylist(long playlistId, Music m) {
        return db.addMusicToPlaylist(playlistId, m);
    }

    public void removeMusicFromPlaylist(long playlistId, long musicId) {
        db.removeMusicFromPlaylist(playlistId, musicId);
    }

    public List<Music> getPlaylistMusics(long playlistId) {
        return db.getPlaylistMusics(playlistId);
    }

    // ------------------------------------------------------------------
    // Historico
    // ------------------------------------------------------------------

    public void addToHistory(Music m) {
        db.addToHistory(m);
    }

    public List<Music> getRecent(int limit) {
        return db.getRecent(limit);
    }

    public Music getLastMusic() {
        return db.getLastMusic();
    }

    public void saveLastPosition(long musicId, int positionMs) {
        db.saveLastPosition(musicId, positionMs);
    }

    public int getLastPosition(long musicId) {
        return db.getLastPosition(musicId);
    }

    public int getPlayCount(long musicId) {
        return db.getPlayCount(musicId);
    }

    public void clearHistory() {
        db.clearHistory();
    }

    // ------------------------------------------------------------------
    // Preferencias
    // ------------------------------------------------------------------

    public boolean isDarkTheme() {
        return prefs.getBoolean(KEY_DARK_THEME, true);
    }

    public void setDarkTheme(boolean dark) {
        prefs.edit().putBoolean(KEY_DARK_THEME, dark).apply();
    }

    public boolean isAutoplayEnabled() {
        return prefs.getBoolean(KEY_AUTOPLAY, true);
    }

    public void setAutoplayEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTOPLAY, enabled).apply();
    }

    public boolean isShuffleEnabled() {
        return prefs.getBoolean(KEY_SHUFFLE, false);
    }

    public void setShuffleEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHUFFLE, enabled).apply();
    }

    public int getRepeatMode() {
        return prefs.getInt(KEY_REPEAT, 0);
    }

    public void setRepeatMode(int mode) {
        prefs.edit().putInt(KEY_REPEAT, mode).apply();
    }

    public long getLastMusicId() {
        return prefs.getLong(KEY_LAST_MUSIC_ID, -1);
    }

    public void setLastMusicId(long id) {
        prefs.edit().putLong(KEY_LAST_MUSIC_ID, id).apply();
    }
}
