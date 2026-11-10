// Supabase Edge Function: daraja-callback
// Handles Safaricom Daraja STK Push callbacks and confirms subscription
import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.38.4";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

serve(async (req: Request) => {
  try {
    const body = await req.json();
    const stkCallback = body?.Body?.stkCallback;

    if (!stkCallback) {
      return new Response(JSON.stringify({ ResultCode: 1, ResultDesc: "Invalid body" }), { status: 400 });
    }

    const checkoutRequestId = stkCallback.CheckoutRequestID;
    const resultCode = stkCallback.ResultCode;
    const resultDesc = stkCallback.ResultDesc;

    if (resultCode === 0) {
      // Payment Successful
      const items = stkCallback.CallbackMetadata?.Item || [];
      let mpesaReceipt = "";
      let phone = "";
      let amount = 0;

      for (const item of items) {
        if (item.Name === "MpesaReceiptNumber") mpesaReceipt = String(item.Value);
        if (item.Name === "PhoneNumber") phone = String(item.Value);
        if (item.Name === "Amount") amount = Number(item.Value);
      }

      await supabase.rpc("confirm_subscription_payment", {
        p_checkout_request_id: checkoutRequestId,
        p_mpesa_receipt: mpesaReceipt,
        p_status: "completed",
        p_result_code: resultCode,
        p_result_desc: resultDesc,
      });
    } else {
      // Payment Failed / Cancelled by user
      const failureStatus = resultCode === 1032 ? "cancelled" : "failed";
      await supabase.rpc("confirm_subscription_payment", {
        p_checkout_request_id: checkoutRequestId,
        p_mpesa_receipt: null,
        p_status: failureStatus,
        p_result_code: resultCode,
        p_result_desc: resultDesc,
      });
    }

    return new Response(JSON.stringify({ ResultCode: 0, ResultDesc: "Accepted" }), {
      headers: { "Content-Type": "application/json" },
    });
  } catch (error: any) {
    return new Response(JSON.stringify({ ResultCode: 1, ResultDesc: error.message }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
