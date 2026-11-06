'use strict';
(function(){
 const $=id=>document.getElementById(id);
 const roots=Array.isArray(window.SETEC_OFFICIAL_CATEGORIES)?window.SETEC_OFFICIAL_CATEGORIES:[];
 const snapshot=window.SETEC_WEB_PRODUCTS||{updated:'',categories:{}};
 const byCategory=snapshot.categories&&typeof snapshot.categories==='object'?snapshot.categories:{};
 const catTrail=[];let catPage=1,previousPane='search',currentProduct=null,searchText='';
 const comparison=[],pcParts=[];
 const rates={3:1.99,6:2.49,9:2.79,12:2.99,15:3.99,18:4.99,24:5.99,36:8.99,48:11.99,60:14.99};
 const storageKey='setec_tools_web_buyer_list_v1';
 const money=n=>new Intl.NumberFormat('mk-MK',{maximumFractionDigits:2,minimumFractionDigits:0}).format(n)+' ден.';
 function priceNum(v){
  if(typeof v==='number')return Number.isFinite(v)?v:NaN;
  let x=String(v||'').trim().replace(/[^\d.,]/g,'');
  if(!x)return NaN;
  if(/^\d+[,.]\d{1,2}$/.test(x))x=x.replace(',','.');
  else x=x.replace(/[.,]/g,'');
  return Number(x);
 }
 function safeSite(raw,prefix='/products/'){
  try{const u=new URL(raw);return u.protocol==='https:'&&/^(?:www\.)?setec\.mk$/i.test(u.hostname)&&u.pathname.startsWith(prefix)?u.href:''}catch(e){return ''}
 }
 function safeImg(raw){
  try{const u=new URL(raw);return u.protocol==='https:'&&(/^(?:www\.)?setec\.mk$/i.test(u.hostname)||u.hostname.endsWith('.digitaloceanspaces.com'))?u.href:''}catch(e){return ''}
 }
 function el(tag,cls,text){
  const a=document.createElement(tag);
  if(cls)a.className=cls;
  if(text!==undefined)a.textContent=text;
  return a;
 }
 function keepStore(value){try{localStorage.setItem(storageKey,JSON.stringify(value))}catch(e){}}
 function loadStore(){try{const v=JSON.parse(localStorage.getItem(storageKey)||'[]');return Array.isArray(v)?v.filter(x=>x&&safeSite(x.url)&&Number.isInteger(x.qty)&&x.qty>0).slice(0,200):[]}catch(e){return []}}
 const basket=loadStore();
 const allProducts=new Map();
 for(const [slug,items] of Object.entries(byCategory)){
  if(!Array.isArray(items))continue;
  for(const p of items){
   if(!p||!safeSite(p.url))continue;
   const product={url:safeSite(p.url),name:String(p.name||'Производ').slice(0,220),sku:String(p.sku||'').slice(0,24),
    img:safeImg(p.img),clubPrice:String(p.clubPrice||'').slice(0,24),regularPrice:String(p.regularPrice||'').slice(0,24),
    category:slug};
   if(!allProducts.has(product.url))allProducts.set(product.url,product);
  }
 }
 const products=[...allProducts.values()];
 function pane(name){
  document.querySelectorAll('.pane').forEach(p=>p.classList.toggle('active',p.id==='pane-'+name));
  document.querySelectorAll('.navigation button').forEach(b=>b.classList.toggle('active',b.dataset.target===name));
  if(name==='list')renderBasket();
  if(name==='compare')renderCompare();
  if(name==='pc')renderPc();
  if(name==='categories')renderCategories();
  if(name==='search')renderSearch();
  window.scrollTo(0,0);
 }
 document.querySelectorAll('[data-target]').forEach(x=>x.addEventListener('click',()=>pane(x.dataset.target)));
 function catName(node){return String(node?.name||'Категорија')}
 function children(node){return Array.isArray(node?.children)?node.children:[]}
 function slugProducts(node){return Array.isArray(byCategory[node?.slug])?byCategory[node.slug].filter(x=>safeSite(x.url)):[]}
 function nodeButton(node,callback){
  const btn=el('button','cat');
  const ico=el('span','emoji',node?.icon||'▦');
  const middle=el('span','catText');
  middle.append(el('b','',catName(node)),el('small','',children(node).length?children(node).length+' подкатегории':slugProducts(node).length+' зачувани производи'));
  btn.append(ico,middle,el('span','chevron','›'));btn.addEventListener('click',callback);
  return btn;
 }
 function rootShortcuts(){
  const h=$('rootShortcut');h.replaceChildren();
  for(const root of roots.slice(0,12))h.appendChild(nodeButton(root,()=>{catTrail.length=0;catTrail.push(root);catPage=1;pane('categories')}));
 }
 const webCount=products.length,covered=Object.keys(byCategory).filter(x=>Array.isArray(byCategory[x])&&byCategory[x].length).length;
 $('homeSummary').textContent=roots.length+' главни категории · '+webCount+' зачувани производи за веб-пребарување';
 $('dataAge').textContent=snapshot.updated?'Последно ажурирање на зачуваните податоци: '+snapshot.updated+'.':'Потребно е освежување на веб-каталогот.';
 rootShortcuts();
 function renderCategories(){
  const box=$('categoryChildren'),crumb=$('crumbs'),list=$('categoryProducts'),head=$('categoryProductsLabel'),pager=$('pager');
  box.replaceChildren();crumb.replaceChildren();list.replaceChildren();pager.replaceChildren();
  const home=el('button','','Сите категории');home.addEventListener('click',()=>{catTrail.length=0;catPage=1;renderCategories()});crumb.appendChild(home);
  const node=catTrail.length?catTrail[catTrail.length-1]:null;
  if(!node){
   $('categoryNotice').textContent='Избери од '+roots.length+' главни категории. Подкатегориите се од официјалниот каталог на Setec.';
   for(const root of roots)box.appendChild(nodeButton(root,()=>{catTrail.push(root);catPage=1;renderCategories()}));
   head.textContent='';return;
  }
  catTrail.forEach((n,i)=>{
   crumb.appendChild(el('span','','/'));
   if(i===catTrail.length-1)crumb.appendChild(el('strong','',n.name));
   else{const c=el('button','',n.name);c.addEventListener('click',()=>{catTrail.splice(i+1);catPage=1;renderCategories()});crumb.appendChild(c)}
  });
  const kids=children(node);
  for(const child of kids)box.appendChild(nodeButton(child,()=>{catTrail.push(child);catPage=1;renderCategories()}));
  const items=slugProducts(node);
  head.textContent='Производи · '+node.name;
  $('categoryNotice').textContent=kids.length+' подкатегории · '+items.length+' производи во зачуваниот веб-каталог. За најнова цена и залиха потребна е проверка кај Setec.';
  if(!items.length){
   list.appendChild(el('div','empty','За оваа категорија нема зачувани производи во веб-верзијата. Категоријата и нејзините подкатегории сепак се достапни.'));
  }else{
   const from=(catPage-1)*20;
   for(const p of items.slice(from,from+20))list.appendChild(productCard(p));
   const pages=Math.ceil(items.length/20);
   if(pages>1){
    const prev=el('button','','‹ Претходна');prev.disabled=catPage===1;prev.onclick=()=>{catPage--;renderCategories()};
    const label=el('span','','Страница '+catPage+' / '+pages);
    const next=el('button','','Следна ›');next.disabled=catPage===pages;next.onclick=()=>{catPage++;renderCategories()};
    pager.append(prev,label,next);
   }
  }
 }
 $('categoryHomeBtn').addEventListener('click',()=>{catTrail.length=0;catPage=1;renderCategories()});
 function priceRow(p){
  const box=el('div','price');
  for(const [key,label,klass] of [['clubPrice','Клуб',''],['regularPrice','Редовна','regular']]){
   const n=priceNum(p[key]);if(!Number.isFinite(n)||n<=0)continue;
   const span=el('span',klass,label+': ');span.appendChild(el('b','',money(n)));box.appendChild(span);
  }
  if(!box.childNodes.length)box.appendChild(el('span','','Цена: непотврдена'));
  return box;
 }
 function productCard(p){
  const card=el('div','resultCard'),pic=el('div','pic'),body=el('div','resultMain'),actions=el('div','resultActions');
  const img=safeImg(p.img);
  if(img){const image=el('img');image.src=img;image.alt='';image.loading='lazy';pic.appendChild(image)}
  else pic.textContent='SETEC';
  body.append(el('b','',p.name),el('small','',p.sku?'Шифра: '+p.sku:'SETEC производ'),priceRow(p));
  const info=el('button','','Детали');info.addEventListener('click',()=>viewProduct(p));
  const add=el('button','buy','+ Листа');add.addEventListener('click',()=>{addBasket(p);add.textContent='✓ Додадено'});
  actions.append(info,add);card.append(pic,body,actions);return card;
 }
 function renderSearch(){
  const q=searchText.toLocaleLowerCase().trim(),box=$('searchResults');
  box.replaceChildren();
  let matches=q?products.filter(p=>(p.name+' '+p.sku).toLocaleLowerCase().includes(q)):products.slice(0,50);
  matches=matches.slice(0,160);
  $('searchQuery').value=searchText;
  $('searchCount').textContent=matches.length+' резултати во зачуваниот веб-каталог ('+webCount+' вкупно).';
  if(!matches.length)box.appendChild(el('div','empty','Нема резултати во зачуваниот веб-каталог. Пробај со друг збор или пократка шифра.'));
  else for(const p of matches)box.appendChild(productCard(p));
 }
 function doSearch(q){
  searchText=String(q||'').trim();pane('search');
 }
 $('homeForm').addEventListener('submit',e=>{e.preventDefault();doSearch($('homeQuery').value)});
 $('searchForm').addEventListener('submit',e=>{e.preventDefault();doSearch($('searchQuery').value)});
 function viewProduct(p){
  previousPane=document.querySelector('.pane.active')?.id.replace('pane-','')||'search';
  currentProduct=p;
  const root=$('productDetail');root.replaceChildren();
  root.append(el('h2','',p.name),el('p','subtle',p.sku?'SETEC шифра: '+p.sku:'Нема потврдена шифра во кешот'),priceRow(p));
  const n=el('div','notice','Достапноста по продавници и актуелните цени не можат да се потврдат од зачуваниот веб-каталог.');
  root.appendChild(n);
  const actions=el('div','controls');
  const add=el('button','','+ Додај во листа');add.onclick=()=>{addBasket(p);add.textContent='✓ Додадено'};
  const comp=el('button','','⚖ Спореди');comp.onclick=()=>{addCompare(p);pane('compare')};
  const pc=el('button','','▣ Додај во PC');pc.onclick=()=>{pcParts.push(p);renderPc();pane('pc')};
  actions.append(add,comp,pc);
  const official=safeSite(p.url);
  if(official){const a=el('a','','Проверка на Setec.mk ↗');a.href=official;a.target='_blank';a.rel='noopener noreferrer';actions.appendChild(a)}
  root.appendChild(actions);pane('product');
 }
 $('backProduct').onclick=()=>pane(previousPane);
 function addBasket(p){
  const found=basket.find(x=>x.url===p.url);
  if(found)found.qty=Math.min(999,found.qty+1);
  else basket.push({url:p.url,name:p.name,sku:p.sku,img:p.img,clubPrice:p.clubPrice,regularPrice:p.regularPrice,qty:1});
  keepStore(basket);
 }
 function listTotals(){
  const result={club:0,regular:0,missing:0};
  for(const item of basket){
   const c=priceNum(item.clubPrice),r=priceNum(item.regularPrice);
   if(Number.isFinite(c)&&c>0)result.club+=c*item.qty;else result.missing+=item.qty;
   if(Number.isFinite(r)&&r>0)result.regular+=r*item.qty;
  }
  return result;
 }
 function renderBasket(){
  const host=$('listRows');host.replaceChildren();
  if(!basket.length)host.appendChild(el('div','empty','Листата е празна. Додај производ од пребарувањето.'));
  for(const item of basket){
   const row=el('div','listRow'),title=el('span','listName'),cnt=el('span','qty');
   title.append(el('b','',item.name),el('small','',item.sku?'Шифра: '+item.sku:''),priceRow(item));
   const minus=el('button','','−'),plus=el('button','','+'),qty=el('strong','',String(item.qty));
   minus.onclick=()=>{if(item.qty<=1)basket.splice(basket.indexOf(item),1);else item.qty--;keepStore(basket);renderBasket()};
   plus.onclick=()=>{item.qty=Math.min(999,item.qty+1);keepStore(basket);renderBasket()};
   cnt.append(minus,qty,plus);row.append(title,cnt);host.appendChild(row);
  }
  const t=listTotals();
  $('clubTotal').textContent=money(t.club)+(t.missing?' + непотврдени':'');
  $('regularTotal').textContent=money(t.regular);
  renderInstallments();
 }
 function renderInstallments(){
  const box=$('installments');box.replaceChildren();
  const t=listTotals(),selected=$('months').value,months=selected==='48-zero'?48:Number(selected);
  const rate=selected==='48-zero'?0:rates[months];
  if(!t.club||t.missing){box.appendChild(el('div','notice',t.missing?'Пресметка не е можна: '+t.missing+' ставки немаат клуб цена.':'Додај производ со клуб цена за да пресметаш рати.'));return}
  const admin=t.club*.03,interest=t.club*(rate/100),total=t.club+admin+interest;
  const rows=[
   ['Клуб цена',money(t.club)],['Административен трошок (3%)',money(admin)],
   [selected==='48-zero'?'Камата (0% · без камата)':'Камата ('+rate.toFixed(2).replace('.',',')+'%)',money(interest)],
   ['Вкупно за плаќање',money(total)],['Месечна рата ('+months+' ×)',money(total/months)]
  ];
  for(const [key,val] of rows){const row=el('div','row');row.append(el('span','',key),el('strong','',val));box.appendChild(row)}
 }
 $('months').addEventListener('change',renderInstallments);
 $('copyList').onclick=()=>{
  const t=listTotals();
  const out=['SETEC TOOLS · Листа на купувач',''];
  basket.forEach(x=>out.push(x.qty+' × '+x.name+(x.sku?' ('+x.sku+')':'')+' — '+(Number.isFinite(priceNum(x.clubPrice))?money(priceNum(x.clubPrice)):'Цена: непотврдено')));
  out.push('','Клуб цена вкупно: '+money(t.club),'Цените и условите се информативни.');
  const txt=out.join('\n');
  if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(txt).then(()=>alert('Понудата е копирана.')).catch(()=>manualCopy(txt))}
  else manualCopy(txt);
 };
 function manualCopy(txt){
  const t=el('textarea');t.value=txt;t.style.cssText='position:fixed;left:-10000px;top:0';document.body.appendChild(t);t.select();
  let ok=false;try{ok=document.execCommand('copy')}catch(e){}t.remove();
  alert(ok?'Понудата е копирана.':'Копирањето е ограничено од прелистувачот. Користи ја листата на екранот.');
 }
 function addCompare(p){
  if(comparison.some(x=>x.url===p.url))return;
  if(comparison.length>=2)comparison.shift();comparison.push(p);
 }
 function renderCompare(){
  const host=$('compareContent');host.replaceChildren();
  if(!comparison.length)host.appendChild(el('div','empty','Нема производи за споредување. Додај од пребарување.'));
  for(const p of comparison){
   const box=el('div','box');
   box.append(el('h3','',p.name),el('p','subtle',p.sku?'Шифра: '+p.sku:''),priceRow(p));
   const clear=el('button','smallBtn','Отстрани');clear.onclick=()=>{comparison.splice(comparison.indexOf(p),1);renderCompare()};
   box.appendChild(clear);host.appendChild(box);
  }
  if(comparison.length===2){
   const a=priceNum(comparison[0].clubPrice),b=priceNum(comparison[1].clubPrice);
   if(Number.isFinite(a)&&Number.isFinite(b)){
    const dif=Math.abs(a-b);
    const line=el('div','summary');line.append(el('small','','Разлика во клуб цените'),el('b','',money(dif)));host.appendChild(line);
   }
  }
 }
 function renderPc(){
  const host=$('pcContent');host.replaceChildren();
  if(!pcParts.length)host.appendChild(el('div','empty','Додај компоненти од пребарувањето.'));
  for(const p of pcParts){
   const row=el('div','box');row.append(el('b','',p.name),priceRow(p));
   const remove=el('button','smallBtn','Отстрани');remove.onclick=()=>{pcParts.splice(pcParts.indexOf(p),1);renderPc()};row.append(remove);host.appendChild(row);
  }
  const sum=pcParts.reduce((a,p)=>a+(Number.isFinite(priceNum(p.clubPrice))?priceNum(p.clubPrice):0),0);
  const res=el('div','summary');res.append(el('small','','Вкупна потврдена клуб цена на избраните компоненти'),el('b','',money(sum)));
  host.appendChild(res);
 }
 renderSearch();renderCategories();renderBasket();
})();
