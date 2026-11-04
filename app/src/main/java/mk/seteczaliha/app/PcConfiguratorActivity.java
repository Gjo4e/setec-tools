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
    private WebView ui, catalog;
    private int searchSerial = 0, stockSerial = 0, stockIndex = 0, stockTotal = 0, stockAvailable = 0;
    private JSONArray stockQueue = new JSONArray();
    private static final String COMPONENT_ROOT = "https://www.setec.mk/category/komp-d1-98uteri-20i-20it-20oprema-19";
    private String categoryKey = "board", categoryUrl = null, localFilter = "";
    private int catalogPage = 1;
    private boolean resolvingCategory = false;
    private final java.util.HashMap<String, String> knownCategories = new java.util.HashMap<>();


    @Override @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        getWindow().setStatusBarColor(Color.rgb(29, 28, 32));
        getWindow().setNavigationBarColor(Color.BLACK);
        FrameLayout root = new FrameLayout(this);
        setContentView(root);
        catalog = makeWeb();
        root.addView(catalog, new FrameLayout.LayoutParams(-1, -1));
        catalog.setAlpha(0.01f);
        catalog.setClickable(false);
        // Exact official category URLs supplied by the user. Never guess a category path.
        knownCategories.put("board", "https://www.setec.mk/category/matichni-20plochi-22");
        knownCategories.put("case", "https://www.setec.mk/category/kukjishta-28");
        knownCategories.put("cpu", "https://www.setec.mk/category/protsesori-21");
        knownCategories.put("gpu", "https://www.setec.mk/category/grafichki-20karti-25");
        knownCategories.put("storage", "https://www.setec.mk/category/tvrdi-20diskovi-20i-20ssd-20diskovi-23");
        knownCategories.put("ram", "https://www.setec.mk/category/ram-20memori-d1-98a-24");
        knownCategories.put("cooler", "https://www.setec.mk/category/kuleri-27");
        knownCategories.put("psu", "https://www.setec.mk/category/napo-d1-98uvanja-29");
        catalog.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                int id = searchSerial;
                if (resolvingCategory) {
                    for (long delay : new long[]{400, 1200, 2600, 4400}) {
                        handler.postDelayed(() -> resolveCategory(id), delay);
                    }
                    return;
                }
                if (!url.contains("/category/")) return;
                for (long delay : new long[]{650, 1600, 3100, 5100}) {
                    handler.postDelayed(() -> readCatalog(id), delay);
                }
            }
        });
        for (int i = 0; i < STOCK_WORKERS; i++) workers.add(new StockWorker(root));
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

    /** Category browsing replaces the unreliable free-text Setec search endpoint. */
    private void startSearch(String category, String text, int page) {
        if (catalog == null) return;
        final int id = ++searchSerial;
        stockSerial++;
        for (StockWorker worker : workers) { worker.completed = true; worker.web.stopLoading(); }
        categoryKey = category == null ? "" : category;
        localFilter = text == null ? "" : text.trim();
        if (localFilter.length() > 75) localFilter = localFilter.substring(0, 75);
        catalogPage = Math.max(1, Math.min(page, 75));
        categoryUrl = knownCategories.get(categoryKey);
        send("window.pcLoading(" + JSONObject.quote(localFilter) + "," + catalogPage + ");");
        catalog.stopLoading();
        if (categoryUrl != null) {
            resolvingCategory = false;
            catalog.loadUrl(categoryPageUrl(categoryUrl, catalogPage));
        } else {
            // Discover the current official subcategory URL from the Setec category menu.
            resolvingCategory = true;
            catalog.loadUrl(COMPONENT_ROOT);
        }
        handler.postDelayed(() -> {
            if (id == searchSerial) send("window.pcSearchTimeout();");
        }, 16000);
    }

    private static String categoryPageUrl(String base, int page) {
        Uri.Builder b = Uri.parse(base).buildUpon().clearQuery();
        if (page > 1) b.appendQueryParameter("page", String.valueOf(page));
        return b.build().toString();
    }

    private static String categoryLabel(String id) {
        switch (id) {
            case "board": return "Матични Плочи";
            case "case": return "Куќишта";
            case "ram": return "РАМ Меморија";
            case "storage": return "Тврди Дискови и SSD Дискови";
            case "psu": return "Напојувања";
            case "cpu": return "Процесори";
            case "gpu": return "Графички Карти";
            case "cooler": return "Кулери";
            default: return "";
        }
    }

    private void resolveCategory(int serial) {
        if (serial != searchSerial || !resolvingCategory || catalog == null) return;
        String label = categoryLabel(categoryKey);
        if (label.isEmpty()) {
            resolvingCategory = false;
            send("window.pcCatalogError('Непозната категорија.');");
            return;
        }
        String javascript = "(function(){"+
            "let wanted="+JSONObject.quote(label.toLowerCase(java.util.Locale.ROOT))+";"+
            "let clean=s=>(s||'').replace(/\\(\\s*\\d+\\s*\\)/g,'').replace(/\\s+/g,' ').trim().toLowerCase();"+
            "let links=[...document.querySelectorAll('a[href*=\\\"/category/\\\"]')];"+
            "let a=links.find(e=>clean(e.textContent)===wanted)||links.find(e=>clean(e.textContent).startsWith(wanted));"+
            "if(a){let u=new URL(a.getAttribute('href'),location.href);return u.origin+u.pathname;}"+
            "let candidates=[...document.querySelectorAll('label,button,span,li,a,[role=button]')];"+
            "let e=candidates.find(e=>clean(e.textContent)===wanted&&e.getBoundingClientRect().width>0);"+
            "if(e){let target=e.closest('a,button,label,[role=button]')||e;target.click();return 'clicked'}"+
            "return ''})()";
        catalog.evaluateJavascript(javascript, value -> {
            if (serial != searchSerial || !resolvingCategory) return;
            String result = decode(value);
            if (result != null && result.startsWith("https://")) {
                Uri uri = Uri.parse(result);
                if (("setec.mk".equals(uri.getHost()) || "www.setec.mk".equals(uri.getHost()))
                        && uri.getPath() != null && uri.getPath().startsWith("/category/")
                        && !uri.getPath().equals(Uri.parse(COMPONENT_ROOT).getPath())) {
                    categoryUrl = result;
                    knownCategories.put(categoryKey, result);
                    resolvingCategory = false;
                    catalog.loadUrl(categoryPageUrl(result, catalogPage));
                }
            } else if (result != null && result.equals("clicked")) {
                handler.postDelayed(() -> {
                    if (serial != searchSerial || !resolvingCategory) return;
                    String url = catalog.getUrl();
                    if (url != null && url.contains("/category/")) {
                        boolean changedPage = !Uri.parse(url).getPath().equals(Uri.parse(COMPONENT_ROOT).getPath());
                        String discovered = changedPage ?
                                Uri.parse(url).buildUpon().clearQuery().build().toString() :
                                COMPONENT_ROOT;
                        // Some Setec sidebar categories are checkboxes that filter in-place
                        // without changing the URL; keep the currently filtered catalogue.
                        categoryUrl = discovered;
                        resolvingCategory = false;
                        if (changedPage) knownCategories.put(categoryKey, discovered);
                        if (changedPage && catalogPage > 1) catalog.loadUrl(categoryPageUrl(discovered, catalogPage));
                        else for (long delay : new long[]{350, 950, 1950, 3400}) {
                            handler.postDelayed(() -> readCatalog(serial), delay);
                        }
                    }
                }, 1000);
            }
        });
    }

    private void readCatalog(int serial) {
        if (serial != searchSerial || catalog == null || resolvingCategory) return;
        if (categoryUrl == null || catalog.getUrl() == null ||
                !Uri.parse(catalog.getUrl()).getPath().equals(Uri.parse(categoryUrl).getPath())) return;
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
            "let image=(function(){\nlet candidates=[...a.querySelectorAll('img,picture source'),...p.querySelectorAll('img,picture source')];\nfor(let im of candidates){\n  let sets=[im.getAttribute('data-srcset'),im.getAttribute('srcset')].filter(Boolean);\n  let opts=[im.getAttribute('data-src'),im.getAttribute('data-original'),im.getAttribute('data-lazy-src'),im.getAttribute('data-image')];\n  for(let set of sets)opts.push(set.split(',').pop().trim().split(/\\s+/)[0]);\n  opts.push(im.getAttribute('src'),im.currentSrc);\n  for(let raw of opts){\n    if(!raw||/^(data:|blob:|about:|javascript:)/i.test(raw))continue;\n    let u;try{u=new URL(raw,location.href)}catch(e){continue}\n    if(u.protocol!=='https:'||/logo|icon|badge|placeholder|spinner|cart|compare|sprite/i.test(u.pathname))continue;\n    return u.href;\n  }\n}\nreturn '';\n})();"+
            "out.set(u.href,{name:name,url:u.href,sku:sku,image:image,clubPrice:price('Клуб цена'),regularPrice:price('Редовна цена')});"+
            "if(out.size>=70)break;}return JSON.stringify([...out.values()]);})()";
        catalog.evaluateJavascript(script, output -> {
            if (serial != searchSerial) return;
            String result = decode(output);
            if (result != null) send("window.pcCandidates(" + JSONObject.quote(result) + ");");
        });
    }


    // Multiple independent WebViews check stock concurrently rather than
    // blocking all products behind one slow product detail page.
    private static final int STOCK_WORKERS = 4;
    private final java.util.ArrayList<StockWorker> workers = new java.util.ArrayList<>();
    private final java.util.HashMap<String, StockCacheEntry> stockCache = new java.util.HashMap<>();
    private int stockCompleted = 0;
    private static final long CACHE_MS = 2 * 60 * 1000L;

    private static final class StockCacheEntry {
        final JSONObject result;
        final long savedAt;
        StockCacheEntry(JSONObject result) {
            this.result = result;
            this.savedAt = android.os.SystemClock.elapsedRealtime();
        }
    }

    private final class StockWorker {
        final WebView web;
        JSONObject product;
        String url = "";
        int serial = -1;
        boolean completed = true;
        boolean clicked = false;
        boolean detailsSent = false;
        int clickAttempts = 0;
        String lastStores = "";
        int consecutiveStoreReads = 0;

        StockWorker(FrameLayout root) {
            web = makeWeb();
            web.setAlpha(0.01f);
            web.setClickable(false);
            root.addView(web, new FrameLayout.LayoutParams(-1, -1));
            web.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String location) {
                    if (!completed && serial == stockSerial && location.startsWith(url)) {
                        final int expected = serial;
                        handler.postDelayed(() -> beginProbe(StockWorker.this, expected), 350);
                    }
                }
            });
        }

        void start(JSONObject item, int id) {
            product = item;
            url = item.optString("url");
            serial = id;
            completed = false;
            clicked = false;
            detailsSent = false;
            clickAttempts = 0;
            lastStores = "";
            consecutiveStoreReads = 0;
            web.stopLoading();
            web.loadUrl(url);
            final String target = url;
            handler.postDelayed(() -> {
                if (!completed && serial == stockSerial && target.equals(url)) finish(this, null);
            }, 8200);
        }
    }

    private void startVerification(String json) {
        stockSerial++;
        int serial = stockSerial;
        stockIndex = 0;
        stockAvailable = 0;
        stockCompleted = 0;
        stockQueue = new JSONArray();
        try {
            JSONArray list = new JSONArray(json);
            for (int i = 0; i < Math.min(list.length(), 70); i++) {
                JSONObject p = list.optJSONObject(i);
                if (p != null && safeProduct(p.optString("url"))) stockQueue.put(p);
            }
        } catch (Exception ignored) {}
        stockTotal = stockQueue.length();
        for (StockWorker worker : workers) {
            worker.completed = true;
            worker.web.stopLoading();
        }
        send("window.pcProgress(0," + stockTotal + ",0);");
        for (StockWorker worker : workers) giveNext(worker, serial);
        if (stockTotal == 0) send("window.pcVerificationDone();");
    }

    private void giveNext(StockWorker worker, int serial) {
        if (serial != stockSerial) return;
        if (stockIndex >= stockTotal) {
            if (stockCompleted >= stockTotal) send("window.pcVerificationDone();");
            return;
        }
        JSONObject p = stockQueue.optJSONObject(stockIndex++);
        if (p == null) {
            stockCompleted++;
            giveNext(worker, serial);
            return;
        }
        String url = p.optString("url");
        StockCacheEntry cached = stockCache.get(url);
        if (cached != null && android.os.SystemClock.elapsedRealtime() - cached.savedAt < CACHE_MS) {
            if (cached.result != null) {
                stockAvailable++;
                send("window.pcVerifiedProduct(" + JSONObject.quote(cached.result.toString()) + ");");
            }
            stockCompleted++;
            send("window.pcProgress("+stockCompleted+","+stockTotal+","+stockAvailable+");");
            giveNext(worker, serial);
            return;
        }
        worker.start(p, serial);
    }

    private void beginProbe(StockWorker worker, int serial) {
        if (serial != stockSerial || worker.completed || worker.clicked) return;
        worker.clicked = true;
        String click = "(function(){try{\nlet visible=e=>{if(!e)return false;let r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none'};\nlet text=e=>[e.innerText,e.textContent,e.getAttribute('aria-label'),e.getAttribute('title'),e.querySelector('img')?.alt].filter(Boolean).join(' ');\nlet targets=[...document.querySelectorAll('button,[role=\"button\"],a,[onclick]')];\nlet btn=targets.find(e=>visible(e)&&/провери\\s*достапност/i.test(text(e)));\nif(!btn){let img=[...document.querySelectorAll('img')].find(e=>visible(e)&&/провери\\s*достапност/i.test([e.alt,e.title].join(' ')));if(img)btn=img.closest('button,a,[role=\"button\"],[onclick]')||img}\nif(!btn)return 'missing';\nlet baseline=new WeakSet();\nfor(let e of document.querySelectorAll('body *'))if(visible(e))baseline.add(e);\nwindow.__pcBefore=baseline;\nbtn.click();\nreturn 'clicked';\n}catch(e){return 'error'}})()";
        worker.clickAttempts++;
        worker.web.evaluateJavascript(click, output -> {
            if (serial != stockSerial || worker.completed) return;
            String status = decode(output);
            if ("clicked".equals(status)) {
                for (long delay : new long[]{650, 1450, 2600, 4200, 6000}) {
                    handler.postDelayed(() -> probe(worker, serial, delay == 6000), delay);
                }
            } else if (worker.clickAttempts < 4) {
                worker.clicked = false;
                handler.postDelayed(() -> beginProbe(worker, serial), 900);
            } else {
                finish(worker, null);
            }
        });
    }

        private void probe(StockWorker worker, int serial, boolean last) {
        if (serial != stockSerial || worker.completed) return;
        String extraction = "(function(){try{\n // Restore the v3.5 mechanism: inspect text that appears AFTER the stock button opens.\n const baseline=window.__pcBefore;\n const visible=e=>{if(!e)return false;let rect=e.getBoundingClientRect(),style=getComputedStyle(e);return rect.width>0&&rect.height>0&&style.visibility!=='hidden'&&style.display!=='none'};\n const textOf=e=>(e?.innerText||e?.textContent||'').replace(/\\s+/g,' ').trim();\n const shops=['СТРУМИЦА RETAIL PARK','КРИВА ПАЛАНКА','СВЕТИ НИКОЛЕ','ЃОРЧЕ ПЕТРОВ','ДАЈМОНД МОЛ','СИТИ МОЛ','Г. МАГАЦИН','ГТЦ ИТ','БИТОЛА 2','РУЗВЕЛТОВА','АЕРОДРОМ','ГОСТИВАР','КАВАДАРЦИ','СТРУМИЦА','ДРАЧЕВО','РАДОВИШ','ДЕЛЧЕВО','НЕГОТИНО','КОЧАНИ','ПРИЛЕП','ТЕТОВО','БИТОЛА','СТРУГА','ШТИП','ЧАИР','ВЕЛЕС','ОХРИД','КИЧЕВО','РЕСЕН','ДЕБАР','ГОДИ','СТАДИОН','ГТЦ','ГЕВГЕЛИЈА','КУМАНОВО'];\n const rejected=/САМО\\s*ЗА\\s*СКОПЈЕ|ДОСТАВА|ДОСТАВУВА|ИСПОРАКА|DELIVERY|ПРЕМИУМ|КОМФОРТ|ОНЛАЈН|ON[\\s-]*LINE|ДО\\s*20\\s*КМ|ПРОВЕРИ\\s*ДОСТАПНОСТ|ЗАЛИХА\\s*ВО\\s*ПРОДАВНИЦИТЕ/;\n const unavailable=/НЕМА\\s*(?:НА\\s*)?ЗАЛИХА|НЕ\\s*Е\\s*ДОСТАПН|НЕДОСТАПН|OUT\\s*OF\\s*STOCK|NOT\\s*AVAILABLE|РАСПРОДАДЕН/;\n const nameOf=raw=>{\n  let t=String(raw||'').normalize('NFKC').toUpperCase().replace(/\\s+/g,' ').trim();\n  if(!t||t.length>130||rejected.test(t)||unavailable.test(t))return '';\n  t=t.replace(/^(?:СЕТЕК|SETEC|SETEK)\\s*[-–:]?\\s*/,'')\n     .replace(/^(?:ПРОДАВНИЦА|САЛОН)\\s*[-–:]?\\s*/,'')\n     .replace(/^СКОПЈЕ\\s*[-–:]\\s*/,'')\n     .replace(/^CAIR(?=$|[\\s,:;·-])/,'ЧАИР')\n     .replace(/^CHAIR(?=$|[\\s,:;·-])/,'ЧАИР')\n     .replace(/^STIP(?=$|[\\s,:;·-])/,'ШТИП')\n     .replace(/^SHTIP(?=$|[\\s,:;·-])/,'ШТИП');\n  for(const shop of shops)if(t===shop||t.startsWith(shop+' ')||t.startsWith(shop+',')||t.startsWith(shop+':')||t.startsWith(shop+'-')||t.startsWith(shop+'·'))return 'Сетек '+shop;\n  return '';\n };\n const stores=[],seen=new Set();\n if(baseline){\n  for(const element of document.querySelectorAll('body *')){\n   if(baseline.has(element)||!visible(element))continue;\n   const label=textOf(element);\n   if(label.length<2||label.length>130)continue;\n   // Exclude container duplicates; use the most specific new text labels.\n   if([...element.children].some(child=>visible(child)&&textOf(child)===label))continue;\n   const store=nameOf(label);\n   if(store&&!seen.has(store)){seen.add(store);stores.push(store)}\n  }\n }\n // Also support newly visible dialog rows when the shop name is in a text node\n // but the element's own text includes a small stock badge.\n if(!stores.length&&baseline){\n  for(const element of document.querySelectorAll('dialog[open] *,[role=\"dialog\"] *,[aria-modal=\"true\"] *')){\n   if(!visible(element)||baseline.has(element))continue;\n   const label=textOf(element);\n   if(label.length<2||label.length>130)continue;\n   if([...element.children].some(child=>visible(child)&&textOf(child)===label))continue;\n   const store=nameOf(label);\n   if(store&&!seen.has(store)){seen.add(store);stores.push(store)}\n  }\n }\n let body=document.body?.innerText||'';\n let title=document.querySelector('h1')?.innerText||'';\n let price=key=>{let m=body.match(new RegExp(key+'\\\\s*:\\\\s*([0-9][0-9., ]*)\\\\s*ден','i'));return m?m[1].replace(/\\s/g,''):''};\n let sku=(body.match(/Шифра\\s*:?\\s*([0-9]{4,8})/i)||[])[1]||'';\n let image='';\n for(const raw of [document.querySelector('meta[property=\"og:image\"]')?.content,document.querySelector('meta[name=\"twitter:image\"]')?.content,document.querySelector('main img')?.getAttribute('data-src'),document.querySelector('main img')?.getAttribute('src')]){\n  if(!raw)continue;\n  let u;try{u=new URL(raw,location.href)}catch(e){continue}\n  if(u.protocol==='https:'&&!/logo|icon|badge|placeholder|spinner|cart|compare|sprite/i.test(u.pathname)){image=u.href;break}\n }\n return JSON.stringify({stores,name:title.trim(),sku,image,clubPrice:price('Клуб цена'),regularPrice:price('Редовна цена'),specs:body.slice(0,4200)});\n}catch(e){return '{}'}})()";
        worker.web.evaluateJavascript(extraction, output -> {
            if (serial != stockSerial || worker.completed) return;
            JSONObject detail = null;
            try {
                String text = decode(output);
                if (text != null) detail = new JSONObject(text);
            } catch (Exception ignored) {}
            // Update code and prices as soon as they are read, even when stock
            // verification is unavailable; never mark unverified stock as available.
            if (detail != null && !worker.detailsSent &&
                    (!detail.optString("image").isEmpty() ||
                     !detail.optString("sku").isEmpty() ||
                     !detail.optString("clubPrice").isEmpty() ||
                     !detail.optString("regularPrice").isEmpty())) {
                worker.detailsSent = true;
                try {
                    JSONObject metadata = new JSONObject();
                    metadata.put("url", worker.url);
                    for (String key : new String[]{"sku", "image", "clubPrice", "regularPrice"}) {
                        if (!detail.optString(key).isEmpty()) metadata.put(key, detail.optString(key));
                    }
                    send("window.pcProductDetails(" + JSONObject.quote(metadata.toString()) + ");");
                } catch (Exception ignored) {}
            }
            JSONArray locations = detail == null ? null : detail.optJSONArray("stores");
            if (locations != null && locations.length() > 0) {
                java.util.ArrayList<String> keys = new java.util.ArrayList<>();
                for (int i = 0; i < locations.length(); i++) keys.add(locations.optString(i));
                java.util.Collections.sort(keys);
                String signature = android.text.TextUtils.join("|", keys);
                if (signature.equals(worker.lastStores)) worker.consecutiveStoreReads++;
                else { worker.lastStores = signature; worker.consecutiveStoreReads = 1; }
                // At least three consistent snapshots; allow the shop list to finish loading.
                if (worker.consecutiveStoreReads >= 2 || last) finish(worker, detail);
            } else if (last) finish(worker, null);
        });
    }

    private void finish(StockWorker worker, JSONObject detail) {
        if (worker.completed || worker.serial != stockSerial) return;
        worker.completed = true;
        String url = worker.url;
        JSONObject verified = null;
        if (detail != null && worker.product != null) {
            try {
                verified = new JSONObject(worker.product.toString());
                if (!detail.optString("name").isEmpty()) verified.put("name", detail.optString("name"));
                for (String key : new String[]{"sku", "image", "clubPrice", "regularPrice", "specs"}) {
                    if (!detail.optString(key).isEmpty()) verified.put(key, detail.optString(key));
                }
                verified.put("stores", detail.optJSONArray("stores"));
                verified.put("stockVerified", true);
                stockAvailable++;
                send("window.pcVerifiedProduct(" + JSONObject.quote(verified.toString()) + ");");
            } catch (Exception ignored) {}
        }
        stockCache.put(url, new StockCacheEntry(verified));
        stockCompleted++;
        send("window.pcProgress("+stockCompleted+","+stockTotal+","+stockAvailable+");");
        giveNext(worker, stockSerial);
    }

    @Override public void onBackPressed() {
        if (ui == null) { finish(); return; }
        ui.evaluateJavascript("window.pcBack ? window.pcBack() : false", value -> {
            if (!"true".equals(value)) finish();
        });
    }

    @Override protected void onDestroy() {
        searchSerial++;
        stockSerial++;
        handler.removeCallbacksAndMessages(null);
        if (ui != null) { ui.removeJavascriptInterface("PcApp"); ui.destroy(); ui = null; }
        if (catalog != null) { catalog.destroy(); catalog = null; }
        for (StockWorker worker : workers) worker.web.destroy();
        workers.clear();
        super.onDestroy();
    }
}
