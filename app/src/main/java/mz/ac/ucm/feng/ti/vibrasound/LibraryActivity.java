package mz.ac.ucm.feng.ti.vibrasound;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Biblioteca musical: lista todas as musicas do dispositivo (MediaStore),
 * com pesquisa e ordenacao. Trata os estados de permissao, carregamento,
 * lista vazia e sem resultados.
 */
public class LibraryActivity extends BaseActivity implements MusicAdapter.Listener {

    private static final int REQ_PERMISSION = 200;

    private static final int SORT_NAME = 0;
    private static final int SORT_ARTIST = 1;
    private static final int SORT_DURATION = 2;

    private EditText search;
    private ListView listView;
    private View stateView;
    private ImageView stateIcon;
    private TextView stateTitle;
    private TextView stateMessage;
    private TextView stateAction;
    private TextView chipName;
    private TextView chipArtist;
    private TextView chipDuration;

    private MusicAdapter adapter;
    private final List<Music> allMusic = new ArrayList<>();
    private List<Music> currentList = new ArrayList<>();
    private int sortMode = SORT_NAME;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        search = findViewById(R.id.library_search);
        listView = findViewById(R.id.library_list);
        stateView = findViewById(R.id.library_state);
        stateIcon = findViewById(R.id.state_icon);
        stateTitle = findViewById(R.id.state_title);
        stateMessage = findViewById(R.id.state_message);
        stateAction = findViewById(R.id.state_action);
        chipName = findViewById(R.id.sort_name);
        chipArtist = findViewById(R.id.sort_artist);
        chipDuration = findViewById(R.id.sort_duration);

        adapter = new MusicAdapter(this, this);
        listView.setAdapter(adapter);

        setupMiniPlayer();
        setupBottomNav(TAB_LIBRARY);

        findViewById(R.id.library_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissionAndLoad();
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilterAndSort();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        chipName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortMode = SORT_NAME;
                applyFilterAndSort();
            }
        });
        chipArtist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortMode = SORT_ARTIST;
                applyFilterAndSort();
            }
        });
        chipDuration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortMode = SORT_DURATION;
                applyFilterAndSort();
            }
        });

        checkPermissionAndLoad();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasAudioPermission() && allMusic.isEmpty()) {
            checkPermissionAndLoad();
        }
    }

    // ------------------------------------------------------------------
    // Permissao e carregamento
    // ------------------------------------------------------------------

    private void checkPermissionAndLoad() {
        if (!hasAudioPermission()) {
            showPermissionState();
            return;
        }
        loadMusic();
    }

    private void loadMusic() {
        showState(R.drawable.ic_music_note, getString(R.string.loading), "", null, null);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Music> result = repository.queryDeviceMusic();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        allMusic.clear();
                        allMusic.addAll(result);
                        if (allMusic.isEmpty()) {
                            showEmptyState();
                        } else {
                            applyFilterAndSort();
                        }
                    }
                });
            }
        }).start();
    }

    private void applyFilterAndSort() {
        String query = search.getText().toString();
        List<Music> filtered = MusicRepository.filter(allMusic, query);
        if (sortMode == SORT_ARTIST) {
            MusicRepository.sortByArtist(filtered);
        } else if (sortMode == SORT_DURATION) {
            MusicRepository.sortByDuration(filtered);
        } else {
            MusicRepository.sortByName(filtered);
        }
        updateChips();

        currentList = filtered;
        adapter.setItems(filtered);
        if (filtered.isEmpty()) {
            showState(R.drawable.ic_search, getString(R.string.no_results), "", null, null);
        } else {
            showList();
        }
        updateNowPlaying();
    }

    private void updateChips() {
        styleChip(chipName, sortMode == SORT_NAME);
        styleChip(chipArtist, sortMode == SORT_ARTIST);
        styleChip(chipDuration, sortMode == SORT_DURATION);
    }

    private void styleChip(TextView chip, boolean selected) {
        chip.setSelected(selected);
        chip.setTextColor(getColor(selected ? R.color.white : R.color.text_primary));
    }

    private void updateNowPlaying() {
        MusicService service = getMusicService();
        Music current = service != null ? service.getCurrentMusic() : null;
        adapter.setCurrentPlayingId(current != null ? current.getId() : -1);
    }

    // ------------------------------------------------------------------
    // Estados visuais
    // ------------------------------------------------------------------

    private void showList() {
        listView.setVisibility(View.VISIBLE);
        stateView.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        showState(R.drawable.ic_music_off, getString(R.string.no_music),
                getString(R.string.no_music_hint), getString(R.string.refresh),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        checkPermissionAndLoad();
                    }
                });
    }

    private void showPermissionState() {
        String message = getString(R.string.perm_audio_msg);
        showState(R.drawable.ic_music_off, getString(R.string.perm_title), message,
                getString(R.string.perm_grant), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (shouldShowRequestPermissionRationale(
                                android.Manifest.permission.READ_MEDIA_AUDIO)
                                || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                            requestPermissions(new String[]{getAudioPermissionName()}, REQ_PERMISSION);
                        } else {
                            requestPermissions(new String[]{android.Manifest.permission.READ_MEDIA_AUDIO},
                                    REQ_PERMISSION);
                        }
                    }
                });
    }

    private String getAudioPermissionName() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return android.Manifest.permission.READ_MEDIA_AUDIO;
        }
        return android.Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private void showPermissionDeniedState() {
        showState(R.drawable.ic_music_off, getString(R.string.perm_title),
                getString(R.string.perm_denied_msg), getString(R.string.perm_open_settings),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.fromParts("package", getPackageName(), null));
                        startActivity(intent);
                    }
                });
    }

    private void showState(int iconRes, String title, String message,
                           String actionText, View.OnClickListener actionListener) {
        listView.setVisibility(View.GONE);
        stateView.setVisibility(View.VISIBLE);
        stateIcon.setImageResource(iconRes);
        stateTitle.setText(title);
        if (message == null || message.isEmpty()) {
            stateMessage.setVisibility(View.GONE);
        } else {
            stateMessage.setVisibility(View.VISIBLE);
            stateMessage.setText(message);
        }
        if (actionText == null) {
            stateAction.setVisibility(View.GONE);
        } else {
            stateAction.setVisibility(View.VISIBLE);
            stateAction.setText(actionText);
            stateAction.setOnClickListener(actionListener);
        }
    }

    // ------------------------------------------------------------------
    // Resultado do pedido de permissao
    // ------------------------------------------------------------------

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMISSION) return;
        boolean granted = grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (granted) {
            loadMusic();
        } else {
            showPermissionDeniedState();
        }
    }

    // ------------------------------------------------------------------
    // Interacao com a lista
    // ------------------------------------------------------------------

    @Override
    public void onMusicClick(Music music, int position) {
        playQueue(currentList, position);
        openPlayer();
    }

    @Override
    public void onMenuClick(final Music music, int position, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, getString(R.string.play));
        popup.getMenu().add(0, 2, 1,
                getString(repository.isFavorite(music.getId()) ? R.string.unfavorite : R.string.favorite));
        popup.getMenu().add(0, 3, 2, getString(R.string.add_to_playlist));
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        playQueue(currentList, currentList.indexOf(music));
                        openPlayer();
                        return true;
                    case 2:
                        toggleFavorite(music);
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
