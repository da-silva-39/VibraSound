package mz.ac.ucm.feng.ti.vibrasound;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Classe base das Activities do VibraSound.
 *
 * Responsabilidades partilhadas:
 *  - aplicar o tema (escuro/claro) escolhido nas definicoes;
 *  - ligar-se (bind) ao MusicService e remover a ligacao corretamente;
 *  - sincronizar o mini leitor e a barra de navegacao inferior;
 *  - utilitarios de permissoes de audio/notificacao.
 */
public abstract class BaseActivity extends Activity implements MusicService.Listener {

    protected MusicRepository repository;
    private MusicService musicService;
    private boolean bound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, android.os.IBinder service) {
            MusicService.LocalBinder binder = (MusicService.LocalBinder) service;
            musicService = binder.getService();
            musicService.addListener(BaseActivity.this);
            onServiceReady(musicService);
            updateMiniPlayer();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new MusicRepository(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, MusicService.class), serviceConnection, Context.BIND_AUTO_CREATE);
        bound = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (bound) {
            if (musicService != null) {
                musicService.removeListener(this);
            }
            try {
                unbindService(serviceConnection);
            } catch (IllegalArgumentException ignored) {
            }
            bound = false;
        }
    }

    public MusicService getMusicService() {
        return musicService;
    }

    /** Chamado quando o servico fica ligado; as subclasses podem usar. */
    protected void onServiceReady(MusicService service) {
    }

    // ------------------------------------------------------------------
    // Mini leitor
    // ------------------------------------------------------------------

    private android.view.View miniPlayer;
    private android.widget.ImageView miniArt;
    private android.widget.TextView miniTitle;
    private android.widget.TextView miniArtist;
    private android.widget.ImageView miniPlay;

    /** Liga os elementos do mini leitor. Chamar depois do setContentView. */
    protected void setupMiniPlayer() {
        miniPlayer = findViewById(R.id.mini_player);
        if (miniPlayer == null) return;
        miniArt = miniPlayer.findViewById(R.id.mini_art);
        miniTitle = miniPlayer.findViewById(R.id.mini_title);
        miniArtist = miniPlayer.findViewById(R.id.mini_artist);
        miniPlay = miniPlayer.findViewById(R.id.mini_play);

        android.view.View root = miniPlayer.findViewById(R.id.mini_root);
        android.view.View prev = miniPlayer.findViewById(R.id.mini_prev);
        android.view.View next = miniPlayer.findViewById(R.id.mini_next);

        android.view.View.OnClickListener openPlayer = new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                openPlayer();
            }
        };
        if (root != null) root.setOnClickListener(openPlayer);
        if (miniArt != null) miniArt.setOnClickListener(openPlayer);
        if (miniTitle != null) miniTitle.setOnClickListener(openPlayer);
        if (miniArtist != null) miniArtist.setOnClickListener(openPlayer);

        if (miniPlay != null) {
            miniPlay.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    MusicService.sendAction(BaseActivity.this, MusicService.ACTION_TOGGLE);
                }
            });
        }
        if (prev != null) {
            prev.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    MusicService.sendAction(BaseActivity.this, MusicService.ACTION_PREV);
                }
            });
        }
        if (next != null) {
            next.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    MusicService.sendAction(BaseActivity.this, MusicService.ACTION_NEXT);
                }
            });
        }
    }

    /** Atualiza o mini leitor com o estado atual do servico. */
    protected void updateMiniPlayer() {
        if (miniPlayer == null) return;
        MusicService service = musicService;
        Music music = service != null ? service.getCurrentMusic() : null;
        if (music == null) {
            miniPlayer.setVisibility(android.view.View.GONE);
            return;
        }
        miniPlayer.setVisibility(android.view.View.VISIBLE);
        miniTitle.setText(music.getDisplayTitle());
        String artist = music.getDisplayArtist().isEmpty()
                ? getString(R.string.unknown_artist) : music.getDisplayArtist();
        miniArtist.setText(artist);

        boolean playing = service.isPlaying();
        miniPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        miniPlay.setContentDescription(getString(playing ? R.string.pause : R.string.play));

        int size = (int) (48 * getResources().getDisplayMetrics().density);
        AlbumArtLoader.get(this).load(music, size, new AlbumArtLoader.Callback() {
            @Override
            public void onArtLoaded(Bitmap bitmap) {
                if (bitmap != null && miniArt != null) {
                    miniArt.setImageBitmap(bitmap);
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Navegacao inferior
    // ------------------------------------------------------------------

    protected static final int TAB_HOME = 0;
    protected static final int TAB_LIBRARY = 1;
    protected static final int TAB_FAVORITES = 2;
    protected static final int TAB_PLAYLISTS = 3;
    protected static final int TAB_SETTINGS = 4;

    /** Liga a barra de navegacao e realca o separador ativo. */
    protected void setupBottomNav(int activeTab) {
        int[] itemIds = {R.id.nav_home, R.id.nav_library, R.id.nav_favorites,
                R.id.nav_playlists, R.id.nav_settings};
        int[] iconIds = {R.id.nav_home_icon, R.id.nav_library_icon, R.id.nav_favorites_icon,
                R.id.nav_playlists_icon, R.id.nav_settings_icon};
        int[] labelIds = {R.id.nav_home_label, R.id.nav_library_label, R.id.nav_favorites_label,
                R.id.nav_playlists_label, R.id.nav_settings_label};
        Class<?>[] targets = {MainActivity.class, LibraryActivity.class, FavoritesActivity.class,
                PlaylistsActivity.class, SettingsActivity.class};

        for (int i = 0; i < itemIds.length; i++) {
            final int tab = i;
            android.view.View item = findViewById(itemIds[i]);
            if (item == null) continue;
            item.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    navigateToTab(targets[tab]);
                }
            });

            boolean active = (i == activeTab);
            int color = getColor(active ? R.color.accent_orange : R.color.text_secondary);
            android.widget.ImageView icon = findViewById(iconIds[i]);
            android.widget.TextView label = findViewById(labelIds[i]);
            if (icon != null) icon.setColorFilter(color);
            if (label != null) label.setTextColor(color);
        }
    }

    private void navigateToTab(Class<?> target) {
        if (target.equals(getClass())) return;
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    protected void openPlayer() {
        startActivity(new Intent(this, PlayerActivity.class));
    }

    /** Envia uma fila ao servico e comeca a tocar a musica indicada. */
    protected void playQueue(java.util.List<Music> list, int index) {
        if (list == null || list.isEmpty()) return;
        MusicService.startQueue(this, new java.util.ArrayList<>(list), index);
    }

    // ------------------------------------------------------------------
    // Listener do servico (as subclasses podem sobrepor)
    // ------------------------------------------------------------------

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        updateMiniPlayer();
    }

    @Override
    public void onTrackChanged(Music music, int index, int queueSize) {
        updateMiniPlayer();
    }

    @Override
    public void onProgress(int positionMs, int durationMs) {
        // Atualizacao de progresso nao afeta o mini leitor.
    }

    @Override
    public void onModeChanged(boolean shuffle, int repeatMode) {
        // Nao usado no mini leitor.
    }

    // ------------------------------------------------------------------
    // Utilitarios
    // ------------------------------------------------------------------

    protected void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    /** Permissao de leitura de audio adequada a versao do Android. */
    public static String audioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_AUDIO;
        }
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    public boolean hasAudioPermission() {
        return checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    protected String[] getAudioPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    protected boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /** Respeita a permissao de audio conforme a versao do Android. */
    protected String[] audioPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{Manifest.permission.READ_MEDIA_AUDIO};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    // ------------------------------------------------------------------
    // Dialogos reutilizaveis (favoritos e listas)
    // ------------------------------------------------------------------

    public interface PlaylistCallback {
        void onPlaylistCreated(long playlistId);
    }

    /** Alterna o estado de favorito de uma musica. */
    protected boolean toggleFavorite(Music music) {
        boolean fav = repository.isFavorite(music.getId());
        if (fav) {
            repository.removeFavorite(music.getId());
            toast(getString(R.string.removed_from_favorites));
        } else {
            repository.addFavorite(music);
            toast(getString(R.string.added_to_favorites));
        }
        return !fav;
    }

    /** Callback usado quando uma lista e criada. */
    public interface OnPlaylistCreated {
        void onCreated(long playlistId);
    }

    /** Mostra o dialogo para criar uma lista de reproducao. */
    protected void showCreatePlaylistDialog(final OnPlaylistCreated callback) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.playlist_name_hint);
        input.setTextColor(getColor(R.color.text_primary));
        input.setHintTextColor(getColor(R.color.text_disabled));
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.new_playlist)
                .setView(input)
                .setPositiveButton(R.string.create, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) {
                            toast(getString(R.string.playlist_name_required));
                            return;
                        }
                        if (repository.playlistNameExists(name)) {
                            toast(getString(R.string.playlist_exists));
                            return;
                        }
                        long id = repository.createPlaylist(name);
                        toast(getString(R.string.playlist_created));
                        if (callback != null) callback.onCreated(id);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Mostra o dialogo para adicionar uma musica a uma lista existente. */
    protected void showAddToPlaylistDialog(final Music music) {
        final java.util.List<Playlist> playlists = repository.getPlaylists();
        final String[] names = new String[playlists.size() + 1];
        for (int i = 0; i < playlists.size(); i++) {
            names[i] = playlists.get(i).getName();
        }
        names[playlists.size()] = getString(R.string.new_playlist);

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.add_to_playlist_title)
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which == playlists.size()) {
                            // Criar uma nova lista e adicionar logo a musica.
                            showCreatePlaylistDialog(new OnPlaylistCreated() {
                                @Override
                                public void onCreated(long playlistId) {
                                    boolean added = repository.addMusicToPlaylist(playlistId, music);
                                    toast(getString(added ? R.string.added_to_playlist
                                            : R.string.already_in_playlist));
                                }
                            });
                        } else {
                            Playlist p = playlists.get(which);
                            boolean added = repository.addMusicToPlaylist(p.getId(), music);
                            toast(getString(added ? R.string.added_to_playlist
                                    : R.string.already_in_playlist));
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
