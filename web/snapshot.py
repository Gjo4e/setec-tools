"""Build a bounded, public, browser-readable snapshot for the optional desktop web app.
Uses visible product cards on the official SETEC site. Never infers price or stock.
This is a one-time/manual utility, not a continuous crawler.
"""
import asyncio
import datetime
import json
import re
from pathlib import Path
from playwright.async_api import async_playwright, TimeoutError as BrowserTimeout

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "catalog.js"
START = "window.SETEC_OFFICIAL_CATEGORIES="
value = SOURCE.read_text("utf-8").split(START, 1)[1].strip().rstrip(";")
tree = json.loads(value)
BLOCKED = re.compile(
    r"(?i)(оруж|ножев|нож\s|пиштол|петард|бомб|airsoft|knife|knives|weapon|firearm|"
    r"alcohol|алкохол|цигар|tobacco|vape|никотин|nicotine|cannabis|"
    r"марихуан|cbd|казино|gambling|обложув|betting|steroid|стероид)"
)
safe_root_names = {
    "Компјутери и IT опрема", "Преносни Компјутери и Таблети",
    "ТВ/Аудио/Видео", "Телефони/Фото и Навигација", "Бела Техника",
    "Мали Апарати", "Ладење и Греење", "Осветлување и Батерии"
}
node_groups = [x for x in tree if x["name"] in safe_root_names]
selected = []
seen = set()
def add(node,depth):
    if node["slug"] not in seen and not BLOCKED.search(node["name"]):
        seen.add(node["slug"])
        selected.append((node["slug"], depth))
for root in node_groups: add(root,0)
for root in node_groups:
    for node in root.get("children",[]): add(node,1)
for root in node_groups:
    for node in root.get("children",[]):
        for sub in node.get("children",[]): add(sub,2)
# Keep load on the third-party website bounded. Favor familiar electronics.
selected = selected[:92]
pages = []
for i,(slug,depth) in enumerate(selected):
    pages.append((slug,1))
    if i < 24: pages.append((slug,2))
print("Selected categories",len(selected),"requested pages",len(pages),flush=True)

results = {}
finished = 0
lock = asyncio.Lock()
async def run_one(context, semaphore, slug, page_no):
    global finished
    async with semaphore:
        page = await context.new_page()
        url = "https://www.setec.mk/category/" + slug + ("?page=2" if page_no==2 else "")
        try:
            await page.goto(url,wait_until="domcontentloaded",timeout=24000)
            try: await page.wait_for_selector('[data-component="ProductCard"]',timeout=7600)
            except BrowserTimeout: pass
            await page.wait_for_timeout(350)
            items = await page.evaluate(r"""() => {
             let out=[];
             for(const node of document.querySelectorAll('[data-component="ProductCard"]')){
               let anchor=node.querySelector('a[href*="/products/"]');
               if(!anchor)continue;
               let u;try{u=new URL(anchor.href,location.href)}catch(e){continue}
               if(!/^(www\.)?setec\.mk$/.test(u.hostname)||!u.pathname.startsWith('/products/'))continue;
               let img=node.querySelector('img'),txt=(node.innerText||'').replace(/\s+/g,' ');
               let price=txt.match(/Клуб\s*цена\s*:\s*([0-9][0-9.,\s]*)\s*ден\.?/i);
               let regular=txt.match(/Редовна\s*цена\s*:\s*([0-9][0-9.,\s]*)\s*ден\.?/i);
               let format=m=>m?m[1].replace(/\s/g,'').replace(/[.,]$/,''):'';
               let title=img?.alt||anchor.getAttribute('title')||anchor.innerText||'';
               let code=txt.match(/(?:Шифра|SKU)\s*:?\s*([0-9]{4,8})/i);
               out.push({url:u.href,name:title.trim().slice(0,200),sku:code?code[1]:'',
                   img:img?.currentSrc||img?.src||'',clubPrice:format(price),regularPrice:format(regular)});
             }
             return out.slice(0,20);
            }""")
            async with lock:
                for item in items:
                    if BLOCKED.search(item["name"]):continue
                    row=results.setdefault(slug,[])
                    if item["url"] not in {p["url"] for p in row}:row.append(item)
                finished+=1
                if finished%15==0:print("Scraped pages",finished,"/",len(pages),flush=True)
        except Exception as e:
            async with lock:print("Skip",slug,page_no,str(e)[:95],flush=True)
        finally:
            await page.close()
            await asyncio.sleep(.35)

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True,args=["--no-sandbox"])
        context = await browser.new_context(viewport={"width":1280,"height":900},locale="mk-MK",ignore_https_errors=False)
        semaphore=asyncio.Semaphore(3)
        await asyncio.gather(*(run_one(context,semaphore,slug,n) for slug,n in pages))
        await browser.close()

asyncio.run(main())
payload={
 "updated":datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M UTC"),
 "categories":{k:v for k,v in results.items() if v}
}
out=ROOT/"products.js"
serialized="window.SETEC_WEB_PRODUCTS="+json.dumps(payload,ensure_ascii=False,separators=(',',':'))+";\n"
out.write_text("// Public SETEC site snapshot; unverified prices are never invented.\n"+serialized,encoding="utf-8")
count=sum(len(v) for v in payload["categories"].values())
print("WEB PRODUCTS",count,"CATEGORIES",len(payload["categories"]),"OUTPUT BYTES",len(serialized.encode("utf-8")),flush=True)
assert count >= 50,"No sufficient live public product data; retaining empty fallback"
