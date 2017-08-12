package james.medianotification.services;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.RemoteViews;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import james.medianotification.R;
import james.medianotification.data.PlayerData;
import james.medianotification.receivers.ActionReceiver;
import james.medianotification.utils.ImageUtils;
import james.medianotification.utils.PaletteUtils;
import james.medianotification.utils.PreferenceUtils;

import static android.support.v4.app.NotificationCompat.EXTRA_MEDIA_SESSION;
import static android.support.v4.app.NotificationCompat.VISIBILITY_PUBLIC;

public class NotificationService extends NotificationListenerService {

    public static final String ACTION_UPDATE = "james.medianotification.ACTION_UPDATE";
    public static final String ACTION_DELETE = "james.medianotification.ACTION_DELETE";

    private PlayerData spotifyPlayerData;

    private NotificationManager notificationManager;
    private ActivityManager activityManager;
    private AudioManager audioManager;
    private MediaReceiver mediaReceiver;

    private String packageName;
    private String appName;
    private Bitmap smallIcon;
    private String title;
    private String subtitle;
    private Bitmap largeIcon;
    private List<NotificationCompat.Action> actions;

    private boolean isConnected;
    private boolean isVisible;
    private boolean isPlaying;

    private MediaSession.Token mMediaToken;
    private MediaController mMediaController;
    private MediaSessionManager mMediaSessionManager;
    private MediaSessionListener mMediaSessionListener;

    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mediaReceiver = new MediaReceiver();
        mMediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        mMediaSessionListener = new MediaSessionListener();

        actions = new ArrayList<>();
        spotifyPlayerData = new PlayerData(
                getString(R.string.app_name_spotify),
                "com.spotify.music",
                PendingIntent.getBroadcast(this, 0, new Intent("com.spotify.mobile.android.ui.widget.PREVIOUS"), 0),
                PendingIntent.getBroadcast(this, 0, new Intent("com.spotify.mobile.android.ui.widget.PLAY"), 0),
                PendingIntent.getBroadcast(this, 0, new Intent("com.spotify.mobile.android.ui.widget.NEXT"), 0),
                "com.spotify.music.playbackstatechanged",
                "com.spotify.music.metadatachanged"
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_UPDATE:
                    if (isVisible)
                        updateNotification();
                    break;
                case ACTION_DELETE:
                    isVisible = false;
                    packageName = null;
                    appName = null;
                    smallIcon = null;
                    title = null;
                    subtitle = null;
                    largeIcon = null;
                    actions.clear();

                    break;
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        isConnected = true;
        IntentFilter filter = new IntentFilter();

        for (String action : spotifyPlayerData.actions) {
            filter.addAction(action);
        }

        filter.addAction("james.medianotification.PREVIOUS");
        filter.addAction("james.medianotification.PLAY_PAUSE");
        filter.addAction("james.medianotification.NEXT");

        registerReceiver(mediaReceiver, filter);

        mMediaSessionManager.addOnActiveSessionsChangedListener(mMediaSessionListener, new ComponentName(this, NotificationService.class));
    }

    private void analyseSystemMediaControllers(List<MediaController> mediaControllerList) {
        for (MediaController mediaController : mediaControllerList) {
            if (mediaController.getPackageName().equals(spotifyPlayerData.packageName))
                mMediaController = mediaController;
                mMediaToken = mMediaController.getSessionToken();
                mMediaController.registerCallback(new MediaController.Callback() {
                @Override
                public void onMetadataChanged(@Nullable MediaMetadata metadata) {
                    super.onMetadataChanged(metadata);
                    title = (String) metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
                    subtitle = (String) metadata.getText(MediaMetadata.METADATA_KEY_ARTIST);
                    largeIcon = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                    updateNotification();
                }
            });
        }
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        if (isConnected) {
            unregisterReceiver(mediaReceiver);
            isConnected = false;
        }

        mMediaSessionManager.removeOnActiveSessionsChangedListener(mMediaSessionListener);
    }

    public void updateNotification() {
        if (mMediaController == null)
            return;

        Intent deleteIntent = new Intent(this, NotificationService.class);
        deleteIntent.setAction(ACTION_DELETE);

        Intent spotifyPlayerIntent = new Intent();
        spotifyPlayerIntent.setComponent(new ComponentName(spotifyPlayerData.packageName, "com.spotify.music.MainActivity"));
        PendingIntent spotifyPlayerPendingIntent = PendingIntent.getActivity(this, 0, spotifyPlayerIntent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "music")
                .setSmallIcon(R.drawable.ic_notification_spotify)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setDeleteIntent(PendingIntent.getService(this, 0, deleteIntent, 0))
                .setStyle(new android.support.v4.media.app.NotificationCompat.MediaStyle().setMediaSession(MediaSessionCompat.Token.fromToken(mMediaToken)))
                .setOngoing(isPlaying)
                .setVisibility(VISIBILITY_PUBLIC)
                .setContentIntent(spotifyPlayerPendingIntent);

        // TODO: setContentIntent with Spotify Player

        if (appName == null)
            appName = getString(R.string.app_name);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            builder.setPriority(NotificationManager.IMPORTANCE_MAX);
        else builder.setPriority(Notification.PRIORITY_MAX);

        for (NotificationCompat.Action action : actions) {
            builder.addAction(action);
        }

        if (smallIcon == null)
            smallIcon = ImageUtils.getVectorBitmap(this, R.drawable.ic_notification_spotify);

        builder.setCustomContentView(getContentView(true));
        if (actions.size() > 0)
            builder.setCustomBigContentView(getContentView(false));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(new NotificationChannel("music", "Music", NotificationManager.IMPORTANCE_HIGH));
            builder.setChannelId("music");
        }

