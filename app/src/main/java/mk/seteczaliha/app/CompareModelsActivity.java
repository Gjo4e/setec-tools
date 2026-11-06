package mk.seteczaliha.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputFilter;
import android.text.InputType;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.inputmethod.EditorInfo;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.annotation.SuppressLint;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compare precisely two real models. The existing opt-in camera scanner
 * finds and verifies each Setec product and extracts its listed specs.
 * Only up to five key specs appear side by side, with unknown values marked.
 */
public final class CompareModelsActivity extends ComponentActivity {
    private static final int ORANGE=0xFF279DFF;
    private int INK=0xFF202531;
    private boolean darkMode=false;
    private int theme(int light,int dark){return darkMode?dark:light;}
    private final Product[] products=new Product[2];
    private final Button[] scanButtons=new Button[2];
    private final Button[] manualButtons=new Button[2];
    private final EditText[] skuInputs=new EditText[2];
    private final Handler inputHandler=new Handler(Looper.getMainLooper());
    private final Runnable[] pendingLookup=new Runnable[2];
    private ActivityResultLauncher<Intent> picker, skuScanner;
    private LinearLayout content,table,screenRoot;
    private TextView overview;
    private int editingSlot=0;
    private final WebView[] lookupBrowsers=new WebView[2];
    private final int[] lookupSerial=new int[2];
    private final String[] lookupSku=new String[2];
    private final boolean[] lookupProduct=new boolean[2];

    private static final class Product {
        String name,club,regular,sku,description="",source="";
        Map<String,String> facts=new LinkedHashMap<>();
        List<String> details=new ArrayList<>();
        List<String> highlights=new ArrayList<>();
        Map<String,String> sourceByLabel=new LinkedHashMap<>();
        static Product parse(String json){
            try{
                JSONObject obj=new JSONObject(json);
                Product p=new Product();
                p.name=obj.optString("name").trim();
                if(p.name.length()<4)return null;
                p.club=obj.optString("club").trim();
                p.regular=obj.optString("regular").trim();
                p.sku=obj.optString("sku").trim();
                String source=obj.optString("source").trim();
                if(safeSource(source)){
                    p.source=source;
                    p.description=obj.optString("description").trim();
                    if(p.description.length()>800)
                        p.description=p.description.substring(0,800);
                    JSONArray detailArray=obj.optJSONArray("details");
                    if(detailArray!=null)
                        for(int i=0;i<detailArray.length()&&p.details.size()<5;i++){
                            String line=detailArray.optString(i).trim();
                            if(line.length()>=3&&line.length()<=230&&
                               !SetecCompareRows.isUiOrSalesText(line)&&
                               !p.details.contains(line))p.details.add(line);
                        }
                    if(p.details.isEmpty())
                        p.details=SetecCompareRows.parseLines(p.description);
                    JSONArray highlightArray=obj.optJSONArray("highlights");
                    if(highlightArray!=null)
                        for(int i=0;i<highlightArray.length()&&p.highlights.size()<5;i++){
                            String line=highlightArray.optString(i).trim();
                            if(line.length()>=3&&line.length()<=230&&
                               !SetecCompareRows.isUiOrSalesText(line)&&
                               !p.highlights.contains(line))p.highlights.add(line);
                        }
                }
                JSONArray arr=obj.optJSONArray("facts");
                if(arr!=null)for(int i=0;i<arr.length();i++){
                    JSONObject f=arr.optJSONObject(i);
                    if(f==null)continue;
                    String label=f.optString("label").trim(),value=f.optString("value").trim();
                    if(label.length()>55||value.length()>110||label.isEmpty()||value.isEmpty())continue;
                    String url=f.optString("url");
                    if(!safeSource(url))continue;
                    p.facts.put(label,value);
                    p.sourceByLabel.put(label,url);
                }
                if(safeSource(p.source)){
                    List<String> fromSetec=SetecCompareRows.highlights(
                        p.name,p.facts,p.details,p.description);
                    for(String line:fromSetec){
                        if(p.highlights.size()>=5)break;
                        if(!p.highlights.contains(line))p.highlights.add(line);
                    }
                }
                return p;
            }catch(Exception e){return null;}
        }
    }

