// DAuxiliary Cloudflare Worker: Telegram bot, GitHub CI/Release and module authorization.
// Secrets: TELEGRAM_BOT_TOKEN, WEBHOOK_SECRET, GITHUB_TOKEN, ADMIN_API_TOKEN.
// Variables: GITHUB_REPOSITORY, ADMIN_USER_IDS, optional MAINTAINER_USER_IDS and ALLOWED_CHAT_ID.
// Durable Object binding: AUTH_DO (TelegramAuthStore).
// Optional KV bindings: AUTH_KV (legacy Bot-admin grants), UPDATE_KV (webhook de-duplication).

const MAX_TEXT = 3900;
const MAX_ISSUE_BODY = 12000;
const MAX_APK_BYTES = 50 * 1024 * 1024;
const REQUEST_TIMEOUT_MS = 15000;
const MAX_RETRIES = 2;

export { TelegramAuthStore } from "./telegram_auth_store.js";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/") {
      return new Response("DAuxiliary Telegram bot is running.");
    }

    if (request.method === "OPTIONS" && [
      "/auth/redeem", "/auth/verify", "/admin/summary", "/admin/revoke",
      "/admin/bot/roles", "/admin/bot/role/grant", "/admin/bot/role/revoke",
    ].includes(url.pathname)) {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }

    if (request.method === "GET" && url.pathname === "/auth/verify") return verifyAuth(request, url, env);
    if (request.method === "POST" && url.pathname === "/auth/redeem") return redeemAuthCode(request, env);
    if (request.method === "GET" && url.pathname === "/admin/summary") return handleAdminSummary(request, env);
    if (request.method === "POST" && url.pathname === "/admin/revoke") return handleAdminRevoke(request, env);
    if (request.method === "GET" && url.pathname === "/admin/bot/roles") return handleAdminBotRoles(request, env);
    if (request.method === "POST" && (url.pathname === "/admin/bot/role/grant" || url.pathname === "/admin/bot/role/revoke")) {
      return handleAdminBotRoleChange(request, url, env);
    }

    if (request.method !== "POST" || url.pathname !== "/telegram") return new Response("Not found", { status: 404 });

    const secret = request.headers.get("X-Telegram-Bot-Api-Secret-Token") || "";
    if (!env.WEBHOOK_SECRET || secret !== env.WEBHOOK_SECRET) return new Response("Unauthorized", { status: 401 });

    let update;
    try {
      update = await request.json();
    } catch {
      return new Response("Bad Request", { status: 400 });
    }

    const updateId = update?.update_id;
    if (env.UPDATE_KV && updateId !== undefined) {
      const key = `telegram_update:${updateId}`;
      if (await env.UPDATE_KV.get(key)) return new Response("OK");
      await env.UPDATE_KV.put(key, "1", { expirationTtl: 86400 });
    }

    try {
      await handleUpdate(update, env);
    } catch (error) {
      console.error(`Update ${updateId ?? "unknown"} failed:`, error);
    }
    return new Response("OK");
  },
};

