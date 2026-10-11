
## SaaS account and security behavior

The current Android app is a tenant-aware client for the Supabase backend:

- A signed-in user creates or selects a business membership; the active business is the cache boundary.
- Android sessions are stored using an Android Keystore-backed AES-GCM envelope. Room is treated as a replaceable cache and is cleared on sign-out or business switch.
- Backend RLS/RPC checks remain authoritative. A cashier, inventory clerk, manager, and owner see different stock actions, but the server independently enforces the same role rules.
- If the platform administrator suspends or closes a business, `my_account` reports the retained membership status while all active-business reads and mutations are blocked. Inventory and sales remain stored for reopening.
- Superadmin accounts use the backend `platform_admins` table and can create client businesses or set them active, suspended, or closed from the in-app admin console.
- `allowBackup=false`, explicit backup exclusions, HTTPS-only traffic, R8 shrinking/obfuscation, and no service-role key in the APK are enabled. No APK can be made impossible to reverse engineer; authorization and secrets therefore remain on the backend.

## Stock at different purchase prices

Receiving stock asks for a **unit cost**. Each receipt becomes a separate backend FIFO batch. For example, 10 bottles at KSh 100 followed by 6 bottles at KSh 120 remain separate layers; sales consume the oldest layer first and retain their exact cost and gross-profit calculation. Undo restores the exact consumed layers. A physical count correction uses the audited `adjust_stock` RPC rather than a direct stock write.
