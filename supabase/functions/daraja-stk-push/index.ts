// Supabase Edge Function: daraja-stk-push
// Initiates Daraja M-Pesa STK Push using Safaricom Daraja API
import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.38.4";

const DARAJA_CONSUMER_KEY = Deno.env.get("DARAJA_CONSUMER_KEY") ?? "";
const DARAJA_CONSUMER_SECRET = Deno.env.get("DARAJA_CONSUMER_SECRET") ?? "";
const DARAJA_PASSKEY = Deno.env.get("DARAJA_PASSKEY") ?? "";
const DARAJA_SHORTCODE = Deno.env.get("DARAJA_SHORTCODE") ?? "174379"; // Paybill or Till Number
const DARAJA_CALLBACK_URL = Deno.env.get("DARAJA_CALLBACK_URL") ?? "";
const DARAJA_ENV = Deno.env.get("DARAJA_ENV") ?? "production"; // "sandbox" or "production"

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

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

async function getDarajaToken(): Promise<string> {
  const credentials = btoa(`${DARAJA_CONSUMER_KEY}:${DARAJA_CONSUMER_SECRET}`);
  const response = await fetch(getAuthUrl(), {
    headers: { Authorization: `Basic ${credentials}` },
  });
  if (!response.ok) {
    throw new Error(`Failed to authenticate with Daraja: ${response.statusText}`);
  }
  const data = await response.json();
  return data.access_token;
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", {
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
      },
    });
  }

  try {
    const { shop_id, phone_number, amount, plan_days = 30 } = await req.json();

    if (!shop_id || !phone_number || !amount) {
      return new Response(JSON.stringify({ error: "Missing required fields" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    // Standardize Kenyan phone format to 254XXXXXXXXX
    let cleanPhone = phone_number.replace(/[^0-9]/g, "");
    if (cleanPhone.startsWith("0")) {
      cleanPhone = "254" + cleanPhone.slice(1);
    } else if (cleanPhone.startsWith("7") || cleanPhone.startsWith("1")) {
      cleanPhone = "254" + cleanPhone;
    }

    const timestamp = new Date()
      .toISOString()
      .replace(/[^0-9]/g, "")
      .slice(0, 14);
    const password = btoa(`${DARAJA_SHORTCODE}${DARAJA_PASSKEY}${timestamp}`);

    const accessToken = await getDarajaToken();

    const stkPayload = {
      BusinessShortCode: DARAJA_SHORTCODE,
      Password: password,
      Timestamp: timestamp,
      TransactionType: "CustomerPayBillOnline",
      Amount: Math.round(amount),
      PartyA: cleanPhone,
      PartyB: DARAJA_SHORTCODE,
      PhoneNumber: cleanPhone,
      CallBackURL: DARAJA_CALLBACK_URL,
      AccountReference: `POS-${shop_id.slice(0, 8)}`,
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

    const stkData = await stkRes.json();

    if (stkData.ResponseCode === "0") {
      // Record payment attempt in subscription_payments
      await supabase.from("subscription_payments").insert({
        shop_id,
        phone: cleanPhone,
        amount_ksh: Math.round(amount),
        checkout_request_id: stkData.CheckoutRequestID,
        merchant_request_id: stkData.MerchantRequestID,
        plan_days,
        status: "pending",
      });

      return new Response(
        JSON.stringify({
          success: true,
          checkout_request_id: stkData.CheckoutRequestID,
          merchant_request_id: stkData.MerchantRequestID,
          customer_message: stkData.CustomerMessage,
        }),
        { headers: { "Content-Type": "application/json" } }
      );
    } else {
      return new Response(
        JSON.stringify({
          success: false,
          error: stkData.errorMessage || stkData.ResponseDescription || "STK Push failed",
        }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      );
    }
  } catch (error: any) {
    return new Response(JSON.stringify({ error: error.message }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
