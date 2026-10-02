// DAuxiliary Release / CI / QA Bot. Deterministic only; no AI.
// Secrets: TELEGRAM_BOT_TOKEN, WEBHOOK_SECRET, optional GITHUB_TOKEN.
// Vars: GITHUB_REPOSITORY, optional ALLOWED_CHAT_ID, ADMIN_USER_IDS, MAINTAINER_USER_IDS.
const MAX = 3900;
export default { async fetch(req, env) {
  const u = new URL(req.url);
  if (req.method === "GET" && u.pathname === "/") return json({ok:true,service:"DAuxiliary Bot",ai:false});
  if (req.method !== "POST" || u.pathname !== "/telegram") return new Response("Not found",{status:404});
  if (!env.WEBHOOK_SECRET || req.headers.get("X-Telegram-Bot-Api-Secret-Token") !== env.WEBHOOK_SECRET) return new Response("Unauthorized",{status:401});
  let update; try { update=await req.json(); } catch { return new Response("Bad Request",{status:400}); }
  try { await handle(update,env); } catch(e) { console.error(e); }
  return new Response("OK");
}};
async function handle(u,e){
  const m=u.message;if(!m)return;const chat=String(m.chat?.id||""),uid=String(m.from?.id||"");
  if(e.ALLOWED_CHAT_ID&&!list(e.ALLOWED_CHAT_ID,chat))return send(e,chat,"此聊天未授权使用机器人。");
  if(e.ALLOWED_USER_IDS&&!list(e.ALLOWED_USER_IDS,uid)&&!maint(e,uid))return send(e,chat,"你的账号未授权使用机器人。");
  const text=String(m.text||"").trim(), parts=text.split(/\s+/), cmd=(parts[0]||"").split("@")[0].toLowerCase(), arg=parts.slice(1).join(" ");
  if(cmd==="/start"||cmd==="/help")return send(e,chat,help());
  if(cmd==="/ci"||cmd==="/stable")return release(e,chat,cmd==="/ci");
  if(cmd==="/latest"||cmd==="/version")return latest(e,chat);
  if(cmd==="/status"||cmd==="/build")return status(e,chat);
  if(cmd==="/changelog"||cmd==="/changes")return changelog(e,chat,/ci|test|测试/i.test(arg));
  if(cmd==="/support")return send(e,chat,"DAuxiliary 支持矩阵\n\nTelegram/NagramXF：持续适配\nQQ：支持，需按版本验证\n微信：支持，需按版本验证\n抖音：支持，需按版本验证\n\n问题请使用 /diagnose 或 /feedback");
  if(cmd==="/diagnose")return diagnose(e,chat,m);
  if(cmd==="/feedback"||cmd==="/bug")return issue(e,chat,uid,arg);
  if(cmd==="/buildtest")return dispatch(e,chat,uid,"test");
  if(cmd==="/promote")return dispatch(e,chat,uid,"stable");
  if(cmd==="/apkcheck")return apkcheck(e,chat,/ci|test|测试/i.test(arg));
  if(cmd.startsWith("/"))return send(e,chat,"未知命令，发送 /help 查看帮助。");
}
function help(){return `DAuxiliary Release / CI / QA Bot（无 AI）\n\n/ci 测试版 APK\n/stable 正式版 APK\n/latest 版本概况\n/changelog [ci|stable] 更新内容\n/status 最近构建状态\n/apkcheck [ci|stable] APK 文件校验\n/diagnose 回复并附日志，规则诊断\n/feedback 问题描述，创建 Issue\n/buildtest 触发测试构建（维护者）\n/promote 触发正式发布（管理员）\n/support 支持矩阵`}
async function release(e,c,ci){try{const r=await find(e,ci),a=r.assets.find(x=>/\.apk$/i.test(x.name||""));if(!a)return send(e,c,"Release 没有 APK。");const b=await fetch(a.browser_download_url,{redirect:"follow"}),blob=await b.blob();const f=new FormData();f.append("chat_id",c);f.append("caption",caption(r),"text");f.append("document",blob,a.name);return tg(e,"sendDocument",f)}catch(x){return send(e,c,"获取 APK 失败："+err(x))}}
async function latest(e,c){try{const[a,b]=await Promise.all([find(e,true,false),find(e,false,false)]);return send(e,c,`版本概况\n\n测试版：${line(a)}\n正式版：${line(b)}`)}catch(x){return send(e,c,"读取失败："+err(x))}}
async function changelog(e,c,ci){try{const r=await find(e,ci);return send(e,c,clip(`DAuxiliary ${ci?"测试版":"正式版"}\n版本：${r.name||r.tag_name}\n\n${r.body||"暂无更新说明"}\n\n${r.html_url||""}`))}catch(x){return send(e,c,"读取失败："+err(x))}}
async function status(e,c){try{const x=await gh(e,"/actions/runs?per_page=5"),rs=x.workflow_runs||[];return send(e,c,clip("最近构建：\n\n"+rs.map(r=>`${icon(r.conclusion,r.status)} ${r.name}\n${r.head_branch} ${String(r.head_sha).slice(0,7)}\n${r.html_url}`).join("\n\n")))}catch(x){return send(e,c,"读取失败："+err(x))}}
async function apkcheck(e,c,ci){try{const r=await find(e,ci),a=r.assets.find(x=>/\.apk$/i.test(x.name||""));if(!a)return send(e,c,"没有 APK。");const res=await fetch(a.browser_download_url,{redirect:"follow"}),buf=await res.arrayBuffer(),d=await crypto.subtle.digest("SHA-256",buf),hash=[...new Uint8Array(d)].map(x=>x.toString(16).padStart(2,"0")).join("");return send(e,c,`APK 检查（规则模式）\n\n版本：${r.name||r.tag_name}\n文件：${a.name}\n大小：${bytes(a.size)}\nSHA-256：${hash}\n\n已检查 Release 附件、大小和校验值。未执行设备安装测试。`)}catch(x){return send(e,c,"检查失败："+err(x))}}
async function diagnose(e,c,m){if(!m.document)return send(e,c,"请发送 /diagnose 并附加 .log 或 .txt 文件。");try{const f=await tg(e,"getFile",{file_id:m.document.file_id}),r=await fetch(`https://api.telegram.org/file/bot${e.TELEGRAM_BOT_TOKEN}/${f.result.file_path}`),t=redact(await r.text());return send(e,c,diagnosis(t))}catch(x){return send(e,c,"日志读取失败："+err(x))}}
async function issue(e,c,u,a){if(!maint(e,u))return send(e,c,"创建 Issue 需要维护者权限。");if(!a)return send(e,c,"用法：/feedback 问题描述");try{const x=await gh(e,"/issues",{method:"POST",body:JSON.stringify({title:"[Bot] "+a.slice(0,100),body:`## Bot 反馈\n\n用户：${u}\n时间：${new Date().toISOString()}\n\n${redact(a)}`,labels:["bot-feedback"]})});return send(e,c,`Issue 已创建：#${x.number}\n${x.html_url}`)}catch(x){return send(e,c,"创建失败："+err(x))}}
async function dispatch(e,c,u,mode){if(mode==="stable"?!admin(e,u):!maint(e,u))return send(e,c,"你没有执行此操作的权限。");try{await gh(e,"/actions/workflows/build.yml/dispatches",{method:"POST",body:JSON.stringify({ref:"Test",inputs:{}})});return send(e,c,`${mode==="stable"?"正式发布":"测试构建"}已触发，使用 /status 查看。`)}catch(x){return send(e,c,"触发失败："+err(x))}}
function diagnosis(t){const x=redact(t),fatal=match(x,/FATAL EXCEPTION|Fatal signal|SIG[A-Z]+/i),ex=match(x,/[A-Za-z]+(?:Exception|Error):.*/i),proc=match(x,/Process:\s*([^,\s]+)/i),tags=[];if(/Compose|SavedState|consumeRestoredState/i.test(x))tags.push("Compose/SavedState 生命周期");if(/Xposed|LibXposed|Hook|intercept/i.test(x))tags.push("Xposed/Hook");if(/ClassNotFound|NoSuchMethod|NoSuchField/i.test(x))tags.push("宿主版本兼容性");const advice=tags.includes("Compose/SavedState 生命周期")?"检查 SavedStateRegistryOwner attach、CREATED 状态和 setContent 顺序。":"提供完整崩溃前后文并进行设备复现。";return clip(`规则诊断（无 AI）\n\n【事实】\n进程：${proc||"未识别"}\n致命标记：${fatal||"未识别"}\n异常：${ex||"未识别"}\n\n【分类】\n${tags.length?tags.join("、"):"未匹配"}\n\n【固定建议】\n${advice}\n\n结果来自关键词和堆栈规则，不是 AI 推断。`)}
async function find(e,ci,fail=true){const r=await gh(e,"/releases?per_page=100"),x=r.find(z=>!z.draft&&!!z.prerelease===ci&&z.assets?.some(a=>/\.apk$/i.test(a.name||"")));if(!x&&fail)throw Error(ci?"没有测试版 Release":"没有正式版 Release");return x||{name:"未发布",tag_name:"-",assets:[],body:"暂无"}}
async function gh(e,path,o={}){const h={Accept:"application/vnd.github+json","X-GitHub-Api-Version":"2022-11-28","User-Agent":"DAuxiliary-Bot"};if(e.GITHUB_TOKEN)h.Authorization="Bearer "+e.GITHUB_TOKEN;const r=await fetch(`https://api.github.com/repos/${e.GITHUB_REPOSITORY}${path}`,{...o,headers:{...h,...o.headers}}),d=await r.json().catch(()=>({}));if(!r.ok)throw Error(`GitHub HTTP ${r.status}: ${d.message||"失败"}`);return d}
async function tg(e,m,p){const f=p instanceof FormData,r=await fetch(`https://api.telegram.org/bot${e.TELEGRAM_BOT_TOKEN}/${m}`,{method:"POST",headers:f?{}:{"Content-Type":"application/json"},body:f?p:JSON.stringify(p)}),d=await r.json().catch(()=>({}));if(!r.ok||!d.ok)throw Error(d.description||r.status);return d}
function send(e,c,t){return tg(e,"sendMessage",{chat_id:c,text:clip(t),disable_web_page_preview:true})}function caption(r){return clip(`DAuxiliary ${r.prerelease?"测试版":"正式版"}\n版本：${r.name||r.tag_name}\n\n更新内容\n${r.body||"暂无更新说明"}\n\nGitHub Release 下载页面：${r.html_url||""}`,1024)}function line(r){return `${r.name||r.tag_name}（${r.tag_name}）`}function match(t,r){const x=t.split(/\r?\n/).find(z=>r.test(z));return x?.trim().slice(0,300)||""}function redact(t){return String(t).replace(/bot\d+:[\w-]+/gi,"[REDACTED_BOT_TOKEN]").replace(/Bearer\s+\S+/gi,"Bearer [REDACTED]").replace(/([?&](?:token|key|secret)=)[^&\s]+/gi,"$1[REDACTED]")}function clip(t,n=MAX){return String(t).length>n?String(t).slice(0,n-30)+"\n…（已截断）":String(t)}function bytes(n){return n?`${(n/1048576).toFixed(2)} MB`:"未知"}function err(x){return String(x?.message||x).slice(0,220)}function list(v,id){return String(v).split(/[;,\s]+/).includes(String(id))}function admin(e,u){return list(e.ADMIN_USER_IDS,u)}function maint(e,u){return admin(e,u)||list(e.MAINTAINER_USER_IDS,u)}function icon(c,s){return c==="success"?"✅":c==="failure"?"❌":s==="in_progress"?"⏳":"⚪"}function json(x){return new Response(JSON.stringify(x),{headers:{"Content-Type":"application/json"}})}