async function handleUpdate(update, env) {
  const message = update?.message || update?.channel_post;
  const text = String(message?.text || "").trim();
  if (!message || !text) return;

  const chatId = String(message.chat?.id || "");
  const userId = String(message.from?.id || "");
  if (!chatId) return;
  if (env.ALLOWED_CHAT_ID && chatId !== String(env.ALLOWED_CHAT_ID)) return;

  const parts = text.split(/\s+/);
  const command = (parts[0] || "").split("@")[0].toLowerCase();
  const argument = parts.slice(1).join(" ");

  if (command === "/start" || command === "/help") {
    await sendText(env, chatId, [
      "DAuxiliary Bot",
      "",
      "/ci —— 获取最新测试版 APK",
      "/stable —— 获取最新正式版 APK",
      "/code —— 私聊获取 Telegram 模块授权码",
      "/module_revoke —— 撤销自己的 Telegram 模块授权（仅私聊）",
      "/status —— 查看 Test 构建状态",
      "/version —— 查看版本信息",
      "/health —— 查看服务状态（管理员）",
      "/issue 问题描述 —— 创建 GitHub Issue",
      "/build —— 触发 Test 构建（维护者）",
      "/promote —— 引用测试版消息并发布为正式版（管理员）",
      "/auth —— 回复用户消息后授予 Bot 管理员权限（管理员）",
      "/revoke —— 回复用户消息后撤销 Bot 管理员权限（管理员）",
      "/maintainer —— 回复用户消息后授予维护者权限（管理员）",
      "/unmaintainer —— 回复用户消息后撤销维护者权限（管理员）",
    ].join("\n"));
    return;
  }

  if (command === "/ci" || command === "/stable") {
    await sendLatestApk(env, chatId, command === "/ci");
    return;
  }
  if (command === "/code") {
    await issueAuthCode(env, chatId, userId, message.chat?.type);
    return;
  }
  if (command === "/module_revoke") {
    await revokeModuleAuthorization(env, chatId, userId, message.chat?.type);
    return;
  }
  if (command === "/status") {
    await sendBuildStatus(env, chatId);
    return;
  }
  if (command === "/version") {
    await sendVersionStatus(env, chatId);
    return;
  }
      if (command === "/health") {
      await sendHealthStatus(env, chatId, userId);
      return;
    }
  if (command === "/issue" || command === "/bug") {
    await createIssue(env, chatId, userId, argument);
    return;
  }
  if (command === "/build") {
    await dispatchBuild(env, chatId, userId);
    return;
  }
  if (command === "/promote") {
    await promoteRelease(env, chatId, userId, message);
    return;
  }
  if (command === "/auth" || command === "/revoke") {
    await changeBotAuthorization(env, chatId, userId, message, command === "/auth");
    return;
  }
  if (command === "/maintainer" || command === "/unmaintainer") {
    await changeMaintainerAuthorization(env, chatId, userId, message, command === "/maintainer");
  }
}

function hasId(list, id) {
  return Boolean(list) && String(list).split(/[;,\s]+/).filter(Boolean).includes(String(id));
}

async function isAdmin(env, id) {
  if (hasId(env.ADMIN_USER_IDS, id)) return true;
  if (await hasDynamicRole(env, "bot_admin", id)) return true;
  return hasLegacyBotAdmin(env, id);
}

async function isMaintainer(env, id) {
  if (hasId(env.MAINTAINER_USER_IDS, id)) return true;
  return (await isAdmin(env, id)) || await hasDynamicRole(env, "maintainer", id);
}

async function hasDynamicRole(env, role, id) {
  if (!env.AUTH_DO || !/^\d+$/.test(String(id))) return false;
  try {
    const query = new URLSearchParams({ role, user_id: String(id) });
    const result = await authStore(env, `/bot/role?${query.toString()}`);
    return result.granted === true;
  } catch (error) {
    console.error(`读取 ${role} 角色失败:`, error);
    return false;
  }
}

async function hasLegacyBotAdmin(env, id) {
  if (!env.AUTH_KV || !/^\d+$/.test(String(id))) return false;
  try {
    return Boolean(await env.AUTH_KV.get(`telegram_auth:${id}`));
  } catch (error) {
    console.error("读取旧 Bot 管理员记录失败:", error);
    return false;
  }
}

