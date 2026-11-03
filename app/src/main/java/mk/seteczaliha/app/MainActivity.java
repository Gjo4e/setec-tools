package mk.seteczaliha.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** Independent app UI; merchant website is read only in a separate background WebView. */
public final class MainActivity extends ComponentActivity {
    private static final String HOME = "https://www.setec.mk/";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView ui;
    private WebView merchant;
    private String activeQuery = "";
    private String queuedQuery = null;
    private String productUrl = null;
    private boolean merchantLoaded = false;
    private int searchSerial = 0;
    private int availabilitySerial = 0;
    private ActivityResultLauncher<Intent> scannerLauncher;

    @Override @SuppressLint({"SetJavaScriptEnabled","JavascriptInterface"})
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(29,28,32));
        getWindow().setNavigationBarColor(Color.BLACK);
        scannerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                String sku = result.getData().getStringExtra(ScannerActivity.EXTRA_SKU);
                if (sku != null && sku.matches("[0-9]{4,8}")) {
                    js("window.onScanned(" + JSONObject.quote(sku) + ");");
                }
            }
        });
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(247,248,250));
        setContentView(root);

        // Give the hidden website a real viewport; 1dp widths break responsive dropdowns.
        merchant = new WebView(this);
        merchant.setAlpha(0.01f);
        merchant.setClickable(false);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(-1, -1);
        root.addView(merchant, mp);
        WebSettings sourceSettings = merchant.getSettings();
        sourceSettings.setJavaScriptEnabled(true);
        sourceSettings.setDomStorageEnabled(true);
        sourceSettings.setLoadsImagesAutomatically(true);
        sourceSettings.setBlockNetworkImage(false);
        merchant.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                merchantLoaded = false;
            }
            @Override public void onPageFinished(WebView view, String url) {
                merchantLoaded = true;
                if (productUrl != null && url.startsWith(productUrl)) {
                    readProduct();
                    handler.postDelayed(() -> { if (productUrl != null && url.equals(productUrl)) readProduct(); }, 1400);
                    handler.postDelayed(() -> { if (productUrl != null && url.equals(productUrl)) readProduct(); }, 3000);
                    int serial = ++availabilitySerial;
                    handler.postDelayed(() -> attemptGodi(serial, url), 1300);
                } else if (queuedQuery != null && isHome(url)) {
                    String q = queuedQuery; queuedQuery = null;
                    runSearch(q);
                } else if (productUrl == null && !activeQuery.isEmpty() && !isHome(url)) {
                    scheduleResults(searchSerial);
                }
            }
        });

        ui = new WebView(this);
        ui.setBackgroundColor(Color.rgb(247,248,250));
        ui.getSettings().setJavaScriptEnabled(true);
        ui.getSettings().setDomStorageEnabled(true);
        ui.getSettings().setAllowFileAccess(true);
        ui.addJavascriptInterface(new UiBridge(), "SetecApp"); // ONLY loaded in local trusted ui.html
        root.addView(ui, new FrameLayout.LayoutParams(-1, -1));
        ui.loadUrl("file:///android_asset/ui.html");
        merchant.loadUrl(HOME);
    }

    private static boolean isHome(String url) {
        try {
            Uri u = Uri.parse(url);
            return ("www.setec.mk".equals(u.getHost()) || "setec.mk".equals(u.getHost()))
                    && (u.getPath() == null || "/".equals(u.getPath()));
        } catch (Exception e) { return false; }
    }
    private boolean safeProduct(String url) {
        try {
            Uri u = Uri.parse(url);
            return "https".equalsIgnoreCase(u.getScheme()) &&
                    ("setec.mk".equalsIgnoreCase(u.getHost()) || "www.setec.mk".equalsIgnoreCase(u.getHost()))
                    && u.getPath() != null && u.getPath().startsWith("/products/");
        } catch (Exception e) { return false; }
    }
    private void js(String command) {
        if (ui != null) ui.evaluateJavascript(command, null);
    }
    private final class UiBridge {
        @JavascriptInterface public void search(String query) {
            runOnUiThread(() -> startSearch(query));
        }
        @JavascriptInterface public void openProduct(String url) {
            runOnUiThread(() -> loadProduct(url));
        }
        @JavascriptInterface public void openOfficial(String url) {
            runOnUiThread(() -> {
                if (safeProduct(url)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                    catch (Exception e) { Toast.makeText(MainActivity.this, "Не може да се отвори Setec", Toast.LENGTH_SHORT).show(); }
                }
            });
        }
        @JavascriptInterface public void scan() {
            runOnUiThread(MainActivity.this::startScanner);
        }
        @JavascriptInterface public void refresh(String query) {
            runOnUiThread(() -> {
                activeQuery = query == null ? "" : query.trim();
                productUrl = null;
                queuedQuery = activeQuery;
                searchSerial++;
                js("window.showLoading('Освежувам податоци од Setec...');");
                merchant.loadUrl(HOME);
            });
        }
    }
    private void startScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            scannerLauncher.launch(new Intent(this, ScannerActivity.class));
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 220);
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if (requestCode == 220) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startScanner();
            else Toast.makeText(this, "Дозволи камера за скенирање", Toast.LENGTH_LONG).show();
        }
    }
    private void startSearch(String query) {
        if (merchant == null) return;
        activeQuery = (query == null ? "" : query.trim()).replaceAll("[\\r\\n]", " ").substring(0, Math.min(90,(query == null ? "" : query.trim()).length()));
        productUrl = null;
        int serial = ++searchSerial;
        if (activeQuery.isEmpty()) {
            js("window.showResults('[]',true);");
            return;
        }
        js("window.showLoading('Пребарувам на Setec.mk...');");
        if (!merchantLoaded || !isHome(merchant.getUrl())) {
            queuedQuery = activeQuery;
            merchant.loadUrl(HOME);
            return;
        }
        runSearch(activeQuery);
    }
    private void runSearch(String query) {
        if (merchant == null || productUrl != null) return;
        final int serial = searchSerial;
        String q = JSONObject.quote(query);
        String code = "(function(){"+
            "let input=[...document.querySelectorAll('input')].find(x=>/пребарај|search/i.test(x.placeholder||'')||x.type==='search');"+
            "if(!input)return 'no-input';"+
            "let setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;setter.call(input,"+q+");"+
            "input.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:"+q+"}));"+
            "input.dispatchEvent(new Event('change',{bubbles:true}));"+
            "let f=input.closest('form');"+
            "if(f){try{f.requestSubmit()}catch(e){}}else{let b=input.parentElement&&input.parentElement.querySelector('button');if(b)b.click();else input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));}"+
            "return 'submitted';})()";
        merchant.evaluateJavascript(code, v -> {});
        scheduleResults(serial);
    }
    private void scheduleResults(int serial) {
        for (long delay : new long[]{600, 1500, 3000, 5200}) {
            handler.postDelayed(() -> {
                if (serial == searchSerial && productUrl == null) readResults(serial);
            }, delay);
        }
    }
    private void readResults(int serial) {
        if (merchant == null) return;
        String query = activeQuery;
        String script = "(function(){let found=new Map();let q="+ JSONObject.quote(query.toLowerCase(Locale.ROOT)) +";"+
            "for(let a of document.querySelectorAll('a[href*=\"/products/\"]')){"+
            "let u;try{u=new URL(a.getAttribute('href'),location.href)}catch(e){continue}"+
            "if(!['setec.mk','www.setec.mk'].includes(u.hostname)||found.has(u.href))continue;"+
            "let p=a.closest('article,li,[class*=\"product\"],[class*=\"Product\"],[class*=\"card\"],[class*=\"Card\"]')||a.parentElement||a;"+
            "let t=p.querySelector('h2,h3,h4,[class*=\"title\"],[class*=\"name\"]');"+
            "let name=(t?.innerText||a.title||a.innerText||u.pathname.split('/').pop().replace(/-/g,' ')).trim().replace(/\\s+/g,' ').slice(0,200);"+
            "let txt=(p.innerText||'').replace(/\\s+/g,' ');"+
            "let m=txt.match(/(?:Шифра|SKU)\\s*:?\\s*([0-9]{4,8})/i);"+
            "let sku=m?m[1]:'';"+
            "if(!/^\\d+$/.test(q) && q && !q.split(/\\s+/).every(w=>(name+' '+txt).toLowerCase().includes(w)))continue;"+
            "let image=p.querySelector('img');let src=image?.currentSrc||image?.src||image?.getAttribute('data-src')||'';"+
            "found.set(u.href,{url:u.href,name:name,sku:sku,img:src});if(found.size>=50)break}"+
            "return JSON.stringify([...found.values()]);})()";
        merchant.evaluateJavascript(script, encoded -> {
            if (serial != searchSerial || productUrl != null) return;
            String json = decodeJsString(encoded);
            if (json == null) json = "[]";
            js("window.showResults(" + JSONObject.quote(json) + ",false);");
        });
    }
    private static String decodeJsString(String encoded) {
        if (encoded == null || "null".equals(encoded)) return null;
        try { return new JSONArray("[" + encoded + "]").getString(0); }
        catch (Exception e) { return null; }
    }
    private void loadProduct(String url) {
        if (!safeProduct(url)) return;
        productUrl = url;
        searchSerial++;
        availabilitySerial++;
        js("window.showProductLoading();");
        merchant.loadUrl(url);
    }
    private void readProduct() {
        if (merchant == null || productUrl == null) return;
        String script="(function(){let title=document.querySelector('h1')?.innerText||document.querySelector('meta[property=\"og:title\"]')?.content||document.title;"+
            "let b=document.body?.innerText||'';let match=b.match(/Шифра\\s*:?\\s*([0-9]{4,8})/i);"+
            "let img=document.querySelector('meta[property=\"og:image\"]')?.content||'';"+
            "if(!img)img=[...document.images].find(x=>x.naturalWidth>=170&&x.naturalHeight>=170)?.src||'';"+
            "return JSON.stringify({name:title.trim(),sku:match?match[1]:'',img:img,url:location.href});})()";
        merchant.evaluateJavascript(script, encoded -> {
            String json = decodeJsString(encoded);
            if (json != null) js("window.showProduct("+ JSONObject.quote(json) + ");");
        });
    }

    /**
     * Read the same product's visible availability dropdown once. Never infer
     * absence from a missing menu, failed click or a partial list.
     */
    /** Open once; record which visible elements existed before the dropdown opened. */
    private void attemptGodi(int serial, String url) {
        if (merchant == null || serial != availabilitySerial || !url.equals(productUrl)) return;
        js("window.showGodiStatus('checking');");
        String clickScript = "(function(){\n try{\n const elements=[...document.querySelectorAll('button,[role=\"button\"]')];\n const visible=e=>{const r=e.getBoundingClientRect();const s=getComputedStyle(e);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';};\n const b=elements.find(e=>visible(e)&&/провери\\s*достапност/i.test((e.innerText||e.textContent||'').trim()));\n if(!b)return 'missing-button';\n const set=new WeakSet();\n for(const e of document.querySelectorAll('body *'))if(visible(e))set.add(e);\n window.__setecStockBaseline=set;\n window.__setecStockOpenedAt=Date.now();\n b.click();\n return 'clicked';\n }catch(e){return 'click-error'}\n})()";
        merchant.evaluateJavascript(clickScript, reply -> {
            if (serial != availabilitySerial || !url.equals(productUrl)) return;
            for (long wait : new long[]{500, 1300, 2800, 4700, 7000}) {
                handler.postDelayed(() -> readGodi(serial, url, wait == 7000), wait);
            }
        });
    }

    /** Check only store labels newly exposed by the availability dropdown. */
    private void readGodi(int serial, String url, boolean finalAttempt) {
        if (merchant == null || serial != availabilitySerial || !url.equals(productUrl)) return;
        String script = "(function(){\n try{\n const visible=e=>{let r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none';};\n const baseline=window.__setecStockBaseline;\n if(!baseline)return 'unknown';\n let all=[...document.querySelectorAll('body *')], fresh=[];\n for(const e of all){\n   if(baseline.has(e)||!visible(e))continue;\n   let text=(e.innerText||e.textContent||'').replace(/\\s+/g,' ').trim();\n   if(text.length<2||text.length>90)continue;\n   let leaves=[...e.children].filter(visible);\n   if(leaves.some(c=>((c.innerText||c.textContent||'').replace(/\\s+/g,' ').trim())===text))continue;\n   fresh.push(text);\n }\n fresh=[...new Set(fresh)];\n if(fresh.some(t=>/^(?:(?:сетек|setec)\\s*)?годи$/i.test(t)||/^(?:сетек\\s*)?годи\\s*[-–]/i.test(t)))return 'yes';\n const stores=fresh.filter(t=>/^(?:сетек\\s*)?(?:чаир|гостивар|охрид|тетово|битола|велес|гтц|радовиш|куманово|штип|аеродром|драчево|скопје|прилеп|струмица|кисела вода)$/i.test(t));\n if(stores.length>=2)return 'no';\n return 'unknown';\n }catch(e){return 'unknown'}\n})()";
        merchant.evaluateJavascript(script, encoded -> {
            if (serial != availabilitySerial || !url.equals(productUrl)) return;
            String state = decodeJsString(encoded);
            if ("yes".equals(state) || "no".equals(state)) {
                availabilitySerial++;
                js("window.showGodiStatus(" + JSONObject.quote(state) + ");");
            } else if (finalAttempt) {
                js("window.showGodiStatus('unknown');");
            }
        });
    }
    @Override public void onBackPressed() {
        js("window.backToResults();");
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (ui != null) {ui.removeJavascriptInterface("SetecApp");ui.destroy();ui=null;}
        if (merchant != null) {merchant.destroy();merchant=null;}
        super.onDestroy();
    }
}
