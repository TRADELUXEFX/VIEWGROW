// verify-purchase: checks a Google Play payment with Google, then raises the member's contact limit.
// The app calls this (instead of a database function) after every Play purchase.
//
// Secrets to set in Supabase (Edge Functions > Secrets):
//   GOOGLE_SERVICE_ACCOUNT_JSON  the whole JSON key file of the Google service account
//   PLAY_PACKAGE_NAME            optional, defaults to com.viewgrow.app
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are added by Supabase itself.
//
// Answers: 200 {ok:true} or 200 {ok:false, code} when the answer is final.
// 503 when it could not finish (Google or database unreachable): the app keeps the purchase and tries again.
import { createClient } from "npm:@supabase/supabase-js@2";

const PACKAGE = Deno.env.get("PLAY_PACKAGE_NAME") ?? "com.viewgrow.app";
const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false },
});

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

function b64url(data: string | Uint8Array): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

// Signs in to Google as the service account and returns a short-lived access token.
async function googleAccessToken(): Promise<string> {
  const sa = JSON.parse(Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON") ?? "{}");
  if (!sa.client_email || !sa.private_key) throw new Error("GOOGLE_SERVICE_ACCOUNT_JSON is missing or incomplete");
  console.log("service account in use:", sa.client_email); // email only, never the key
  const now = Math.floor(Date.now() / 1000);
  const unsigned =
    b64url(JSON.stringify({ alg: "RS256", typ: "JWT" })) + "." +
    b64url(JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/androidpublisher",
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
  return (await r.json()).access_token;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ ok: false, code: "METHOD" }, 405);
  let b: Record<string, unknown>;
  try { b = await req.json(); } catch { return json({ ok: false, code: "BAD_REQUEST" }); }

  const phone = String(b.p_phone ?? "");
  const pin = String(b.p_pin ?? "");
  const productId = String(b.p_product_id ?? "");
  const token = String(b.p_purchase_token ?? "");
  const orderId = String(b.p_order_id ?? "");

  // 1. Right number and password, known product, token not used before.
  const pre = await db.rpc("web_purchase_precheck", {
    p_phone: phone, p_pin: pin, p_product_id: productId, p_purchase_token: token,
  });
  if (pre.error) { console.error("precheck", pre.error); return json({ ok: false, code: "SERVER" }, 503); }
  if (!pre.data?.ok) return json(pre.data);
  if (pre.data.already) return json({ ok: true, already: true });

  // 2. Ask Google whether this payment is real and paid.
  let res: Response;
  try {
    const at = await googleAccessToken();
    const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${
      encodeURIComponent(PACKAGE)
    }/purchases/products/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(token)}`;
    res = await fetch(url, { headers: { Authorization: `Bearer ${at}` } });
  } catch (e) {
    console.error("google call", e);
    return json({ ok: false, code: "GOOGLE_UNREACHABLE" }, 503);
  }
  if (res.status === 400 || res.status === 404 || res.status === 410) return json({ ok: false, code: "NOT_FOUND_AT_GOOGLE" });
  if (!res.ok) {
    // 401/403 here usually means the service account has no permission in Play Console yet.
    console.error("google answered", res.status, await res.text());
    return json({ ok: false, code: "GOOGLE_ERROR" }, 503);
  }
  const p = await res.json();
  if (p.purchaseState !== 0) return json({ ok: false, code: p.purchaseState === 2 ? "PENDING" : "NOT_PAID" });
  // Already used up (consumed) by an earlier purchase: do not accept it again.
  if (p.consumptionState === 1) return json({ ok: false, code: "ALREADY_USED" });

  // 3. Record it and raise the limit.
  const ap = await db.rpc("web_apply_purchase", {
    p_phone: phone, p_pin: pin, p_product_id: productId, p_purchase_token: token,
    p_order_id: String(p.orderId ?? orderId),
  });
  if (ap.error) { console.error("apply", ap.error); return json({ ok: false, code: "SERVER" }, 503); }
  return json(ap.data);
});