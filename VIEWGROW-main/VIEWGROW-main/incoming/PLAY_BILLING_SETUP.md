# Google Play billing for the contact packs

The app side is done (`BillingManager.kt`, `UpgradeSheet.kt`). Three things are left outside the app.

## 1. Play Console: the three products
Play Console > your app > Monetize > Products > In-app products (or One-time products).
Create three products. The IDs must match `BillingManager.PRODUCT_IDS` exactly:

| Product ID      | Pack          | Base price (set in naira) |
|-----------------|---------------|---------------------------|
| `contacts_1000` | +1,000        | your price                |
| `contacts_3000` | +3,000        | your price (Recommended)  |
| `contacts_5000` | +5,000        | your price                |

- The base price is the one price you type; Google converts it to every other country's currency and adds tax.
- They are bought again and again, so they are consumable (the app consumes each one after the database confirms it).
- Set each product Active. The app must be uploaded to a Play testing track (internal testing is enough), and the
  tester's Google account added under License testing, before Google returns any prices.

## 2. Database: `web_verify_purchase`
The app calls this once per payment and only raises nothing itself. Arguments:
`p_phone`, `p_pin`, `p_product_id`, `p_purchase_token`, `p_order_id`.

It must:
1. Check the phone and password, like the other web_* functions.
2. Ask Google (Play Developer API, `purchases.products.get` for this package, product ID and token) whether the
   purchase is real and paid (`purchaseState = 0`). Use a service account kept on the server (an Edge Function is the
   usual place); never put that key in the app.
3. Refuse a token that was already used (store tokens in a table with a unique constraint) so one payment cannot
   raise the limit twice.
4. Raise the member's contact limit by the pack size (1000, 3000 or 5000, taken from the product ID, not from the app).
5. Answer `{"ok": true}`. On any failure answer `{"ok": false, "code": "..."}`.

If the answer is not ok (or there is no network) the app leaves the purchase open and tries again each time it opens.
Google refunds a purchase that is not acknowledged or consumed within 3 days, so a failed check never keeps the money.

## 3. Test
Install from the internal testing track (billing does not work in a sideloaded APK). Use a licence tester account:
test purchases show the real price but charge nothing.
