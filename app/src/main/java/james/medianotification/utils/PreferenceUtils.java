package james.medianotification.utils;

public class PreferenceUtils {

    public static final String PREF_COLOR_METHOD = "colorMethod";
    public static final String PREF_CUSTOM_COLOR = "customColor";
    public static final String PREF_HIGH_CONTRAST_TEXT = "highContrastText";
    public static final String PREF_USE_RECEIVER = "useReceiver";
    public static final String PREF_USE_LASTFM = "useLastFm";
    public static final String PREF_MEDIA_CONTROLS_METHOD = "mediaControlsMethod";
    public static final String PREF_ALWAYS_DISMISSIBLE = "alwaysDismissible";
    public static final String PREF_FC_ON_DISMISS = "fcOnDismiss";
    public static final String PREF_DEFAULT_MUSIC_PLAYER = "defaultMusicPlayer";
    public static final String PREF_SHOW_ALBUM_ART = "showAlbumArt";
    public static final String PREF_INVERSE_TEXT_COLORS = "inverseTextColors";

    public static final int COLOR_METHOD_DOMINANT = 0;
    public static final int COLOR_METHOD_PRIMARY = 1;
    public static final int COLOR_METHOD_VIBRANT = 2;
    public static final int COLOR_METHOD_MUTED = 3;
    public static final int COLOR_METHOD_DEFAULT = 4;

    public static final int CONTROLS_METHOD_NONE = 0;
    public static final int CONTROLS_METHOD_AUDIO_MANAGER = 1;
    public static final int CONTROLS_METHOD_REFLECTION = 2;
    public static final int CONTROLS_METHOD_BROADCAST = 3;
    public static final int CONTROLS_METHOD_BROADCAST_STRING = 4;
    public static final int CONTROLS_METHOD_BROADCAST_PARCELABLE = 5;

}
