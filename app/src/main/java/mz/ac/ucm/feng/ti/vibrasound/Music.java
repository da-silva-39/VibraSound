package mz.ac.ucm.feng.ti.vibrasound;

import java.io.Serializable;

/**
 * Modelo que representa uma musica do dispositivo.
 * Guarda apenas informacao leve (nunca bitmaps), por isso pode ser
 * enviada entre Activities/Servico atraves de um Serializable.
 */
public class Music implements Serializable {

    private long id;          // id do audio no MediaStore
    private long albumId;     // id do album (para carregar a capa)
    private String title;     // titulo
    private String artist;    // artista
    private String album;     // album
    private long duration;    // duracao em milissegundos
    private String uri;       // content:// do ficheiro de audio

    public Music() {
    }

    public Music(long id, long albumId, String title, String artist, String album,
                 long duration, String uri) {
        this.id = id;
        this.albumId = albumId;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.uri = uri;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getAlbumId() {
        return albumId;
    }

    public void setAlbumId(long albumId) {
        this.albumId = albumId;
    }

    public String getTitle() {
        return title != null ? title : "";
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist != null ? artist : "";
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album != null ? album : "";
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    /**
     * Devolve o artista para mostrar no ecra, tratando os casos em que o
     * MediaStore devolve null, vazio ou a constante "unknown".
     */
    public String getDisplayArtist() {
        if (artist == null || artist.trim().isEmpty()
                || artist.equalsIgnoreCase("<unknown>")
                || artist.equalsIgnoreCase("unknown")) {
            return "";
        }
        return artist.trim();
    }

    /** Album para mostrar, tratando valores desconhecidos. */
    public String getDisplayAlbum() {
        if (album == null || album.trim().isEmpty()
                || album.equalsIgnoreCase("<unknown>")
                || album.equalsIgnoreCase("unknown")) {
            return "";
        }
        return album.trim();
    }

    /** Titulo para mostrar, com recurso a um nome generico quando falta. */
    public String getDisplayTitle() {
        if (title == null || title.trim().isEmpty()) {
            return "Sem titulo";
        }
        return title.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Music music = (Music) o;
        return id == music.id;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }
}
