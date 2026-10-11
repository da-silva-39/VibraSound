package mz.ac.ucm.feng.ti.vibrasound;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

/**
 * Receptor nativo registado dinamicamente pelo MusicService.
 *
 * Trata o evento ACTION_AUDIO_BECOMING_NOISY, emitido pelo sistema quando os
 * fones de ouvido sao desligados. Nesse caso a reproducao e pausada por
 * seguranca.
 */
public class MusicReceiver extends BroadcastReceiver {

    private final MusicService service;

    public MusicReceiver(MusicService service) {
        this.service = service;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
            service.onAudioBecomingNoisy();
        }
    }
}
