const CODE_TTL_MS = 10 * 60 * 1000;

/** Durable Object storage serializes requests, making code consumption atomic. */
class TelegramAuthStore {
  constructor(state) {
    this.storage = state.storage;
  }

  async fetch(request) {
    const url = new URL(request.url);
    try {
      if (request.method === "POST" && url.pathname === "/issue") return this.issue(await request.json());
      if (request.method === "POST" && url.pathname === "/redeem") return this.redeem(await request.json());
      if (request.method === "GET" && url.pathname === "/verify") return this.verify(url, request);
      if (request.method === "POST" && url.pathname === "/module/revoke") return this.revokeModule(await request.json());
      if (request.method === "POST" && url.pathname === "/admin/revoke") return this.revokeModule(await request.json());
      if (request.method === "POST" && url.pathname === "/bot/grant") return this.setBotAdmin(await request.json(), true);
      if (request.method === "POST" && url.pathname === "/bot/revoke") return this.setBotAdmin(await request.json(), false);
      if (request.method === "POST" && url.pathname === "/bot/maintainer/grant") return this.setMaintainer(await request.json(), true);
      if (request.method === "POST" && url.pathname === "/bot/maintainer/revoke") return this.setMaintainer(await request.json(), false);
      if (request.method === "GET" && url.pathname === "/bot/role") return this.getRole(url);
      if (request.method === "GET" && url.pathname === "/bot/roles") return this.getBotRoles();
      if (request.method === "GET" && url.pathname === "/admin/summary") return this.summary();
      return this.reply({ error: "Not found" }, 404);
    } catch (error) {
      return this.reply({ error: String(error?.message || "授权服务错误") }, 400);
    }
  }

  async issue(payload) {
    const userId = validateUserId(payload?.userId);
    const moduleRecord = await this.storage.get(`module:${userId}`);
    if (moduleRecord) {
      return this.reply({
        already_authorized: true,
        module_authorized: true,
        telegram_id: userId,
      });
    }

    const activeKey = `active_code:${userId}`;
    const previousCode = await this.storage.get(activeKey);
    if (previousCode) {
      const previousRecord = await this.storage.get(`code:${previousCode}`);
      if (previousRecord?.expiresAt > Date.now()) {
        return this.reply({
          code: previousCode,
          expires_in: Math.max(1, Math.ceil((previousRecord.expiresAt - Date.now()) / 1000)),
          reused: true,
        });
      }
      await this.storage.delete(`code:${previousCode}`);
      await this.storage.delete(activeKey);
    }

    let code;
    do {
      code = randomHex(32);
    } while (await this.storage.get(`code:${code}`));
    const now = Date.now();
    await this.storage.put(`code:${code}`, { userId, createdAt: now, expiresAt: now + CODE_TTL_MS });
    await this.storage.put(activeKey, code);
    await this.increment("issued");
    return this.reply({ code, expires_in: CODE_TTL_MS / 1000, reused: false });
  }

  async redeem(payload) {
    const code = String(payload?.code || "").trim().toUpperCase();
    if (!/^[A-F0-9]{64}$/.test(code)) throw new Error("授权码格式无效");
    const key = `code:${code}`;
    const record = await this.storage.get(key);
    if (!record) return this.reply({ authorized: false, error: "授权码无效或已使用" }, 401);

    const activeKey = `active_code:${record.userId}`;
    if (record.expiresAt <= Date.now()) {
      await this.storage.delete(key);
      if ((await this.storage.get(activeKey)) === code) await this.storage.delete(activeKey);
      return this.reply({ authorized: false, error: "授权码已过期" }, 401);
    }

    // One Durable Object serializes this operation: exactly one redeem request wins.
    await this.storage.delete(key);
    if ((await this.storage.get(activeKey)) === code) await this.storage.delete(activeKey);

    const moduleKey = `module:${record.userId}`;
    const oldRecord = await this.storage.get(moduleKey);
    if (oldRecord?.tokenHash) await this.storage.delete(`token:${oldRecord.tokenHash}`);
    const token = randomHex(32);
    const tokenHash = await sha256(token);
    const now = Date.now();
    await this.storage.put(moduleKey, {
      userId: record.userId,
      tokenHash,
      grantedAt: now,
      lastVerifiedAt: now,
      method: "self_code",
    });
    await this.storage.put(`token:${tokenHash}`, { userId: record.userId });
    await this.increment("redeemed");
    return this.reply({
      authorized: true,
      module_authorized: true,
      telegram_id: record.userId,
      auth_token: token,
    });
  }

  async verify(url, request) {
    const bearer = request.headers.get("Authorization")?.match(/^Bearer\s+(.+)$/i)?.[1] || "";
    let userId = "";
    if (bearer) {
      const tokenHash = await sha256(bearer.trim());
      const tokenRecord = await this.storage.get(`token:${tokenHash}`);
      if (tokenRecord?.userId) {
        const record = await this.storage.get(`module:${tokenRecord.userId}`);
        if (record?.tokenHash === tokenHash) {
          userId = tokenRecord.userId;
          record.lastVerifiedAt = Date.now();
          await this.storage.put(`module:${userId}`, record);
        }
      }
    }
    if (!userId) {
      return this.reply({ authorized: false, module_authorized: false, error: "缺少或无效的授权凭据" }, 401);
    }
    const requestedId = String(url.searchParams.get("telegram_id") || "");
    if (requestedId && requestedId !== userId) {
      return this.reply({ authorized: false, module_authorized: false, error: "授权凭据与 Telegram ID 不匹配" }, 403);
    }
    const moduleRecord = await this.storage.get(`module:${userId}`);
    const botId = userId;
    const botAdmin = /^\d+$/.test(botId) && Boolean(await this.storage.get(`bot_admin:${botId}`));
    return this.reply({
      authorized: Boolean(moduleRecord),
      module_authorized: Boolean(moduleRecord),
      bot_admin: botAdmin,
      telegram_id: moduleRecord?.userId,
    });
  }

