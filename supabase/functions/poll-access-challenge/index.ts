// Supabase Edge Function: poll-access-challenge
// Polls an access_challenges record by normalized token hash and issues Supabase
// session tokens once approved by an authorized owner or platform administrator.
import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.38.4";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? SUPABASE_SERVICE_ROLE_KEY;

const supabaseAdmin = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
  auth: { autoRefreshToken: false, persistSession: false },
});

function jsonResponse(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function normalizeToken(raw: unknown): string {
  if (typeof raw !== "string") return "";
  const trimmed = raw.trim();
  const stripped = trimmed.toLowerCase().startsWith("cpr1|")
    ? trimmed.slice(5).trim()
    : trimmed;
  return stripped.toUpperCase();
}

async function sha256Hex(input: string): Promise<string> {
  const bytes = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", {
      headers: {
        "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
        "Access-Control-Allow-Methods": "POST, OPTIONS",
      },
    });
  }

  if (req.method !== "POST") {
    return jsonResponse({ error: "method_not_allowed" }, 405);
  }

  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
    return jsonResponse({ error: "qr_service_not_configured" }, 503);
  }

  try {
    let body: Record<string, unknown>;
    try {
      body = await req.json();
    } catch {
      return jsonResponse({ error: "invalid_request_body" }, 400);
    }

    const token = normalizeToken(body?.token);
    if (!token) {
      return jsonResponse({ error: "qr_not_found" }, 400);
    }

    const tokenHash = await sha256Hex(token);
    const { data: challenge, error: readError } = await supabaseAdmin
      .from("access_challenges")
      .select("*")
      .eq("token_hash", tokenHash)
      .maybeSingle();

    if (readError) {
      return jsonResponse(
        { error: "could_not_read_qr", details: readError.message },
        500
      );
    }

    if (!challenge) {
      return jsonResponse({ error: "qr_not_found" }, 404);
    }

    const status = String(challenge.status ?? "pending").toLowerCase();
    const expiresAtMs = challenge.expires_at ? Date.parse(challenge.expires_at) : 0;
    const isExpired = expiresAtMs > 0 && expiresAtMs <= Date.now();

    if (status === "consumed" || status === "used" || status === "cancelled" || status === "rejected" || status === "expired") {
      return jsonResponse({ status });
    }

    if (status !== "approved") {
      if (isExpired) {
        await supabaseAdmin
          .from("access_challenges")
          .update({ status: "expired", updated_at: new Date().toISOString() })
          .eq("id", challenge.id)
          .eq("status", "pending");
        return jsonResponse({ status: "expired" });
      }
      return jsonResponse({ status: "pending" });
    }

    if (challenge.consumed_at) {
      return jsonResponse({ status: "consumed" });
    }

    if (isExpired && !challenge.approved_at) {
      return jsonResponse({ status: "expired" });
    }

    // Acquire a short lease so concurrent polls do not consume the approved challenge twice.
    const leaseCutoff = new Date(Date.now() - 15_000).toISOString();
    const nowIso = new Date().toISOString();
    const { data: leasedRows, error: leaseError } = await supabaseAdmin
      .from("access_challenges")
      .update({ processing_at: nowIso, updated_at: nowIso })
      .eq("id", challenge.id)
      .eq("status", "approved")
      .is("consumed_at", null)
      .or(`processing_at.is.null,processing_at.lt.${leaseCutoff}`)
      .select("*");

    if (leaseError) {
      return jsonResponse(
        { error: "could_not_read_qr", details: leaseError.message },
        500
      );
    }

    if (!leasedRows || leasedRows.length === 0) {
      // Another poll request is actively completing session issuance.
      return jsonResponse({ status: "pending" });
    }

    const leased = leasedRows[0];
    const shopId = leased.shop_id as string | null;
    const userId = leased.user_id as string | null;

    if (!shopId || !userId) {
      return jsonResponse({ error: "could_not_redeem_qr_missing_target" }, 500);
    }

    const [{ data: shop }, { data: membership }, { data: userData, error: userErr }] = await Promise.all([
      supabaseAdmin.from("shops").select("id, status").eq("id", shopId).maybeSingle(),
      supabaseAdmin
        .from("shop_members")
        .select("role, employment_status")
        .eq("shop_id", shopId)
        .eq("user_id", userId)
        .maybeSingle(),
      supabaseAdmin.auth.admin.getUserById(userId),
    ]);

    if (!shop || shop.status !== "active") {
      return jsonResponse({ error: "business_not_active" }, 403);
    }
    if (!membership || membership.employment_status !== "active") {
      return jsonResponse({ error: "employee_inactive" }, 403);
    }
    if (userErr || !userData?.user?.email) {
      return jsonResponse({ error: "could_not_find_enrolled_user" }, 500);
    }

    const { data: linkData, error: linkError } = await supabaseAdmin.auth.admin.generateLink({
      type: "magiclink",
      email: userData.user.email,
    });

    const hashedToken = linkData?.properties?.hashed_token;
    if (linkError || !hashedToken) {
      await supabaseAdmin
        .from("access_challenges")
        .update({ processing_at: null, updated_at: new Date().toISOString() })
        .eq("id", leased.id);
      return jsonResponse(
        { error: "could_not_create_qr_session", details: linkError?.message ?? "missing_token" },
        500
      );
    }

    const authClient = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
      auth: { autoRefreshToken: false, persistSession: false },
    });
    const { data: otpData, error: otpError } = await authClient.auth.verifyOtp({
      token_hash: hashedToken,
      type: "magiclink",
    });

    if (otpError || !otpData?.session?.access_token || !otpData?.session?.refresh_token) {
      await supabaseAdmin
        .from("access_challenges")
        .update({ processing_at: null, updated_at: new Date().toISOString() })
        .eq("id", leased.id);
      return jsonResponse(
        { error: "could_not_create_qr_session", details: otpError?.message ?? "otp_verify_failed" },
        500
      );
    }

    await supabaseAdmin
      .from("access_challenges")
      .update({
        status: "consumed",
        consumed_at: new Date().toISOString(),
        processing_at: null,
        updated_at: new Date().toISOString(),
      })
      .eq("id", leased.id);

    const station = String(leased.station ?? "waiter").toLowerCase() === "butchery"
      ? "butchery"
      : "waiter";

    return jsonResponse({
      status: "approved",
      access_token: otpData.session.access_token,
      refresh_token: otpData.session.refresh_token,
      user_id: userData.user.id,
      email: userData.user.email,
      shop_id: shopId,
      business_id: shopId,
      role: membership.role ?? leased.role ?? "cashier",
      station,
      station_name: leased.station_name ?? null,
    });
  } catch (err) {
    return jsonResponse(
      {
        error: "could_not_read_qr",
        details: err instanceof Error ? err.message : "Unexpected error",
      },
      500
    );
  }
});
