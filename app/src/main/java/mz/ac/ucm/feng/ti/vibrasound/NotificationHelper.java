package mz.ac.ucm.feng.ti.vibrasound;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.session.MediaSession;
import android.os.Build;

/**
 * Cria o canal de notificacao e constroi a notificacao de reproducao.
 * Usa a API nativa Notification.MediaStyle ligada a MediaSession,
 * sem AndroidX.
 */
public class NotificationHelper {

    public static final String CHANNEL_ID = "vibrasound_playback";
    public static final int NOTIFICATION_ID = 1001;

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm == null) return;
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notif_channel_name),
                        NotificationManager.IMPORTANCE_LOW);
                channel.setDescription(context.getString(R.string.notif_channel_desc));
                channel.setShowBadge(false);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Constroi a notificacao com capa, titulo, artista e botoes de controlo.
     *
     * @param largeIcon capa do album (pode ser null)
     */
    public static Notification build(Context context,
                                     MediaSession session,
                                     Music music,
                                     boolean isPlaying,
                                     Bitmap largeIcon) {

        PendingIntent prevPI = serviceAction(context, MusicService.ACTION_PREV, 11);
        PendingIntent togglePI = serviceAction(context,
                isPlaying ? MusicService.ACTION_PAUSE : MusicService.ACTION_PLAY, 12);
        PendingIntent nextPI = serviceAction(context, MusicService.ACTION_NEXT, 13);
        PendingIntent stopPI = serviceAction(context, MusicService.ACTION_STOP, 14);

        // Abrir o ecra de reproducao ao tocar na notificacao.
        Intent openIntent = new Intent(context, PlayerActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentPI = PendingIntent.getActivity(context, 20, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }

        String title = music != null ? music.getDisplayTitle() : context.getString(R.string.app_name);
        String artist = music != null && !music.getDisplayArtist().isEmpty()
                ? music.getDisplayArtist() : context.getString(R.string.unknown_artist);

        int toggleIcon = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play;

        builder.setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(title)
                .setContentText(artist)
                .setContentIntent(contentPI)
                .setDeleteIntent(stopPI)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying)
                .addAction(R.drawable.ic_previous, context.getString(R.string.previous), prevPI)
                .addAction(toggleIcon, context.getString(isPlaying ? R.string.pause : R.string.play), togglePI)
                .addAction(R.drawable.ic_next, context.getString(R.string.next), nextPI);

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon);
        }

        // Estilo multimédia nativo, ligado a MediaSession.
        Notification.MediaStyle style = new Notification.MediaStyle();
        style.setShowActionsInCompactView(0, 1, 2);
        if (session != null) {
            style.setMediaSession(session.getSessionToken());
        }
        builder.setStyle(style);

        return builder.build();
    }

    private static PendingIntent serviceAction(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(action);
        return PendingIntent.getService(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
