package mk.seteczaliha.app;

import java.net.URI;

/** Reads ONLY the loaded Setec product page, without search engines or AI. */
final class SetecPageData {
    private SetecPageData(){}

    static boolean isOfficialProductUrl(String url){
        if(url==null)return false;
        try{
            URI u=URI.create(url);
            String host=u.getHost();
            return "https".equalsIgnoreCase(u.getScheme()) && host!=null
                &&("setec.mk".equalsIgnoreCase(host)||
                   "www.setec.mk".equalsIgnoreCase(host)||
                   "set.mk".equalsIgnoreCase(host)||
                   "www.set.mk".equalsIgnoreCase(host))
                &&u.getPath()!=null&&u.getPath().startsWith("/products/");
        }catch(Exception e){return false;}
    }

    /**
     * Extract product identity + club/regular prices and THREE independent text
     * regions of the very SAME product page:
     *  - Technical specifications tab
     *  - The short product description
     *  - H1 product title (frequently a long specs list on Setec)
     *
     * No AI, no cross-product recommendations, no Google, no other domains.
     */
    static final String READ_PRODUCT_SCRIPT = """
(function(){
  try{
    const body=document.body?.innerText||'';
    const h1=(document.querySelector('h1')?.innerText||'').replace(/\\s+/g,' ').trim();
    const sku=(body.match(/(?:Шифра|SKU)\\s*:?\\s*(\\d{4,8})/i)||[])[1]||'';
    const price=label=>{
      const m=body.match(new RegExp(label+'\\\\s*:?\\\\s*([\\\\d., ]+)\\\\s*ден','i'));
      return m?m[1].replace(/\\s/g,''):'';
    };
    const afterTitle=body.indexOf(h1);
    const productTop=afterTitle>=0?body.substring(afterTitle,afterTitle+4500):body.substring(0,4500);
    const shortMatch=productTop.match(/Краток\\s*опис\\s*:\\s*([\\s\\S]{0,2400}?)(?=\\n\\s*(?:Шифра|SKU|Гаранција|Клуб\\s*цена|Редовна\\s*цена|Cashback|Во\\s*кошничка)\\b|$)/i);
    let short=shortMatch?shortMatch[1].trim().slice(0,2200):'';
    if(!short){
      const el=[...document.querySelectorAll('p,div,span')]
        .find(x=>x.children.length<2 && /^Краток\\s*опис\\s*:/i.test((x.innerText||'').trim())
                    && (x.innerText||'').length<2200);
      if(el)short=(el.innerText||'').replace(/^Краток\\s*опис\\s*:/i,'').trim();
    }

    const heading=/Технички\\s*спецификации|Technical\\s*specifications/i;
    const end=/\\n\\s*(?:Оцени|Рецензии|Слични\\s*производи|Поврзани\\s*производи|Препорачани\\s*производи|Достава|Кредит|Сетек\\s*Сè\\s*од\\s*Техника\\s*не\\s*одговара|Newsletter|Претплатете)\\b/i;
    let specs='';
    // Use the LAST occurrence after the product heading, to avoid menu references.
    let pos=-1;
    for(const m of body.matchAll(/Технички\\s*спецификации|Technical\\s*specifications/gi))
      if(m.index>afterTitle)pos=m.index+m[0].length;
    if(pos>=0){
      let tail=body.substring(pos,pos+8000);
      const cut=tail.search(end);
      if(cut>=0)tail=tail.substring(0,cut);
      specs=tail.trim().slice(0,5500);
    }
    // Some Setec variants load the specification panel dynamically. Click it
    // ONCE per page, not on every later evaluation (which used to toggle it
    // closed). Delayed reads from the Activity observe the loaded content.
    if(specs.length<22 && !window.__setecSpecsOpened){
      const tab=[...document.querySelectorAll('button,[role=tab],a')].find(n=>{
        const t=(n.innerText||n.textContent||'').replace(/\\s+/g,' ').trim();
        return /^Технички\\s*спецификации$|^Technical\\s*specifications$/i.test(t);
      });
      if(tab){
        window.__setecSpecsOpened=true;
        try{tab.click()}catch(e){}
      }
    }
    // Extract tab panel directly if available. It may not include a heading.
    const panels=[...document.querySelectorAll(
      '[role=tabpanel],.tab-pane,[class*="specifications"],[id*="specifications"]'
    )];
    for(const p of panels){
      const txt=(p.innerText||'').trim();
      if(txt.length>specs.length && txt.length>=25 && txt.length<7500 &&
         !/Клуб\\s*цена|Редовна\\s*цена/.test(txt.substring(0,220)))
          specs=txt.slice(0,5500);
    }
    return JSON.stringify({
      name:h1,sku:sku,club:price('Клуб цена'),regular:price('Редовна цена'),
      specs:specs,short:short
    });
  }catch(e){return '{}'}
})()
""";
}
