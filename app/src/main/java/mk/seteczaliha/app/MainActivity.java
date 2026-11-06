package mk.seteczaliha.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** Independent app UI; merchant website is read only in a separate background WebView. */
public final class MainActivity extends ComponentActivity {
    private static final String HOME = "https://www.setec.mk/";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView ui;
    private WebView merchant;
    private WebView batchMerchant;
    private WebView categoryMerchant;
    private int categorySerial=0;
    private String categoryUrl="";
    private boolean batchMerchantLoaded = false;
    private String batchCurrentSku = null;
    private int batchSerial = 0;
    private boolean nextScanBatch = false;
    private String activeQuery = "";
    private String queuedQuery = null;
    private String productUrl = null;
    private boolean merchantLoaded = false;
    private int searchSerial = 0;
    private boolean searchFound=false;
    private boolean moreSearchPending = false;
    private int morePageIndex = 1;
    private final java.util.HashSet<String> moreSeenUrls = new java.util.HashSet<>();
    private int availabilitySerial = 0;
    private ActivityResultLauncher<Intent> scannerLauncher;
    private ActivityResultLauncher<Intent> dataMatrixLauncher;

    @Override @SuppressLint({"SetJavaScriptEnabled","JavascriptInterface"})
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(8,13,20));
        getWindow().setNavigationBarColor(Color.BLACK);
        scannerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                String sku = result.getData().getStringExtra(ScannerActivity.EXTRA_SKU);
                if (sku != null && sku.matches("[0-9]{4,8}")) {
                    js((nextScanBatch ? "window.onBatchScanned(" : "window.onScanned(") + JSONObject.quote(sku) + ");");
                    nextScanBatch = false;
                }
            }
        });
        dataMatrixLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if(result.getResultCode()==RESULT_OK && result.getData()!=null){
                String payload=result.getData().getStringExtra("data_matrix_payload");
                if(payload!=null && !payload.trim().isEmpty()){
                    nextScanBatch=false;
                    js("window.onDataMatrixScanned("+JSONObject.quote(payload.trim())+");");
                } else {
                    String sku=result.getData().getStringExtra(ScannerActivity.EXTRA_SKU);
                    if(sku!=null && sku.matches("[0-9]{4,8}"))
                        js("window.onScanned("+JSONObject.quote(sku)+");");
                }
            }
        });
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8,13,20));
        // Android 15 edge-to-edge: keep the header below status icons and bottom dock above system navigation.
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
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
                } else if (moreSearchPending && productUrl == null) {
                    // Pagination can navigate and discard the JavaScript click callback.
                    // The independent more-results poller remains responsible for updates.
                } else if (productUrl == null && !activeQuery.isEmpty() && !isHome(url)) {
                    scheduleResults(searchSerial);
                }
            }
        });

        // Dedicated secondary merchant browser for bulk SKU name lookups.
        // Never navigates the original single-product merchant WebView.
        batchMerchant = new WebView(this);
        batchMerchant.setAlpha(0.01f);
        batchMerchant.setClickable(false);
        WebSettings batchSettings = batchMerchant.getSettings();
        batchSettings.setJavaScriptEnabled(true);
        batchSettings.setDomStorageEnabled(true);
        batchSettings.setLoadsImagesAutomatically(false);
        batchMerchant.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                batchMerchantLoaded = false;
            }
            @Override public void onPageFinished(WebView view, String url) {
                batchMerchantLoaded = true;
                if (batchCurrentSku == null) return;
                int serial = batchSerial;
                String sku = batchCurrentSku;
                if (isHome(url)) {
                    handler.postDelayed(() -> submitBatchSearch(sku, serial, 0), 500);
                } else {
                    scheduleBatchResults(sku, serial);
                }
            }
        });
        root.addView(batchMerchant, new FrameLayout.LayoutParams(-1, -1));
        batchMerchant.loadUrl(HOME);

        // Isolated catalog browser: read actual SETEC category links, products and pagination.
        categoryMerchant = new WebView(this);
        categoryMerchant.setAlpha(0.01f);
        categoryMerchant.setClickable(false);
        categoryMerchant.getSettings().setJavaScriptEnabled(true);
        categoryMerchant.getSettings().setDomStorageEnabled(true);
        categoryMerchant.getSettings().setLoadsImagesAutomatically(false);
        categoryMerchant.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){
                if(!safeCategoryUrl(url)||!url.equals(categoryUrl))return;
                int serial=categorySerial;
                for(long delay:new long[]{650,1700,3500}){
                    handler.postDelayed(()->readCategoryPage(serial,url),delay);
                }
            }
        });
        root.addView(categoryMerchant,new FrameLayout.LayoutParams(-1,-1));

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
        @JavascriptInterface public void openReleasePage() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Gjo4e/setec-zaliha/actions"));
                    startActivity(intent);
                } catch (Exception ex) {
                    Toast.makeText(MainActivity.this, "Не може да се отвори GitHub.", Toast.LENGTH_SHORT).show();
                }
            });
        }
        @JavascriptInterface public void setTheme(String theme) {
            getSharedPreferences("setec_preferences",MODE_PRIVATE).edit().putBoolean("dark_theme","dark".equals(theme)).apply();
        }
        @JavascriptInterface public void openCompare() {
            runOnUiThread(() -> startActivity(new Intent(MainActivity.this, CompareModelsActivity.class)));
        }
        @JavascriptInterface public void openPcConfigurator() {
            runOnUiThread(() -> startActivity(new Intent(MainActivity.this, PcConfiguratorActivity.class)));
        }
        @JavascriptInterface public void openModelScanner() {
            runOnUiThread(() -> dataMatrixLauncher.launch(new Intent(MainActivity.this, DataMatrixScannerActivity.class)));
        }
        @JavascriptInterface public void moreResults() {
            runOnUiThread(MainActivity.this::startMoreResults);
        }
        @JavascriptInterface public void search(String query) {
            runOnUiThread(() -> startSearch(query));
        }
        @JavascriptInterface public void openProduct(String url) {
            runOnUiThread(() -> loadProduct(url));
        }
        @JavascriptInterface public void loadCategory(String url) {
            runOnUiThread(()->startCategoryPage(url));
        }
        @JavascriptInterface public void filterCategory(String name) {
            runOnUiThread(() -> selectCategoryFilter(name));
        }
        @JavascriptInterface public void openCategory(String url) {
            runOnUiThread(() -> {
                try {
                    Uri u = Uri.parse(url);
                    String path = u.getPath();
                    boolean safe = "https".equalsIgnoreCase(u.getScheme())
                            && ("setec.mk".equalsIgnoreCase(u.getHost()) || "www.setec.mk".equalsIgnoreCase(u.getHost()))
                            && path != null && ("/category".equals(path) || path.startsWith("/category/"));
                    if (safe) startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Не може да се отвори категоријата.", Toast.LENGTH_SHORT).show();
                }
            });
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
            runOnUiThread(() -> {
                nextScanBatch = false;
                startScanner();
            });
        }
        @JavascriptInterface public void scanBatch() {
            runOnUiThread(() -> {
                nextScanBatch = true;
                startScanner();
            });
        }
        @JavascriptInterface public void saveBatch(String json) {
            if (json != null && json.length() < 180000)
                getSharedPreferences("bulk_scans", MODE_PRIVATE).edit().putString("list", json).apply();
        }
        @JavascriptInterface public String loadBatch() {
            return getSharedPreferences("bulk_scans", MODE_PRIVATE).getString("list", "[]");
        }
        @JavascriptInterface public void copyText(String text) {
            if (text == null || text.length() > 180000) return;
            runOnUiThread(() -> {
                ClipboardManager board = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (board != null) board.setPrimaryClip(ClipData.newPlainText("Setec шифри", text));
                Toast.makeText(MainActivity.this, "Шифрите се копирани", Toast.LENGTH_SHORT).show();
            });
        }
        @JavascriptInterface public void batchLookup(String sku) {
            runOnUiThread(() -> startBatchLookup(sku));
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
    private boolean safeCategoryUrl(String url){
        try{
            Uri u=Uri.parse(url);
            return "https".equalsIgnoreCase(u.getScheme())
                && ("setec.mk".equalsIgnoreCase(u.getHost())||"www.setec.mk".equalsIgnoreCase(u.getHost()))
                && u.getPath()!=null&&(u.getPath().equals("/category")||u.getPath().startsWith("/category/")||u.getPath().equals("/search-result"));
        }catch(Exception ignored){return false;}
    }
    private void startCategoryPage(String url){
        if(!safeCategoryUrl(url)||categoryMerchant==null){
            js("window.onCategoryError('Невалидна категорија.');");return;
        }
        categoryUrl=url;categorySerial++;
        js("window.onCategoryLoading();");
        categoryMerchant.loadUrl(url);
    }
    // Setec renders subcategories as checkboxes rather than <a> links.
    // Activate the real website filter instead of guessing category URLs.
    private void selectCategoryFilter(String name){
        if(categoryMerchant==null||name==null||name.trim().isEmpty()||categoryUrl.isEmpty())return;
        final int serial=categorySerial;
        // Search actual rendered text and clickable ancestors, not only <label>.
        String script="(function(){let target="+JSONObject.quote(name)+";"+
            "let norm=s=>String(s||'').replace(/\\s*\\(\\d+\\)\\s*$/,'').replace(/\\s+/g,' ').trim().toLocaleLowerCase();"+
            "let all=[...document.querySelectorAll('label,button,span,div,p,a,[role=checkbox],input[type=checkbox]')];"+
            "let matches=all.filter(x=>norm(x.innerText||x.textContent||x.getAttribute('aria-label'))===norm(target));"+
            "let el=matches.sort((a,b)=>a.querySelectorAll('*').length-b.querySelectorAll('*').length)[0];"+
            "if(!el)return 'missing';"+
            "let link=el.closest('a[href]')||el.querySelector('a[href]');"+
            "if(link&&link.href&&link.href.startsWith('https://www.setec.mk/category/'))return 'link:'+link.href;"+
            "let row=el.closest('label,[role=checkbox]')||el.parentElement||el;"+
            "let box=row.querySelector('input[type=checkbox],[role=checkbox]')||"+
            "(row.parentElement&&row.parentElement.querySelector('input[type=checkbox],[role=checkbox]'));"+
            "if(box){if(box.type==='checkbox'&&box.checked)return 'selected';box.click()}"+
            "else row.click();return 'selected';})()";
        categoryMerchant.evaluateJavascript(script,result->{
            if(serial!=categorySerial)return;
            String outcome=decodeJsString(result);
            if(outcome!=null&&outcome.startsWith("link:")&&safeCategoryUrl(outcome.substring(5))){
                startCategoryPage(outcome.substring(5));return;
            }
            if("missing".equals(outcome)){
                // Do not strand the user with a missing-filter error. Search Setec's
                // actual catalog for the selected subcategory label instead.
                String url="https://www.setec.mk/search-result?q="+Uri.encode(name.trim());
                categoryUrl=url;
                categorySerial++;
                js("window.onCategorySearchFallback("+JSONObject.quote(url)+","+JSONObject.quote(name)+");");
                categoryMerchant.loadUrl(url);
                return;
            }
            js("window.onCategoryLoading();");
            for(long delay:new long[]{900,2000,3800,6000})
                handler.postDelayed(()->readCategoryPage(serial,categoryUrl),delay);
        });
    }

    private void readCategoryPage(int serial,String url){
        if(serial!=categorySerial||categoryMerchant==null||!url.equals(categoryMerchant.getUrl()))return;
        String script="(function(){"+labelledPricesJs()+
            "let categories=new Map(),products=new Map(),subcategories=[];"+
            "let headings=[...document.querySelectorAll('*')].filter(x=>x.children.length<3&&/^(Подкатегории|Subcategories)$/i.test((x.innerText||'').trim()));"+
            "for(let head of headings){let panel=head.parentElement;for(let d=0;d<5&&panel;d++,panel=panel.parentElement){"+
            "let checks=[...panel.querySelectorAll('input[type=checkbox],[role=checkbox]')];"+
            "if(checks.length>1){for(let check of checks){let row=check.closest('label')||check.parentElement?.parentElement||check.parentElement;"+
            "let n=(row?.innerText||row?.textContent||'').replace(/\\s*\\(\\d+\\)\\s*$/,'').trim();"+
            "if(n.length>1&&n.length<100&&!subcategories.includes(n))subcategories.push(n)}break}}"+
            "if(subcategories.length)break}"+
            "for(let a of document.querySelectorAll('a[href]')){"+
            "let u;try{u=new URL(a.getAttribute('href'),location.href)}catch(e){continue}"+
            "if(!['setec.mk','www.setec.mk'].includes(u.hostname))continue;"+
            "let name=(a.innerText||a.title||a.getAttribute('aria-label')||'').replace(/\\s+/g,' ').trim().slice(0,130);"+
            "if(u.pathname.startsWith('/category/')&&u.href!==location.href&&name.length>1&&name.length<110){"+
            "let holder=a.closest('nav,aside,[class*=category],[class*=Category],[class*=menu],[class*=Menu]');"+
            "if(holder||a.querySelector('img')||a.parentElement?.querySelector('img'))categories.set(u.href,{name:name,url:u.href});"+
            "}"+
            "if(!u.pathname.startsWith('/products/')||products.has(u.href))continue;"+
            "let p=a.closest('article,li,[class*=product],[class*=Product],[class*=card],[class*=Card]')||a.parentElement||a;"+
            "let t=p.querySelector('h2,h3,h4,[class*=title],[class*=name]');"+
            "let productName=(t?.innerText||a.title||a.innerText||u.pathname.split('/').pop().replace(/-/g,' ')).replace(/\\s+/g,' ').trim().slice(0,200);"+
            "let txt=(p.innerText||'').replace(/\\s+/g,' '),prices=setecPrices(txt);"+
            "let m=txt.match(/(?:Шифра|SKU)\\s*:?\\s*([0-9]{4,8})/i);"+
            "let img=p.querySelector('img'),src=img?.currentSrc||img?.src||img?.getAttribute('data-src')||'';"+
            "products.set(u.href,{url:u.href,name:productName,sku:m?m[1]:'',img:src,clubPrice:prices.clubPrice,regularPrice:prices.regularPrice});"+
            "}"+
            "let next='';"+
            "for(let a of document.querySelectorAll('a[href]')){let txt=(a.innerText||a.getAttribute('aria-label')||'').trim();"+
            "if(a.rel==='next'||/^(следна|next|›|»|→)$/i.test(txt)){try{let u=new URL(a.href,location.href);if(u.hostname===location.hostname&&u.pathname===location.pathname&&u.href!==location.href){next=u.href;break}}catch(e){}}}"+
            "if(!next){let current=Number(new URL(location.href).searchParams.get('page')||1);for(let a of document.querySelectorAll('a[href]')){try{let u=new URL(a.href,location.href);if(u.hostname===location.hostname&&u.pathname===location.pathname&&Number(u.searchParams.get('page'))===current+1){next=u.href;break}}catch(e){}}}"+
            "return JSON.stringify({title:document.querySelector('h1')?.innerText||document.title,url:location.href,categories:[...categories.values()].slice(0,350),subcategories:subcategories.slice(0,120),products:[...products.values()].slice(0,150),next:next});})()";
        categoryMerchant.evaluateJavascript(script,encoded->{
            if(serial!=categorySerial||!url.equals(categoryUrl))return;
            String result=decodeJsString(encoded);
            if(result!=null)js("window.onCategoryData("+JSONObject.quote(result)+");");
        });
    }

    private void startSearch(String query) {
        if (merchant == null) return;
        activeQuery = (query == null ? "" : query.trim()).replaceAll("[\\r\\n]", " ").substring(0, Math.min(90,(query == null ? "" : query.trim()).length()));
        productUrl = null;
        moreSearchPending = false;
        morePageIndex = 1;
        int serial = ++searchSerial;
        searchFound=false;
        if (activeQuery.isEmpty()) {
            js("window.showResults('[]',true);");
            return;
        }
        js("window.showLoading('Пребарувам на Setec.mk...');");
        // Navigate directly to the catalog search route. React homepage input
        // is often absent in WebView and must not block all three search tools.
        queuedQuery=null;
        String url="https://www.setec.mk/search-result?q="+android.net.Uri.encode(activeQuery);
        merchant.loadUrl(url);
        scheduleResults(serial);
    }
    private void runSearch(String query) {
        if (merchant == null || productUrl != null || !query.equals(activeQuery)) return;
        trySubmitSearch(query, searchSerial, 0);
    }

    /**
     * Setec's homepage is React-rendered. A WebView onPageFinished event does
     * not guarantee that its search input has mounted yet. Retry readiness,
     * but never submit the same query twice within one search generation.
     */
    private void trySubmitSearch(String query, int serial, int retry) {
        if (merchant == null || serial != searchSerial || productUrl != null ||
                !query.equals(activeQuery)) return;
        if (!merchantLoaded || !isHome(merchant.getUrl())) {
            if (retry < 18) handler.postDelayed(
                () -> trySubmitSearch(query, serial, retry + 1), 400);
            return;
        }
        String q = JSONObject.quote(query);
        String code = "(function(){"+
            "let input=[...document.querySelectorAll('input')].find(x=>"+
            "(/пребарај|search/i.test(x.placeholder||'')||x.type==='search')&&"+
            "x.getBoundingClientRect().width>0);"+
            "if(!input)return 'no-input';"+
            "let setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;"+
            "setter.call(input,"+q+");"+
            "input.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:"+q+"}));"+
            "input.dispatchEvent(new Event('change',{bubbles:true}));"+
            "let f=input.closest('form');"+
            "if(f){try{f.requestSubmit()}catch(e){return 'retry'}}else{"+
            "let b=input.parentElement&&input.parentElement.querySelector('button');"+
            "if(b)b.click();else input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));}"+
            "return 'submitted';})()";
        merchant.evaluateJavascript(code, outcome -> {
            if (serial != searchSerial || productUrl != null || !query.equals(activeQuery)) return;
            String status = decodeJsString(outcome);
            if ("submitted".equals(status)) scheduleResults(serial);
            else if (retry < 18) handler.postDelayed(
                () -> trySubmitSearch(query, serial, retry + 1), 400);
            else { merchant.loadUrl("https://www.setec.mk/search-result?q="+android.net.Uri.encode(query)); scheduleResults(serial); }
        });
    }
    private void scheduleResults(int serial) {
        for (long delay : new long[]{900, 1800, 3000, 4800, 7000, 9500}) {
            handler.postDelayed(() -> {
                if (serial == searchSerial && productUrl == null) readResults(serial);
            }, delay);
        }
    }
    private void readResults(int serial) {
        if (merchant == null || moreSearchPending) return;
        String query = activeQuery;
        String script = "(function(){"+labelledPricesJs()+"let found=new Map();let q="+ JSONObject.quote(query.toLowerCase(Locale.ROOT)) +";"+
            "for(let a of document.querySelectorAll('a[href*=\"/products/\"]')){"+
            "let u;try{u=new URL(a.getAttribute('href'),location.href)}catch(e){continue}"+
            "if(!['setec.mk','www.setec.mk'].includes(u.hostname)||found.has(u.href))continue;"+
            "let p=a.closest('article,li,[class*=\"product\"],[class*=\"Product\"],[class*=\"card\"],[class*=\"Card\"]')||a.parentElement||a;"+
            "let t=p.querySelector('h2,h3,h4,[class*=\"title\"],[class*=\"name\"]');"+
            "let name=(t?.innerText||a.title||a.innerText||u.pathname.split('/').pop().replace(/-/g,' ')).trim().replace(/\\s+/g,' ').slice(0,200);"+
            "let txt=(p.innerText||'').replace(/\\s+/g,' ');let prices=setecPrices(txt);"+
            "let m=txt.match(/(?:Шифра|SKU)\\s*:?\\s*([0-9]{4,8})/i);"+
            "let sku=m?m[1]:'';"+
            "if(/^\\d{4,8}$/.test(q)){if(sku&&sku!==q)continue;}"+
            "else if(/^\\d{13}$/.test(q)){/* Keep website EAN search results: their cards show SKU, not EAN. */}"+
            "else if(q&&!q.split(/\\s+/).every(w=>(name+' '+txt).toLowerCase().includes(w)))continue;"+
            "let image=p.querySelector('img');let src=image?.currentSrc||image?.src||image?.getAttribute('data-src')||'';"+
            "found.set(u.href,{url:u.href,name:name,sku:sku,img:src,clubPrice:prices.clubPrice,regularPrice:prices.regularPrice});if(found.size>=50)break}"+
            "return JSON.stringify([...found.values()]);})()";
        merchant.evaluateJavascript(script, encoded -> {
            if (serial != searchSerial || productUrl != null) return;
            String json = decodeJsString(encoded);
            if (json == null) json = "[]";
            try{if(new JSONArray(json).length()>0)searchFound=true;}catch(Exception ignored){}
            js("window.showResults(" + JSONObject.quote(json) + ",false);");
        });
    }

    /** Only invoked from the new "Прикажи повеќе" control. Original search is unchanged. */
    private void startMoreResults() {
        if (merchant == null || activeQuery.isEmpty() || activeQuery.matches("[0-9]+") ||
                productUrl != null || moreSearchPending) return;
        final int serial = searchSerial;
        moreSearchPending = true;
        final int nextPage = morePageIndex + 1;
        handler.postDelayed(() -> {
            if (serial == searchSerial && moreSearchPending) {
                moreSearchPending = false;
                js("window.showMoreResults('[]');");
            }
        }, 16000);
        String baseline = "(function(){return JSON.stringify([...document.querySelectorAll('a[href*=\\\"/products/\\\"]')].map(a=>{try{return new URL(a.getAttribute('href'),location.href).href}catch(e){return ''}}));})()";
        merchant.evaluateJavascript(baseline, value -> {
            if (serial != searchSerial || !moreSearchPending) return;
            moreSeenUrls.clear();
            try {
                JSONArray old = new JSONArray(decodeJsString(value));
                for (int i=0;i<old.length();i++) moreSeenUrls.add(old.optString(i));
            } catch (Exception ignored) {}
            // Direct URL verified from Setec page 2.
            String pageUrl = Uri.parse("https://www.setec.mk/search-result").buildUpon()
                    .appendQueryParameter("q", activeQuery)
                    .appendQueryParameter("page", String.valueOf(nextPage))
                    .build().toString();
            for (long delay : new long[]{1800, 3500, 6000, 9000, 13500}) {
                handler.postDelayed(() -> readMoreResults(serial, delay == 13500), delay);
            }
            merchant.loadUrl(pageUrl);
        });
    }
    private void readMoreResults(int serial, boolean finalAttempt) {
        if (merchant == null || serial != searchSerial || !moreSearchPending || productUrl != null) return;
        // Never accept stale results from page one.
        int expectedPage = morePageIndex + 1;
        String location = merchant.getUrl();
        if (!merchantLoaded || location == null || !location.contains("page=" + expectedPage)) {
            if (finalAttempt) {
                moreSearchPending = false;
                js("window.showMoreResults('[]');");
            }
            return;
        }
        String script = collectMoreResultScript(activeQuery);
        merchant.evaluateJavascript(script, encoded -> {
            if (serial != searchSerial || !moreSearchPending || productUrl != null) return;
            JSONArray additions = new JSONArray();
            try {
                JSONArray all = new JSONArray(decodeJsString(encoded));
                for (int i=0;i<all.length();i++) {
                    JSONObject p = all.optJSONObject(i);
                    if(p == null)continue;
                    String url = p.optString("url","");
                    if(!url.isEmpty()&&!moreSeenUrls.contains(url)){moreSeenUrls.add(url);additions.put(p);}
                }
            }catch(Exception ignored){}
            if(additions.length()>0 || finalAttempt){
                if(additions.length()>0) morePageIndex++;
                moreSearchPending=false;
                js("window.showMoreResults(" + JSONObject.quote(additions.toString()) + ");");
            }
        });
    }
    private String collectMoreResultScript(String query) {
        return "(function(){"+labelledPricesJs()+"let found=new Map();let q="+ JSONObject.quote(query.toLowerCase(Locale.ROOT)) +";"+
            "for(let a of document.querySelectorAll('a[href*=\"/products/\"]')){"+
            "let u;try{u=new URL(a.getAttribute('href'),location.href)}catch(e){continue}"+
            "if(!['setec.mk','www.setec.mk'].includes(u.hostname)||found.has(u.href))continue;"+
            "let p=a.closest('article,li,[class*=\"product\"],[class*=\"Product\"],[class*=\"card\"],[class*=\"Card\"]')||a.parentElement||a;"+
            "let t=p.querySelector('h2,h3,h4,[class*=\"title\"],[class*=\"name\"]');"+
            "let name=(t?.innerText||a.title||a.innerText||u.pathname.split('/').pop().replace(/-/g,' ')).trim().replace(/\\s+/g,' ').slice(0,200);"+
            "let txt=(p.innerText||'').replace(/\\s+/g,' ');let prices=setecPrices(txt);"+
            "let m=txt.match(/(?:Шифра|SKU)\\s*:?\\s*([0-9]{4,8})/i);"+
            "let sku=m?m[1]:'';"+
            "if(!/^\\d+$/.test(q) && q && !q.split(/\\s+/).every(w=>(name+' '+txt).toLowerCase().includes(w)))continue;"+
            "let image=p.querySelector('img');let src=image?.currentSrc||image?.src||image?.getAttribute('data-src')||'';"+
            "found.set(u.href,{url:u.href,name:name,sku:sku,img:src,clubPrice:prices.clubPrice,regularPrice:prices.regularPrice});if(found.size>=150)break}"+
            "return JSON.stringify([...found.values()]);})()";
    }
    /** Read only price values explicitly labelled by Setec, without estimation. */
    private static String labelledPricesJs() {
        return "function setecPrices(text){"+
            "let club=text.match(/Клуб\\s*цена\\s*:\\s*([0-9][0-9.,\\s]*)\\s*ден\\.?/i);"+
            "let regular=text.match(/Редовна\\s*цена\\s*:\\s*([0-9][0-9.,\\s]*)\\s*ден\\.?/i);"+
            "let fmt=m=>m?m[1].replace(/\\s/g,'').replace(/[.,]$/,''):'';"+
            "return {clubPrice:fmt(club),regularPrice:fmt(regular)};}";
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
        String script="(function(){"+labelledPricesJs()+"let title=document.querySelector('h1')?.innerText||document.querySelector('meta[property=\"og:title\"]')?.content||document.title;"+
            "let b=document.body?.innerText||'';let match=b.match(/Шифра\\s*:?\\s*([0-9]{4,8})/i);"+
            "let node=document.querySelector('h1'),price={clubPrice:'',regularPrice:''};"+
            "for(let i=0;node&&i<7;i++,node=node.parentElement){let p=setecPrices(node.innerText||'');if(p.clubPrice||p.regularPrice){price=p;break}}"+
            "let img=document.querySelector('meta[property=\"og:image\"]')?.content||'';"+
            "if(!img)img=[...document.images].find(x=>x.naturalWidth>=170&&x.naturalHeight>=170)?.src||'';"+
            "return JSON.stringify({name:title.trim(),sku:match?match[1]:'',img:img,url:location.href,clubPrice:price.clubPrice,regularPrice:price.regularPrice});})()";
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
    /** Collect only store names revealed after the official availability button was opened.
     * Missing or incomplete data must remain unconfirmed rather than falsely claiming no stock. */
    private void readGodi(int serial, String url, boolean finalAttempt) {
        if (merchant == null || serial != availabilitySerial || !url.equals(productUrl)) return;
        String script = "(function(){"+
            "try{const visible=e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none'};"+
            "const baseline=window.__setecStockBaseline;if(!baseline)return JSON.stringify({stores:[],complete:false});"+
            "const all=[...document.querySelectorAll('body *')],fresh=[];"+
            "for(const e of all){if(baseline.has(e)||!visible(e))continue;"+
            "let t=(e.innerText||e.textContent||'').replace(/\\s+/g,' ').trim();if(t.length<2||t.length>90)continue;"+
            "if([...e.children].filter(visible).some(x=>(x.innerText||x.textContent||'').replace(/\\s+/g,' ').trim()===t))continue;fresh.push(t)}"+
            "const known=/^(?:(?:сетек|setec)\\s*)?(?:годи|главен\\s*магацин|магацин|чаир|гостивар|охрид|тетово|битола|велес|гтц|радовиш|куманово|штип|аеродром|драчево|скопје|прилеп|струмица|кисела\\s*вода)(?:\\s*[-–:]\\s*.+)?$/i;"+
            "const stores=[...new Set(fresh.filter(t=>known.test(t)))];"+
            "return JSON.stringify({stores:stores,complete:stores.length>0})"+
            "}catch(e){return JSON.stringify({stores:[],complete:false})}})()";
        merchant.evaluateJavascript(script, encoded -> {
            if (serial != availabilitySerial || !url.equals(productUrl)) return;
            String payload = decodeJsString(encoded);
            if (payload == null) {
                if (finalAttempt) js("window.showGodiStatus('unknown');");
                return;
            }
            try {
                JSONObject parsed = new JSONObject(payload);
                JSONArray stores = parsed.optJSONArray("stores");
                if (stores != null && stores.length() > 0) {
                    availabilitySerial++;
                    js("window.showStoreAvailability(" + JSONObject.quote(payload) + ");");
                } else if (finalAttempt) {
                    js("window.showStoreAvailability(" + JSONObject.quote(payload) + ");");
                }
            } catch (Exception ignored) {
                if (finalAttempt) js("window.showGodiStatus('unknown');");
            }
        });
    }

    /**
     * Independent bulk-lookup browser. Only the text and a possible matching
     * model name are sent to the list page. No stock claims are generated here.
     */
    private void startBatchLookup(String sku) {
        if (batchMerchant == null || sku == null || !sku.matches("[0-9]{4,8}")) return;
        batchCurrentSku = sku;
        batchSerial++;
        batchMerchantLoaded = false;
        batchMerchant.stopLoading();
        batchMerchant.loadUrl(HOME);
    }
    private void submitBatchSearch(String sku, int serial, int attempt) {
        if (batchMerchant == null || serial != batchSerial || !sku.equals(batchCurrentSku)) return;
        if (!batchMerchantLoaded || !isHome(batchMerchant.getUrl())) {
            if (attempt < 12) handler.postDelayed(() -> submitBatchSearch(sku,serial,attempt+1), 450);
            return;
        }
        String q = JSONObject.quote(sku);
        String code = "(function(){"+
            "let input=[...document.querySelectorAll('input')].find(x=>/пребарај|search/i.test(x.placeholder||'')||x.type==='search');"+
            "if(!input)return 'no-input';"+
            "let setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;setter.call(input,"+q+");"+
            "input.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:"+q+"}));"+
            "input.dispatchEvent(new Event('change',{bubbles:true}));"+
            "let f=input.closest('form');"+
            "if(f){try{f.requestSubmit()}catch(e){}}else{let b=input.parentElement&&input.parentElement.querySelector('button');if(b)b.click();else input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));}"+
            "return 'submitted';})()";
        batchMerchant.evaluateJavascript(code, result -> {
            if (serial != batchSerial || !sku.equals(batchCurrentSku)) return;
            if ("submitted".equals(decodeJsString(result))) scheduleBatchResults(sku,serial);
            else if (attempt < 12) handler.postDelayed(() -> submitBatchSearch(sku,serial,attempt+1),450);
            else deliverBatchResult(sku,serial,"[]");
        });
    }
    private void scheduleBatchResults(String sku, int serial) {
        for (long delay : new long[]{900, 2000, 3500, 5700, 8500}) {
            handler.postDelayed(() -> readBatchResults(sku, serial, delay == 8500), delay);
        }
    }
    private void readBatchResults(String sku, int serial, boolean finalRead) {
        if (batchMerchant == null || serial != batchSerial || !sku.equals(batchCurrentSku)) return;
        String script = batchResultScript(sku);
        batchMerchant.evaluateJavascript(script, encoded -> {
            if (serial != batchSerial || !sku.equals(batchCurrentSku)) return;
            String json = decodeJsString(encoded);
            if (json != null && json.startsWith("[") && json.length() > 2) {
                deliverBatchResult(sku,serial,json);
            } else if (finalRead) {
                deliverBatchResult(sku,serial,"[]");
            }
        });
    }
    private String batchResultScript(String query) {
        String script = "(function(){"+labelledPricesJs()+"let found=new Map();let q="+JSONObject.quote(query)+";"+
            "for(let a of document.querySelectorAll('a[href*=\\\"/products/\\\"]')){"+
            "let u;try{u=new URL(a.getAttribute('href'),location.href)}catch(e){continue}"+
            "if(!['setec.mk','www.setec.mk'].includes(u.hostname)||found.has(u.href))continue;"+
            "let p=a.closest('article,li,[class*=\\\"product\\\"],[class*=\\\"Product\\\"],[class*=\\\"card\\\"],[class*=\\\"Card\\\"]')||a.parentElement||a;"+
            "let t=p.querySelector('h2,h3,h4,[class*=\\\"title\\\"],[class*=\\\"name\\\"]');"+
            "let name=(t?.innerText||a.title||a.innerText||u.pathname.split('/').pop().replace(/-/g,' ')).trim().replace(/\\s+/g,' ').slice(0,200);"+
            "let txt=(p.innerText||'').replace(/\\s+/g,' ');let prices=setecPrices(txt);"+
            "let m=txt.match(/(?:Шифра|SKU)\\s*:?\\s*([0-9]{4,8})/i);let sku=m?m[1]:'';"+
            "if(sku!==q && !txt.includes(q))continue;"+
            "let img=p.querySelector('img');let src=img?.currentSrc||img?.src||'';"+
            "found.set(u.href,{name:name,sku:sku,img:src,url:u.href,clubPrice:prices.clubPrice,regularPrice:prices.regularPrice});if(found.size>=30)break}"+
            "return JSON.stringify([...found.values()]);})()";
        return script;
    }
    private void deliverBatchResult(String sku,int serial,String json) {
        if (serial != batchSerial || !sku.equals(batchCurrentSku)) return;
        batchCurrentSku = null;
        js("window.onBatchLookup("+JSONObject.quote(sku)+","+JSONObject.quote(json)+");");
    }

    @Override public void onBackPressed() {
        js("window.backToResults();");
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (ui != null) {ui.removeJavascriptInterface("SetecApp");ui.destroy();ui=null;}
        if (merchant != null) {merchant.destroy();merchant=null;}
        if (batchMerchant != null) {batchMerchant.destroy();batchMerchant=null;}
        super.onDestroy();
    }
}
