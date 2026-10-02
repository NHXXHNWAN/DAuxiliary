// DAuxiliary Telegram bot: issue creation, build dispatch, quoted release promotion, authorization.
// Required secrets: TELEGRAM_BOT_TOKEN, WEBHOOK_SECRET, GITHUB_TOKEN.
// Required var: GITHUB_REPOSITORY=owner/repo.
// Optional: ADMIN_USER_IDS (comma/semicolon/newline separated), MAINTAINER_USER_IDS.
const MAX = 3900;
export default { async fetch(req, env) {
  const u=new URL(req.url);
  if(req.method==="GET"&&u.pathname==="/")return new Response("DAuxiliary bot is running.");
  if(req.method==="GET"&&u.pathname==="/auth/verify")return verifyAuth(u,env);
  if(req.method!=="POST"||u.pathname!=="/telegram")return new Response("Not found",{status:404});
  if(!env.WEBHOOK_SECRET||req.headers.get("X-Telegram-Bot-Api-Secret-Token")!==env.WEBHOOK_SECRET)return new Response("Unauthorized",{status:401});
  let update;try{update=await req.json()}catch{return new Response("Bad Request",{status:400})}
  try{await handle(update,env)}catch(e){console.error(e)}return new Response("OK");
}};
async function handle(u,e){const m=u.message;if(!m)return;const c=String(m.chat?.id||""),uid=String(m.from?.id||"");
 const p=String(m.text||"").trim().split(/\s+/),cmd=(p[0]||"").split("@")[0].toLowerCase();
 if(cmd==="/start"||cmd==="/help")return send(e,c,"可用命令：\n/issue 问题描述（维护者）\n/build 触发 Test 构建（维护者）\n/promote 引用一条测试版发布消息后发送（管理员）\n/auth 用户授权：回复用户消息发送 /auth（管理员）\n/revoke 回复用户消息发送 /revoke（管理员）");
 if(cmd==="/issue"||cmd==="/bug")return issue(e,c,uid,p.slice(1).join(" "));
 if(cmd==="/build")return build(e,c,uid);
 if(cmd==="/promote")return promote(e,c,uid,m);
 if(cmd==="/auth"||cmd==="/revoke")return auth(e,c,uid,m,cmd==="/auth");
}
function admin(e,u){return has(e.ADMIN_USER_IDS,u)}function maint(e,u){return admin(e,u)||has(e.MAINTAINER_USER_IDS,u)}function has(v,u){return Boolean(v)&&String(v).split(/[;,\s]+/).includes(String(u))}
async function issue(e,c,u,text){if(!maint(e,u))return send(e,c,"创建 Issue 需要维护者权限。");if(!text)return send(e,c,"用法：/issue 问题描述");try{const x=await gh(e,"/issues",{method:"POST",body:JSON.stringify({title:"[Telegram Bot] "+text.slice(0,100),body:`## Telegram Bot 反馈\n\n提交者 Telegram ID：${u}\n时间：${new Date().toISOString()}\n\n${redact(text)}`,labels:["bot-feedback"]})});return send(e,c,`Issue 已创建：#${x.number}\n${x.html_url}`)}catch(x){return send(e,c,"创建失败："+err(x))}}
async function build(e,c,u){if(!maint(e,u))return send(e,c,"触发构建需要维护者权限。");try{await gh(e,"/actions/workflows/build.yml/dispatches",{method:"POST",body:JSON.stringify({ref:"Test",inputs:{}})});return send(e,c,"Test 构建已触发。\n使用 /status 查看进度。链路完成后会由频道发布测试版。 ")}catch(x){return send(e,c,"触发失败："+err(x))}}
async function promote(e,c,u,m){if(!admin(e,u))return send(e,c,"发布正式版需要管理员权限。");const q=m.reply_to_message;if(!q)return send(e,c,"请先引用要发布的测试版频道消息，再发送 /promote。");const text=String(q.caption||q.text||"");const tag=(text.match(/(?:标签|tag|Release)[:： ]+([A-Za-z0-9._-]+)/i)||[])[1];const url=(text.match(/https?:\/\/github\.com\/[^\s)]+\/releases\/tag\/([^\s)]+)/i)||[])[1];const releaseTag=tag||url;if(!releaseTag)return send(e,c,"引用消息中没有识别到测试版 Release 标签或链接。");try{const r=await gh(e,"/releases/tags/"+encodeURIComponent(releaseTag));if(!r.prerelease)return send(e,c,"引用的 Release 不是测试版。");await gh(e,"/releases/"+r.id,{method:"PATCH",body:JSON.stringify({prerelease:false,name:(r.name||"").replace(/^测试版/,"正式版")})});return send(e,c,`已将测试版发布为正式版：${r.tag_name}\n${r.html_url}`)}catch(x){return send(e,c,"发布失败："+err(x))}}
async function auth(e,c,u,m,grant){if(!admin(e,u))return send(e,c,"授权管理需要管理员权限。");const target=m.reply_to_message?.from?.id;if(!target)return send(e,c,"请回复目标用户的消息后发送 /auth 或 /revoke。");const key="telegram_auth";if(!e.AUTH_KV)return send(e,c,"未配置 AUTH_KV，授权功能暂不可用。");const id=String(target);if(grant)await e.AUTH_KV.put(key+":"+id,"1");else await e.AUTH_KV.delete(key+":"+id);return send(e,c,`${grant?"已授权":"已撤销授权"} Telegram 用户：${id}`)}
async function verifyAuth(u,e){const id=u.searchParams.get("telegram_id")||"";if(!/^\\d+$/.test(id)||!e.AUTH_KV)return new Response(JSON.stringify({authorized:false}),{status:400,headers:{"Content-Type":"application/json"}});const ok=Boolean(await e.AUTH_KV.get("telegram_auth:"+id));return new Response(JSON.stringify({authorized:ok}),{headers:{"Content-Type":"application/json"}})}
async function gh(e,path,o={}){const h={Accept:"application/vnd.github+json","X-GitHub-Api-Version":"2022-11-28","User-Agent":"DAuxiliary-Bot"};if(e.GITHUB_TOKEN)h.Authorization="Bearer "+e.GITHUB_TOKEN;const r=await fetch(`https://api.github.com/repos/${e.GITHUB_REPOSITORY}${path}`,{...o,headers:{...h,"Content-Type":"application/json",...(o.headers||{})}}),d=await r.json().catch(()=>({}));if(!r.ok)throw Error(`GitHub HTTP ${r.status}: ${d.message||"请求失败"}`);return d}
async function tg(e,m,p){const f=p instanceof FormData,r=await fetch(`https://api.telegram.org/bot${e.TELEGRAM_BOT_TOKEN}/${m}`,{method:"POST",headers:f?{}:{"Content-Type":"application/json"},body:f?p:JSON.stringify(p)}),d=await r.json().catch(()=>({}));if(!r.ok||!d.ok)throw Error(d.description||r.status);return d}function send(e,c,t){return tg(e,"sendMessage",{chat_id:c,text:clip(t),disable_web_page_preview:true})}function clip(t,n=MAX){return String(t).length>n?String(t).slice(0,n-30)+"\n…（已截断）":String(t)}function redact(t){return String(t).replace(/bot\d+:[\w-]+/gi,"[REDACTED]").replace(/Bearer\s+\S+/gi,"Bearer [REDACTED]")}function err(x){return String(x?.message||x).slice(0,240)}