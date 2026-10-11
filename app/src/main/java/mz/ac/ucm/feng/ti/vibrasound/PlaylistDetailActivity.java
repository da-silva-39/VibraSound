package mz.ac.ucm.feng.ti.vibrasound;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.List;

/**
 * Detalhe de uma lista de reproducao: mostra as musicas, permite reproduzir
 * a lista completa, remover musicas e eliminar a lista.
 */
public class PlaylistDetailActivity extends BaseActivity implements MusicAdapter.Listener {

    public static final String EXTRA_PLAYLIST_ID = "extra_playlist_id";
    public static final String EXTRA_PLAYLIST_NAME = "extra_playlist_name";

    private long playlistId;
    private String playlistName;

    private ListView listView;
    private View stateView;
    private TextView title;
    private TextView count;
    private MusicAdapter adapter;
    private List<Music> musics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_detail);

        playlistId = getIntent().getLongExtra(EXTRA_PLAYLIST_ID, -1);
        playlistName = getIntent().getStringExtra(EXTRA_PLAYLIST_NAME);

        title = findViewById(R.id.detail_title);
        count = findViewById(R.id.detail_count);
        listView = findViewById(R.id.detail_list);
        stateView = findViewById(R.id.detail_state);

        adapter = new MusicAdapter(this, this);
        listView.setAdapter(adapter);

        title.setText(playlistName != null ? playlistName : getString(R.string.playlists_title));

        setupMiniPlayer();

        findViewById(R.id.detail_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViewById(R.id.detail_delete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete();
            }
        });

        findViewById(R.id.detail_play_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (musics != null && !musics.isEmpty()) {
                    playQueue(musics, 0);
                    openPlayer();
                } else {
                    toast(getString(R.string.playlist_empty));
                }
            }
        });

        loadMusics();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMusics();
    }

    private void loadMusics() {
        musics = repository.getPlaylistMusics(playlistId);
        adapter.setItems(musics);
        count.setText(getString(R.string.song_count, musics.size()));
        if (musics.isEmpty()) {
            listView.setVisibility(View.GONE);
            stateView.setVisibility(View.VISIBLE);
        } else {
            stateView.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
        }
        updateNowPlaying();
    }

    private void updateNowPlaying() {
        MusicService service = getMusicService();
        Music current = service != null ? service.getCurrentMusic() : null;
        adapter.setCurrentPlayingId(current != null ? current.getId() : -1);
    }

    private void confirmDelete() {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.delete_playlist)
                .setMessage(getString(R.string.delete_playlist_confirm,
                        playlistName != null ? playlistName : ""))
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        repository.deletePlaylist(playlistId);
                        finish();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onMusicClick(Music music, int position) {
        playQueue(musics, position);
        openPlayer();
    }

    @Override
    public void onMenuClick(final Music music, int position, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, getString(R.string.play));
        popup.getMenu().add(0, 2, 1,
                getString(repository.isFavorite(music.getId()) ? R.string.unfavorite : R.string.favorite));
        popup.getMenu().add(0, 3, 2, getString(R.string.remove_from_playlist));
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        playQueue(musics, musics.indexOf(music));
                        openPlayer();
                        return true;
                    case 2:
                        toggleFavorite(music);
                        return true;
                    case 3:
                        repository.removeMusicFromPlaylist(playlistId, music.getId());
                        loadMusics();
                        return true;
                }
                return false;
            }
        });
        popup.show();
    }

    @Override
    public void onTrackChanged(Music music, int index, int queueSize) {
        super.onTrackChanged(music, index, queueSize);
        updateNowPlaying();
    }
}
