package mk.seteczaliha.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
    private static final int INK=0xFF202531, ORANGE=0xFFF58220;
    private final Product[] products=new Product[2];
    private final Button[] scanButtons=new Button[2];
    private ActivityResultLauncher<Intent> picker, skuScanner;
    private LinearLayout content,table;
    private TextView overview;
    private int editingSlot=0;

    private static final class Product {
        String name,club,regular,sku;
        Map<String,String> facts=new LinkedHashMap<>();
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
                JSONArray arr=obj.optJSONArray("facts");
                if(arr!=null)for(int i=0;i<arr.length();i++){
                    JSONObject f=arr.optJSONObject(i);
                    if(f==null)continue;
                    String label=f.optString("label").trim(),value=f.optString("value").trim();
                    if(label.length()>55||value.length()>110||label.isEmpty()||value.isEmpty())continue;
                    p.facts.put(label,value);
                    String url=f.optString("url");
                    if(safeSource(url))p.sourceByLabel.put(label,url);
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
        getWindow().setStatusBarColor(0xFF1B1C21);
        getWindow().setNavigationBarColor(Color.BLACK);
        skuScanner=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result->{
            if(result.getResultCode()!=RESULT_OK||result.getData()==null)return;
            String code=result.getData().getStringExtra(ScannerActivity.EXTRA_SKU);
            if(code==null||!code.matches("[0-9]{4,8}")){
                overview.setText("Не се прочита валидна SETEC шифра. Пробај повторно.");
                return;
            }
            Intent intent=new Intent(this,ModelScannerActivity.class);
            intent.putExtra("compare_pick",true);
            intent.putExtra("compare_slot",editingSlot+1);
            intent.putExtra("compare_sku",code);
            picker.launch(intent);
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
        screen.setBackgroundColor(0xFFF6F7FA);
        setContentView(screen);

        LinearLayout header=new LinearLayout(this);
        header.setBackgroundColor(0xFF1C1C21);
        header.setPadding(dp(9),dp(10),dp(9),dp(11));
        header.setGravity(Gravity.CENTER_VERTICAL);
        screen.addView(header);
        Button back=button("‹ Назад",0xFF3C3C43);
        header.addView(back,new LinearLayout.LayoutParams(dp(90),dp(45)));
        back.setOnClickListener(v->finish());
        TextView title=text("⚖ Спореди модели",19,true,Color.WHITE);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        lp.leftMargin=dp(12);
        header.addView(title,lp);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(13),dp(16),dp(13),dp(32));
        scroll.addView(content);

        overview=text("Скенирај две SETEC шифри. Апликацијата ги наоѓа моделите и ги споредува најважните спецификации само од Setec.mk.",13,false,0xFF626B78);
        content.addView(overview);

        LinearLayout buttons=new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);
        bp.topMargin=dp(16);
        content.addView(buttons,bp);
        for(int i=0;i<2;i++){
            final int slot=i;
            Button b=button("▣ Скенирај шифра "+(i+1),ORANGE);
            scanButtons[i]=b;
            LinearLayout.LayoutParams bParams=new LinearLayout.LayoutParams(0,dp(58),1);
            if(i>0)bParams.leftMargin=dp(8);
            buttons.addView(b,bParams);
            b.setOnClickListener(v->openScanner(slot));
        }

        TextView heading=text("СПОРЕДБА ЛЕВО / ДЕСНО",15,true,INK);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
        hp.topMargin=dp(22);
        content.addView(heading,hp);

        table=new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);
        tp.topMargin=dp(8);
        content.addView(table,tp);

        TextView note=text("Моделите, шифрите, цените и прикажаните спецификации се исклучиво од Setec.mk. "+
            "Непотврдено значи дека не е најдена сигурна вредност, не дека производот ја нема функцијата.",
            12,false,0xFF687180);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);
        np.topMargin=dp(16);
        content.addView(note,np);
        render();
    }

    private void openScanner(int slot){
        editingSlot=slot;
        // Reuse the existing dedicated SETEC number scanner. It reads only
        // 4–8 digit retail codes, not the surrounding model text or prices.
        skuScanner.launch(new Intent(this,ScannerActivity.class));
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
            table.addView(text("Прво скенирај ја SETEC шифрата за левиот производ, па за десниот.",13,false,0xFF657081));
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
        if(left==null||right==null)return;
        List<String> rows=ComparisonSummary.topSpecs(
            left.name,left.facts,right.name,right.facts);
        if(rows.isEmpty()){
            TextView empty=text("За овие модели не се најдени проверени технички спецификации. "+
                "Пробај повторно да ги скенираш или внеси ја точната ознака.",13,false,0xFF835A2D);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.topMargin=dp(13);
            table.addView(empty,p);
            return;
        }
        for(String key:rows){
            row(key,known(left.facts.get(key)),known(right.facts.get(key)),true);
        }
        TextView count=text("Прикажани "+rows.size()+" најважни спецификации (најмногу 5). "+
                "Ознаката ≠ покажува различни пријавени вредности, не автоматски победник.",
                12,false,0xFF707887);
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
        TextView heading=text((spec?"• ":"")+label,12,true,0xFF4E5764);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
        hp.topMargin=dp(13);
        table.addView(heading,hp);
        LinearLayout values=new LinearLayout(this);
        values.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2);
        vp.topMargin=dp(4);
        table.addView(values,vp);
        boolean different=!a.equalsIgnoreCase(b)&&!"Непотврдено".equals(a)&&!"Непотврдено".equals(b)&&!"—".equals(a)&&!"—".equals(b);
        values.addView(valueBox(a,different),columnParams(false));
        values.addView(valueBox(b,different),columnParams(true));
    }

    private TextView valueBox(String value,boolean different){
        TextView tv=text(value,14,true,INK);
        tv.setGravity(Gravity.CENTER);
        tv.setMinHeight(dp(57));
        tv.setPadding(dp(8),dp(10),dp(8),dp(10));
        GradientDrawable background=new GradientDrawable();
        background.setColor(different?0xFFFFF0DF:Color.WHITE);
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1),different?0xFFF9C78B:0xFFE4E7ED);
        tv.setBackground(background);
        return tv;
    }

    private TextView columnCard(String name,String subtitle){
        TextView v=text(name+"\n"+subtitle,13,true,INK);
        v.setMinHeight(dp(102));
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(7),dp(12),dp(7),dp(12));
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(11));
        bg.setStroke(dp(1),0xFFE2E6ED);
        v.setBackground(bg);
        return v;
    }

    private LinearLayout.LayoutParams columnParams(boolean second){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        if(second)p.leftMargin=dp(7);
        return p;
    }

    private void addSource(LinearLayout host,Product product,boolean second){
        String source=null;
        for(String url:product.sourceByLabel.values()){
            if(safeSource(url)){source=url;break;}
        }
        TextView link=text(source==null?"Извор: непотврден":"Извор ↗",12,true,
            source==null?0xFF7B8493:0xFFE17319);
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
