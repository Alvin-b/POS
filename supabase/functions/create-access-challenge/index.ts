// Supabase Edge Function: create-access-challenge
// Creates a short-lived QR enrollment challenge for an unauthenticated device.
import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.38.4";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const supabaseAdmin = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

const CHALLENGE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const TOKEN_GROUPS = 10;
const GROUP_LENGTH = 4;
const EXPIRES_IN_SECONDS = 600;

function jsonResponse(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

async function sha256Hex(input: string): Promise<string> {
  const bytes = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function generateChallengeToken(): string {
  const totalChars = TOKEN_GROUPS * GROUP_LENGTH;
  const randomBytes = new Uint8Array(totalChars);
  crypto.getRandomValues(randomBytes);
  const chars: string[] = [];
  for (let i = 0; i < totalChars; i++) {
    chars.push(CHALLENGE_ALPHABET[randomBytes[i] % CHALLENGE_ALPHABET.length]);
  }
  const groups: string[] = [];
  for (let i = 0; i < totalChars; i += GROUP_LENGTH) {
    groups.push(chars.slice(i, i + GROUP_LENGTH).join(""));
  }
  return groups.join("-");
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
    let deviceId = "";
    try {
      const body = await req.json();
      if (body && typeof body.device_id === "string") {
        deviceId = body.device_id.trim().slice(0, 128);
      }
    } catch {
      // Allow empty POST body
    }

    const token = generateChallengeToken();
    const tokenHash = await sha256Hex(token.toUpperCase());
    const deviceKeyHash = deviceId
      ? await sha256Hex(`device:${deviceId}`)
      : await sha256Hex(`anon:${crypto.randomUUID()}`);
    const expiresAt = new Date(Date.now() + EXPIRES_IN_SECONDS * 1000).toISOString();

    const { error: insertError } = await supabaseAdmin
      .from("access_challenges")
      .insert({
        token_hash: tokenHash,
        device_key_hash: deviceKeyHash,
        device_id: deviceId || null,
        status: "pending",
        station: "waiter",
        expires_at: expiresAt,
      });

    if (insertError) {
      return jsonResponse(
        { error: "could_not_create_qr", details: insertError.message },
        500
      );
    }

    return jsonResponse({
      token,
      qr_payload: `CPR1|${token}`,
      expires_in_seconds: EXPIRES_IN_SECONDS,
    });
  } catch (err) {
    return jsonResponse(
      {
        error: "could_not_create_qr",
        details: err instanceof Error ? err.message : "Unexpected error",
      },
      500
    );
  }
});