async function issueAuthCode(env, chatId, userId, chatType) {
  if (chatType !== "private" || !/^\d+$/.test(userId)) {
    await sendText(env, chatId, "请在 Bot 私聊中发送 /code 获取专属于自己的授权码。");
    return;
  }
  if (!env.AUTH_DO) {
    await sendText(env, chatId, "授权码服务未配置 AUTH_DO，暂不可用。");
    return;
  }
  try {
    const result = await authStore(env, "/issue", "POST", { userId });
    if (result.already_authorized) {
      await sendText(env, chatId, "你的 Telegram 模块已经授权，无需再次申请授权码。若要重新绑定，请先使用 /module_revoke 撤销后再申请。\n当前绑定 Telegram ID：" + result.telegram_id);
      return;
    }
    await sendText(env, chatId, [
      result.reused ? "你当前仍有未兑换的授权码：" : "你的 Telegram 模块授权码：",
      result.code,
      "",
      `有效期约 ${Math.ceil(Number(result.expires_in || 0) / 60)} 分钟；同一账号重复发送 /code 不会生成新码。`,
      "授权码只能兑换一次，兑换后绑定你的 Telegram 账号。",
    ].join("\n"));
  } catch (error) {
    await sendText(env, chatId, "生成授权码失败：" + safeError(error));
  }
}

async function revokeModuleAuthorization(env, chatId, userId, chatType) {
  if (chatType !== "private" || !/^\d+$/.test(userId)) {
    await sendText(env, chatId, "请在 Bot 私聊中撤销自己的模块授权。");
    return;
  }
  if (!env.AUTH_DO) {
    await sendText(env, chatId, "授权服务未配置 AUTH_DO。");
    return;
  }
  try {
    await authStore(env, "/module/revoke", "POST", { userId });
    await sendText(env, chatId, "你的 Telegram 模块授权已撤销；模块下次校验时会停止 Telegram 功能。");
  } catch (error) {
    await sendText(env, chatId, "撤销失败：" + safeError(error));
  }
}

async function redeemAuthCode(request, env) {
  if (!env.AUTH_DO) return json({ authorized: false, error: "授权服务未配置" }, 503);
  let payload;
  try {
    payload = await request.json();
  } catch {
    return json({ authorized: false, error: "JSON 无效" }, 400);
  }
  const code = String(payload?.code || "").trim().toUpperCase();
  if (!/^[A-F0-9]{64}$/.test(code)) return json({ authorized: false, error: "授权码格式无效" }, 400);
  try {
    const result = await authStore(env, "/redeem", "POST", { code });
    return json(result, result.authorized ? 200 : 401);
  } catch (error) {
    return json({ authorized: false, error: safeError(error) }, 503);
  }
}

async function verifyAuth(request, url, env) {
  const id = url.searchParams.get("telegram_id") || "";
  if (!/^\d+$/.test(id)) return json({ authorized: false }, 400);
  try {
  const result = await authStore(env, `/verify?telegram_id=${encodeURIComponent(id)}`, "GET", undefined, {
      Authorization: request.headers.get("Authorization") || "",
    });
    const authorized = Boolean(result.module_authorized);
    return json({ authorized, module_authorized: authorized });
  } catch {
    return json({ authorized: false, error: "授权服务暂不可用" }, 503);
  }
}

