/**
 * SETEC ŠIFRI AI proxy. Deploy as a Cloudflare Worker (module).
 *
 * Secrets (NEVER put these in the Android APK or GitHub):
 *   OPENAI_API_KEY     - OpenAI server API key
 *   APP_CLIENT_TOKEN   - random high-entropy token used by one app installation
 * Optional:
 *   OPENAI_MODEL       - default gpt-5.4 (web_search capable)
 *
 * The Android client sends only the product model and the separate client token.
 * OpenAI credentials never leave this server.
 */
const json=(data,status=200)=>new Response(JSON.stringify(data),{
  status,headers:{"content-type":"application/json; charset=utf-8","cache-control":"no-store"}
});
function safeEq(a,b){
  if(typeof a!=="string"||typeof b!=="string"||!b)return false;
  const x=new TextEncoder().encode(a),y=new TextEncoder().encode(b);
  let mismatch=x.length^y.length;
  for(let i=0;i<Math.max(x.length,y.length);i++)mismatch|=(x[i]||0)^(y[i]||0);
  return mismatch===0;
}
const officialSources=(response)=>{
  const urls=new Map();
  for(const item of response?.output||[]){
    if(item.type!=="message")continue;
    for(const c of item.content||[]){
      for(const ann of c.annotations||[]){
        if(ann.type!=="url_citation"||typeof ann.url!=="string")continue;
        try{
          const u=new URL(ann.url);
          if(u.protocol!=="https:"||!u.hostname)continue;
          // No Google search redirect links, only final cited page links.
          if(/(^|\.)google\.[a-z.]+$/i.test(u.hostname))continue;
          urls.set(u.href,{url:u.href,title:String(ann.title||u.hostname).slice(0,100)});
        }catch{}
      }
    }
  }
  return [...urls.values()].slice(0,5);
};
export default {
  async fetch(request,env){
    const url=new URL(request.url);
    if(request.method==="GET"&&url.pathname==="/health")
      return json({ok:true,service:"setec-ai",configured:!!(env.OPENAI_API_KEY&&env.APP_CLIENT_TOKEN)});
    if(request.method!=="POST"||url.pathname!=="/research")
      return json({error:"Endpoint not found"},404);
    if(!env.OPENAI_API_KEY||!env.APP_CLIENT_TOKEN)
      return json({error:"AI server is not configured yet."},503);
    const sent=request.headers.get("authorization")||"";
    if(!safeEq(sent,"Bearer "+env.APP_CLIENT_TOKEN))
      return json({error:"Unauthorized"},401);
    if(!(request.headers.get("content-type")||"").toLowerCase().includes("application/json"))
      return json({error:"JSON required"},415);
    const size=Number(request.headers.get("content-length")||0);
    if(size>2048)return json({error:"Request too large"},413);
    let body;
    try{body=await request.json()}catch{return json({error:"Invalid JSON"},400)}
    const model=String(body?.model||"").replace(/[\r\n\t]/g," ").replace(/\s+/g," ").trim();
    if(model.length<4||model.length>100||!/[\p{L}]/u.test(model))
      return json({error:"Invalid model"},400);
    const instruction=[
      "You are a cautious electronics and home-appliance product specifications assistant.",
      "Use web search for the EXACT product model specified by the user.",
      "Return at most FIVE genuinely important technical specifications for the product type.",
      "Use concise Macedonian (Cyrillic) labels. Format each as its own bullet with Label: value.",
      "Only give facts supported by sources about that EXACT model and variant/revision.",
      "Never infer, guess, mix nearby model variants, invent prices or compatibility.",
      "Do not copy Setec listing descriptions; check manufacturer or other independent sources.",
      "If sources disagree, explicitly say 'Непотврдено' for the disputed attribute.",
      "If no trustworthy exact-model information exists, say so, with no made-up facts.",
      "Use web citations for factual claims, be brief, no introduction, no long narrative."
    ].join(" ");
    let upstream;
    try{
      upstream=await fetch("https://api.openai.com/v1/responses",{
        method:"POST",
        headers:{"Authorization":"Bearer "+env.OPENAI_API_KEY,"Content-Type":"application/json"},
        body:JSON.stringify({
          model:env.OPENAI_MODEL||"gpt-5.4",
          input:[
            {role:"developer",content:instruction},
            {role:"user",content:"Истражи го точно овој модел: "+model}
          ],
          tools:[{type:"web_search",search_context_size:"low"}],
          tool_choice:"required",
          max_output_tokens:850,
          store:false
        })
      });
    }catch{return json({error:"AI service unreachable"},502)}
    let result;
    try{result=await upstream.json()}catch{return json({error:"Invalid AI response"},502)}
    if(!upstream.ok){
      const status=upstream.status===429?429:502;
      return json({error:status===429?"AI quota/rate limit reached":"AI service not available"},status);
    }
    let answer=String(result.output_text||"");
    if(!answer){
      for(const item of result.output||[]){
        if(item.type!=="message")continue;
        for(const c of item.content||[])if(c.type==="output_text")answer+=String(c.text||"")+"\n";
      }
    }
    answer=answer.trim().slice(0,2300);
    const sources=officialSources(result);
    if(!answer)return json({error:"No readable AI response"},502);
    return json({
      model,summary:answer,
      sources,
      verified:sources.length>0,
      note:sources.length>0?"Провери ги линковите пред продажна препорака.":
        "Нема потврдени линкови. Не користи го текстот како сигурни спецификации."
    });
  }
};
