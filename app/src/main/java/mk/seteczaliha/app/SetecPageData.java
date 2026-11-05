package mk.seteczaliha.app;

import java.net.URI;
import java.util.Locale;

/**
 * Setec-only product detail extraction in the already-loaded product WebView.
 * Never leaves the domain, never uses Google, Bing or independent sources.
 */
final class SetecPageData {
    private SetecPageData(){}

    static boolean isOfficialProductUrl(String url) {
        if(url==null)return false;
        try {
            URI u=URI.create(url);
            String host=u.getHost();
            return "https".equalsIgnoreCase(u.getScheme())
                &&host!=null&&("setec.mk".equalsIgnoreCase(host)||
                   "www.setec.mk".equalsIgnoreCase(host)||
                   "set.mk".equalsIgnoreCase(host)||
                   "www.set.mk".equalsIgnoreCase(host))
                &&u.getPath()!=null&&u.getPath().startsWith("/products/");
        }catch(Exception e){return false;}
    }

    /** Serialized string literal from evaluateJavascript is decoded by the Activity. */
    static final String READ_PRODUCT_SCRIPT = """
(function(){
 try {
  const body = document.body?.innerText || '';
  const h1 = (document.querySelector('h1')?.innerText || '').replace(/\\s+/g,' ').trim();
  const skuMatch = body.match(/(?:Шифра|SKU)\\s*:?\\s*(\\d{4,8})/i);
  const sku = skuMatch ? skuMatch[1] : '';
  const price = label => {
    const m = body.match(new RegExp(label+'\\\\s*:?\\\\s*([\\\\d., ]+)\\\\s*ден','i'));
    return m ? m[1].replace(/\\s/g,'') : '';
  };
  const tabs=[...document.querySelectorAll('button,[role=tab],a')];
  const tab=tabs.find(n=>{
    const text=(n.innerText||n.textContent||'').replace(/\\s+/g,' ').trim();
    return /^Технички спецификации$/i.test(text);
  });
  if(tab && !tab.dataset.setecScannerClicked) {
    tab.dataset.setecScannerClicked='1';
    try {tab.click();}catch(e){}
  }
  let specs='';
  // Isolate ONLY the technical specifications portion, not the full product
  // page: prices, warranty, installments and related products live elsewhere.
  const heading=/Технички\\s*спецификации/i;
  const matches=[...body.matchAll(new RegExp(heading.source,'gi'))];
  for(let k=matches.length-1;k>=0;k--){
    let tail=body.substring(matches[k].index+matches[k][0].length);
    if(tail.length<15)continue;
    const end=tail.search(/\\n\\s*(?:Оцени|Рецензии|Слични производи|Поврзани производи|Препорачани производи|Сетек Сè од Техника не одговара|Информации за испорака)\\b/i);
    if(end>=0)tail=tail.substring(0,end);
    tail=tail.trim().slice(0,7000);
    if(tail.length>15){specs=tail;break;}
  }
  return JSON.stringify({name:h1,sku:sku,club:price('Клуб цена'),
                         regular:price('Редовна цена'),specs:specs});
 }catch(e){return '{}';}
})()
""";
}
