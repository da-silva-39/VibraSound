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

/** Adaptador nativo para apresentar listas de reproducao numa ListView. */
public class PlaylistAdapter extends BaseAdapter {

    public interface Listener {
        void onPlaylistClick(Playlist playlist);

        void onMenuClick(Playlist playlist, View anchor);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final Listener listener;
    private final List<Playlist> items = new ArrayList<>();

    public PlaylistAdapter(Context context, Listener listener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    public void setItems(List<Playlist> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Playlist getItem(int position) {
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
            convertView = inflater.inflate(R.layout.item_playlist, parent, false);
            holder = new ViewHolder();
            holder.name = convertView.findViewById(R.id.playlist_name);
            holder.count = convertView.findViewById(R.id.item_count);
            holder.menu = convertView.findViewById(R.id.item_menu);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final Playlist playlist = items.get(position);
        holder.name.setText(playlist.getName());
        holder.count.setText(context.getString(R.string.song_count, playlist.getSongCount()));

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onPlaylistClick(playlist);
            }
        });
        holder.menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onMenuClick(playlist, v);
            }
        });

        return convertView;
    }

    private static class ViewHolder {
        TextView name;
        TextView count;
        ImageView menu;
    }
}
