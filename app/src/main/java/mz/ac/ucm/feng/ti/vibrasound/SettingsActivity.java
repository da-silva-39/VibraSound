package mz.ac.ucm.feng.ti.vibrasound;

import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Definicoes da aplicacao. Todas as opcoes tem efeito real e sao guardadas
 * em SharedPreferences ou SQLite.
 */
public class SettingsActivity extends BaseActivity {

    private Switch switchAutoplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        setupMiniPlayer();
        setupBottomNav(TAB_SETTINGS);

        switchAutoplay = findViewById(R.id.switch_autoplay);

        switchAutoplay.setChecked(repository.isAutoplayEnabled());
        switchAutoplay.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                repository.setAutoplayEnabled(isChecked);
            }
        });

        findViewById(R.id.row_clear_history).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClearHistory();
            }
        });

        showVersion();
    }

    private void confirmClearHistory() {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.settings_clear_history)
                .setMessage(R.string.settings_clear_history_desc)
                .setPositiveButton(R.string.clear, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        repository.clearHistory();
                        toast(getString(R.string.history_cleared));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showVersion() {
        TextView version = findViewById(R.id.settings_version);
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            version.setText(getString(R.string.version_label, info.versionName));
        } catch (Exception e) {
            version.setText(getString(R.string.version_label, "1.0"));
        }
    }
}
