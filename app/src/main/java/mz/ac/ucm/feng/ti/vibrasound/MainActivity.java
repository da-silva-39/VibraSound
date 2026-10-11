package mz.ac.ucm.feng.ti.vibrasound;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;

/**
 * Ecra inicial do VibraSound: saudacao, numero de musicas, reproducoes
 * recentes, favoritas e atalhos. Abre diretamente, sem login.
 */
public class MainActivity extends BaseActivity {

    private static final int REQ_PERMISSIONS = 100;

    private TextView greeting;
    private TextView subtitle;
    private TextView songCount;
    private TextView recentEmpty;
    private TextView favEmpty;
    private View recentScroll;
    private View favScroll;
    private LinearLayout recentContainer;
    private LinearLayout favContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        greeting = findViewById(R.id.home_greeting);
        subtitle = findViewById(R.id.home_subtitle);
        songCount = findViewById(R.id.home_song_count);
        recentEmpty = findViewById(R.id.recent_empty);
        favEmpty = findViewById(R.id.fav_empty);
        recentScroll = findViewById(R.id.recent_scroll);
        favScroll = findViewById(R.id.fav_scroll);
        recentContainer = findViewById(R.id.recent_container);
        favContainer = findViewById(R.id.fav_container);

        setupMiniPlayer();
        setupBottomNav(TAB_HOME);
        setupHeader();
        setupQuickAccess();

        requestAudioPermissionIfNeeded();
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Atualiza secoes ao voltar de outros ecras.
        loadData();
    }

    private void setupHeader() {
        greeting.setText(getGreeting());
        subtitle.setText(getString(R.string.home_subtitle));
        findViewById(R.id.home_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
    }

    private String getGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return getString(R.string.greeting_morning);
        if (hour < 19) return getString(R.string.greeting_afternoon);
        return getString(R.string.greeting_night);
    }

    private void requestAudioPermissionIfNeeded() {
        if (hasAudioPermission() && hasNotificationPermission()) return;
        String[] perms;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms = new String[]{android.Manifest.permission.READ_MEDIA_AUDIO,
                    android.Manifest.permission.POST_NOTIFICATIONS};
        } else {
            perms = new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE};
        }
        requestPermissions(perms, REQ_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            loadData();
        }
    }

    /** Carrega contagens e secoes em segundo plano. */
    private void loadData() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int total = repository.queryDeviceMusic().size();
                final List<Music> recent = repository.getRecent(10);
                final List<Music> favorites = repository.getFavorites();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        songCount.setText(String.valueOf(total));
                        populateRecent(recent);
                        populateFavorites(favorites);
                    }
                });
            }
        }).start();
    }

    private void populateRecent(final List<Music> recent) {
        recentContainer.removeAllViews();
        if (recent.isEmpty()) {
            recentEmpty.setVisibility(View.VISIBLE);
            recentScroll.setVisibility(View.GONE);
            return;
        }
        recentEmpty.setVisibility(View.GONE);
        recentScroll.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < recent.size(); i++) {
            final Music music = recent.get(i);
            final int index = i;
            View card = inflater.inflate(R.layout.item_recent_card, recentContainer, false);
            TextView title = card.findViewById(R.id.card_title);
            TextView artist = card.findViewById(R.id.card_subtitle);
            ImageView art = card.findViewById(R.id.card_art);
            title.setText(music.getDisplayTitle());
            artist.setText(music.getDisplayArtist().isEmpty()
                    ? getString(R.string.unknown_artist) : music.getDisplayArtist());
            AlbumArtLoader.get(this).load(music,
                    (int) (140 * getResources().getDisplayMetrics().density), art);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playQueue(recent, index);
                    openPlayer();
                }
            });
            recentContainer.addView(card);
        }
    }

    private void populateFavorites(final List<Music> favorites) {
        favContainer.removeAllViews();
        if (favorites.isEmpty()) {
            favEmpty.setVisibility(View.VISIBLE);
            favScroll.setVisibility(View.GONE);
            return;
        }
        favEmpty.setVisibility(View.GONE);
        favScroll.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < favorites.size(); i++) {
            final Music music = favorites.get(i);
            final int index = i;
            View card = inflater.inflate(R.layout.item_recent_card, favContainer, false);
            TextView title = card.findViewById(R.id.card_title);
            TextView artist = card.findViewById(R.id.card_subtitle);
            ImageView art = card.findViewById(R.id.card_art);
            title.setText(music.getDisplayTitle());
            artist.setText(music.getDisplayArtist().isEmpty()
                    ? getString(R.string.unknown_artist) : music.getDisplayArtist());
            AlbumArtLoader.get(this).load(music,
                    (int) (140 * getResources().getDisplayMetrics().density), art);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playQueue(favorites, index);
                    openPlayer();
                }
            });
            favContainer.addView(card);
        }
    }

    private void setupQuickAccess() {
        findViewById(R.id.card_library).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, LibraryActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            }
        });
        findViewById(R.id.card_favorites).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, FavoritesActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            }
        });
        findViewById(R.id.card_playlists).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, PlaylistsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            }
        });
    }
}
