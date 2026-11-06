import json, re, sys
from pathlib import Path
from playwright.sync_api import sync_playwright

ASSETS=Path("app/src/main/assets")
JAVA=Path("app/src/main/java/mk/seteczaliha/app/MainActivity.java").read_text()
TOKEN=re.compile(r'"(?:\\.|[^"\\])*"|labelledPricesJs\(\)|JSONObject\.quote\(url\)|\+',re.S)

def java_expr_value(expr,url):
    pos=0;chunks=[]
    for m in TOKEN.finditer(expr):
        if expr[pos:m.start()].strip():
            raise AssertionError("Unsupported Java token "+repr(expr[pos:m.start()])[:200])
        tok=m.group()
        if tok.startswith('"'):chunks.append(json.loads(tok))
        elif tok=="JSONObject.quote(url)":chunks.append(json.dumps(url))
        elif tok=="labelledPricesJs()":
            marker="private static String labelledPricesJs()"
            i=JAVA.index(marker)
            expr2=JAVA[i:JAVA.index("\n    }",i)]
            expr2=expr2.split("return ",1)[1].rsplit(";",1)[0]
            chunks.append(java_expr_value(expr2,url))
        pos=m.end()
    assert not expr[pos:].strip(),repr(expr[pos:])[:200]
    return "".join(chunks)

def extract_java_script(url):
    i=JAVA.index("private void readCategoryPage(int serial,String url)")
    src=JAVA[i:JAVA.index("categoryMerchant.evaluateJavascript(script,encoded->",i)]
    expr=src.split("String script=",1)[1].rsplit(";",1)[0]
    return java_expr_value(expr,url)

def test_app_ui(browser):
    page=browser.new_page(viewport={"width":430,"height":920})
    errors=[]
    page.on("pageerror",lambda e:errors.append(str(e)))
    page.add_init_script("""window.SetecApp = {
      loadCategory: (url)=>{ window.__loaded=(window.__loaded||[]).concat(url) },
      setTheme:()=>{}, search:()=>{}, copyText:()=>{},openCategory:()=>{},
      openProduct:()=>{}, openPcConfigurator:()=>{},openCompare:()=>{},
      getVersion:()=>'',setLocation:()=>{}
    }""")
    page.goto(ASSETS.joinpath("ui.html").resolve().as_uri(),wait_until="load")
    page.evaluate("showCategories()")
    roots=page.locator("#categoriesList .categoryRootCard")
    assert roots.count()==12,"Expected 12 main categories, got "+str(roots.count())
    page.locator("#categoriesList .categoryRootCard",has_text="Компјутери и IT опрема").click()
    children=page.locator("#categoriesList .categorySubCard")
    assert children.count()>=12,"Root has no subcategories"
    children.filter(has_text="Компјутери и Компјутерски Делови").click()
    children=page.locator("#categoriesList .categorySubCard")
    assert children.count()>=9,"Subcategory has no subcategories"
    children.filter(has_text="Матични Плочи").click()
    urls=page.evaluate("window.__loaded||[]")
    assert urls and urls[-1].endswith("/matichni-20plochi-22"), urls
    stub_url=urls[-1]
    fake=[{"url":"https://www.setec.mk/products/test-product","name":"Тест матична плоча","sku":"12345","clubPrice":"1999","regularPrice":"2299"}]
    page.evaluate("""p=>window.onCategoryData(JSON.stringify(p))""",{"url":stub_url,"products":fake,"page":1,"pageSize":1,"total":2,"next":stub_url+"?page=2","title":"Матични плочи"})
    assert page.locator("#categoryProducts .categoryProductCard").count()==1,"Product card not shown"
    assert page.locator("#categoryNext").is_enabled(),"Next page disabled"
    page.locator("#categoryNext").click()
    urls=page.evaluate("window.__loaded||[]")
    assert urls[-1].endswith("?page=2"),urls
    page.evaluate("categoryHome()")
    assert page.locator("#categoriesList .categoryRootCard").count()==12
    for root in page.locator("#categoriesList .categoryRootCard").all():
        root.click()
        assert page.locator("#categoryBreadcrumb").inner_text()!="","Category did not open"
        page.evaluate("categoryHome()")
    assert not errors,"JavaScript exceptions: "+repr(errors)
    print("PASS UI 12 roots, nested links, product rendering, pagination",flush=True)
    page.close()

def test_site_category(browser,url):
    page=browser.new_page(viewport={"width":1200,"height":920},locale="mk-MK")
    page.goto(url,wait_until="domcontentloaded",timeout=25000)
    page.wait_for_timeout(3000)
    script=extract_java_script(url)
    # Raw generated JS from MainActivity evaluated on the real Setec page
    for attempt in range(5):
        try:
            res=page.evaluate(script)
            info=json.loads(res)
            print("JAVA EXTRACT",url,"attempt",attempt,"products",len(info.get("products",[])),"next",info.get("next"),flush=True)
            if info.get("products"):
                assert len(info["products"])>=5,info
                page.close()
                return True
        except Exception as e:print("JAVA ERROR",url,repr(e)[:700],flush=True)
        page.wait_for_timeout(1500)
    page.close()
    return False

with sync_playwright() as p:
    browser=p.chromium.launch(headless=True,args=["--no-sandbox"])
    test_app_ui(browser)
    urls=["https://www.setec.mk/category/televizori-55","https://www.setec.mk/category/konfiguratsii-32","https://www.setec.mk/category/matichni-20plochi-22"]
    results={url:test_site_category(browser,url) for url in urls}
    print("REAL SITE EXTRACT RESULTS",results,flush=True)
    browser.close()
    assert all(results.values()),"Category extraction failed; see logs"
