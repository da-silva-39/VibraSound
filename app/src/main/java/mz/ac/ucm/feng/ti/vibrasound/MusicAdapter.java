package mz.ac.ucm.feng.ti.vibrasound;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adaptador nativo (BaseAdapter) que apresenta musicas numa ListView.
 * Inclui a capa (carregada em segundo plano), titulo, artista, duracao e um
 * botao de menu para acoes adicionais.
 */
public class MusicAdapter extends BaseAdapter {

    public interface Listener {
        void onMusicClick(Music music, int position);

        void onMenuClick(Music music, int position, View anchor);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final Listener listener;
    private final List<Music> items = new ArrayList<>();
    private long currentPlayingId = -1;
    private final int artSizePx;

    public MusicAdapter(Context context, Listener listener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
        this.artSizePx = (int) (64 * context.getResources().getDisplayMetrics().density);
    }

    public void setItems(List<Music> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void setCurrentPlayingId(long id) {
        this.currentPlayingId = id;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Music getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_music, parent, false);
            holder = new ViewHolder();
            holder.art = convertView.findViewById(R.id.item_art);
            holder.title = convertView.findViewById(R.id.item_title);
            holder.subtitle = convertView.findViewById(R.id.item_subtitle);
            holder.duration = convertView.findViewById(R.id.item_duration);
            holder.menu = convertView.findViewById(R.id.item_menu);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final Music music = items.get(position);
        final int pos = position;

        holder.title.setText(music.getDisplayTitle());

        // Subtitulo: artista e album, tratando os valores em falta.
        String artist = music.getDisplayArtist().isEmpty()
                ? context.getString(R.string.unknown_artist) : music.getDisplayArtist();
        String album = music.getDisplayAlbum();
        holder.subtitle.setText(album.isEmpty() ? artist : artist + " \u2022 " + album);

        holder.duration.setText(formatDuration(music.getDuration()));

        // Realca a musica que esta a tocar.
        if (music.getId() == currentPlayingId) {
            holder.title.setTextColor(context.getColor(R.color.accent_orange));
        } else {
            holder.title.setTextColor(context.getColor(R.color.text_primary));
        }

        AlbumArtLoader.get(context).load(music, artSizePx, holder.art);

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onMusicClick(music, pos);
            }
        });
        holder.menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onMenuClick(music, pos, v);
            }
        });

        return convertView;
    }

    public static String formatDuration(long ms) {
        if (ms <= 0) return "--:--";
        long totalSec = ms / 1000;
        long minutes = totalSec / 60;
        long seconds = totalSec % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private static class ViewHolder {
        ImageView art;
        TextView title;
        TextView subtitle;
        TextView duration;
        ImageView menu;
    }
}