  async revokeModule(payload) {
    const userId = validateUserId(payload?.userId);
    const moduleKey = `module:${userId}`;
    const record = await this.storage.get(moduleKey);
    if (record?.tokenHash) await this.storage.delete(`token:${record.tokenHash}`);
    await this.storage.delete(moduleKey);
    const code = await this.storage.get(`active_code:${userId}`);
    if (code) await this.storage.delete(`code:${code}`);
    await this.storage.delete(`active_code:${userId}`);
    if (record) await this.increment("revoked");
    return this.reply({ authorized: false, module_authorized: false, telegram_id: userId });
  }

  async setBotAdmin(payload, grant) {
    return this.setRole(payload, grant, "bot_admin");
  }

  async setMaintainer(payload, grant) {
    return this.setRole(payload, grant, "maintainer");
  }

  async setRole(payload, grant, role) {
    const userId = validateUserId(payload?.userId);
    const operatorId = String(payload?.operatorId || "");
    if (operatorId !== "admin_api") validateUserId(operatorId);
    const key = `${role}:${userId}`;
    if (grant) await this.storage.put(key, { userId, role, grantedBy: operatorId, grantedAt: Date.now() });
    else await this.storage.delete(key);
    return this.reply({ role, granted: grant, telegram_id: userId });
  }

  async getRole(url) {
    const role = String(url.searchParams.get("role") || "");
    const userId = String(url.searchParams.get("user_id") || "");
    if (!["bot_admin", "maintainer"].includes(role) || !/^\d+$/.test(userId)) {
      return this.reply({ error: "role 或 user_id 无效" }, 400);
    }
    const record = await this.storage.get(`${role}:${userId}`);
    return this.reply({ role, telegram_id: userId, granted: Boolean(record), record: record || null });
  }

  async getBotRoles() {
    const admins = await this.storage.list({ prefix: "bot_admin:" });
    const maintainers = await this.storage.list({ prefix: "maintainer:" });
    const summarize = (values) => [...values.values()]
      .filter((value) => value?.userId)
      .sort((a, b) => Number(a.grantedAt || 0) - Number(b.grantedAt || 0))
      .map((value) => ({
        telegram_id: value.userId,
        granted_by: value.grantedBy,
        granted_at: new Date(value.grantedAt || 0).toISOString(),
      }));
    return this.reply({ admins: summarize(admins), maintainers: summarize(maintainers) });
  }

  async summary() {
    const now = Date.now();
    const codes = await this.storage.list({ prefix: "code:" });
    let activeCodes = 0;
    for (const [key, value] of codes) {
      if (value?.expiresAt > now) activeCodes += 1;
      else await this.storage.delete(key);
    }
    const modules = await this.storage.list({ prefix: "module:" });
    const bots = await this.storage.list({ prefix: "bot_admin:" });
    const maintainers = await this.storage.list({ prefix: "maintainer:" });
    const users = [...modules.values()]
      .filter((value) => value?.userId)
      .sort((a, b) => Number(b.grantedAt || 0) - Number(a.grantedAt || 0))
      .slice(0, 500)
      .map((value) => ({
        telegram_id: value.userId,
        granted_at: new Date(value.grantedAt || 0).toISOString(),
        last_verified_at: value.lastVerifiedAt ? new Date(value.lastVerifiedAt).toISOString() : null,
        method: value.method || "unknown",
      }));
    const stats = (await this.storage.get("stats")) || {};
    return this.reply({
      module_authorized_count: modules.size,
      active_code_count: activeCodes,
      bot_admin_count: bots.size,
      maintainer_count: maintainers.size,
      issued_count: Number(stats.issued || 0),
      redeemed_count: Number(stats.redeemed || 0),
      revoked_count: Number(stats.revoked || 0),
      users,
      generated_at: new Date().toISOString(),
    });
  }

  async increment(name) {
    const stats = (await this.storage.get("stats")) || {};
    stats[name] = Number(stats[name] || 0) + 1;
    await this.storage.put("stats", stats);
  }

  reply(value, status = 200) {
    return new Response(JSON.stringify(value), {
      status,
      headers: { "Content-Type": "application/json" },
    });
  }
}

function validateUserId(value) {
  const userId = String(value || "");
  if (!/^\d+$/.test(userId)) throw new Error("Telegram 用户 ID 无效");
  return userId;
}

function randomHex(byteLength) {
  const data = new Uint8Array(byteLength);
  crypto.getRandomValues(data);
  return Array.from(data, (byte) => byte.toString(16).padStart(2, "0")).join("").toUpperCase();
}

async function sha256(value) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

export { TelegramAuthStore };