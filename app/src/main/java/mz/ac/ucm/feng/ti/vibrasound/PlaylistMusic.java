package mz.ac.ucm.feng.ti.vibrasound;

/**
 * Representa a ligacao entre uma lista de reproducao e uma musica.
 * Usado pela base de dados para a tabela playlist_music.
 */
public class PlaylistMusic {

    private long playlistId;
    private long musicId;
    private int position;

    public PlaylistMusic(long playlistId, long musicId, int position) {
        this.playlistId = playlistId;
        this.musicId = musicId;
        this.position = position;
    }

    public long getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(long playlistId) {
        this.playlistId = playlistId;
    }

    public long getMusicId() {
        return musicId;
    }

    public void setMusicId(long musicId) {
        this.musicId = musicId;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
