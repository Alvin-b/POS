// Supabase Edge Function: approve-access-challenge
// Authorizes a pending QR enrollment challenge after verifying the caller is an
// active business owner (waiter/butchery) or platform administrator.
import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.38.4";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const supabaseAdmin = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
  auth: { autoRefreshToken: false, persistSession: false },
});

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ALLOWED_ADMIN_ROLES = new Set(["owner", "manager", "inventory_clerk", "cashier"]);
const ALLOWED_OWNER_ROLES = new Set(["cashier"]);

function jsonResponse(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function normalizeToken(raw: unknown): string {
  if (typeof raw !== "string") return "";
  let current = raw.trim().replace(/^["']|["']$/g, "");
  while (current.toLowerCase().startsWith("cpr1|")) {
    current = current.slice(5).trim();
  }
  return (current.split(/\s+/)[0] ?? "").trim();
}

async function sha256Hex(input: string): Promise<string> {
  const bytes = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

async function candidateTokenHashes(rawToken: string): Promise<string[]> {
  const upper = rawToken.toUpperCase();
  const lower = rawToken.toLowerCase();
  const variants = Array.from(
    new Set([
      upper,
      rawToken,
      lower,
      `CPR1|${upper}`,
      `CPR1|${rawToken}`,
      `cpr1|${lower}`,
    ].filter((v) => v.length > 0))
  );
  return await Promise.all(variants.map((v) => sha256Hex(v)));
}

function generateStrongPassword(): string {
  const bytes = new Uint8Array(24);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
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
    const authHeader = req.headers.get("Authorization") ?? "";
    const jwt = authHeader.toLowerCase().startsWith("bearer ")
      ? authHeader.slice(7).trim()
      : "";
    if (!jwt) {
      return jsonResponse({ error: "unauthorized" }, 401);
    }

    const { data: userData, error: authError } = await supabaseAdmin.auth.getUser(jwt);
    if (authError || !userData?.user) {
      return jsonResponse({ error: "unauthorized" }, 401);
    }
    const actorId = userData.user.id;

    let body: Record<string, unknown>;
    try {
      body = await req.json();
    } catch {
      return jsonResponse({ error: "invalid_request_body" }, 400);
    }

    const token = normalizeToken(body?.token);
    const shopId = typeof body?.shop_id === "string" ? body.shop_id.trim() : "";
    const requestedRole = typeof body?.role === "string" ? body.role.trim().toLowerCase() : "cashier";
    const requestedStation = typeof body?.station === "string" ? body.station.trim().toLowerCase() : "waiter";
    const station = requestedStation === "butchery" ? "butchery" : "waiter";
    const stationName = typeof body?.station_name === "string"
      ? body.station_name.trim().slice(0, 80) || null
      : null;

    if (!token || !UUID_REGEX.test(shopId)) {
      return jsonResponse({ error: "invalid_qr_approval_request" }, 400);
    }

    const [{ data: adminRow }, { data: ownerRow }, { data: shop }] = await Promise.all([
      supabaseAdmin.from("platform_admins").select("user_id").eq("user_id", actorId).maybeSingle(),
      supabaseAdmin
        .from("shop_members")
        .select("role, employment_status")
        .eq("shop_id", shopId)
        .eq("user_id", actorId)
        .maybeSingle(),
      supabaseAdmin.from("shops").select("id, name, status").eq("id", shopId).maybeSingle(),
    ]);

    const isPlatformAdmin = Boolean(adminRow?.user_id);
    const isShopOwner = ownerRow?.role === "owner" && ownerRow?.employment_status === "active";

    if (!isPlatformAdmin && !isShopOwner) {
      return jsonResponse({ error: "forbidden" }, 403);
    }
    if (isPlatformAdmin && !ALLOWED_ADMIN_ROLES.has(requestedRole)) {
      return jsonResponse({ error: "invalid_role" }, 400);
    }
    if (!isPlatformAdmin && !ALLOWED_OWNER_ROLES.has(requestedRole)) {
      return jsonResponse({ error: "forbidden_role" }, 403);
    }
    if (!shop || shop.status !== "active") {
      return jsonResponse({ error: "business_not_active" }, 400);
    }

    const candidateHashes = await candidateTokenHashes(token);
    const { data: challenge, error: challengeReadError } = await supabaseAdmin
      .from("access_challenges")
      .select("*")
      .in("token_hash", candidateHashes)
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (challengeReadError) {
      return jsonResponse({ error: "could_not_read_qr", details: challengeReadError.message }, 500);
    }
    if (!challenge) {
      return jsonResponse({ error: "qr_not_found" }, 404);
    }

    // Idempotent retry: if the exact same approver scans an already-approved unconsumed QR
    // for the same shop, confirm approval cleanly instead of reporting qr_already_used.
    if (
      challenge.status === "approved" &&
      !challenge.consumed_at &&
      challenge.shop_id === shopId &&
      challenge.approved_by === actorId &&
      challenge.user_id
    ) {
      return jsonResponse({
        approved: true,
        already_approved: true,
        shop_id: shopId,
        role: challenge.role ?? requestedRole,
        station: challenge.station ?? station,
        station_name: challenge.station_name ?? stationName,
        user_id: challenge.user_id,
      });
    }

    if (challenge.status !== "pending" || challenge.consumed_at || challenge.approved_at) {
      return jsonResponse({ error: "qr_already_used" }, 409);
    }
    if (challenge.expires_at && Date.parse(challenge.expires_at) <= Date.now()) {
      await supabaseAdmin
        .from("access_challenges")
        .update({ status: "expired", updated_at: new Date().toISOString() })
        .eq("id", challenge.id);
      return jsonResponse({ error: "qr_expired" }, 410);
    }

    const deviceKeyHash = typeof challenge.device_key_hash === "string" && challenge.device_key_hash.trim()
      ? challenge.device_key_hash.trim()
      : await sha256Hex(`challenge:${challenge.id}`);

    // Reuse existing identity for this phone + business + role if already enrolled before.
    const { data: existingIdentity } = await supabaseAdmin
      .from("access_device_identities")
      .select("user_id")
      .eq("device_key_hash", deviceKeyHash)
      .eq("shop_id", shopId)
      .eq("role", requestedRole)
      .maybeSingle();

    let enrolledUserId = existingIdentity?.user_id as string | undefined;

    if (enrolledUserId) {
      const { data: existingUser } = await supabaseAdmin.auth.admin.getUserById(enrolledUserId);
      if (!existingUser?.user) {
        enrolledUserId = undefined;
      }
    }

    if (!enrolledUserId) {
      const labelPrefix = station === "butchery" ? "butchery" : requestedRole;
      const syntheticEmail = `cp-${labelPrefix}-${deviceKeyHash.slice(0, 12)}-${Date.now().toString(36)}@devices.counterpos.local`;
      const { data: createdUser, error: createUserError } = await supabaseAdmin.auth.admin.createUser({
        email: syntheticEmail,
        password: generateStrongPassword(),
        email_confirm: true,
        user_metadata: {
          enrolled_via: "qr_challenge",
          station,
          station_name: stationName,
          shop_id: shopId,
        },
      });
      if (createUserError || !createdUser?.user?.id) {
        return jsonResponse(
          { error: "could_not_create_enrolled_user", details: createUserError?.message ?? "unknown" },
          500
        );
      }
      enrolledUserId = createdUser.user.id;
    }

    const displayName = station === "butchery"
      ? stationName || `${shop.name} Butchery`
      : null;

    const { error: memberError } = await supabaseAdmin
      .from("shop_members")
      .upsert(
        {
          shop_id: shopId,
          user_id: enrolledUserId,
          role: requestedRole,
          employment_status: "active",
          status_reason: null,
          ...(displayName ? { display_name: displayName } : {}),
        },
        { onConflict: "shop_id,user_id" }
      );

    if (memberError) {
      return jsonResponse({ error: "could_not_enroll_member", details: memberError.message }, 500);
    }

    const nowIso = new Date().toISOString();
    const { error: identityUpsertError } = await supabaseAdmin
      .from("access_device_identities")
      .upsert(
        {
          device_key_hash: deviceKeyHash,
          shop_id: shopId,
          user_id: enrolledUserId,
          role: requestedRole,
          station,
          station_name: stationName,
          updated_at: nowIso,
        },
        { onConflict: "device_key_hash" }
      );

    if (identityUpsertError) {
      await supabaseAdmin
        .from("access_device_identities")
        .delete()
        .eq("shop_id", shopId)
        .eq("user_id", enrolledUserId)
        .eq("role", requestedRole);
      await supabaseAdmin
        .from("access_device_identities")
        .upsert(
          {
            device_key_hash: deviceKeyHash,
            shop_id: shopId,
            user_id: enrolledUserId,
            role: requestedRole,
            station,
            station_name: stationName,
            updated_at: nowIso,
          },
          { onConflict: "device_key_hash" }
        );
    }

    if (station === "butchery") {
      const { data: existingButchery } = await supabaseAdmin
        .from("butchery_config")
        .select("shop_id, butchery_name")
        .eq("shop_id", shopId)
        .maybeSingle();
      if (!existingButchery) {
        await supabaseAdmin.from("butchery_config").insert({
          shop_id: shopId,
          enabled: true,
          butchery_name: stationName || `${shop.name} Butchery`,
          updated_at: nowIso,
        });
      } else {
        await supabaseAdmin
          .from("butchery_config")
          .update({
            enabled: true,
            ...(stationName ? { butchery_name: stationName } : {}),
            updated_at: nowIso,
          })
          .eq("shop_id", shopId);
      }
    }

    const { error: approveError } = await supabaseAdmin
      .from("access_challenges")
      .update({
        status: "approved",
        shop_id: shopId,
        user_id: enrolledUserId,
        approved_by: actorId,
        role: requestedRole,
        station,
        station_name: stationName,
        approved_at: nowIso,
        updated_at: nowIso,
      })
      .eq("id", challenge.id)
      .eq("status", "pending");

    if (approveError) {
      return jsonResponse({ error: "could_not_approve_qr", details: approveError.message }, 500);
    }

    return jsonResponse({
      approved: true,
      shop_id: shopId,
      role: requestedRole,
      station,
      station_name: stationName,
      user_id: enrolledUserId,
    });
  } catch (err) {
    return jsonResponse(
      {
        error: "could_not_approve_qr",
        details: err instanceof Error ? err.message : "Unexpected error",
      },
      500
    );
  }
});
