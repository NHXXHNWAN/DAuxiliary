// Cloudflare Worker: Telegram /ci and /stable bot for GitHub Releases.
// Required Worker secrets: TELEGRAM_BOT_TOKEN, WEBHOOK_SECRET
// Required variable: GITHUB_REPOSITORY (owner/repo)
// Optional secret: GITHUB_TOKEN (fine-grained, selected repository, Contents: read-only)
// Optional variable: ALLOWED_CHAT_ID (restrict commands to one Telegram chat/user ID)

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/") {
      return new Response("Telegram release bot is running.", { status: 200 });
    }
    if (request.method !== "POST" || url.pathname !== "/telegram") {
      return new Response("Not found", { status: 404 });
    }

    const suppliedSecret = request.headers.get("X-Telegram-Bot-Api-Secret-Token") || "";
    if (!env.WEBHOOK_SECRET || suppliedSecret !== env.WEBHOOK_SECRET) {
      return new Response("Unauthorized", { status: 401 });
    }
    let update;
    try {
      update = await request.json();
    } catch {
      return new Response("Bad Request", { status: 400 });
    }
    const message = update.message;
    const text = message?.text?.trim();
    if (!message || !text) return new Response("OK");
    const chatId = String(message.chat?.id ?? "");
    if (env.ALLOWED_CHAT_ID && chatId !== String(env.ALLOWED_CHAT_ID)) {
      await sendText(env, chatId, "此机器人未授权在此聊天中使用。");
      return new Response("OK");
    }
    const command = text.split(/\s+/)[0].split("@")[0].toLowerCase();
    if (command === "/start" || command === "/help") {
      await sendText(env, chatId, "可用命令：\n/ci — 获取最新测试版 APK\n/stable — 获取最新正式版 APK");
      return new Response("OK");
    }
    if (command !== "/ci" && command !== "/stable") return new Response("OK");

    try {
      const release = await findRelease(env, command === "/ci");
      const apk = release.assets?.find(a => a.name?.toLowerCase().endsWith(".apk"));
      if (!apk) {
        await sendText(env, chatId, `找到版本 ${release.tag_name || "(无标签)"}，但该 Release 没有 APK 附件。`);
        return new Response("OK");
      }
      const caption = `${release.name || release.tag_name || "DAuxiliary"}\n${release.body || "无更新说明"}`.slice(0, 1024);
      await sendDocument(env, chatId, apk.browser_download_url, apk.name, caption);
    } catch (error) {
      console.error("Release lookup/send failed:", error);
      await sendText(env, chatId, `获取 APK 失败：${safeError(error)}`);
    }
    return new Response("OK");
  },
};

async function findRelease(env, isCi) {
  if (!env.GITHUB_REPOSITORY || !/^[\w.-]+\/[\w.-]+$/.test(env.GITHUB_REPOSITORY)) {
    throw new Error("请正确设置 GITHUB_REPOSITORY（owner/repo）。");
  }
  const headers = {
    Accept: "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "Cloudflare-Telegram-Release-Bot",
  };
  if (env.GITHUB_TOKEN) headers.Authorization = `Bearer ${env.GITHUB_TOKEN}`;
  const response = await fetch(`https://api.github.com/repos/${env.GITHUB_REPOSITORY}/releases?per_page=100`, { headers });
  if (!response.ok) throw new Error(`GitHub API 返回 HTTP ${response.status}。`);
  const releases = await response.json();
  if (!Array.isArray(releases)) throw new Error("GitHub 返回了无法识别的数据。");
  const release = releases.find(r => !r.draft && Boolean(r.prerelease) === isCi && r.assets?.some(a => a.name?.toLowerCase().endsWith(".apk")));
  if (!release) throw new Error(isCi ? "没有找到带 APK 的测试版 Release（需标记为 Pre-release）。" : "没有找到带 APK 的正式版 Release（需取消 Pre-release 标记）。");
  return release;
}
async function sendText(env, chatId, text) {
  return telegram(env, "sendMessage", { chat_id: chatId, text });
}
async function sendDocument(env, chatId, documentUrl, filename, caption) {
  if (!env.TELEGRAM_BOT_TOKEN) throw new Error("未设置 TELEGRAM_BOT_TOKEN。");
  const assetResponse = await fetch(documentUrl, {
    headers: env.GITHUB_TOKEN ? { Authorization: `Bearer ${env.GITHUB_TOKEN}` } : {},
    redirect: "follow",
  });
  if (!assetResponse.ok) throw new Error(`下载 GitHub APK 失败（HTTP ${assetResponse.status}）。`);
  const contentLength = Number(assetResponse.headers.get("content-length") || 0);
  if (contentLength > 50 * 1024 * 1024) throw new Error("APK 超过 Telegram Bot API 的 50 MB 文件限制。");
  const blob = await assetResponse.blob();
  if (blob.size > 50 * 1024 * 1024) throw new Error("APK 超过 Telegram Bot API 的 50 MB 文件限制。");
  const form = new FormData();
  form.append("chat_id", chatId);
  form.append("caption", caption);
  form.append("document", blob, filename || "DAuxiliary.apk");
  return telegram(env, "sendDocument", form);
}
async function telegram(env, method, payload) {
  if (!env.TELEGRAM_BOT_TOKEN) throw new Error("未设置 TELEGRAM_BOT_TOKEN。");
  const isFormData = payload instanceof FormData;
  const response = await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/${method}`, {
    method: "POST",
    headers: isFormData ? {} : { "Content-Type": "application/json" },
    body: isFormData ? payload : JSON.stringify(payload),
  });
  let result;
  try {
    result = await response.json();
  } catch {
    throw new Error(`Telegram API 返回无法解析的响应（HTTP ${response.status}）。`);
  }
  if (!response.ok || !result.ok) {
    const description = String(result.description || "未知错误").slice(0, 200);
    throw new Error(`Telegram API 请求失败（HTTP ${response.status}）：${description}`);
  }
  return result;
}
function safeError(error) {
  return String(error?.message || "未知错误").slice(0, 250);
}
