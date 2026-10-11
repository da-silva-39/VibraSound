package mz.ac.ucm.feng.ti.vibrasound;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;

import java.util.List;

/** Lista de todas as listas de reproducao criadas pelo utilizador. */
public class PlaylistsActivity extends BaseActivity implements PlaylistAdapter.Listener {

    private ListView listView;
    private View stateView;
    private PlaylistAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlists);

        listView = findViewById(R.id.playlists_list);
        stateView = findViewById(R.id.playlists_state);

        adapter = new PlaylistAdapter(this, this);
        listView.setAdapter(adapter);

        setupMiniPlayer();
        setupBottomNav(TAB_PLAYLISTS);

        findViewById(R.id.playlists_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreatePlaylistDialog(new OnPlaylistCreated() {
                    @Override
                    public void onCreated(long playlistId) {
                        loadPlaylists();
                    }
                });
            }
        });

        loadPlaylists();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPlaylists();
    }

    private void loadPlaylists() {
        List<Playlist> playlists = repository.getPlaylists();
        adapter.setItems(playlists);
        if (playlists.isEmpty()) {
            listView.setVisibility(View.GONE);
            stateView.setVisibility(View.VISIBLE);
        } else {
            stateView.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPlaylistClick(Playlist playlist) {
        Intent intent = new Intent(this, PlaylistDetailActivity.class);
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, playlist.getId());
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_NAME, playlist.getName());
        startActivity(intent);
    }

    @Override
    public void onMenuClick(final Playlist playlist, View anchor) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.delete_playlist)
                .setMessage(getString(R.string.delete_playlist_confirm, playlist.getName()))
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        repository.deletePlaylist(playlist.getId());
                        loadPlaylists();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
