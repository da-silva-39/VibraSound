package mz.ac.ucm.feng.ti.vibrasound;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servico central de reproducao do VibraSound.
 *
 * E o componente principal do projeto: gere o MediaPlayer, a fila de
 * reproducao, o foco de audio, a MediaSession, a notificacao e a promocao
 * a Foreground Service. A reproducao nao depende de nenhuma Activity.
 *
 * A comunicacao faz-se por:
 *  - Intents explícitos (startService / startForegroundService) para enviar comandos;
 *  - Binding local (LocalBinder) para a interface ler o estado em tempo real.
 */
public class MusicService extends Service {

    // Comandos enviados por Intent
    public static final String ACTION_SET_QUEUE = "mz.ac.ucm.feng.ti.vibrasound.SET_QUEUE";
    public static final String ACTION_PLAY = "mz.ac.ucm.feng.ti.vibrasound.PLAY";
    public static final String ACTION_PAUSE = "mz.ac.ucm.feng.ti.vibrasound.PAUSE";
    public static final String ACTION_TOGGLE = "mz.ac.ucm.feng.ti.vibrasound.TOGGLE";
    public static final String ACTION_NEXT = "mz.ac.ucm.feng.ti.vibrasound.NEXT";
    public static final String ACTION_PREV = "mz.ac.ucm.feng.ti.vibrasound.PREV";
    public static final String ACTION_STOP = "mz.ac.ucm.feng.ti.vibrasound.STOP";
    public static final String ACTION_SEEK = "mz.ac.ucm.feng.ti.vibrasound.SEEK";
    public static final String ACTION_TOGGLE_SHUFFLE = "mz.ac.ucm.feng.ti.vibrasound.TOGGLE_SHUFFLE";
    public static final String ACTION_CYCLE_REPEAT = "mz.ac.ucm.feng.ti.vibrasound.CYCLE_REPEAT";

    public static final String EXTRA_QUEUE = "extra_queue";
    public static final String EXTRA_INDEX = "extra_index";
    public static final String EXTRA_POSITION = "extra_position";

    // Modos de repeticao
    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    /** Callback para as Activities refletirem o estado real do servico. */
    public interface Listener {
        void onPlaybackStateChanged(boolean isPlaying);

        void onTrackChanged(Music music, int index, int queueSize);

        void onProgress(int positionMs, int durationMs);

        void onModeChanged(boolean shuffle, int repeatMode);
    }

    public class LocalBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    private final LocalBinder binder = new LocalBinder();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private MusicRepository repository;

    private MediaPlayer player;
    private boolean prepared = false;

    private final List<Music> baseQueue = new ArrayList<>();  // ordem original
    private List<Music> queue = new ArrayList<>();            // ordem de reproducao (com aleatorio)
    private int currentIndex = -1;
    private Music currentMusic;

    private boolean isPlaying = false;
    private boolean currentTrackRecorded = false;
    private boolean resumeOnFocusGain = false;

    private boolean shuffle = false;
    private int repeatMode = REPEAT_OFF;
    private boolean autoplayEnabled = true;

    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;

    private MediaSession mediaSession;
    private Bitmap currentArt;

    private PowerManager.WakeLock wakeLock;
    private MusicReceiver noisyReceiver;
    private boolean noisyReceiverRegistered = false;

    private int progressTicks = 0;

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new MusicRepository(this);
        shuffle = repository.isShuffleEnabled();
        repeatMode = repository.getRepeatMode();
        autoplayEnabled = repository.isAutoplayEnabled();

