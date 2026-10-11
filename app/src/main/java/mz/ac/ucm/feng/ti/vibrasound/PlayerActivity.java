package mz.ac.ucm.feng.ti.vibrasound;

import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Ecra de reproducao completo. Mostra a capa, metadados, progresso e todos
 * os controlos. Le o estado real do MusicService e envia-lhe comandos.
 */
public class PlayerActivity extends BaseActivity {

    private ImageView art;
    private ImageView favoriteBtn;
    private ImageView playBtn;
    private ImageView shuffleBtn;
    private ImageView repeatBtn;
    private TextView title;
    private TextView artist;
    private TextView album;
    private TextView positionText;
    private TextView durationText;
    private SeekBar seek;
    private SeekBar volume;

    private AudioManager audioManager;
    private boolean userSeeking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        art = findViewById(R.id.player_art);
        favoriteBtn = findViewById(R.id.player_favorite);
        playBtn = findViewById(R.id.player_play);
        shuffleBtn = findViewById(R.id.player_shuffle);
        repeatBtn = findViewById(R.id.player_repeat);
        title = findViewById(R.id.player_title);
        artist = findViewById(R.id.player_artist);
        album = findViewById(R.id.player_album);
        positionText = findViewById(R.id.player_position);
        durationText = findViewById(R.id.player_duration);
        seek = findViewById(R.id.player_seek);
        volume = findViewById(R.id.player_volume);

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        findViewById(R.id.player_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicService.sendAction(PlayerActivity.this, MusicService.ACTION_TOGGLE);
            }
        });
        findViewById(R.id.player_prev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicService.sendAction(PlayerActivity.this, MusicService.ACTION_PREV);
            }
        });
        findViewById(R.id.player_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicService.sendAction(PlayerActivity.this, MusicService.ACTION_NEXT);
            }
        });
        shuffleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicService.sendAction(PlayerActivity.this, MusicService.ACTION_TOGGLE_SHUFFLE);
            }
        });
        repeatBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicService.sendAction(PlayerActivity.this, MusicService.ACTION_CYCLE_REPEAT);
            }
        });
        favoriteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicService service = getMusicService();
                if (service != null && service.getCurrentMusic() != null) {
                    toggleFavorite(service.getCurrentMusic());
                    updateFavoriteIcon(service.getCurrentMusic());
                }
            }
        });

        setupSeekBar();
        setupVolume();
    }

    @Override
    protected void onServiceReady(MusicService service) {
        updateFromService(service);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getMusicService() != null) {
            updateFromService(getMusicService());
        }
        updateVolumeFromSystem();
    }

    // ------------------------------------------------------------------
    // Atualizacao da interface
    // ------------------------------------------------------------------

    private void updateFromService(MusicService service) {
        Music music = service.getCurrentMusic();
        if (music == null) {
            title.setText(R.string.no_song_selected);
            artist.setText("");
            album.setText("");
            art.setImageBitmap(null);
            seek.setMax(0);
            seek.setProgress(0);
            positionText.setText("0:00");
            durationText.setText("0:00");
            playBtn.setImageResource(R.drawable.ic_play);
            favoriteBtn.setImageResource(R.drawable.ic_favorite_border);
            favoriteBtn.setColorFilter(getColor(R.color.text_secondary));
            updateModeIcons(service.isShuffleEnabled(), service.getRepeatMode());
            return;
        }

        title.setText(music.getDisplayTitle());
        artist.setText(music.getDisplayArtist().isEmpty()
                ? getString(R.string.unknown_artist) : music.getDisplayArtist());
        if (music.getDisplayAlbum().isEmpty()) {
            album.setVisibility(View.GONE);
        } else {
            album.setVisibility(View.VISIBLE);
            album.setText(music.getDisplayAlbum());
        }

        int duration = (int) music.getDuration();
        seek.setMax(duration > 0 ? duration : 0);
        durationText.setText(MusicAdapter.formatDuration(duration));

        AlbumArtLoader.get(this).load(music,
                (int) (280 * getResources().getDisplayMetrics().density), art);

        updateFavoriteIcon(music);
        playBtn.setImageResource(service.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        updateModeIcons(service.isShuffleEnabled(), service.getRepeatMode());
    }

    private void updateFavoriteIcon(Music music) {
        boolean fav = repository.isFavorite(music.getId());
        favoriteBtn.setImageResource(fav ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        favoriteBtn.setColorFilter(getColor(fav ? R.color.accent_orange : R.color.text_secondary));
        favoriteBtn.setContentDescription(
                getString(fav ? R.string.unfavorite : R.string.favorite));
    }

    private void updateModeIcons(boolean shuffle, int repeatMode) {
        int on = getColor(R.color.accent_orange);
        int off = getColor(R.color.text_secondary);

        shuffleBtn.setColorFilter(shuffle ? on : off);
        shuffleBtn.setContentDescription(getString(shuffle ? R.string.shuffle_on : R.string.shuffle_off));

        if (repeatMode == MusicService.REPEAT_ONE) {
            repeatBtn.setImageResource(R.drawable.ic_repeat_one);
            repeatBtn.setColorFilter(getColor(R.color.accent_orange));
            repeatBtn.setContentDescription(getString(R.string.repeat_one));
        } else if (repeatMode == MusicService.REPEAT_ALL) {
            repeatBtn.setImageResource(R.drawable.ic_repeat);
            repeatBtn.setColorFilter(getColor(R.color.accent_orange));
            repeatBtn.setContentDescription(getString(R.string.repeat_all));
        } else {
            repeatBtn.setImageResource(R.drawable.ic_repeat);
            repeatBtn.setColorFilter(getColor(R.color.text_secondary));
            repeatBtn.setContentDescription(getString(R.string.repeat_off));
        }
    }

    // ------------------------------------------------------------------
    // Barra de progresso
    // ------------------------------------------------------------------

    private void setupSeekBar() {
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    positionText.setText(MusicAdapter.formatDuration(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
                MusicService.sendSeek(PlayerActivity.this, seekBar.getProgress());
            }
        });
    }

    // ------------------------------------------------------------------
    // Volume (stream de musica do sistema)
    // ------------------------------------------------------------------

    private void setupVolume() {
        if (audioManager == null) return;
        volume.setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        updateVolumeFromSystem();
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && audioManager != null) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void updateVolumeFromSystem() {
        if (audioManager != null) {
            volume.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
        }
    }

    // ------------------------------------------------------------------
    // Callbacks do servico
    // ------------------------------------------------------------------

    @Override
    public void onTrackChanged(Music music, int index, int queueSize) {
        super.onTrackChanged(music, index, queueSize);
        if (getMusicService() != null) updateFromService(getMusicService());
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        super.onPlaybackStateChanged(isPlaying);
        playBtn.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    @Override
    public void onProgress(int positionMs, int durationMs) {
        if (userSeeking) return;
        if (durationMs > 0 && seek.getMax() != durationMs) {
            seek.setMax(durationMs);
            durationText.setText(MusicAdapter.formatDuration(durationMs));
        }
        seek.setProgress(positionMs);
        positionText.setText(MusicAdapter.formatDuration(positionMs));
    }

    @Override
    public void onModeChanged(boolean shuffle, int repeatMode) {
        updateModeIcons(shuffle, repeatMode);
    }
}