async function authStore(env, path, method = "GET", body, headers = {}) {
  if (!env.AUTH_DO) throw new Error("未配置 AUTH_DO。");
  const id = env.AUTH_DO.idFromName("dauxiliary-telegram-auth-v1");
  const response = await env.AUTH_DO.get(id).fetch(`https://auth.internal${path}`, {
    method,
    headers: { ...(body === undefined ? {} : { "Content-Type": "application/json" }), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(result.error || `授权服务 HTTP ${response.status}`);
  return result;
}

async function changeBotAuthorization(env, chatId, operatorId, message, grant) {
  await changeRole(env, chatId, operatorId, message, grant, "bot_admin");
}

async function changeMaintainerAuthorization(env, chatId, operatorId, message, grant) {
  await changeRole(env, chatId, operatorId, message, grant, "maintainer");
}

async function changeRole(env, chatId, operatorId, message, grant, role) {
  if (!(await isAdmin(env, operatorId))) {
    await sendText(env, chatId, "角色权限管理需要管理员权限。");
    return;
  }
  const target = message.reply_to_message?.from?.id;
  if (!target) {
    await sendText(env, chatId, `请回复目标用户消息后发送对应命令。`);
    return;
  }
  try {
    if (env.AUTH_DO) {
      const path = role === "bot_admin"
        ? (grant ? "/bot/grant" : "/bot/revoke")
        : (grant ? "/bot/maintainer/grant" : "/bot/maintainer/revoke");
      await authStore(env, path, "POST", { userId: String(target), operatorId: String(operatorId) });
    }
    if (role === "bot_admin" && env.AUTH_KV) {
      const key = `telegram_auth:${target}`;
      if (grant) {
        await env.AUTH_KV.put(key, JSON.stringify({ userId: String(target), grantedBy: String(operatorId), grantedAt: new Date().toISOString() }));
      } else {
        await env.AUTH_KV.delete(key);
      }
    }
    if (!env.AUTH_DO && !(role === "bot_admin" && env.AUTH_KV)) throw new Error("未配置可用的授权存储。");
    await sendText(env, chatId, `${grant ? "已授予" : "已撤销"}${role === "maintainer" ? "维护者" : "Bot 管理员"}权限：${target}`);
  } catch (error) {
    await sendText(env, chatId, "权限操作失败：" + safeError(error));
  }
}

async function handleAdminSummary(request, env) {
  if (!adminApiAuthorized(request, env)) return json({ error: "Unauthorized" }, 401);
  try {
    return json(await authStore(env, "/admin/summary"));
  } catch (error) {
    return json({ error: safeError(error) }, 503);
  }
}

async function handleAdminRevoke(request, env) {
  if (!adminApiAuthorized(request, env)) return json({ error: "Unauthorized" }, 401);
  let payload;
  try {
    payload = await request.json();
  } catch {
    return json({ error: "JSON 无效" }, 400);
  }
  const userId = String(payload?.telegram_id || "");
  if (!/^\d+$/.test(userId)) return json({ error: "telegram_id 无效" }, 400);
  try {
    const result = await authStore(env, "/module/revoke", "POST", { userId });
    if (env.AUTH_KV) await env.AUTH_KV.delete(`telegram_auth:${userId}`);
    return json(result);
  } catch (error) {
    return json({ error: safeError(error) }, 503);
  }
}

async function handleAdminBotRoles(request, env) {
  if (!adminApiAuthorized(request, env)) return json({ error: "Unauthorized" }, 401);
  try {
    return json(await authStore(env, "/bot/roles"));
  } catch (error) {
    return json({ error: safeError(error) }, 503);
  }
}

async function handleAdminBotRoleChange(request, url, env) {
  if (!adminApiAuthorized(request, env)) return json({ error: "Unauthorized" }, 401);
  let payload;
  try {
    payload = await request.json();
  } catch {
    return json({ error: "JSON 无效" }, 400);
  }
  const userId = String(payload?.telegram_id || "");
  if (!/^\d+$/.test(userId)) return json({ error: "telegram_id 无效" }, 400);
  const role = String(payload?.role || "");
  if (!["bot_admin", "maintainer"].includes(role)) return json({ error: "role 无效" }, 400);
  const grant = url.pathname.endsWith("/grant");
  const path = role === "bot_admin"
    ? (grant ? "/bot/grant" : "/bot/revoke")
    : (grant ? "/bot/maintainer/grant" : "/bot/maintainer/revoke");
  try {
    const result = await authStore(env, path, "POST", { userId, operatorId: "admin_api" });
    if (role === "bot_admin" && env.AUTH_KV) {
      const key = `telegram_auth:${userId}`;
      if (grant) await env.AUTH_KV.put(key, JSON.stringify({ userId, grantedBy: "admin_api", grantedAt: new Date().toISOString() }));
      else await env.AUTH_KV.delete(key);
    }
    return json(result);
  } catch (error) {
    return json({ error: safeError(error) }, 503);
  }
}

function adminApiAuthorized(request, env) {
  return Boolean(env.ADMIN_API_TOKEN) && constantTimeEqual(bearerToken(request), env.ADMIN_API_TOKEN);
}

function bearerToken(request) {
  return request.headers.get("Authorization")?.replace(/^Bearer\s+/i, "") || "";
}

function constantTimeEqual(a, b) {
  const left = new TextEncoder().encode(String(a));
  const right = new TextEncoder().encode(String(b));
  let diff = left.length ^ right.length;
  const size = Math.max(left.length, right.length);
  for (let i = 0; i < size; i += 1) {
    diff |= (left[i % Math.max(1, left.length)] || 0) ^ (right[i % Math.max(1, right.length)] || 0);
  }
  return diff === 0;
}

async function createIssue(env, chatId, userId, body) {
  if (!body) {
    await sendText(env, chatId, "用法：/issue 问题描述");
    return;
  }
  body = redact(body.slice(0, MAX_ISSUE_BODY));
  try {
    const issue = await github(env, "/issues", {
      method: "POST",
      body: JSON.stringify({
        title: "[Telegram Bot] " + body.slice(0, 100),
        body: `## Telegram Bot 反馈\n\n提交者 Telegram ID：${userId}\n时间：${new Date().toISOString()}\n\n${body}`,
        labels: ["bot-feedback"],
      }),
    });
    await sendText(env, chatId, `Issue 已创建：#${issue.number}\n${issue.html_url}`);
  } catch (error) {
    await sendText(env, chatId, "创建失败：" + safeError(error));
  }
}

async function dispatchBuild(env, chatId, userId) {
  if (!(await isMaintainer(env, userId))) {
    await sendText(env, chatId, "触发构建需要维护者权限。");
    return;
  }
  try {
    await github(env, "/actions/workflows/build.yml/dispatches", {
      method: "POST",
      body: JSON.stringify({ ref: "Test", inputs: {} }),
    });
    await sendText(env, chatId, "Test 构建已触发。构建完成后可使用 /ci 获取测试版 APK。");
  } catch (error) {
    await sendText(env, chatId, "触发失败：" + safeError(error));
  }
}

async function sendLatestApk(env, chatId, isCi) {
  try {
    const release = await findRelease(env, isCi);
    await sendReleaseApk(env, chatId, release);
  } catch (error) {
    await sendText(env, chatId, "获取 APK 失败：" + safeError(error));
  }
}

async function findRelease(env, isCi) {
  const releases = await github(env, "/releases?per_page=100");
  if (!Array.isArray(releases)) throw new Error("GitHub 返回了无法识别的数据。");
  const release = releases.find((item) => !item.draft && Boolean(item.prerelease) === isCi && hasApk(item));
  if (!release) throw new Error(isCi ? "没有找到带 APK 的测试版 Release。" : "没有找到带 APK 的正式版 Release。");
  return release;
}

function hasApk(release) {
  return release.assets?.some((asset) => /\.apk$/i.test(asset.name || ""));
}

async function sendReleaseApk(env, chatId, release) {
  const apk = release.assets?.find((asset) => /\.apk$/i.test(asset.name || ""));
  if (!apk) throw new Error(`版本 ${release.tag_name || "(无标签)"} 没有 APK 附件。`);
  const caption = `${release.name || release.tag_name || "DAuxiliary"}\n${release.body || "无更新说明"}`.slice(0, 1024);
  await sendDocument(env, chatId, apk.browser_download_url, apk.name, caption);
}

async function sendBuildStatus(env, chatId) {
  try {
    const runs = await github(env, "/actions/runs?branch=Test&per_page=5");
    const run = runs.workflow_runs?.[0];
    if (!run) return sendText(env, chatId, "Test 分支暂无构建记录。");
    await sendText(env, chatId, `Test 构建状态：${translateStatus(run.status, run.conclusion)}\n提交：${String(run.head_sha || "").slice(0, 7)}\n${run.html_url || ""}`);
  } catch (error) {
    await sendText(env, chatId, "查询失败：" + safeError(error));
  }
}

async function sendVersionStatus(env, chatId) {
  try {
    const releases = await github(env, "/releases?per_page=20");
    const test = releases.find((item) => item.prerelease && !item.draft && hasApk(item));
    const stable = releases.find((item) => !item.prerelease && !item.draft && hasApk(item));
    const line = (label, item) => item ? `${label}：${item.tag_name}\n${item.html_url}` : `${label}：未找到`;
    await sendText(env, chatId, `${line("测试版", test)}\n\n${line("正式版", stable)}`);
  } catch (error) {
    await sendText(env, chatId, "查询版本失败：" + safeError(error));
  }
}

function translateStatus(status, conclusion) {
  if (status !== "completed") return "进行中";
  return conclusion === "success" ? "成功" : conclusion === "failure" ? "失败" : String(conclusion || "已完成");
}

async function promoteRelease(env, chatId, userId, message) {
  if (!(await isAdmin(env, userId))) {
    await sendText(env, chatId, "发布正式版需要管理员权限。");
    return;
  }
  const tag = extractReleaseTag(message);
  if (!tag) {
    await sendText(env, chatId, "请回复包含测试版 Release 链接或 Tag 的消息后发送 /promote。");
    return;
  }
  try {
    const release = await github(env, `/releases/tags/${encodeURIComponent(tag)}`);
    if (!release.prerelease || release.draft) {
      await sendText(env, chatId, "目标 Release 不是可发布的测试版。");
      return;
    }
    await github(env, `/releases/${release.id}`, {
      method: "PATCH",
      body: JSON.stringify({ prerelease: false }),
    });
    await sendText(env, chatId, `已发布正式版：${tag}`);
  } catch (error) {
    await sendText(env, chatId, "发布失败：" + safeError(error));
  }
}

function extractReleaseTag(message) {
  const text = [message.text, message.caption, message.reply_to_message?.text, message.reply_to_message?.caption]
    .filter(Boolean).join(" ");
  const tag = text.match(/(?:\/releases\/tag\/|tag[=:：]?\s*)([A-Za-z0-9._-]+)/i)?.[1];
  return tag || text.match(/\bv?\d+\.\d+(?:\.\d+)?(?:[-+][A-Za-z0-9._-]+)?\b/)?.[0] || "";
}

async function sendHealthStatus(env, chatId, userId) {
  if (!(await isAdmin(env, userId))) {
    await sendText(env, chatId, "服务状态查询需要管理员权限。");
    return;
  }
  const rows = [
    `GITHUB_REPOSITORY：${env.GITHUB_REPOSITORY ? "已配置" : "缺失"}`,
    `GITHUB_TOKEN：${env.GITHUB_TOKEN ? "已配置" : "缺失"}`,
    `AUTH_DO：${env.AUTH_DO ? "已绑定" : "缺失"}`,
    `AUTH_KV：${env.AUTH_KV ? "已绑定（兼容）" : "未绑定"}`,
    `UPDATE_KV：${env.UPDATE_KV ? "已绑定" : "未绑定"}`,
  ];
  try {
    await github(env, "/rate_limit");
    rows.push("GitHub API：正常");
  } catch (error) {
    rows.push("GitHub API：" + safeError(error));
  }
  await sendText(env, chatId, rows.join("\n"));
}

async function fetchWithRetry(url, options = {}) {
  const method = String(options.method || "GET").toUpperCase();
  const retries = ["GET", "HEAD"].includes(method) ? MAX_RETRIES : 0;
  let lastError;
  for (let attempt = 0; attempt <= retries; attempt += 1) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    try {
      return await fetch(url, { ...options, signal: controller.signal });
    } catch (error) {
      lastError = error;
      if (attempt < retries) await new Promise((resolve) => setTimeout(resolve, 300 * (attempt + 1)));
    } finally {
      clearTimeout(timeout);
    }
  }
  throw new Error(`网络请求失败：${safeError(lastError)}`);
}

async function github(env, path, options = {}) {
  if (!env.GITHUB_REPOSITORY || !/^[\w.-]+\/[\w.-]+$/.test(env.GITHUB_REPOSITORY)) throw new Error("请正确设置 GITHUB_REPOSITORY（owner/repo）。");
  if (!env.GITHUB_TOKEN || !String(env.GITHUB_TOKEN).trim()) throw new Error("未配置 GITHUB_TOKEN；请在 Worker Secrets 中设置并重新部署。");
  const headers = {
    Accept: "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "DAuxiliary-Telegram-Bot",
    "Content-Type": "application/json",
    Authorization: `Bearer ${String(env.GITHUB_TOKEN).trim()}`,
  };
  const response = await fetchWithRetry(`https://api.github.com/repos/${env.GITHUB_REPOSITORY}${path}`, { ...options, headers: { ...headers, ...(options.headers || {}) } });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const detail = data.message || "请求失败";
    const hint = response.status === 403 ? "；请确认 Token 已绑定仓库、具备对应权限，并检查组织 SSO/策略" : "";
    throw new Error(`GitHub HTTP ${response.status}：${detail}${hint}`);
  }
  return data;
}

async function sendDocument(env, chatId, url, filename, caption) {
  const response = await fetchWithRetry(url, { headers: { Authorization: `Bearer ${String(env.GITHUB_TOKEN).trim()}` }, redirect: "follow" });
  if (!response.ok) throw new Error(`下载 GitHub APK 失败（HTTP ${response.status}）。`);
  const declaredLength = Number(response.headers.get("content-length") || 0);
  if (declaredLength > MAX_APK_BYTES) throw new Error("APK 超过 Telegram Bot API 的 50 MB 限制。");
  const blob = await response.blob();
  if (blob.size > MAX_APK_BYTES) throw new Error("APK 超过 Telegram Bot API 的 50 MB 限制。");
  const form = new FormData();
  form.append("chat_id", chatId);
  form.append("caption", caption);
  form.append("document", blob, safeFilename(filename || "DAuxiliary.apk"));
  await telegram(env, "sendDocument", form);
}

async function sendText(env, chatId, text) {
  await telegram(env, "sendMessage", { chat_id: chatId, text: clip(text) });
}

async function telegram(env, method, payload) {
  if (!env.TELEGRAM_BOT_TOKEN) throw new Error("未设置 TELEGRAM_BOT_TOKEN。");
  const form = payload instanceof FormData;
  const response = await fetchWithRetry(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/${method}`, {
    method: "POST",
    headers: form ? {} : { "Content-Type": "application/json" },
    body: form ? payload : JSON.stringify(payload),
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok || !result.ok) throw new Error(`Telegram API：${result.description || response.status}`);
  return result;
}

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
  };
}

function json(value, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json", ...corsHeaders() } });
}

function clip(value, limit = MAX_TEXT) {
  const text = String(value);
  return text.length <= limit ? text : text.slice(0, limit - 30) + "\n…（已截断）";
}

function safeFilename(value) {
  const name = String(value).replace(/[^\w.()+-]/g, "_").slice(0, 120);
  return name.toLowerCase().endsWith(".apk") ? name : `${name || "DAuxiliary"}.apk`;
}

function redact(value) {
  return String(value).replace(/bot\d+:[\w-]+/gi, "[REDACTED_BOT_TOKEN]").replace(/Bearer\s+\S+/gi, "Bearer [REDACTED]");
}

function safeError(error) {
  return String(error?.message || "未知错误").slice(0, 250);
}