    private static boolean safeSource(String value){
        try{
            Uri uri=Uri.parse(value);
            return SetecPageData.isOfficialProductUrl(value);
        }catch(Exception e){return false;}
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        darkMode=true;
        INK=0xFFEAF5FF;
        getWindow().setStatusBarColor(0xFF0B1423);
        getWindow().setNavigationBarColor(Color.BLACK);
        skuScanner=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result->{
            if(result.getResultCode()!=RESULT_OK||result.getData()==null)return;
            String code=result.getData().getStringExtra(ScannerActivity.EXTRA_SKU);
            if(code==null||!code.matches("[0-9]{4,8}")){
                overview.setText("Не се прочита валидна SETEC шифра. Пробај повторно.");
                return;
            }
            startSkuLookup(code);
        });
        picker=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result->{
            if(result.getResultCode()!=RESULT_OK||result.getData()==null)return;
            Product model=Product.parse(result.getData().getStringExtra("compare_result"));
            if(model==null){
                overview.setText("Моделот не е пренесен. Пробај повторно.");
                return;
            }
            products[editingSlot]=model;
            render();
        });

        LinearLayout screen=new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(0xFF080D14);
        setContentView(screen);
        screenRoot=screen;

        LinearLayout header=new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14),dp(20),dp(14),dp(19));
        header.setBackground(gradient(0xFF101F35,0xFF0A121F,0xFF2B5B86,18));
        screen.addView(header,new LinearLayout.LayoutParams(-1,-2));
        Button back=button("‹",0xFF1A3550);
        back.setTextSize(25);
        header.addView(back,new LinearLayout.LayoutParams(dp(52),dp(50)));
        back.setOnClickListener(v->finish());
        LinearLayout titles=new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,-2,1);
        tl.leftMargin=dp(14);
        header.addView(titles,tl);
        titles.addView(text("SETEC TOOLS",11,true,0xFF55BFFF));
        TextView title=text("Спореди производи",23,true,Color.WHITE);
        titles.addView(title);
        titles.addView(text("Два производи · една јасна споредба",12,false,0xFFAAC0D7));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14),dp(18),dp(14),dp(45));
        scroll.addView(content);

        TextView intro=text("Скенирај или внеси SETEC шифра. Производот автоматски се проверува на Setec.mk и се додава во споредбата.",13,false,0xFFB6C8DC);
        intro.setLineSpacing(dp(3),1f);
        content.addView(intro);

        for(int i=0;i<2;i++){
            final int slot=i;
            LinearLayout panel=new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(15),dp(16),dp(15),dp(16));
            panel.setBackground(gradient(i==0?0xFF12374E:0xFF252443,0xFF111D2B,i==0?0xFF1D7298:0xFF514C88,16));
            LinearLayout.LayoutParams panelParams=new LinearLayout.LayoutParams(-1,-2);
            panelParams.topMargin=dp(i==0?16:12);
            content.addView(panel,panelParams);
            TextView slotTitle=text(i==0?"01  ПРВ ПРОИЗВОД":"02  ВТОР ПРОИЗВОД",12,true,i==0?0xFF36C5FF:0xFFAA9AFF);
            panel.addView(slotTitle);
            TextView sub=text("Скенирај или напиши шифра од 4–8 цифри",12,false,0xFFACBED2);
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(7);
            panel.addView(sub,sp);
            LinearLayout entry=new LinearLayout(this);
            entry.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.topMargin=dp(12);
            panel.addView(entry,ep);
            EditText input=new EditText(this);
            skuInputs[i]=input;
            input.setSingleLine(true);
            input.setTextSize(16);
            input.setTypeface(null,Typeface.BOLD);
            input.setTextColor(Color.WHITE);
            input.setHintTextColor(0xFF7792AA);
            input.setHint("SETEC шифра");
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setImeOptions(EditorInfo.IME_ACTION_DONE);
            input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
            input.setPadding(dp(13),0,dp(8),0);
            input.setBackground(gradient(0xFF0B1827,0xFF0B1827,0xFF365979,11));
            entry.addView(input,new LinearLayout.LayoutParams(0,dp(52),1));
            input.setOnEditorActionListener((v,action,event)->{
                if(action==EditorInfo.IME_ACTION_DONE){lookupTypedSku(slot);return true;}return false;
            });
            input.addTextChangedListener(new TextWatcher(){
                @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                    if(pendingLookup[slot]!=null)inputHandler.removeCallbacks(pendingLookup[slot]);
                    String code=s.toString().trim();
                    if(!code.matches("[0-9]{4,8}"))return;
                    pendingLookup[slot]=()->{if(code.equals(input.getText().toString().trim()))lookupTypedSku(slot);};
                    inputHandler.postDelayed(pendingLookup[slot],1000);
                }
                @Override public void afterTextChanged(Editable e){}
            });
            Button scan=button("▣  Скенирај",i==0?0xFF167CB7:0xFF6153A9);
            scanButtons[i]=scan;
            LinearLayout.LayoutParams sb=new LinearLayout.LayoutParams(dp(125),dp(52));
            sb.leftMargin=dp(8);entry.addView(scan,sb);
            scan.setOnClickListener(v->openScanner(slot));
            manualButtons[i]=scan;
        }

        overview=text("Внеси ја првата шифра за да започне проверката.",13,false,0xFFACBED2);
        overview.setPadding(dp(2),dp(20),dp(2),dp(8));
        content.addView(overview);

        LinearLayout compareHeader=new LinearLayout(this);
        compareHeader.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ch=new LinearLayout.LayoutParams(-1,-2);ch.topMargin=dp(12);
        content.addView(compareHeader,ch);
        compareHeader.addView(text("РЕЗУЛТАТИ ОД СПОРЕДБАТА",14,true,0xFF51B9FF));
        compareHeader.addView(text("Клуб цена, редовна цена и потврдени спецификации",12,false,0xFF93A8BE));

        table=new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(14);
        content.addView(table,tp);

        TextView note=text("Податоците се од Setec.mk. Ако некоја спецификација не е потврдена, нема да се измислува вредност.",12,false,0xFF91A8C0);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(20);
        content.addView(note,np);
        render();
        for(int i=0;i<2;i++)initLookupBrowser(i);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initLookupBrowser(int slot){
        WebView web=new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setLoadsImagesAutomatically(false);
        web.setAlpha(0.01f);
        web.setClickable(false);
        screenRoot.addView(web,new LinearLayout.LayoutParams(dp(1),dp(1)));
        lookupBrowsers[slot]=web;
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView v,String url){
                final int serial=lookupSerial[slot];
                for(long delay:new long[]{900,2200,4200})
                    inputHandler.postDelayed(()->inspectLookup(slot,serial,url),delay);
            }
        });
    }
    private void inspectLookup(int slot,int serial,String url){
        if(serial!=lookupSerial[slot]||lookupBrowsers[slot]==null||
            !url.equals(lookupBrowsers[slot].getUrl()))return;
        String sku=lookupSku[slot];
        if(sku==null)return;
        if(lookupProduct[slot]){
            String script="(function(){let body=document.body?.innerText||'';"+
                "let m=body.match(/(?:Шифра|SKU)\\s*:?\\s*([0-9]{4,8})/i);"+
                "if(!m||m[1]!=="+JSONObject.quote(sku)+")return '';"+
                "let title=(document.querySelector('h1')?.innerText||document.title||'').trim();"+
                "let club=body.match(/Клуб\\s*цена\\s*:?\\s*([0-9.,\\s]+)\\s*ден/i);"+
                "let regular=body.match(/Редовна\\s*цена\\s*:?\\s*([0-9.,\\s]+)\\s*ден/i);"+
                "let specs=[...document.querySelectorAll('table tr,dl div,li')].map(x=>x.innerText?.replace(/\\s+/g,' ').trim()).filter(x=>x&&x.length>8&&x.length<200).slice(0,70);"+
                "return JSON.stringify({name:title,sku:m[1],club:club?club[1].trim():'',regular:regular?regular[1].trim():'',source:location.href,details:specs.slice(0,5),description:specs.slice(0,12).join('\\n')});})()";
            lookupBrowsers[slot].evaluateJavascript(script,result->{
                if(serial!=lookupSerial[slot])return;
                String json=decodeWebString(result);
                if(json==null||json.isEmpty())return;
                Product p=Product.parse(json);
                if(p==null||!sku.equals(p.sku))return;
                products[slot]=p;
                lookupSerial[slot]++;
                overview.setText("✓ Пронајден "+p.name+". "+(products[1-slot]==null?"Внеси го и другиот производ.":"Споредбата е подготвена."));
                render();
            });
        }else{
            String script="(function(){let sku="+JSONObject.quote(sku)+";"+
                "for(let a of document.querySelectorAll('a[href*=\\\"/products/\\\"]')){"+
                "let node=a.closest('article,li,[class*=product],[class*=Product],[class*=card],[class*=Card]')||a.parentElement;"+
                "let txt=node?.innerText||'';"+
                "if(new RegExp('(?:Шифра|SKU)\\\\s*:?\\\\s*'+sku+'(?![0-9])','i').test(txt)){try{let u=new URL(a.href,location.href);if(['setec.mk','www.setec.mk'].includes(u.hostname))return u.href}catch(e){}}}return '';})()";
            lookupBrowsers[slot].evaluateJavascript(script,result->{
                if(serial!=lookupSerial[slot]||lookupProduct[slot])return;
                String found=decodeWebString(result);
                if(found!=null&&SetecPageData.isOfficialProductUrl(found)){
                    lookupProduct[slot]=true;lookupBrowsers[slot].loadUrl(found);
                }
            });
        }
    }
    private static String decodeWebString(String encoded){
        if(encoded==null||"null".equals(encoded))return null;
        try{return new JSONArray("["+encoded+"]").getString(0);}catch(Exception e){return null;}
    }
    @Override protected void onDestroy(){
        for(WebView w:lookupBrowsers)if(w!=null)w.destroy();
        super.onDestroy();
    }

    private GradientDrawable gradient(int top,int bottom,int stroke,int radius){
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{top,bottom});
        bg.setCornerRadius(dp(radius));bg.setStroke(dp(1),stroke);return bg;
    }

    private void lookupTypedSku(int slot){
        String code=skuInputs[slot].getText().toString().trim();
        if(!code.matches("[0-9]{4,8}")){skuInputs[slot].setError("Шифра од 4–8 цифри");return;}
        if(pendingLookup[slot]!=null)inputHandler.removeCallbacks(pendingLookup[slot]);
        editingSlot=slot;
        overview.setText("🔎 Се проверува шифрата "+code+" на Setec.mk…");
        startSkuLookup(code);
    }

    private void openScanner(int slot){
        editingSlot=slot;
        // Reuse the existing dedicated SETEC number scanner. It reads only
        // 4–8 digit retail codes, not the surrounding model text or prices.
        skuScanner.launch(new Intent(this,ScannerActivity.class));
    }

    private void startSkuLookup(String code){
        if(code==null||!code.matches("[0-9]{4,8}")){
            overview.setText("Внеси SETEC шифра од 4 до 8 цифри.");
            return;
        }
        int slot=editingSlot;
        lookupSku[slot]=code;
        lookupProduct[slot]=false;
        int serial=++lookupSerial[slot];
        overview.setText("🔎 Го барам производот со шифра "+code+" на Setec.mk…");
        String url="https://www.setec.mk/search-result?q="+Uri.encode(code);
        lookupBrowsers[slot].loadUrl(url);
        inputHandler.postDelayed(()->{
            if(serial==lookupSerial[slot]&&!lookupProduct[slot])
                overview.setText("Сѐ уште ја проверувам шифрата "+code+". Ако нема резултат, провери ја шифрата.");
        },10000);
    }

    private void enterSkuManually(int slot){
        editingSlot=slot;
        EditText input=new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        input.setHint("пр. 84554");
        input.setPadding(dp(17),dp(12),dp(17),dp(12));
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("SETEC шифра – модел "+(slot+1))
            .setMessage("Внеси 4–8 цифри. Ќе го проверам производот, цената и описот на Setec.mk.")
            .setView(input)
            .setNegativeButton("Откажи",null)
            .setPositiveButton("Побарај производ",null)
            .create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v->{
                String sku=input.getText().toString().trim();
                if(!sku.matches("[0-9]{4,8}")){
                    input.setError("Внеси 4–8 цифри.");
                    return;
                }
                dialog.dismiss();
                startSkuLookup(sku);
            }));
        dialog.show();
    }

    private void render(){
        table.removeAllViews();
        for(int i=0;i<2;i++){
            scanButtons[i].setText(products[i]==null
                ?"▣ Скенирај шифра "+(i+1)
                :"↻ Замени шифра "+(i+1));
        }
        Product left=products[0],right=products[1];
        if(left==null&&right==null){
            table.addView(text("Прво скенирај ја SETEC шифрата за левиот производ, па за десниот.",13,false,theme(0xFF657081,0xFFB6C3D3)));
            return;
        }
        overview.setText(left!=null&&right!=null
            ?"Избрани се два модели. Сите разлики подолу се од потврдени извори."
            :"Скенирај го и другиот модел за целосна споредба.");

        LinearLayout models=new LinearLayout(this);
        models.setOrientation(LinearLayout.HORIZONTAL);
        table.addView(models);
        models.addView(columnCard(left==null?"Модел 1":left.name,left==null?"Неизбран":subtitle(left)),
            columnParams(false));
        models.addView(columnCard(right==null?"Модел 2":right.name,right==null?"Неизбран":subtitle(right)),
            columnParams(true));

        row("Клуб цена", left==null?"—":price(left.club),right==null?"—":price(right.club),false);
        row("Редовна цена",left==null?"—":price(left.regular),right==null?"—":price(right.regular),false);

        TextView keyTitle=text("5 НАЈВАЖНИ ПОДАТОЦИ ЗА СЕКОЈ ПРОИЗВОД",15,true,INK);
        LinearLayout.LayoutParams kh=new LinearLayout.LayoutParams(-1,-2);
        kh.topMargin=dp(20);
        table.addView(keyTitle,kh);
        LinearLayout keyColumns=new LinearLayout(this);
        keyColumns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(-1,-2);
        kp.topMargin=dp(9);
        table.addView(keyColumns,kp);
        keyColumns.addView(highlightCard(left,1),columnParams(false));
        keyColumns.addView(highlightCard(right,2),columnParams(true));

        if(left==null||right==null)return;
        TextView compareHeading=text("СПОРЕДБА ПО ЗАЕДНИЧКИ КАРАКТЕРИСТИКИ",14,true,INK);
        LinearLayout.LayoutParams chp=new LinearLayout.LayoutParams(-1,-2);
        chp.topMargin=dp(23);
        table.addView(compareHeading,chp);
        List<SetecCompareRows.Row> rows=SetecCompareRows.build(
            left.name,left.facts,left.details,right.name,right.facts,right.details);
        if(rows.isEmpty()){
            TextView empty=text("Setec.mk не достави технички опис за овие два производи. "+
                "Отвори ги и зачувај ги повторно за проверка.",13,false,theme(0xFF835A2D,0xFFFFC28A));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.topMargin=dp(13);
            table.addView(empty,p);
            return;
        }
        addEvidenceVerdict(left,right,rows);
        boolean hasOriginal=false;
        for(SetecCompareRows.Row spec:rows){
            row(spec.label,spec.left,spec.right,spec.comparable);
            if(!spec.comparable)hasOriginal=true;
        }
        TextView count=text("Прикажани "+rows.size()+" технички редови (најмногу 5). "+
            (hasOriginal
                ?"„Опис од Setec“ е оригинална ставка: бројот не означува иста "+
                 "карактеристика кај двата модела. "
                :"Се означуваат разлики само за иста техничка карактеристика. ")+
            "Податоците се само од потврдените Setec производи.",
            12,false,theme(0xFF707887,0xFFB6C3D3));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.topMargin=dp(12);
        table.addView(count,cp);

        LinearLayout references=new LinearLayout(this);
        references.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);
        rp.topMargin=dp(12);
        table.addView(references,rp);
        addSource(references,left,false);
        addSource(references,right,true);
    }


    /** Evidence-only recommendation: no blanket winner from unrelated specifications. */
    private void addEvidenceVerdict(Product left,Product right,List<SetecCompareRows.Row> rows){
        StringBuilder report=new StringBuilder();
        double lp=priceNumber(left.club),rp=priceNumber(right.club);
        if(!Double.isNaN(lp)&&!Double.isNaN(rp)&&lp!=rp){
            Product cheaper=lp<rp?left:right;
            report.append("• Подобра клуб цена: ").append(cheaper.name)
                .append(" — ").append((long)Math.abs(lp-rp))
                .append(" ден. поевтин.\\n");
        }
        int count=0;
        for(SetecCompareRows.Row r:rows){
            if(!r.comparable||r.left==null||r.right==null)continue;
            String label=r.label.toLowerCase(java.util.Locale.ROOT);
            boolean largerBetter=label.matches(".*(ram|мемори|капацитет|батери|ssd|складира|refresh|освежува|hz|резолуц).*");
            if(!largerBetter)continue;
            double a=unitNumber(r.left),b=unitNumber(r.right);
            if(Double.isNaN(a)||Double.isNaN(b)||a==b)continue;
            String unitA=unitOf(r.left),unitB=unitOf(r.right);
            if(!unitA.equals(unitB)||unitA.isEmpty())continue;
            Product winner=a>b?left:right;
            report.append("• ").append(r.label).append(": ").append(winner.name)
                .append(" има ").append(a>b?r.left:r.right).append(" наспроти ")
                .append(a>b?r.right:r.left).append(".\\n");
            if(++count>=3)break;
        }
        String message=report.length()==0
            ?"Нема доволно директно споредливи потврдени податоци за да се утврди предност."
            :report.toString().trim();
        message+="\\n\\nОва се проверливи разлики, не универзален победник. Изборот зависи од намената; спецификациите се од Setec.mk.";
        TextView heading=text("АВТОМАТСКА АНАЛИЗА · СО ФАКТИ",15,true,INK);
        LinearLayout.LayoutParams h=new LinearLayout.LayoutParams(-1,-2);
        h.topMargin=dp(19);table.addView(heading,h);
        TextView body=text(message,13,false,0xFFEAF5FF);
        body.setPadding(dp(12),dp(12),dp(12),dp(12));
        GradientDrawable bg=new GradientDrawable();bg.setColor(theme(0xFFFFF4E8,0xFF2B2B30));
        bg.setCornerRadius(dp(12));body.setBackground(bg);
        table.addView(body);
    }
    private static double priceNumber(String raw){
        if(raw==null)return Double.NaN;
        String cleaned=raw.replaceAll("[^0-9]","");
        if(cleaned.isEmpty())return Double.NaN;
        try{return Double.parseDouble(cleaned);}catch(Exception e){return Double.NaN;}
    }
    private static double unitNumber(String raw){
        if(raw==null)return Double.NaN;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)([0-9]+(?:[.,][0-9]+)?)\\s*(TB|GB|MB|MHZ|GHZ|HZ|MAH|WH)\\b").matcher(raw);
        if(!m.find())return Double.NaN;
        try{
            double v=Double.parseDouble(m.group(1).replace(',','.'));
            String u=m.group(2).toUpperCase(java.util.Locale.ROOT);
            if(u.equals("TB"))v*=1024;
            if(u.equals("MB"))v/=1024;
            if(u.equals("GHZ"))v*=1000;
            return v;
        }catch(Exception e){return Double.NaN;}
    }
    private static String unitOf(String raw){
        if(raw==null)return "";
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)[0-9]+(?:[.,][0-9]+)?\\s*(TB|GB|MB|MHZ|GHZ|HZ|MAH|WH)\\b").matcher(raw);
        if(!m.find())return "";
        String u=m.group(1).toUpperCase(java.util.Locale.ROOT);
        if(u.equals("TB")||u.equals("GB")||u.equals("MB"))return "STORAGE";
        if(u.equals("GHZ")||u.equals("MHZ"))return "FREQUENCY";
        return u;
    }

    private TextView highlightCard(Product product,int slot){
        StringBuilder t=new StringBuilder("МОДЕЛ "+slot+"\n");
        if(product==null){
            t.append("Скенирај или внеси шифра.");
        }else if(product.highlights.isEmpty()){
            t.append("Нема потврдени технички детали на Setec.");
        }else{
            for(int i=0;i<product.highlights.size()&&i<5;i++)
                t.append("\n• ").append(product.highlights.get(i));
        }
        TextView v=text(t.toString(),13,false,0xFFE9F4FF);
        v.setPadding(dp(10),dp(12),dp(10),dp(12));
        v.setMinHeight(dp(135));
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xFF15283B);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1),0xFF2C5575);
        v.setBackground(bg);
        return v;
    }

    private String subtitle(Product p){
        return p.sku==null||p.sku.isEmpty()
            ?"Setec шифра: непотврдена":"Setec шифра: "+p.sku;
    }
    private static String known(String value){
        return value==null||value.trim().isEmpty()?"Непотврдено":value.trim();
    }
    private static String price(String amount){
        if(amount==null||amount.isEmpty())return "Непотврдено";
        return amount+" ден.";
    }

    private void row(String label,String a,String b,boolean spec){
        TextView heading=text((spec?"• ":"")+label,12,true,theme(0xFF4E5764,0xFFB6C3D3));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
        hp.topMargin=dp(13);
        table.addView(heading,hp);
        LinearLayout values=new LinearLayout(this);
        values.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2);
        vp.topMargin=dp(4);
        table.addView(values,vp);
        boolean different=spec&&!a.equalsIgnoreCase(b)&&!"Непотврдено".equals(a)&&!"Непотврдено".equals(b)&&!"—".equals(a)&&!"—".equals(b);
        values.addView(valueBox(a,different),columnParams(false));
        values.addView(valueBox(b,different),columnParams(true));
    }

    private TextView valueBox(String value,boolean different){
        TextView tv=text(value,14,true,0xFFEAF5FF);
        tv.setGravity(Gravity.CENTER);
        tv.setMinHeight(dp(57));
        tv.setPadding(dp(8),dp(10),dp(8),dp(10));
        GradientDrawable background=new GradientDrawable();
        background.setColor(different?0xFF163B4B:0xFF142335);
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1),different?0xFF16B8B1:0xFF2C4A65);
        tv.setBackground(background);
        return tv;
    }

    private TextView columnCard(String name,String subtitle){
        TextView v=text(name+"\n"+subtitle,13,true,0xFFEAF5FF);
        v.setMinHeight(dp(102));
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(7),dp(12),dp(7),dp(12));
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xFF15283B);
        bg.setCornerRadius(dp(11));
        bg.setStroke(dp(1),0xFF2C5575);
        v.setBackground(bg);
        return v;
    }

    private LinearLayout.LayoutParams columnParams(boolean second){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        if(second)p.leftMargin=dp(7);
        return p;
    }

    private void addSource(LinearLayout host,Product product,boolean second){
        String source=safeSource(product.source)?product.source:null;
        for(String url:product.sourceByLabel.values()){
            if(safeSource(url)){source=url;break;}
        }
        TextView link=text(source==null?"Извор: непотврден":"Извор ↗",12,true,
            source==null?theme(0xFF7B8493,0xFFB6C3D3):0xFFE17319);
        link.setGravity(Gravity.CENTER);
        link.setPadding(dp(6),dp(10),dp(6),dp(10));
        host.addView(link,columnParams(second));
        final String url=source;
        if(url!=null)link.setOnClickListener(v->{
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
            catch(Exception ignored){}
        });
    }

    private TextView text(String s,int size,boolean bold,int color){
        TextView v=new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        if(bold)v.setTypeface(null,Typeface.BOLD);
        return v;
    }
    private Button button(String title,int bg){
        Button b=new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setTypeface(null,Typeface.BOLD);
        b.setBackgroundTintList(ColorStateList.valueOf(bg));
        return b;
    }
    private int dp(int n){
        return Math.round(getResources().getDisplayMetrics().density*n);
    }
}
