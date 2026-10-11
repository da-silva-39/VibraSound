package mz.ac.ucm.feng.ti.vibrasound;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Carrega as capas de album em segundo plano e guarda-as em cache.
 * Usa apenas APIs nativas (LruCache, Executor, MediaMetadataRetriever).
 *
 * A capa e obtida por tentativas sucessivas:
 *  1. ContentResolver.loadThumbnail (Android 10+);
 *  2. content://media/external/audio/albumart (versoes antigas);
 *  3. imagem embutida no ficheiro, via MediaMetadataRetriever.
 */
public class AlbumArtLoader {

    public interface Callback {
        void onArtLoaded(Bitmap bitmap);
    }

    private static AlbumArtLoader instance;

    private final Context appContext;
    private final LruCache<String, Bitmap> cache;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private AlbumArtLoader(Context context) {
        this.appContext = context.getApplicationContext();
        this.executor = Executors.newFixedThreadPool(3);
        this.mainHandler = new Handler(Looper.getMainLooper());
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSizeKb = maxKb / 8; // ate 1/8 da memoria disponivel
        cache = new LruCache<String, Bitmap>(cacheSizeKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    public static synchronized AlbumArtLoader get(Context context) {
        if (instance == null) {
            instance = new AlbumArtLoader(context);
        }
        return instance;
    }

    public static String cacheKey(Music music, int sizePx) {
        return music.getId() + ":" + music.getAlbumId() + ":" + sizePx;
    }

    /** Coloca a capa no ImageView, usando cache ou carregando em segundo plano. */
    public void load(final Music music, final int sizePx, final ImageView imageView) {
        if (music == null || imageView == null) return;
        final String key = cacheKey(music, sizePx);

        Bitmap cached = cache.get(key);
        if (cached != null) {
            imageView.setTag(key);
            imageView.setImageBitmap(cached);
            return;
        }

        imageView.setTag(key);
        final Bitmap placeholder = null;
        imageView.setImageBitmap(placeholder);
        imageView.setContentDescription(music.getDisplayTitle());

        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = decode(music, sizePx);
                if (bitmap != null) {
                    cache.put(key, bitmap);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            // So aplica se a ImageView ainda estiver a espera desta capa.
                            if (key.equals(imageView.getTag())) {
                                imageView.setImageBitmap(bitmap);
                            }
                        }
                    });
                }
            }
        });
    }

    /** Variante com callback, util para ecras que precisam da capa isolada. */
    public void load(final Music music, final int sizePx, final Callback callback) {
        if (music == null || callback == null) return;
        final String key = cacheKey(music, sizePx);
        Bitmap cached = cache.get(key);
        if (cached != null) {
            callback.onArtLoaded(cached);
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = decode(music, sizePx);
                if (bitmap != null) cache.put(key, bitmap);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onArtLoaded(bitmap);
                    }
                });
            }
        });
    }

    private Bitmap decode(Music music, int sizePx) {
        ContentResolver resolver = appContext.getContentResolver();
        Uri musicUri = Uri.parse(music.getUri());
        Bitmap bitmap = null;

        // Tentativa 1: API de miniaturas do MediaStore (Android 10+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                bitmap = resolver.loadThumbnail(musicUri, new Size(sizePx, sizePx), null);
            } catch (Exception ignored) {
            }
        }

        // Tentativa 2: content uri de album art (versoes antigas).
        if (bitmap == null && music.getAlbumId() > 0) {
            InputStream is = null;
            try {
                Uri albumArt = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), music.getAlbumId());
                is = resolver.openInputStream(albumArt);
                if (is != null) {
                    bitmap = BitmapFactory.decodeStream(is);
                }
            } catch (Exception ignored) {
            } finally {
                if (is != null) {
                    try { is.close(); } catch (Exception ignored) { }
                }
            }
        }

        // Tentativa 3: imagem embutida no proprio ficheiro de audio.
        if (bitmap == null) {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
                mmr.setDataSource(appContext, musicUri);
                byte[] art = mmr.getEmbeddedPicture();
                if (art != null) {
                    bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                }
            } catch (Exception ignored) {
            } finally {
                try { mmr.release(); } catch (Exception ignored) { }
            }
        }

        // Reduz bitmaps grandes para poupar memoria.
        if (bitmap != null && bitmap.getWidth() > sizePx) {
            bitmap = scaleDown(bitmap, sizePx);
        }
        return bitmap;
    }

    private Bitmap scaleDown(Bitmap source, int maxSize) {
        int w = source.getWidth();
        int h = source.getHeight();
        float ratio = Math.min((float) maxSize / w, (float) maxSize / h);
        int newW = Math.max(1, Math.round(w * ratio));
        int newH = Math.max(1, Math.round(h * ratio));
        Bitmap scaled = Bitmap.createScaledBitmap(source, newW, newH, true);
        if (scaled != source) source.recycle();
        return scaled;
    }
}