        notificationManager.notify(948, builder.build());
        isVisible = true;
    }

    private RemoteViews getContentView(boolean isCollapsed) {
        RemoteViews remoteViews = new RemoteViews(getPackageName(), isCollapsed ? R.layout.layout_notification_collapsed : R.layout.layout_notification_expanded);
        remoteViews.setTextViewText(R.id.appName, spotifyPlayerData.name);
        remoteViews.setTextViewText(R.id.title, title);
        remoteViews.setTextViewText(R.id.subtitle, subtitle);

        remoteViews.setViewVisibility(R.id.largeIcon, View.VISIBLE);
        remoteViews.setImageViewBitmap(R.id.largeIcon, largeIcon);
        Palette.Swatch swatch = PaletteUtils.generateSwatch(this, largeIcon);

        int color = PaletteUtils.getTextColor(this, swatch);
        remoteViews.setInt(R.id.image, "setBackgroundColor", swatch.getRgb());
        remoteViews.setInt(R.id.foregroundImage, "setColorFilter", swatch.getRgb());
        remoteViews.setImageViewBitmap(R.id.smallIcon, ImageUtils.setBitmapColor(smallIcon, color));
        remoteViews.setTextColor(R.id.appName, color);
        remoteViews.setTextColor(R.id.title, color);
        remoteViews.setTextColor(R.id.subtitle, color);

        TypedArray typedArray = obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground});
        int selectableItemBackground = typedArray.getResourceId(0, 0);
        typedArray.recycle();

        remoteViews.setInt(R.id.content, "setBackgroundResource", selectableItemBackground);

        for (int i = 0; i < 5; i++) {
            int id = -1;
            switch (i) {
                case 0:
                    id = R.id.first;
                    break;
                case 1:
                    id = R.id.second;
                    break;
                case 2:
                    id = R.id.third;
                    break;
                case 3:
                    id = R.id.fourth;
                    break;
                case 4:
                    id = R.id.fifth;
                    break;
            }

            NotificationCompat.Action action;
            if (i >= actions.size()) {
                remoteViews.setViewVisibility(id, View.GONE);
                continue;
            } else action = actions.get(i);

            remoteViews.setViewVisibility(id, View.VISIBLE);
            remoteViews.setImageViewBitmap(id, ImageUtils.setBitmapColor(ImageUtils.getVectorBitmap(this, action.getIcon()), color));
            remoteViews.setInt(id, "setBackgroundResource", selectableItemBackground);
            remoteViews.setOnClickPendingIntent(id, action.getActionIntent());
        }

        return remoteViews;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {

    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {

    }

    private boolean contains(String container, String containee) {
        return container != null && containee != null && container.toLowerCase().contains(containee.toLowerCase());
    }

    public static boolean isRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (NotificationService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private class MediaReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            switch (action) {
                case "james.medianotification.PLAY_PAUSE":
                    int keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
                    Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON);
                    i.setPackage(spotifyPlayerData.packageName);
                    synchronized (this) {
                        i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
                        context.sendOrderedBroadcast(i, null);

                        i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, keyCode));
                        context.sendOrderedBroadcast(i, null);
                    }
                    return;
                case "james.medianotification.PREVIOUS":
                    Intent previousIntent = new Intent("com.spotify.mobile.android.ui.widget.PREVIOUS");
                    context.sendBroadcast(previousIntent);
                    return;
                case "james.medianotification.NEXT":
                    Intent nextIntent = new Intent("com.spotify.mobile.android.ui.widget.NEXT");
                    context.sendBroadcast(nextIntent);
                    return;
            }

            if (!action.startsWith("com.spotify.music"))
                return;

            if (intent.hasExtra("playing"))
                isPlaying = intent.getBooleanExtra("playing", false);
            else isPlaying = audioManager.isMusicActive();


            actions.clear();

            Intent previousIntent = new Intent();
            previousIntent.setAction("james.medianotification.PREVIOUS");
            PendingIntent previousPendingIntent = PendingIntent.getBroadcast(context, 12345, previousIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            actions.add(new NotificationCompat.Action(
                    R.drawable.ic_skip_previous,
                    "Previous",
                    previousPendingIntent
            ));

            Intent playPauseIntent = new Intent();
            playPauseIntent.setAction("james.medianotification.PLAY_PAUSE");
            PendingIntent playPausePendingIntent = PendingIntent.getBroadcast(context, 12345, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            actions.add(new NotificationCompat.Action(
                    isPlaying ? R.drawable.ic_pause : R.drawable.ic_play,
                    isPlaying ? "Pause" : "Play",
                    playPausePendingIntent
            ));

            Intent nextIntent = new Intent();
            nextIntent.setAction("james.medianotification.NEXT");
            PendingIntent nextPendingIntent = PendingIntent.getBroadcast(context, 12345, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            actions.add(new NotificationCompat.Action(
                    R.drawable.ic_skip_next,
                    "Next",
                    nextPendingIntent
            ));

            updateNotification();
        }
    }

    private class MediaSessionListener implements MediaSessionManager.OnActiveSessionsChangedListener {
        @Override
        public void onActiveSessionsChanged(@Nullable List<MediaController> list) {
            analyseSystemMediaControllers(list);
        }
    }
}
