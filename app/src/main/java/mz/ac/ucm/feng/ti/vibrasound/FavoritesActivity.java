package mz.ac.ucm.feng.ti.vibrasound;

import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.PopupMenu;

import java.util.ArrayList;
import java.util.List;

/** Lista de musicas favoritas, guardadas em SQLite. */
public class FavoritesActivity extends BaseActivity implements MusicAdapter.Listener {

    private ListView listView;
    private View stateView;
    private MusicAdapter adapter;
    private List<Music> favorites = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        listView = findViewById(R.id.favorites_list);
        stateView = findViewById(R.id.favorites_state);

        adapter = new MusicAdapter(this, this);
        listView.setAdapter(adapter);

        setupMiniPlayer();
        setupBottomNav(TAB_FAVORITES);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFavorites();
    }

    private void loadFavorites() {
        favorites = repository.getFavorites();
        adapter.setItems(favorites);
        if (favorites.isEmpty()) {
            listView.setVisibility(View.GONE);
            findViewById(R.id.favorites_state).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.favorites_state).setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
        }
        updateNowPlaying();
    }

    private void updateNowPlaying() {
        MusicService service = getMusicService();
        Music current = service != null ? service.getCurrentMusic() : null;
        adapter.setCurrentPlayingId(current != null ? current.getId() : -1);
    }

    @Override
    public void onMusicClick(Music music, int position) {
        playQueue(favorites, position);
        openPlayer();
    }

    @Override
    public void onMenuClick(final Music music, int position, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, getString(R.string.play));
        popup.getMenu().add(0, 2, 1, getString(R.string.unfavorite));
        popup.getMenu().add(0, 3, 2, getString(R.string.add_to_playlist));
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        playQueue(favorites, favorites.indexOf(music));
                        openPlayer();
                        return true;
                    case 2:
                        repository.removeFavorite(music.getId());
                        toast(getString(R.string.removed_from_favorites));
                        loadFavorites();
                        return true;
                    case 3:
                        showAddToPlaylistDialog(music);
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
