package mz.ac.ucm.feng.ti.vibrasound;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Acesso a base de dados SQLite nativa do Android.
 * Guarda favoritos, listas de reproducao e historico de reproducao.
 *
 * Os favoritos, listas e historico guardam copias dos metadados das musicas
 * (titulo, artista, album, uri), para continuarem disponiveis mesmo que a
 * biblioteca do dispositivo mude.
 */
public class MusicDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "vibrasound.db";
    private static final int DB_VERSION = 1;

    // Tabela de favoritos
    private static final String T_FAVORITES = "favorites";
    // Tabela de listas
    private static final String T_PLAYLISTS = "playlists";
    // Tabela que liga listas a musicas
    private static final String T_PLAYLIST_MUSIC = "playlist_music";
    // Tabela de historico
    private static final String T_HISTORY = "history";

    // Colunas partilhadas pelas tabelas que guardam musicas
    private static final String C_MUSIC_ID = "music_id";
    private static final String C_TITLE = "title";
    private static final String C_ARTIST = "artist";
    private static final String C_ALBUM = "album";
    private static final String C_DURATION = "duration";
    private static final String C_URI = "uri";
    private static final String C_ALBUM_ID = "album_id";

    public MusicDatabaseHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_FAVORITES + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + C_MUSIC_ID + " INTEGER UNIQUE, "
                + C_TITLE + " TEXT, "
                + C_ARTIST + " TEXT, "
                + C_ALBUM + " TEXT, "
                + C_DURATION + " INTEGER, "
                + C_URI + " TEXT, "
                + C_ALBUM_ID + " INTEGER, "
                + "added_at INTEGER)");

        db.execSQL("CREATE TABLE " + T_PLAYLISTS + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, "
                + "created_at INTEGER)");

        db.execSQL("CREATE TABLE " + T_PLAYLIST_MUSIC + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "playlist_id INTEGER, "
                + C_MUSIC_ID + " INTEGER, "
                + C_TITLE + " TEXT, "
                + C_ARTIST + " TEXT, "
                + C_ALBUM + " TEXT, "
                + C_DURATION + " INTEGER, "
                + C_URI + " TEXT, "
                + C_ALBUM_ID + " INTEGER, "
                + "position INTEGER)");

        db.execSQL("CREATE TABLE " + T_HISTORY + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + C_MUSIC_ID + " INTEGER UNIQUE, "
                + C_TITLE + " TEXT, "
                + C_ARTIST + " TEXT, "
                + C_ALBUM + " TEXT, "
                + C_DURATION + " INTEGER, "
                + C_URI + " TEXT, "
                + C_ALBUM_ID + " INTEGER, "
                + "last_played INTEGER, "
                + "play_count INTEGER DEFAULT 0, "
                + "last_position INTEGER DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Nesta versao ainda nao existem migracoes: recria as tabelas.
        db.execSQL("DROP TABLE IF EXISTS " + T_FAVORITES);
        db.execSQL("DROP TABLE IF EXISTS " + T_PLAYLISTS);
        db.execSQL("DROP TABLE IF EXISTS " + T_PLAYLIST_MUSIC);
        db.execSQL("DROP TABLE IF EXISTS " + T_HISTORY);
        onCreate(db);
    }

    // ------------------------------------------------------------------
    // Favoritos
    // ------------------------------------------------------------------

    public void addFavorite(Music m) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = musicValues(m);
        v.put("added_at", System.currentTimeMillis());
        // REPLACE garante que a musica nao fica duplicada nos favoritos.
        db.insertWithOnConflict(T_FAVORITES, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void removeFavorite(long musicId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(T_FAVORITES, C_MUSIC_ID + "=?", new String[]{String.valueOf(musicId)});
    }

    public boolean isFavorite(long musicId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_FAVORITES, new String[]{"_id"},
                C_MUSIC_ID + "=?", new String[]{String.valueOf(musicId)},
                null, null, null);
        boolean fav = c.moveToFirst();
        c.close();
        return fav;
    }

    public List<Music> getFavorites() {
        List<Music> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_FAVORITES, null, null, null,
                null, null, "added_at DESC");
        while (c.moveToNext()) {
            list.add(readMusic(c));
        }
        c.close();
        return list;
    }

    // ------------------------------------------------------------------
    // Listas de reproducao
    // ------------------------------------------------------------------

    /** Cria uma lista e devolve o id. Devolve -1 se o nome for invalido. */
    public long createPlaylist(String name) {
        if (name == null || name.trim().isEmpty()) return -1;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("name", name.trim());
        v.put("created_at", System.currentTimeMillis());
        return db.insert(T_PLAYLISTS, null, v);
    }

    public boolean playlistNameExists(String name) {
        if (name == null) return false;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_PLAYLISTS, new String[]{"_id"},
                "name=?", new String[]{name.trim()}, null, null, null);
        boolean exists = c.moveToFirst();
        c.close();
        return exists;
    }

    /** Devolve todas as listas com a contagem de musicas. */
    public List<Playlist> getPlaylists() {
        List<Playlist> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT p._id, p.name, p.created_at, "
                + "(SELECT COUNT(*) FROM " + T_PLAYLIST_MUSIC
                + " pm WHERE pm.playlist_id = p._id) AS cnt "
                + "FROM " + T_PLAYLISTS + " p ORDER BY p.created_at DESC";
        Cursor c = db.rawQuery(sql, null);
        while (c.moveToNext()) {
            list.add(new Playlist(c.getLong(0), c.getString(1), c.getInt(3), c.getLong(2)));
        }
        c.close();
        return list;
    }

    public void deletePlaylist(long playlistId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(T_PLAYLISTS, "_id=?", new String[]{String.valueOf(playlistId)});
        db.delete(T_PLAYLIST_MUSIC, "playlist_id=?", new String[]{String.valueOf(playlistId)});
    }

    /** Adiciona uma musica a lista. Devolve false se ja existir. */
    public boolean addMusicToPlaylist(long playlistId, Music m) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.query(T_PLAYLIST_MUSIC, new String[]{"_id"},
                "playlist_id=? AND " + C_MUSIC_ID + "=?",
                new String[]{String.valueOf(playlistId), String.valueOf(m.getId())},
                null, null, null);
        boolean already = c.moveToFirst();
        c.close();
        if (already) return false;

        ContentValues v = musicValues(m);
        v.put("playlist_id", playlistId);
        v.put("position", nextPosition(db, playlistId));
        db.insert(T_PLAYLIST_MUSIC, null, v);
        return true;
    }

    private int nextPosition(SQLiteDatabase db, long playlistId) {
        Cursor c = db.rawQuery("SELECT MAX(position) FROM " + T_PLAYLIST_MUSIC
                + " WHERE playlist_id=?", new String[]{String.valueOf(playlistId)});
        int next = 0;
        if (c.moveToFirst() && !c.isNull(0)) {
            next = c.getInt(0) + 1;
        }
        c.close();
        return next;
    }

    public void removeMusicFromPlaylist(long playlistId, long musicId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(T_PLAYLIST_MUSIC, "playlist_id=? AND " + C_MUSIC_ID + "=?",
                new String[]{String.valueOf(playlistId), String.valueOf(musicId)});
    }

    public List<Music> getPlaylistMusics(long playlistId) {
        List<Music> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_PLAYLIST_MUSIC, null, "playlist_id=?",
                new String[]{String.valueOf(playlistId)}, null, null, "position ASC");
        while (c.moveToNext()) {
            list.add(readMusic(c));
        }
        c.close();
        return list;
    }

    // ------------------------------------------------------------------
    // Historico / reproducoes recentes
    // ------------------------------------------------------------------

    /**
     * Regista a reproducao de uma musica. Se ja existir, atualiza a data e
     * incrementa o contador em vez de criar uma linha duplicada.
     */
    public void addToHistory(Music m) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = musicValues(m);
        v.put("last_played", System.currentTimeMillis());
        int updated = db.update(T_HISTORY, v, C_MUSIC_ID + "=?",
                new String[]{String.valueOf(m.getId())});
        if (updated == 0) {
            v.put("play_count", 1);
        } else {
            db.execSQL("UPDATE " + T_HISTORY + " SET play_count = play_count + 1 WHERE "
                    + C_MUSIC_ID + "=?", new Object[]{m.getId()});
        }
        // insertWithOnConflict com IGNORE: se ja existir mantem os valores atualizados.
        db.insertWithOnConflict(T_HISTORY, null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public List<Music> getRecent(int limit) {
        List<Music> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_HISTORY, null, null, null,
                null, null, "last_played DESC", String.valueOf(limit));
        while (c.moveToNext()) {
            list.add(readMusic(c));
        }
        c.close();
        return list;
    }

    public Music getLastMusic() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_HISTORY, null, null, null,
                null, null, "last_played DESC", "1");
        Music m = c.moveToFirst() ? readMusic(c) : null;
        c.close();
        return m;
    }

    public void saveLastPosition(long musicId, int positionMs) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("last_position", positionMs);
        db.update(T_HISTORY, v, C_MUSIC_ID + "=?", new String[]{String.valueOf(musicId)});
    }

    public int getLastPosition(long musicId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_HISTORY, new String[]{"last_position"},
                C_MUSIC_ID + "=?", new String[]{String.valueOf(musicId)},
                null, null, null);
        int pos = 0;
        if (c.moveToFirst()) pos = c.getInt(0);
        c.close();
        return pos;
    }

    public int getPlayCount(long musicId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_HISTORY, new String[]{"play_count"},
                C_MUSIC_ID + "=?", new String[]{String.valueOf(musicId)},
                null, null, null);
        int count = 0;
        if (c.moveToFirst()) count = c.getInt(0);
        c.close();
        return count;
    }

    public void clearHistory() {
        getWritableDatabase().delete(T_HISTORY, null, null);
    }

    // ------------------------------------------------------------------
    // Utilitarios
    // ------------------------------------------------------------------

    private ContentValues musicValues(Music m) {
        ContentValues v = new ContentValues();
        v.put(C_MUSIC_ID, m.getId());
        v.put(C_TITLE, m.getDisplayTitle());
        v.put(C_ARTIST, m.getDisplayArtist());
        v.put(C_ALBUM, m.getDisplayAlbum());
        v.put(C_DURATION, m.getDuration());
        v.put(C_URI, m.getUri());
        v.put(C_ALBUM_ID, m.getAlbumId());
        return v;
    }

    /** Le uma musica de um cursor que contenha as colunas de musica. */
    private Music readMusic(Cursor c) {
        Music m = new Music();
        m.setId(c.getLong(c.getColumnIndexOrThrow(C_MUSIC_ID)));
        m.setTitle(c.getString(c.getColumnIndexOrThrow(C_TITLE)));
        String artist = c.getString(c.getColumnIndexOrThrow(C_ARTIST));
        m.setArtist(TextUtils.isEmpty(artist) ? "" : artist);
        String album = c.getString(c.getColumnIndexOrThrow(C_ALBUM));
        m.setAlbum(TextUtils.isEmpty(album) ? "" : album);
        m.setDuration(c.getLong(c.getColumnIndexOrThrow(C_DURATION)));
        m.setUri(c.getString(c.getColumnIndexOrThrow(C_URI)));
        m.setAlbumId(c.getLong(c.getColumnIndexOrThrow(C_ALBUM_ID)));
        return m;
    }
}
