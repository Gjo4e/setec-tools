package mk.seteczaliha.app;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import androidx.activity.ComponentActivity;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Independent optional PC builder. It never touches MainActivity's scanner,
 * search browser, Godi lookup, preferences, or saved code list.
 *
 * Stock candidates are NOT surfaced as selectable items until an official
 * Setec product-page "Провери Достапност" menu reveals a shop name.
 * Unverifiable stock is omitted, not interpreted as "out of stock".
 */
public final class PcConfiguratorActivity extends ComponentActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView ui, catalog, checker;
    private int searchSerial = 0, stockSerial = 0, stockIndex = 0, stockTotal = 0, stockAvailable = 0;
    private JSONArray stockQueue = new JSONArray();
    private JSONObject current;
    private String currentUrl;
    private boolean checkComplete = true, stockClicked = false;

    @Override @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        getWindow().setStatusBarColor(Color.rgb(29, 28, 32));
        getWindow().setNavigationBarColor(Color.BLACK);
        FrameLayout root = new FrameLayout(this);
        setContentView(root);
        catalog = makeWeb();
        checker = makeWeb();
        root.addView(catalog, new FrameLayout.LayoutParams(-1, -1));
        root.addView(checker, new FrameLayout.LayoutParams(-1, -1));
        catalog.setAlpha(0.01f);
        checker.setAlpha(0.01f);
        catalog.setClickable(false);
        checker.setClickable(false);
        catalog.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                int id = searchSerial;
                for (long delay : new long[]{650, 1600, 3100, 5100}) {
                    handler.postDelayed(() -> readCatalog(id), delay);
                }
            }
        });
        checker.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (currentUrl == null || !url.startsWith(currentUrl) || checkComplete) return;
                int id = stockSerial;
                handler.postDelayed(() -> beginStockProbe(id), 700);
            }
        });
        ui = makeWeb();
        ui.setBackgroundColor(Color.rgb(248, 249, 251));
        ui.addJavascriptInterface(new Bridge(), "PcApp");
        root.addView(ui, new FrameLayout.LayoutParams(-1, -1));
        ui.loadUrl("file:///android_asset/pc.html");
    }

    private WebView makeWeb() {
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        return w;
    }

    private void send(String command) {
        if (ui != null) ui.evaluateJavascript(command, null);
    }

    private static boolean safeProduct(String url) {
        try {
            Uri u = Uri.parse(url);
            return "https".equals(u.getScheme()) &&
                ("setec.mk".equals(u.getHost()) || "www.setec.mk".equals(u.getHost())) &&
                u.getPath() != null && u.getPath().startsWith("/products/");
        } catch (Exception ex) { return false; }
    }

    private static String decode(String encoded) {
        if (encoded == null || "null".equals(encoded)) return null;
        try { return new JSONArray("[" + encoded + "]").getString(0); }
        catch (Exception ex) { return null; }
    }

    private final class Bridge {
        @JavascriptInterface public void close() {
            runOnUiThread(PcConfiguratorActivity.this::finish);
        }
        @JavascriptInterface public void search(String category, String term, int page) {
            runOnUiThread(() -> startSearch(category, term, page));
        }
        @JavascriptInterface public void verify(String json) {
            runOnUiThread(() -> startVerification(json));
        }
    }

    private void startSearch(String category, String text, int page) {
        if (catalog == null) return;
        searchSerial++;
        stockSerial++;
        checkComplete = true;
        currentUrl = null;
        String query = text == null ? "" : text.replaceAll("[\\r\\n]", " ").trim();
        if (query.length() > 75) query = query.substring(0, 75);
        int safePage = Math.max(1, Math.min(75, page));
        String address = Uri.parse("https://www.setec.mk/search-result").buildUpon()
            .appendQueryParameter("q", query)
            .appendQueryParameter("page", String.valueOf(safePage))
            .build().toString();
        send("window.pcLoading(" + JSONObject.quote(query) + "," + safePage + ");");
        catalog.stopLoading();
        catalog.loadUrl(address);
        final int id = searchSerial;
        handler.postDelayed(() -> {
            if (id == searchSerial) send("window.pcSearchTimeout();");
        }, 13000);
    }

    private void readCatalog(int serial) {
        if (serial != searchSerial || catalog == null) return;
        String script = "(function(){let out=new Map();"+
            "for(let a of document.querySelectorAll('a[href*=\"/products/\"]')){"+
            "let u;try{u=new URL(a.getAttribute('href'),location.href)}catch(e){continue}"+
            "if(!['setec.mk','www.setec.mk'].includes(u.hostname)||out.has(u.href))continue;"+
            "let p=a.closest('article,li,[class*=\"product\"],[class*=\"Product\"],[class*=\"card\"],[class*=\"Card\"]')||a.parentElement||a;"+
            "let t=p.querySelector('h2,h3,h4,[class*=\"title\"],[class*=\"name\"]');"+
            "let name=(t?.innerText||a.title||a.innerText||u.pathname.split('/').pop().replace(/-/g,' ')).trim().replace(/\\s+/g,' ').slice(0,220);"+
            "let body=(p.innerText||'').replace(/\\s+/g,' ');"+
            "let sku=(body.match(/(?:Шифра|SKU)\\s*:?\\s*([0-9]{4,8})/i)||[])[1]||'';"+
            "let price=(key)=>{let m=body.match(new RegExp(key+'\\\\s*:\\\\s*([0-9][0-9., ]*)\\\\s*ден','i'));return m?m[1].trim().replace(/\\s/g,''):''};"+
            "out.set(u.href,{name:name,url:u.href,sku:sku,clubPrice:price('Клуб цена'),regularPrice:price('Редовна цена')});"+
            "if(out.size>=70)break;}return JSON.stringify([...out.values()]);})()";
        catalog.evaluateJavascript(script, output -> {
            if (serial != searchSerial) return;
            String result = decode(output);
            if (result != null) send("window.pcCandidates(" + JSONObject.quote(result) + ");");
        });
    }

    private void startVerification(String json) {
        stockSerial++;
        int id = stockSerial;
        stockIndex = 0;
        stockAvailable = 0;
        stockQueue = new JSONArray();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < Math.min(arr.length(), 22); i++) {
                JSONObject p = arr.optJSONObject(i);
                if (p != null && safeProduct(p.optString("url"))) stockQueue.put(p);
            }
        } catch (Exception ignored) {}
        stockTotal = stockQueue.length();
        send("window.pcProgress(0," + stockTotal + ",0);");
        nextVerification(id);
    }

    private void nextVerification(int serial) {
        if (serial != stockSerial || checker == null) return;
        if (stockIndex >= stockTotal) {
            send("window.pcVerificationDone();");
            return;
        }
        current = stockQueue.optJSONObject(stockIndex);
        stockIndex++;
        if (current == null) { nextVerification(serial); return; }
        currentUrl = current.optString("url");
        if (!safeProduct(currentUrl)) { nextVerification(serial); return; }
        checkComplete = false;
        stockClicked = false;
        checker.stopLoading();
        checker.loadUrl(currentUrl);
        final String expected = currentUrl;
        handler.postDelayed(() -> {
            if (serial == stockSerial && !checkComplete && expected.equals(currentUrl))
                finishVerification(serial, null);
        }, 10500);
    }

    private void beginStockProbe(int serial) {
        if (serial != stockSerial || checkComplete || stockClicked || checker == null) return;
        stockClicked = true;
        // A shop must appear after the official availability control is opened.
        // Existing visible navigation / footer shop names are ignored.
        String click = "(function(){try{"+
            "let visible=e=>{let r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none'};"+
            "let baseline=new WeakSet();for(let e of document.querySelectorAll('body *'))if(visible(e))baseline.add(e);"+
            "window.__pcBefore=baseline;"+
            "let btn=[...document.querySelectorAll('button,[role=\"button\"],a')].find(e=>visible(e)&&/провери\\s*достапност/i.test((e.innerText||e.textContent||'').trim()));"+
            "if(!btn)return 'missing';btn.click();return 'clicked'"+
            "}catch(e){return 'error'}})()";
        checker.evaluateJavascript(click, ignored -> {});
        for (long ms : new long[]{950, 2250, 4050, 6200}) {
            handler.postDelayed(() -> probeStock(serial, ms == 6200), ms);
        }
    }

    private void probeStock(int serial, boolean last) {
        if (serial != stockSerial || checkComplete || checker == null) return;
        String extraction = "(function(){try{"+
            "let base=window.__pcBefore;let visible=e=>{let r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none'};"+
            "let newly=[];if(base)for(let e of document.querySelectorAll('body *')){"+
            "if(base.has(e)||!visible(e))continue;let t=(e.innerText||e.textContent||'').replace(/\\s+/g,' ').trim();"+
            "if(t.length<2||t.length>105)continue;"+
            "if([...e.children].some(c=>visible(c)&&(c.innerText||c.textContent||'').trim()===t))continue;newly.push(t)}"+
            "let pattern=/(?:Годи|ГТЦ|Рузвелтова|Главен\\s+магацин|магацин|Аеродром|Чаир|Кисела\\s+Вода|Карпош|Битола|Тетово|Охрид|Велес|Куманово|Штип|Струмица|Прилеп|Гостивар|Радовиш|Драчево|Скопје|Ѓорче|Кавадарци|Кочани|Неготино|Кичево|Струга|Ресен)/i;"+
            "let stores=[...new Set(newly.filter(t=>pattern.test(t)&&t.length<75&&!/избери|провери|продавниц|достапност/i.test(t)))].slice(0,35);"+
            "let text=document.body?.innerText||'';let title=document.querySelector('h1')?.innerText||'';"+
            "let price=k=>{let m=text.match(new RegExp(k+'\\\\s*:\\\\s*([0-9][0-9., ]*)\\\\s*ден','i'));return m?m[1].replace(/\\s/g,''):''};"+
            "let sku=(text.match(/Шифра\\s*:?\\s*([0-9]{4,8})/i)||[])[1]||'';"+
            "return JSON.stringify({stores,name:title.trim(),sku,clubPrice:price('Клуб цена'),regularPrice:price('Редовна цена'),specs:text.slice(0,4200)});"+
            "}catch(e){return '{}'}})()";
        checker.evaluateJavascript(extraction, output -> {
            if (serial != stockSerial || checkComplete) return;
            JSONObject data = null;
            try { String text = decode(output); if (text != null) data = new JSONObject(text); }
            catch (Exception ignored) {}
            if (data != null && data.optJSONArray("stores") != null && data.optJSONArray("stores").length() > 0) {
                finishVerification(serial, data);
            } else if (last) {
                finishVerification(serial, null);
            }
        });
    }

    private void finishVerification(int serial, JSONObject detail) {
        if (serial != stockSerial || checkComplete) return;
        checkComplete = true;
        if (detail != null && current != null) {
            try {
                JSONObject verified = new JSONObject(current.toString());
                if (!detail.optString("name").isEmpty()) verified.put("name", detail.optString("name"));
                for (String key : new String[]{"sku", "clubPrice", "regularPrice", "specs"}) {
                    if (!detail.optString(key).isEmpty()) verified.put(key, detail.optString(key));
                }
                verified.put("stores", detail.optJSONArray("stores"));
                verified.put("stockVerified", true);
                stockAvailable++;
                send("window.pcVerifiedProduct(" + JSONObject.quote(verified.toString()) + ");");
            } catch (Exception ignored) {}
        }
        send("window.pcProgress(" + stockIndex + "," + stockTotal + "," + stockAvailable + ");");
        handler.postDelayed(() -> nextVerification(serial), 100);
    }

    @Override protected void onDestroy() {
        searchSerial++;
        stockSerial++;
        handler.removeCallbacksAndMessages(null);
        if (ui != null) { ui.removeJavascriptInterface("PcApp"); ui.destroy(); ui = null; }
        if (catalog != null) { catalog.destroy(); catalog = null; }
        if (checker != null) { checker.destroy(); checker = null; }
        super.onDestroy();
    }
}