        NotificationHelper.createChannel(this);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        initWakeLock();
        initMediaSession();
        registerNoisyReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Promove a Foreground imediatamente (regra dos 5 segundos do
        // startForegroundService) antes de tratar o comando.
        startForegroundSafely();

        String action = intent != null ? intent.getAction() : null;
        if (action != null) {
            handleAction(intent, action);
        }
        // START_STICKY: o sistema pode recriar o servico, mas sem fila nao
        // retoma reproducao automaticamente.
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Se a aplicacao for removida dos recentes e nao estiver a tocar,
        // terminamos o servico e removemos a notificacao.
        if (!isPlaying) {
            stopPlayback();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        saveCurrentPosition();
        stopProgressUpdates();
        releasePlayer();
        abandonAudioFocus();
        releaseWakeLock();

        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        unregisterNoisyReceiver();
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // Tratamento dos comandos
    // ------------------------------------------------------------------

    private void handleAction(Intent intent, String action) {
        switch (action) {
            case ACTION_SET_QUEUE: {
                ArrayList<Music> incoming = (ArrayList<Music>) intent.getSerializableExtra(EXTRA_QUEUE);
                int index = intent.getIntExtra(EXTRA_INDEX, 0);
                if (incoming != null && !incoming.isEmpty()) {
                    setQueue(incoming, index, true);
                }
                break;
            }
            case ACTION_PLAY:
                playPlayback();
                break;
            case ACTION_PAUSE:
                pausePlayback();
                break;
            case ACTION_TOGGLE:
                if (isPlaying) pausePlayback();
                else playPlayback();
                break;
            case ACTION_NEXT:
                skipNext();
                break;
            case ACTION_PREV:
                skipPrevious();
                break;
            case ACTION_STOP:
                stopPlayback();
                break;
            case ACTION_SEEK:
                seekTo(intent.getIntExtra(EXTRA_POSITION, 0));
                break;
            case ACTION_TOGGLE_SHUFFLE:
                toggleShuffle();
                break;
            case ACTION_CYCLE_REPEAT:
                cycleRepeat();
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------
    // Fila de reproducao
    // ------------------------------------------------------------------

    /** Define a fila e comeca a tocar a musica indicada. */
    public void setQueue(List<Music> list, int index, boolean autoPlay) {
        baseQueue.clear();
        baseQueue.addAll(list);

        Music selected = null;
        if (index >= 0 && index < list.size()) {
            selected = list.get(index);
        } else if (!list.isEmpty()) {
            selected = list.get(0);
        }

        if (shuffle && list.size() > 1 && selected != null) {
            List<Music> rest = new ArrayList<>(list);
            rest.remove(selected);
            Collections.shuffle(rest);
            queue = new ArrayList<>();
            queue.add(selected);
            queue.addAll(rest);
            currentIndex = 0;
        } else {
            queue = new ArrayList<>(list);
            currentIndex = index >= 0 && index < list.size() ? index : 0;
        }

        if (queue.isEmpty()) return;
        currentMusic = queue.get(currentIndex);
        currentTrackRecorded = false;
        openCurrent(autoPlay, 0);
    }

    private void openCurrent(boolean autoPlay, int startPosition) {
        releasePlayer();
        if (queue.isEmpty() || currentIndex < 0 || currentIndex >= queue.size()) {
            return;
        }
        currentMusic = queue.get(currentIndex);
        notifyTrackChanged();

        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());

        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                prepared = true;
                if (startPosition > 0 && startPosition < mp.getDuration()) {
                    mp.seekTo(startPosition);
                }
                if (autoPlay) {
                    startPlaybackInternal(false);
                } else {
                    updateNotificationAndSession();
                    notifyStateChanged();
                }
            }
        });

        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                onSongCompleted();
            }
        });

        player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                // Erro na preparacao/reproducao: nao crashar, avancar quando possivel.
                handlePlayerError();
                return true;
            }
        });

        try {
            player.setDataSource(this, Uri.parse(currentMusic.getUri()));
            player.prepareAsync();
        } catch (Exception e) {
            e.printStackTrace();
            handlePlayerError();
        }

        refreshArtwork();
    }

    private void handlePlayerError() {
        prepared = false;
        isPlaying = false;
        // Tenta passar a proxima musica; se nao houver, para.
        if (queue.size() > 1) {
            skipNext();
        } else {
            updateNotificationAndSession();
            notifyStateChanged();
        }
    }

    // ------------------------------------------------------------------
    // Reproducao
    // ------------------------------------------------------------------

    private void playPlayback() {
        if (player != null && prepared) {
            startPlaybackInternal(false);
        } else if (currentMusic != null) {
            openCurrent(true, 0);
        } else if (!queue.isEmpty()) {
            openCurrent(true, 0);
        }
    }

    private void startPlaybackInternal(boolean resumeFromFocus) {
        if (player == null || !prepared) return;
        if (!requestAudioFocus()) {
            // Sem foco de audio nao iniciamos a reproducao.
            return;
        }
        try {
            player.start();
            player.setVolume(1f, 1f);
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return;
        }
        isPlaying = true;
        acquireWakeLock();
        startForegroundSafely();
        startProgressUpdates();
        recordHistoryIfNeeded();
        updateNotificationAndSession();
        notifyStateChanged();
    }

    public void pausePlayback() {
        if (player != null && prepared) {
            try {
                player.pause();
            } catch (IllegalStateException ignored) {
            }
        }
        isPlaying = false;
        saveCurrentPosition();
        releaseWakeLock();
        stopProgressUpdates();
        abandonAudioFocus();
        updateNotificationAndSession();
        notifyStateChanged();
    }

    private void stopPlayback() {
        saveCurrentPosition();
        isPlaying = false;
        releasePlayer();
        stopProgressUpdates();
        releaseWakeLock();
        abandonAudioFocus();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NotificationHelper.NOTIFICATION_ID);
        stopSelf();
    }

    public void skipNext() {
        if (queue.isEmpty()) return;
        currentIndex = (currentIndex + 1) % queue.size();
        currentTrackRecorded = false;
        openCurrent(true, 0);
    }

    public void skipPrevious() {
        if (queue.isEmpty()) return;
        // Se ja passou alguns segundos, recomeca a musica atual.
        if (player != null && prepared && player.getCurrentPosition() > 3000) {
            seekTo(0);
            return;
        }
        currentIndex = (currentIndex - 1 + queue.size()) % queue.size();
        currentTrackRecorded = false;
        openCurrent(true, 0);
    }

    /** Chamado quando uma musica termina naturalmente. */
    private void onSongCompleted() {
        if (!autoplayEnabled) {
            pausePlayback();
            return;
        }
        if (repeatMode == REPEAT_ONE) {
            openCurrent(true, 0);
            return;
        }
        if (currentIndex < queue.size() - 1) {
            currentIndex++;
            currentTrackRecorded = false;
            openCurrent(true, 0);
        } else if (repeatMode == REPEAT_ALL) {
            currentIndex = 0;
            currentTrackRecorded = false;
            openCurrent(true, 0);
        } else {
            // Fim da fila sem repeticao: mantem a ultima musica em pausa.
            isPlaying = false;
            if (player != null && prepared) {
                try {
                    player.pause();
                    player.seekTo(0);
                } catch (IllegalStateException ignored) {
                }
            }
            stopProgressUpdates();
            releaseWakeLock();
            updateNotificationAndSession();
            notifyStateChanged();
        }
    }

    public void seekTo(int positionMs) {
        if (player != null && prepared) {
            try {
                player.seekTo(positionMs);
                updateNotificationAndSession();
                notifyProgress();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    public void toggleShuffle() {
        shuffle = !shuffle;
        repository.setShuffleEnabled(shuffle);
        rebuildOrderPreservingCurrent();
        notifyModeChanged();
    }

    public void cycleRepeat() {
        // Ordem: desligado -> repetir fila -> repetir musica -> desligado
        if (repeatMode == REPEAT_OFF) repeatMode = REPEAT_ALL;
        else if (repeatMode == REPEAT_ALL) repeatMode = REPEAT_ONE;
        else repeatMode = REPEAT_OFF;
        repository.setRepeatMode(repeatMode);
        notifyModeChanged();
    }

    /** Reorganiza a fila ao ligar/desligar o aleatorio, mantendo a musica atual. */
    private void rebuildOrderPreservingCurrent() {
        if (baseQueue.isEmpty()) return;
        Music current = currentMusic;
        if (shuffle) {
            List<Music> rest = new ArrayList<>(baseQueue);
            if (current != null) rest.remove(current);
            Collections.shuffle(rest);
            queue = new ArrayList<>();
            if (current != null) queue.add(current);
            queue.addAll(rest);
            currentIndex = 0;
        } else {
            queue = new ArrayList<>(baseQueue);
            currentIndex = current != null ? Math.max(0, queue.indexOf(current)) : 0;
        }
    }

    private void recordHistoryIfNeeded() {
        if (currentMusic == null || currentTrackRecorded) return;
        currentTrackRecorded = true;
        repository.addToHistory(currentMusic);
        repository.setLastMusicId(currentMusic.getId());
    }

    private void saveCurrentPosition() {
        if (currentMusic != null && player != null && prepared) {
            try {
                int pos = player.getCurrentPosition();
                if (pos > 0) repository.saveLastPosition(currentMusic.getId(), pos);
            } catch (IllegalStateException ignored) {
            }
        }
    }

    public void onAudioBecomingNoisy() {
        // Fones de ouvido desligados: pausar por seguranca.
        if (isPlaying) pausePlayback();
    }

    // ------------------------------------------------------------------
    // Foco de audio
    // ------------------------------------------------------------------

    private final AudioManager.OnAudioFocusChangeListener focusChangeListener =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    switch (focusChange) {
                        case AudioManager.AUDIOFOCUS_GAIN:
                            if (resumeOnFocusGain && !isPlaying) {
                                startPlaybackInternal(true);
                            }
                            resumeOnFocusGain = false;
                            if (player != null && prepared) {
                                try { player.setVolume(1f, 1f); } catch (IllegalStateException ignored) { }
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS:
                            resumeOnFocusGain = false;
                            pausePlayback();
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            if (isPlaying) {
                                resumeOnFocusGain = true;
                                pausePlayback();
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            // Baixa o volume em vez de parar.
                            if (player != null && prepared) {
                                try { player.setVolume(0.3f, 0.3f); } catch (IllegalStateException ignored) { }
                            }
                            break;
                    }
                }
            };

    private boolean requestAudioFocus() {
        if (audioManager == null) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build();
            int result = audioManager.requestAudioFocus(focusRequest);
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        return true;
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        }
    }

    // ------------------------------------------------------------------
    // MediaPlayer: libertacao de recursos
    // ------------------------------------------------------------------

    private void releasePlayer() {
        prepared = false;
        if (player != null) {
            try {
                player.reset();
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    // ------------------------------------------------------------------
    // Wake lock
    // ------------------------------------------------------------------

    private void initWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VibraSound::Playback");
            wakeLock.setReferenceCounted(false);
        }
    }

    private void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    // ------------------------------------------------------------------
    // MediaSession
    // ------------------------------------------------------------------

    private void initMediaSession() {
        mediaSession = new MediaSession(this, "VibraSound");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                playPlayback();
            }

            @Override
            public void onPause() {
                pausePlayback();
            }

            @Override
            public void onSkipToNext() {
                skipNext();
            }

            @Override
            public void onSkipToPrevious() {
                skipPrevious();
            }

            @Override
            public void onStop() {
                stopPlayback();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
            }
        });
        mediaSession.setActive(true);
    }

    private void updateNotificationAndSession() {
        if (mediaSession != null) {
            long actions = PlaybackState.ACTION_PLAY
                    | PlaybackState.ACTION_PAUSE
                    | PlaybackState.ACTION_PLAY_PAUSE
                    | PlaybackState.ACTION_SKIP_TO_NEXT
                    | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackState.ACTION_SEEK_TO
                    | PlaybackState.ACTION_STOP;

            int state = isPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
            long position = 0;
            if (player != null && prepared) {
                try { position = player.getCurrentPosition(); } catch (IllegalStateException ignored) { }
            }
            mediaSession.setPlaybackState(new PlaybackState.Builder()
                    .setActions(actions)
                    .setState(state, position, isPlaying ? 1f : 0f)
                    .build());

            if (currentMusic != null) {
                MediaMetadata.Builder md = new MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, currentMusic.getDisplayTitle())
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, currentMusic.getDisplayArtist())
                        .putString(MediaMetadata.METADATA_KEY_ALBUM, currentMusic.getDisplayAlbum())
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, currentMusic.getDuration());
                if (currentArt != null) {
                    md.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, currentArt);
                }
                mediaSession.setMetadata(md.build());
            }
        }
        updateNotification();
    }

    private void updateNotification() {
        if (currentMusic == null && !isPlaying) {
            return;
        }
        Notification notification = NotificationHelper.build(
                this, mediaSession, currentMusic, isPlaying, currentArt);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NotificationHelper.NOTIFICATION_ID, notification);
        }
    }

    private void startForegroundSafely() {
        Notification notification = NotificationHelper.build(
                this, mediaSession, currentMusic, isPlaying, currentArt);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification);
        }
    }

    private void refreshArtwork() {
        currentArt = null;
        if (currentMusic == null) return;
        final Music music = currentMusic;
        AlbumArtLoader.get(this).load(music, 256, new AlbumArtLoader.Callback() {
            @Override
            public void onArtLoaded(Bitmap bitmap) {
                // So atualiza se ainda for a musica atual.
                if (bitmap != null && currentMusic == music) {
                    currentArt = bitmap;
                    updateNotificationAndSession();
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Atualizacao de progresso
    // ------------------------------------------------------------------

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPlaying && player != null && prepared) {
                notifyProgress();
                // Atualiza a posicao na MediaSession (barra da notificacao).
                if (mediaSession != null) {
                    mediaSession.setPlaybackState(new PlaybackState.Builder()
                            .setActions(PlaybackState.ACTION_PLAY
                                    | PlaybackState.ACTION_PAUSE
                                    | PlaybackState.ACTION_PLAY_PAUSE
                                    | PlaybackState.ACTION_SKIP_TO_NEXT
                                    | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                                    | PlaybackState.ACTION_SEEK_TO
                                    | PlaybackState.ACTION_STOP)
                            .setState(PlaybackState.STATE_PLAYING,
                                    player.getCurrentPosition(), 1f)
                            .build());
                }
                // Guarda a posicao a cada ~5 segundos (evita escritas excessivas).
                progressTicks++;
                if (progressTicks % 10 == 0) {
                    saveCurrentPosition();
                }
                handler.postDelayed(this, 500);
            }
        }
    };

    private void startProgressUpdates() {
        handler.removeCallbacks(progressRunnable);
        progressTicks = 0;
        handler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        handler.removeCallbacks(progressRunnable);
    }

    // ------------------------------------------------------------------
    // Notificacao para os listeners (Activities)
    // ------------------------------------------------------------------

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            // Envia logo o estado atual para a interface sincronizar.
            listener.onModeChanged(shuffle, repeatMode);
            listener.onPlaybackStateChanged(isPlaying);
            if (currentMusic != null) {
                listener.onTrackChanged(currentMusic, currentIndex, queue.size());
            }
            notifyProgress();
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyStateChanged() {
        for (Listener l : listeners) l.onPlaybackStateChanged(isPlaying);
    }

    private void notifyTrackChanged() {
        for (Listener l : listeners) l.onTrackChanged(currentMusic, currentIndex, queue.size());
    }

    private void notifyProgress() {
        int pos = 0;
        int dur = 0;
        if (player != null && prepared) {
            try {
                pos = player.getCurrentPosition();
                dur = player.getDuration();
            } catch (IllegalStateException ignored) {
            }
        }
        if (dur <= 0 && currentMusic != null) {
            dur = (int) currentMusic.getDuration();
        }
        for (Listener l : listeners) l.onProgress(pos, dur);
    }

    private void notifyModeChanged() {
        for (Listener l : listeners) l.onModeChanged(shuffle, repeatMode);
    }

    // ------------------------------------------------------------------
    // Receptor de "tornou-se ruidoso" (fones desligados)
    // ------------------------------------------------------------------

    private void registerNoisyReceiver() {
        if (noisyReceiverRegistered) return;
        noisyReceiver = new MusicReceiver(this);
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(noisyReceiver, filter);
        noisyReceiverRegistered = true;
    }

    private void unregisterNoisyReceiver() {
        if (noisyReceiverRegistered && noisyReceiver != null) {
            try {
                unregisterReceiver(noisyReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            noisyReceiverRegistered = false;
        }
    }

    // ------------------------------------------------------------------
    // Metodos de leitura de estado (usados pela interface)
    // ------------------------------------------------------------------

    public boolean isPlaying() {
        return isPlaying;
    }

    public Music getCurrentMusic() {
        return currentMusic;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int getQueueSize() {
        return queue.size();
    }

    public boolean isShuffleEnabled() {
        return shuffle;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public int getPosition() {
        if (player != null && prepared) {
            try {
                return player.getCurrentPosition();
            } catch (IllegalStateException ignored) {
            }
        }
        return 0;
    }

    public int getDuration() {
        if (player != null && prepared) {
            try {
                return player.getDuration();
            } catch (IllegalStateException ignored) {
            }
        }
        return currentMusic != null ? (int) currentMusic.getDuration() : 0;
    }

    // ------------------------------------------------------------------
    // Metodos estaticos para enviar comandos por Intent
    // ------------------------------------------------------------------

    public static void startQueue(Context context, ArrayList<Music> queue, int index) {
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(ACTION_SET_QUEUE);
        intent.putExtra(EXTRA_QUEUE, queue);
        intent.putExtra(EXTRA_INDEX, index);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void sendAction(Context context, String action) {
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(action);
        context.startService(intent);
    }

    public static void sendSeek(Context context, int positionMs) {
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(ACTION_SEEK);
        intent.putExtra(EXTRA_POSITION, positionMs);
        context.startService(intent);
    }
}
