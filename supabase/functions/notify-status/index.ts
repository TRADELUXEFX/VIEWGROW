// notify-status: every notification the admin sends to app members, plus the nightly wake-up. Firebase Cloud Messaging.
//
// Requests (POST JSON):
//   {p_key, kind:"status"}                         "Today's status is up" to everyone (topic "members")
//   {p_key, kind:"all", title, body}               the admin's own message to everyone (topic "members")
//   {p_key, kind:"group", group, title, body}      a reminder to one group: "never" (never reshared) or "limit_full".
//                                                  Sent to each member's own topic "u<id>", only those who use the app.
//   {kind:"wake"} + header x-cron-secret           the 10pm wake-up from the Supabase scheduled job: phones save
//                                                  tonight's contacts. Silent: nothing shows on the phone.
// Old admin pages send {p_key} with no kind: treated as "status".
//
// Secrets (Supabase > Edge Functions > Secrets):
//   FIREBASE_SERVICE_ACCOUNT_JSON  Firebase private key file (project viewgrow-site). Never put it in the app or website.
//   CRON_SECRET                    any long random text; the same text goes in the scheduled job (changelog.md).
// SUPABASE_URL and SUPABASE_ANON_KEY are added by Supabase itself.
// "Verify JWT" must be OFF for this function (the admin key or the cron secret is checked here instead).
//
// The messages carry no personal data. Answers: {ok:true, sent?, failed?} or {ok:false, code}.

const SB_URL = Deno.env.get("SUPABASE_URL")!;
const SB_ANON = Deno.env.get("SUPABASE_ANON_KEY")!;

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info, x-cron-secret",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });

function b64url(data: string | Uint8Array): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function firebaseAccess(): Promise<{ token: string; project: string }> {
  const sa = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON") ?? "{}");
  if (!sa.client_email || !sa.private_key || !sa.project_id) throw new Error("NOT_SET_UP");
  const now = Math.floor(Date.now() / 1000);
  const unsigned =
    b64url(JSON.stringify({ alg: "RS256", typ: "JWT" })) + "." +
    b64url(JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    }));
  const pem = String(sa.private_key).replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const sig = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)));
  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: unsigned + "." + b64url(sig),
    }),
  });
  if (!r.ok) throw new Error("Google sign-in failed: " + r.status);
  return { token: (await r.json()).access_token, project: sa.project_id };
}

// Calls a database function with the public key, like the admin page does.
async function rpc(fn: string, args: Record<string, unknown>): Promise<any> {
  const r = await fetch(`${SB_URL}/rest/v1/rpc/${fn}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", apikey: SB_ANON, Authorization: `Bearer ${SB_ANON}` },
    body: JSON.stringify(args),
  });
  if (!r.ok) throw new Error(fn + " " + r.status);
  return await r.json();
}

async function send(fb: { token: string; project: string }, topic: string, data: Record<string, string>, ttl: string): Promise<boolean> {
  const r = await fetch(`https://fcm.googleapis.com/v1/projects/${fb.project}/messages:send`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${fb.token}` },
    body: JSON.stringify({ message: { topic, data, android: { priority: "HIGH", ttl } } }),
  });
  if (!r.ok) console.error("fcm", topic, r.status, await r.text());
  return r.ok;
}

const clean = (v: unknown, max: number) => String(v ?? "").replace(/\s+$/g, "").slice(0, max);

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ ok: false, code: "METHOD" }, 405);
  let b: Record<string, unknown>;
  try { b = await req.json(); } catch { return json({ ok: false, code: "BAD_REQUEST" }); }
  const kind = String(b.kind ?? "status");

  // ---- The nightly wake-up: only with the cron secret ----
  if (kind === "wake") {
    const secret = Deno.env.get("CRON_SECRET") ?? "";
    if (!secret || req.headers.get("x-cron-secret") !== secret) return json({ ok: false, code: "WRONG_KEY" }, 401);
    try {
      const fb = await firebaseAccess();
      return json({ ok: await send(fb, "members", { type: "wake" }, "7200s") });
    } catch (e) {
      console.error("wake", e);
      return json({ ok: false, code: "FIREBASE_ERROR" });
    }
  }

  // ---- Everything else needs the admin key ----
  const key = String(b.p_key ?? "");
  let ids: number[] = [];
  try {
    if (kind === "group") {
      const g = String(b.group ?? "");
      const r = await rpc("web_admin_group_ids", { p_key: key, p_group: g });
      if (!r?.ok) return json({ ok: false, code: r?.code === "LOCKED" ? "LOCKED" : r?.code === "BAD_GROUP" ? "BAD_GROUP" : "WRONG_KEY" });
      ids = (r.ids ?? []).map((x: unknown) => Number(x)).filter((x: number) => Number.isFinite(x));
    } else {
      const r = await rpc("web_admin_stats", { p_key: key });
      if (!r?.ok) return json({ ok: false, code: r?.code === "LOCKED" ? "LOCKED" : "WRONG_KEY" });
    }
  } catch (e) {
    console.error("admin check", e);
    return json({ ok: false, code: "SERVER" }, 503);
  }

  let fb: { token: string; project: string };
  try { fb = await firebaseAccess(); } catch (e) {
    console.error("firebase sign-in", e);
    return json({ ok: false, code: String(e).includes("NOT_SET_UP") ? "NOT_SET_UP" : "FIREBASE_ERROR" });
  }

  if (kind === "status") {
    return (await send(fb, "members", { type: "status" }, "43200s")) ? json({ ok: true }) : json({ ok: false, code: "FIREBASE_ERROR" });
  }

  const title = clean(b.title, 80), body = clean(b.body, 300);
  if (!title || !body) return json({ ok: false, code: "EMPTY" });

  if (kind === "all") {
    const ok = await send(fb, "members", { type: "msg", title, body, open: "app" }, "86400s");
    return ok ? json({ ok: true }) : json({ ok: false, code: "FIREBASE_ERROR" });
  }

  if (kind === "group") {
    // "never" opens the admin's WhatsApp chat (to reshare); "limit_full" opens the Increase my contact limit pop-up.
    const open = String(b.group) === "limit_full" ? "upgrade" : "admin";
    let sent = 0, failed = 0;
    for (let i = 0; i < ids.length; i += 25) {
      const part = ids.slice(i, i + 25);
      const res = await Promise.all(part.map((id) => send(fb, "u" + id, { type: "msg", title, body, open }, "86400s")));
      res.forEach((ok) => ok ? sent++ : failed++);
    }
    return json({ ok: true, sent, failed });
  }

  return json({ ok: false, code: "BAD_REQUEST" });
});
