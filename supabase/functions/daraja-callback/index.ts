// Supabase Edge Function: daraja-callback
// Handles Safaricom Daraja STK Push callbacks and confirms subscription
import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.38.4";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ ResultCode: 1, ResultDesc: "Method not allowed" }), {
      status: 405,
      headers: { "Content-Type": "application/json" },
    });
  }

  try {
    if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
      return new Response(JSON.stringify({ ResultCode: 1, ResultDesc: "Server misconfigured" }), {
        status: 503,
        headers: { "Content-Type": "application/json" },
      });
    }

    const body = await req.json();
    const stkCallback = body?.Body?.stkCallback;
    const checkoutRequestId =
      typeof stkCallback?.CheckoutRequestID === "string" ? stkCallback.CheckoutRequestID.trim() : "";

    if (!stkCallback || !checkoutRequestId) {
      return new Response(JSON.stringify({ ResultCode: 1, ResultDesc: "Invalid callback body" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    const resultCode = Number(stkCallback.ResultCode);
    const resultDesc = typeof stkCallback.ResultDesc === "string" ? stkCallback.ResultDesc : "Callback received";

    if (resultCode === 0) {
      const items = Array.isArray(stkCallback.CallbackMetadata?.Item) ? stkCallback.CallbackMetadata.Item : [];
      let mpesaReceipt = "";
      let paidAmount = 0;

      for (const item of items) {
        if (item?.Name === "MpesaReceiptNumber" && item?.Value != null) {
          mpesaReceipt = String(item.Value).trim();
        }
        if (item?.Name === "Amount" && item?.Value != null) {
          paidAmount = Number(item.Value) || 0;
        }
      }

      // Look up expected payment amount to prevent partial/underpaid spoofing
      const { data: expectedPayment } = await supabase
        .from("subscription_payments")
        .select("amount_ksh")
        .eq("checkout_request_id", checkoutRequestId)
        .maybeSingle();

      if (
        expectedPayment &&
        Number(expectedPayment.amount_ksh) > 0 &&
        paidAmount > 0 &&
        paidAmount < Number(expectedPayment.amount_ksh)
      ) {
        await supabase.rpc("confirm_subscription_payment", {
          p_checkout_request_id: checkoutRequestId,
          p_mpesa_receipt: mpesaReceipt || null,
          p_status: "failed",
          p_result_code: -1,
          p_result_desc: `Paid amount (${paidAmount}) is less than required amount (${expectedPayment.amount_ksh}).`,
        });
        return new Response(JSON.stringify({ ResultCode: 0, ResultDesc: "Accepted" }), {
          headers: { "Content-Type": "application/json" },
        });
      }

      const { error: rpcError } = await supabase.rpc("confirm_subscription_payment", {
        p_checkout_request_id: checkoutRequestId,
        p_mpesa_receipt: mpesaReceipt,
        p_status: "completed",
        p_result_code: resultCode,
        p_result_desc: resultDesc,
      });
      if (rpcError) {
        return new Response(JSON.stringify({ ResultCode: 1, ResultDesc: rpcError.message }), {
          status: 500,
          headers: { "Content-Type": "application/json" },
        });
      }
    } else {
      const failureStatus = resultCode === 1032 ? "cancelled" : "failed";
      const { error: rpcError } = await supabase.rpc("confirm_subscription_payment", {
        p_checkout_request_id: checkoutRequestId,
        p_mpesa_receipt: null,
        p_status: failureStatus,
        p_result_code: Number.isFinite(resultCode) ? resultCode : -1,
        p_result_desc: resultDesc,
      });
      if (rpcError) {
        return new Response(JSON.stringify({ ResultCode: 1, ResultDesc: rpcError.message }), {
          status: 500,
          headers: { "Content-Type": "application/json" },
        });
      }
    }

    return new Response(JSON.stringify({ ResultCode: 0, ResultDesc: "Accepted" }), {
      headers: { "Content-Type": "application/json" },
    });
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : "Callback processing error";
    return new Response(JSON.stringify({ ResultCode: 1, ResultDesc: message }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
