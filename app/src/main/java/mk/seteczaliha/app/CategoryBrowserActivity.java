package mk.seteczaliha.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Visible in-app SETEC category browser. A category is opened by its verified
 * official URL instead of scraping a hidden WebView (which can stall on React).
 * The website owns product listings, next/previous pages and product details.
 */
public final class CategoryBrowserActivity extends Activity {
    public static final String EXTRA_URL = "setec_category_url";
    private final Set<String> approvedCategoryPaths = new HashSet<>();
    private WebView browser;
    private ProgressBar loading;
    private TextView heading;

    @Override @SuppressLint("SetJavaScriptEnabled")
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(8, 15, 26));
        getWindow().setNavigationBarColor(Color.rgb(8, 15, 26));
        loadApprovedCategoryPaths();

        String initial = getIntent().getStringExtra(EXTRA_URL);
        if (!safeDestination(initial)) {
            Toast.makeText(this, "Непотврдена категорија на Setec.mk.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8, 15, 26));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(9), dp(5), dp(9), dp(5));
        toolbar.setBackgroundColor(Color.rgb(16, 31, 49));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(57)));

        Button back = toolbarButton("‹ Назад");
        back.setOnClickListener(v -> goBackOrClose());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(91), dp(46)));

        heading = new TextView(this);
        heading.setText("SETEC · Каталог");
        heading.setTextSize(15);
        heading.setTextColor(Color.WHITE);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));

        Button refresh = toolbarButton("⟳");
        refresh.setContentDescription("Освежи ја категоријата");
        refresh.setOnClickListener(v -> { if (browser != null) browser.reload(); });
        toolbar.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(46)));

        loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loading.setMax(100);
        loading.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(49, 175, 250)));
        root.addView(loading, new LinearLayout.LayoutParams(-1, dp(3)));

        FrameLayout frame = new FrameLayout(this);
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));

        browser = new WebView(this);
        browser.setBackgroundColor(Color.WHITE);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false);
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int percent) {
                if (loading == null) return;
                loading.setProgress(percent);
                loading.setVisibility(percent >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                if (safeDestination(request.getUrl().toString())) return false;
                Toast.makeText(CategoryBrowserActivity.this,
                        "Овој линк е надвор од избраниот SETEC каталог.", Toast.LENGTH_SHORT).show();
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                if (loading != null) loading.setVisibility(View.VISIBLE);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame())
                    Toast.makeText(CategoryBrowserActivity.this,
                            "Проблем со мрежата. Притисни ⟳ за повторно вчитување.", Toast.LENGTH_LONG).show();
            }
        });
        frame.addView(browser, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        browser.loadUrl(initial);
    }

    private int dp(int px) { return Math.round(px * getResources().getDisplayMetrics().density); }

    private Button toolbarButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(Color.rgb(180, 221, 255));
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private void loadApprovedCategoryPaths() {
        try (InputStream input = getAssets().open("setec_categories.js");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
            String js = new String(output.toByteArray(), StandardCharsets.UTF_8);
            String marker = "window.SETEC_OFFICIAL_CATEGORIES=";
            int start = js.indexOf(marker);
            int end = js.lastIndexOf(';');
            if (start < 0 || end < start) return;
            JSONArray roots = new JSONArray(js.substring(start + marker.length(), end));
            addApprovedNodes(roots);
        } catch (Exception ignored) {
            // Fail closed: an invalid or missing allowlist never opens a random URL.
        }
    }

    private void addApprovedNodes(JSONArray nodes) {
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject item = nodes.optJSONObject(i);
            if (item == null) continue;
            String slug = item.optString("slug", "");
            if (slug.matches("[a-zA-Z0-9%._-]{3,180}"))
                approvedCategoryPaths.add("/category/" + slug);
            JSONArray children = item.optJSONArray("children");
            if (children != null) addApprovedNodes(children);
        }
    }

    private boolean safeDestination(String raw) {
        if (raw == null) return false;
        try {
            Uri url = Uri.parse(raw);
            if (!"https".equalsIgnoreCase(url.getScheme())) return false;
            if (!"setec.mk".equalsIgnoreCase(url.getHost()) &&
                !"www.setec.mk".equalsIgnoreCase(url.getHost())) return false;
            String path = url.getPath();
            if (path == null) return false;
            if (approvedCategoryPaths.contains(path) || (path.endsWith("/") &&
                approvedCategoryPaths.contains(path.substring(0, path.length()-1)))) return true;
            return path.matches("/products/[a-zA-Z0-9%._-]{3,240}");
        } catch (Exception ignored) { return false; }
    }

    private void goBackOrClose() {
        if (browser != null && browser.canGoBack()) browser.goBack();
        else finish();
    }

    @Override public void onBackPressed() {
        goBackOrClose();
    }

    @Override public void onDestroy() {
        if (browser != null) {
            browser.stopLoading();
            browser.setWebChromeClient(null);
            browser.setWebViewClient(null);
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}
