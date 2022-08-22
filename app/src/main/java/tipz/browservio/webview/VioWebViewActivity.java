package tipz.browservio.webview;

import android.graphics.Bitmap;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

public class VioWebViewActivity extends AppCompatActivity implements VioWebViewInterface {
    public ProgressBar progressBar;

    @Override
    public void onUrlUpdated(String url) {

    }

    @Override
    public void onTitleUpdated(String title) {

    }

    @Override
    public void onDropDownDismissed() {

    }

    @Override
    public void onFaviconUpdated(Bitmap icon, boolean checkInstance) {

    }

    @Override
    public void onFaviconProgressUpdated(boolean isLoading) {

    }

    @Override
    public void onSwipeRefreshLayoutRefreshingUpdated(boolean isRefreshing) {

    }

    @Override
    public void onPageLoadProgressChanged(int progress) {
        progressBar.setProgress(progress == 100 ? 0 : progress);
    }
}
