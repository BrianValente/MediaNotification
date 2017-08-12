package james.medianotification.activities;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import james.colorpickerdialog.dialogs.ColorPickerDialog;
import james.colorpickerdialog.dialogs.PreferenceDialog;
import james.medianotification.R;
import james.medianotification.services.NotificationService;
import james.medianotification.utils.MarkdownUtils;
import james.medianotification.utils.PreferenceUtils;
import james.medianotification.views.ColorImageView;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATION = 1034;

    private SwitchCompat mediaNotificationSwitch;
    private AppCompatSpinner colorMethodSpinner;
    private View customColorView;
    private ColorImageView customColor;
    private SwitchCompat inverseTextSwitch;
    private SwitchCompat highContrastSwitch;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        mediaNotificationSwitch = findViewById(R.id.mediaNotificationSwitch);
        colorMethodSpinner = findViewById(R.id.colorMethodSpinner);
        customColorView = findViewById(R.id.customColorView);
        customColor = findViewById(R.id.customColor);
        inverseTextSwitch = findViewById(R.id.inverseTextSwitch);
        highContrastSwitch = findViewById(R.id.highContrastSwitch);

        mediaNotificationSwitch.setChecked(NotificationService.isRunning(this));
        mediaNotificationSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityForResult(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"), REQUEST_NOTIFICATION);
            }
        });

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.array_color_methods, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        colorMethodSpinner.setAdapter(adapter);
        colorMethodSpinner.setSelection(prefs.getInt(PreferenceUtils.PREF_COLOR_METHOD, 0));
        colorMethodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                prefs.edit().putInt(PreferenceUtils.PREF_COLOR_METHOD, i).apply();
                updateNotification();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        customColor.setColor(prefs.getInt(PreferenceUtils.PREF_CUSTOM_COLOR, Color.WHITE));
        customColorView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new ColorPickerDialog(MainActivity.this)
                        .setDefaultPreference(Color.WHITE)
                        .setPreference(prefs.getInt(PreferenceUtils.PREF_CUSTOM_COLOR, Color.WHITE))
                        .setListener(new PreferenceDialog.OnPreferenceListener<Integer>() {
                            @Override
                            public void onPreference(PreferenceDialog dialog, Integer preference) {
                                prefs.edit().putInt(PreferenceUtils.PREF_CUSTOM_COLOR, preference).apply();
                                customColor.setColor(preference);
                                updateNotification();
                            }

                            @Override
                            public void onCancel(PreferenceDialog dialog) {
                            }
                        })
                        .show();
            }
        });

        inverseTextSwitch.setChecked(prefs.getBoolean(PreferenceUtils.PREF_INVERSE_TEXT_COLORS, true));
        inverseTextSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                prefs.edit().putBoolean(PreferenceUtils.PREF_INVERSE_TEXT_COLORS, b).apply();
                if (b && highContrastSwitch.isChecked())
                    highContrastSwitch.setChecked(false);
                else updateNotification();
            }
        });

        highContrastSwitch.setChecked(prefs.getBoolean(PreferenceUtils.PREF_HIGH_CONTRAST_TEXT, false));
        highContrastSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                prefs.edit().putBoolean(PreferenceUtils.PREF_HIGH_CONTRAST_TEXT, b).apply();
                if (b && inverseTextSwitch.isChecked())
                    inverseTextSwitch.setChecked(false);
                else updateNotification();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_github:
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/BrianValente/MediaNotification")));
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_NOTIFICATION && mediaNotificationSwitch != null)
            mediaNotificationSwitch.setChecked(NotificationService.isRunning(this));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void updateNotification() {
        if (NotificationService.isRunning(this)) {
            Intent intent = new Intent(this, NotificationService.class);
            intent.setAction(NotificationService.ACTION_UPDATE);
            startService(intent);
        }
    }
}