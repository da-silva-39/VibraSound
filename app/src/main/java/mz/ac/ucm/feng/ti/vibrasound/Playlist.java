package mz.ac.ucm.feng.ti.vibrasound;

import java.io.Serializable;

/** Modelo simples de uma lista de reproducao. */
public class Playlist implements Serializable {

    private long id;
    private String name;
    private int songCount;
    private long createdAt;

    public Playlist() {
    }

    public Playlist(long id, String name, int songCount, long createdAt) {
        this.id = id;
        this.name = name;
        this.songCount = songCount;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name != null ? name : "";
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSongCount() {
        return songCount;
    }

    public void setSongCount(int songCount) {
        this.songCount = songCount;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
