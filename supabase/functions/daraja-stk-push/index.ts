// Supabase Edge Function: daraja-stk-push
// Initiates Daraja M-Pesa STK Push using Safaricom Daraja API with server-side authorization and authoritative pricing.
import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.38.4";

const DARAJA_CONSUMER_KEY = Deno.env.get("DARAJA_CONSUMER_KEY") ?? "";
const DARAJA_CONSUMER_SECRET = Deno.env.get("DARAJA_CONSUMER_SECRET") ?? "";
const DARAJA_PASSKEY = Deno.env.get("DARAJA_PASSKEY") ?? "";
const DARAJA_SHORTCODE = Deno.env.get("DARAJA_SHORTCODE") ?? "";
const DARAJA_CALLBACK_URL = Deno.env.get("DARAJA_CALLBACK_URL") ?? "";
const DARAJA_ENV = Deno.env.get("DARAJA_ENV") ?? "production";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const KENYA_PHONE_REGEX = /^254[17]\d{8}$/;

function jsonResponse(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function getAuthUrl(): string {
  return DARAJA_ENV === "production"
    ? "https://api.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials"
    : "https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials";
}

function getStkUrl(): string {
  return DARAJA_ENV === "production"
    ? "https://api.safaricom.co.ke/mpesa/stkpush/v1/processrequest"
    : "https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest";
}

function normalizeKenyanPhone(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  let clean = raw.replace(/[^0-9]/g, "");
  if (clean.startsWith("0") && clean.length === 10) {
    clean = "254" + clean.slice(1);
  } else if ((clean.startsWith("7") || clean.startsWith("1")) && clean.length === 9) {
    clean = "254" + clean;
  }
  return KENYA_PHONE_REGEX.test(clean) ? clean : null;
}

function formatDarajaTimestamp(now = new Date()): string {
  // Safaricom Daraja expects East Africa Time (UTC+3) YYYYMMDDHHMMSS or consistent 14-digit timestamp
  const eatMs = now.getTime() + 3 * 60 * 60 * 1000;
  return new Date(eatMs).toISOString().replace(/[^0-9]/g, "").slice(0, 14);
}

async function getDarajaToken(): Promise<string> {
  const credentials = btoa(`${DARAJA_CONSUMER_KEY}:${DARAJA_CONSUMER_SECRET}`);
  const response = await fetch(getAuthUrl(), {
    headers: { Authorization: `Basic ${credentials}` },
  });
  if (!response.ok) {
    throw new Error(`Failed to authenticate with Daraja (${response.status})`);
  }
  const data = await response.json();
  if (!data?.access_token) {
    throw new Error("Daraja auth response did not include an access token");
  }
  return data.access_token;
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
    return jsonResponse({ success: false, error: "Method not allowed" }, 405);
  }

  try {
    if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
      return jsonResponse({ success: false, error: "Payment service is not configured." }, 503);
    }
    if (!DARAJA_CONSUMER_KEY || !DARAJA_CONSUMER_SECRET || !DARAJA_PASSKEY || !DARAJA_SHORTCODE || !DARAJA_CALLBACK_URL) {
      return jsonResponse({ success: false, error: "M-Pesa Daraja credentials are not configured on the server." }, 503);
    }

    const authHeader = req.headers.get("Authorization") ?? "";
    const jwt = authHeader.toLowerCase().startsWith("bearer ")
      ? authHeader.slice(7).trim()
      : "";
    if (!jwt) {
      return jsonResponse({ success: false, error: "Sign in before starting an M-Pesa payment." }, 401);
    }

    const { data: userData, error: userErr } = await supabase.auth.getUser(jwt);
    if (userErr || !userData?.user?.id) {
      return jsonResponse({ success: false, error: "Invalid or expired session. Please sign in again." }, 401);
    }
    const userId = userData.user.id;

    let body: Record<string, unknown>;
    try {
      body = await req.json();
    } catch {
      return jsonResponse({ success: false, error: "Invalid JSON request body." }, 400);
    }

    const shopId = typeof body.shop_id === "string" ? body.shop_id.trim() : "";
    const cleanPhone = normalizeKenyanPhone(body.phone_number);

    if (!UUID_REGEX.test(shopId)) {
      return jsonResponse({ success: false, error: "Valid shop_id is required." }, 400);
    }
    if (!cleanPhone) {
      return jsonResponse({ success: false, error: "Enter a valid Kenyan M-Pesa phone number (07XX or 01XX)." }, 400);
    }

    // Authorize caller: active owner/manager of the shop or a platform administrator
    const [{ data: adminRow }, { data: memberRow }] = await Promise.all([
      supabase.from("platform_admins").select("user_id").eq("user_id", userId).maybeSingle(),
      supabase
        .from("shop_members")
        .select("role, employment_status")
        .eq("shop_id", shopId)
        .eq("user_id", userId)
        .maybeSingle(),
    ]);

    const isPlatformAdmin = Boolean(adminRow?.user_id);
    const isAuthorizedMember =
      memberRow &&
      (memberRow.role === "owner" || memberRow.role === "manager") &&
      (memberRow.employment_status ?? "active") === "active";

    if (!isPlatformAdmin && !isAuthorizedMember) {
      return jsonResponse(
        { success: false, error: "Only the business owner, manager, or platform admin can pay for this business." },
        403
      );
    }

    // Resolve authoritative amount and plan duration from the shops record (never trust client amount)
    const { data: shop, error: shopErr } = await supabase
      .from("shops")
      .select("id, subscription_price_ksh, subscription_plan_days, payment_lock_active, payment_lock_amount_ksh")
      .eq("id", shopId)
      .maybeSingle();

    if (shopErr || !shop) {
      return jsonResponse({ success: false, error: "Business not found." }, 404);
    }

    const standardPrice = Number(shop.subscription_price_ksh) > 0 ? Math.round(Number(shop.subscription_price_ksh)) : 2500;
    const lockAmount = Number(shop.payment_lock_amount_ksh) > 0 ? Math.round(Number(shop.payment_lock_amount_ksh)) : standardPrice;
    const authoritativeAmount = shop.payment_lock_active ? lockAmount : standardPrice;
    const authoritativePlanDays = Number(shop.subscription_plan_days) > 0 ? Math.round(Number(shop.subscription_plan_days)) : 30;

    const timestamp = formatDarajaTimestamp();
    const password = btoa(`${DARAJA_SHORTCODE}${DARAJA_PASSKEY}${timestamp}`);
    const accessToken = await getDarajaToken();

    const stkPayload = {
      BusinessShortCode: DARAJA_SHORTCODE,
      Password: password,
      Timestamp: timestamp,
      TransactionType: "CustomerPayBillOnline",
      Amount: authoritativeAmount,
      PartyA: cleanPhone,
      PartyB: DARAJA_SHORTCODE,
      PhoneNumber: cleanPhone,
      CallBackURL: DARAJA_CALLBACK_URL,
      AccountReference: `POS-${shopId.slice(0, 8)}`,
      TransactionDesc: "Counter POS SaaS Subscription",
    };

    const stkRes = await fetch(getStkUrl(), {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(stkPayload),
    });

    const stkText = await stkRes.text();
    let stkData: Record<string, unknown> = {};
    try {
      stkData = JSON.parse(stkText || "{}");
    } catch {
      return jsonResponse({ success: false, error: `M-Pesa returned an unexpected response (${stkRes.status}).` }, 502);
    }

    if (stkRes.ok && stkData.ResponseCode === "0" && typeof stkData.CheckoutRequestID === "string") {
      const { error: insertErr } = await supabase.from("subscription_payments").insert({
        shop_id: shopId,
        phone: cleanPhone,
        amount_ksh: authoritativeAmount,
        checkout_request_id: stkData.CheckoutRequestID,
        merchant_request_id: typeof stkData.MerchantRequestID === "string" ? stkData.MerchantRequestID : null,
        plan_days: authoritativePlanDays,
        status: "pending",
      });

      if (insertErr) {
        return jsonResponse(
          {
            success: false,
            error: "STK prompt was sent, but saving the payment record failed. Wait a moment and tap Check Now before retrying.",
            checkout_request_id: stkData.CheckoutRequestID,
          },
          500
        );
      }

      return jsonResponse({
        success: true,
        checkout_request_id: stkData.CheckoutRequestID,
        merchant_request_id: stkData.MerchantRequestID ?? null,
        customer_message: stkData.CustomerMessage ?? "Check your phone and enter your M-Pesa PIN.",
        amount_ksh: authoritativeAmount,
      });
    }

    const darajaErrMsg =
      (typeof stkData.errorMessage === "string" && stkData.errorMessage) ||
      (typeof stkData.ResponseDescription === "string" && stkData.ResponseDescription) ||
      "M-Pesa STK Push request failed.";
    return jsonResponse({ success: false, error: darajaErrMsg }, 400);
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : "Unexpected payment service error.";
    return jsonResponse({ success: false, error: message }, 500);
  }
});
