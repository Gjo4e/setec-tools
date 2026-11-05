package mk.seteczaliha.app;

import java.net.URI;

/** Reads only an exact loaded Setec product page; no AI or external search. */
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
     * Reads original product identity, prices, breadcrumbs and technical
     * specification panels (including collapsed content rendered in the DOM).
     * Never uses global catalog navigation or recommended-product cards.
     */
    static final String READ_PRODUCT_SCRIPT = """
(function() {
  try {
    if (!/^https:\\/\\/(?:www\\.)?(?:setec|set)\\.mk\\/products\\//i.test(location.href))
      return '{}';
    const compact = s => String(s || '').replace(/\\u00a0/g, ' ').replace(/[ \\t]+/g, ' ').trim();
    const textOf = el => compact(el?.innerText || el?.textContent || '');
    const body = document.body?.innerText || '';
    const h1 = textOf(document.querySelector('h1'));
    if (h1.length < 3) return '{}';
    const headingIndex = body.indexOf(h1);
    const area = headingIndex >= 0 ? body.slice(headingIndex, headingIndex + 7000) : body.slice(0, 5000);
    const sku = (area.match(/(?:Шифра|SKU)\\s*:?\\s*(\\d{4,8})/i) || [])[1] || '';
    const price = label => {
      const m = area.match(new RegExp(label + '\\\\s*:?\\\\s*([\\\\d., ]+)\\\\s*ден', 'i'));
      return m ? m[1].replace(/\\s/g, '') : '';
    };
    let category = '';
    const breadcrumbs = Array.from(document.querySelectorAll(
      '[class*="breadcrumb" i],nav[aria-label*="breadcrumb" i],[aria-label="Breadcrumb"]'
    ));
    for (const el of breadcrumbs) {
      const nodes = Array.from(el.querySelectorAll('a,li,span')).filter(n => n.children.length === 0)
        .map(textOf).filter(s => s.length > 1 && s.length < 65);
      if (nodes.length >= 2 && nodes.join(' / ').length > category.length &&
          nodes.join(' / ').length < 180) category = nodes.join(' / ');
    }
    if (!category) {
      const m = body.slice(0, Math.max(0, headingIndex)).match(/Дома\\s*\\/\\s*([^\\n]{4,140})/);
      if (m) category = m[1];
    }
    category = category.slice(0, 170);

    // The short description often repeats ONLY the product model. The real
    // details live under Technical Specifications; never conflate those.
    let short = (area.match(/Краток\\s*опис\\s*:?\\s*([^\\n\\r]{1,1500})/i) || [])[1] || '';
    short = compact(short).slice(0, 1600);

    function readTechnical(node) {
      if (!node) return '';
      // List items and table rows keep their boundaries even if an accordion
      // panel is hidden and innerText is empty. textContent is still present.
      const lis = Array.from(node.querySelectorAll('li')).map(textOf)
        .filter(x => x.length > 1 && x.length < 350);
      if (lis.length >= 2 && lis.length <= 110) return lis.join('\\n').slice(0, 6300);
      const rows = Array.from(node.querySelectorAll('tr')).map(textOf)
        .filter(x => x.length > 2 && x.length < 350);
      if (rows.length >= 2 && rows.length <= 100) return rows.join('\\n').slice(0, 6300);
      const paras = Array.from(node.querySelectorAll('p')).map(textOf)
        .filter(x => x.length > 2 && x.length < 330);
      if (paras.length >= 3 && paras.length <= 100) return paras.join('\\n').slice(0, 6300);
      return compact(node.innerText || node.textContent || '').slice(0, 6300);
    }
    const candidates = [];
    function offer(node, priority) {
      if (!node || !node.isConnected) return;
      const text = readTechnical(node);
      if (text.length < 18 || text.length > 6300) return;
      const top = text.slice(0, 350);
      if (/Клуб\\s*цена|Редовна\\s*цена|Cashback|Во\\s*кошничка|Препорачани\\s*производи/i.test(top))
        return;
      const count = text.split('\\n').filter(Boolean).length;
      // A real panel normally contains a list of specs, not just a label.
      const score = priority * 1000 + Math.min(500, text.length) + Math.min(count, 60) * 8;
      candidates.push({ text, score });
    }

    const specsLabel = /^(?:Технички\\s*спецификации|Technical\\s*specifications|Карактеристики|Specifications)$/i;
    const tabs = Array.from(document.querySelectorAll(
      'button,[role="tab"],summary,a,h2,h3,h4'
    )).filter(n => specsLabel.test(textOf(n)));
    for (const tab of tabs) {
      const id = tab.getAttribute('aria-controls') ||
        tab.getAttribute('data-bs-target') || tab.getAttribute('data-target') ||
        (tab.getAttribute('href') || '').replace(/^#/, '');
      if (id) offer(document.getElementById(id.replace(/^#/, '')), 10);
      offer(tab.nextElementSibling, 8);
      offer(tab.parentElement?.nextElementSibling, 7);
      offer(tab.parentElement?.parentElement?.nextElementSibling, 5);
      const block = tab.closest('[class*="accordion-item"],[class*="tab-pane"],[class*="specification"]');
      if (block) offer(block, 6);
    }
    // Some pages keep the full specifications inside CSS-hidden tab panels.
    const panels = Array.from(document.querySelectorAll(
      '[role="tabpanel"],.tab-pane,[class*="specification" i],[id*="specification" i],'+
      '[class*="accordion-content" i],[class*="accordion-body" i]'
    ));
    for (const el of panels) offer(el, 4);

    // Last-resort visible text, restricted to the exact page's technical
    // section; never scrape the navigation, offers or related-product cards.
    const re = /(?:Технички\\s*спецификации|Technical\\s*specifications)/gi;
    let pos = -1, m;
    while ((m = re.exec(body)) !== null)
      if (m.index > headingIndex) pos = m.index + m[0].length;
    if (pos >= 0) {
      let tail = body.slice(pos, pos + 6200);
      const stop = tail.search(/\\n\\s*(?:Оцени|Рецензии|Слични\\s*производи|Поврзани\\s*производи|Препорачани|Достава|Клуб\\s*цена|Контакт|Претплатете|Сетек\\s*С[èе]\\s*од\\s*Техника)/i);
      if (stop >= 0) tail = tail.slice(0, stop);
      if (tail.length >= 18) candidates.push({ text: tail.slice(0, 5800), score: 1800 });
    }
    candidates.sort((a, b) => b.score - a.score);
    let specs = candidates.length ? candidates[0].text : '';

    // Open a collapsed accordion only once per loaded page. Activity retries
    // reads after animation / asynchronous data loading.
    if (specs.length < 25 && !window.__setecSpecsOpened && tabs.length) {
      window.__setecSpecsOpened = true;
      try { tabs[0].click(); } catch (e) {}
    }
    return JSON.stringify({
      name: h1, sku, club: price('Клуб цена'), regular: price('Редовна цена'),
      specs: specs.slice(0, 5800), short, category
    });
  } catch (e) {
    return '{}';
  }
})()
""";
}
