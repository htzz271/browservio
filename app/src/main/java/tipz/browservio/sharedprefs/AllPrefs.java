package tipz.browservio.sharedprefs;

public class AllPrefs {
    /* browservio_saver */
    public static final String browservio_saver = "browservio.cfg";
    public static final String defaultHomePage = "defaultHomePage";
    public static final String defaultSearch = "defaultSearch";
    public static final String needReload = "needReload";
    public static final String needRestart = "needRestart";
    public static final String isFirstLaunch = "isFirstLaunch";
    public static final String isJavaScriptEnabled = "isJavaScriptEnabled";
    public static final String showFavicon = "showFavicon";
    public static final String showBrowseBtn = "showBrowseBtn";
    public static final String showZoomKeys = "showZoomKeys";
    public static final String showCustomError = "showCustomError";

    /* bookmarks */
    public static final String bookmarks = "bookmarks.cfg";
    public static final String bookmark = "bookmark_";
    public static final String bookmarked = "bookmarked_";
    public static final String bookmarked_count = bookmarked.concat("count");
    public static final String bookmarked_count_title = "_title";
    public static final String bookmarked_count_show = "_show";

    /* history */
    public static final String history_cfg = "history.cfg";
    public static final String history = "history"; // Also used by browservio_saver
    public static final String historyApi = "historyApi";
}