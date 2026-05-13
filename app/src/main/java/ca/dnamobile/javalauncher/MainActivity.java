package ca.dnamobile.javalauncher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.Collections;

import ca.dnamobile.javalauncher.data.AccountStore;
import ca.dnamobile.javalauncher.instance.LauncherInstance;
import ca.dnamobile.javalauncher.ui.instance.CreateInstanceDialog;
import ca.dnamobile.javalauncher.ui.instance.LauncherInstanceAdapter;

/**
 * Main launcher activity — instance list, account badge, and game launch entry.
 */
public final class MainActivity extends AppCompatActivity
        implements LauncherInstanceAdapter.Listener {

    private static WeakReference<Activity> currentActivity = new WeakReference<>(null);

    public static void setCurrentActivity(@Nullable Activity activity) {
        currentActivity = new WeakReference<>(activity);
    }

    public static void clearCurrentActivity(@Nullable Activity activity) {
        Activity current = currentActivity.get();
        if (current == activity) {
            currentActivity.clear();
        }
    }

    @Nullable
    public static Activity getCurrentActivity() {
        return currentActivity.get();
    }

    private AccountStore accountStore;
    private LauncherInstanceAdapter instanceAdapter;
    @Nullable
    private LauncherInstance selectedInstance;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MainActivity.setCurrentActivity(this);

        accountStore = new AccountStore(this);

        RecyclerView recyclerView = findViewById(R.id.recycler_instances);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            instanceAdapter = new LauncherInstanceAdapter(this, this);
            recyclerView.setAdapter(instanceAdapter);
        }

        View buttonSettings = findViewById(R.id.button_open_settings);
        if (buttonSettings != null) {
            buttonSettings.setOnClickListener(v ->
                    startActivity(new Intent(this, LauncherSettingsActivity.class)));
        }

        View fabCreateInstance = findViewById(R.id.fab_create_instance);
        if (fabCreateInstance != null) {
            fabCreateInstance.setOnClickListener(v -> showCreateInstanceDialog());
        }

        View buttonPlay = findViewById(R.id.button_play);
        if (buttonPlay != null) {
            buttonPlay.setEnabled(false);
            buttonPlay.setOnClickListener(v -> launchSelectedInstance());
        }

        updateAccountBadge();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MainActivity.setCurrentActivity(this);
        updateAccountBadge();
    }

    @Override
    protected void onDestroy() {
        MainActivity.clearCurrentActivity(this);
        super.onDestroy();
    }

    @Override
    public void onInstanceSelected(@NonNull LauncherInstance instance) {
        selectedInstance = instance;
        updatePlayButton();
    }

    @Override
    public void onInstanceQuickPlayRequested(@NonNull LauncherInstance instance) {
        selectedInstance = instance;
        launchSelectedInstance();
    }

    @Override
    public void onInstanceDeleteRequested(@NonNull LauncherInstance instance) {
        if (instance.equals(selectedInstance)) selectedInstance = null;
        updatePlayButton();
    }

    private void updateAccountBadge() {
        TextView badge = findViewById(R.id.text_account_badge);
        if (badge == null) return;
        AccountStore.Account account = accountStore.load();
        badge.setText(account != null ? account.getBestDisplayName() : getString(R.string.status_signed_out));
    }

    private void updatePlayButton() {
        View btn = findViewById(R.id.button_play);
        if (btn != null) btn.setEnabled(selectedInstance != null);
    }

    private void showCreateInstanceDialog() {
        new CreateInstanceDialog(this, Collections.emptyList(), new CreateInstanceDialog.Listener() {
            @Override
            public void onPickIcon(@NonNull CreateInstanceDialog dialog) {
            }

            @Override
            public void onCreateInstance(@NonNull CreateInstanceDialog.Request request) {
                updateAccountBadge();
            }
        }).show();
    }

    private void launchSelectedInstance() {
        if (selectedInstance == null) return;
        AccountStore.Account account = accountStore.load();
        if (account == null) {
            startActivity(new Intent(this, LauncherSettingsActivity.class));
            return;
        }
        MainActivity.setCurrentActivity(this);
    }
}

