# Administrator guide

How to run Agri Marketplace as a platform administrator: providers, listings, users, support staff, referrals, reports and settings.

## At a glance

- The **first administrator** created during setup is the **platform owner**. Only the owner can override a provider's trial or plan.
- Providers can only take orders while they are **active** and have a **free trial or paid plan**. Others are hidden from buyers automatically.
- The **dashboard** is an overview. Details live in the menu: Orders & payments, Providers, Listings, Users, Support users, Referrals and Settings.

## First-time setup

When the platform is new, the first visitor is sent to **Setup** to create the platform administrator. This account becomes the **platform owner**. Use a strong password: 8 to 64 characters, not common, not containing your email name.

## Dashboard

Choose a **date range** at the top. The dashboard shows:

- **Revenue** from sales and rentals in the period (cancelled orders excluded)
- **Orders and bookings**, **New providers** (and new users) and **Live listings**
- a **Revenue** chart over time
- **Providers who can take orders**: how many are on a free trial, on Basic, on Premium, have a trial that ended with no plan, or are pending or suspended

It updates by itself when orders, payments or subscriptions change.

## Providers

Open **Providers** to see every provider business and its status.

- **Pending**: signed up but not yet active
- **Active**: can trade (while on a trial or plan)
- **Suspended**: blocked; their listings are hidden

Open a provider to see their business details, listings and staff.

### Overriding a trial or plan (platform owner only)

On a provider's page, the **Subscription** panel lets the platform owner:

- **Extend the trial** by a number of days (1 to 365)
- **End the trial** now
- **Set the plan** (Basic or Premium) and an **active until** date, without a payment

Every change needs a **reason**, and is recorded with who made it and when. The provider is notified. Other administrators can see the panel but not change it.

## Listings

Open **Listings** to review everything on the platform:

- **Unpublish** hides a listing from buyers without deleting it; **Publish** shows it again.
- **Delete** removes a listing that breaks the rules.
- **Delete all** removes every listing on the platform. Only use it to reset a test environment.

## Users and support users

- **Users** lists every account with its email, role, provider and whether it is enabled. **Delete** disables an account so it can no longer sign in. **Delete all** exists for resetting a test environment; never use it on live data.
- **Support users**: create accounts for your support team. They get a temporary password; ask them to change it from **Profile**.

## Orders and payments

Open **Orders & payments** for every order and payment on the platform. Filter by status, payment status, method, provider, type and dates, and **Export** the result as **CSV** or **Excel**.

## Seller payouts

Orders and rentals paid online (**Card / Instant EFT**) are collected by the platform. Open **Seller payouts** to pay sellers their share:

1. **Owed per seller** lists each seller with payments **awaiting payout**, the amount buyers paid, the platform fee, the amount owed and the seller's bank details.
2. Pay the seller by bank transfer outside the platform.
3. Type the **bank transfer reference** at the top of the page, then press **Mark all paid out** for that seller, or filter **Payments** by seller, tick the payments you paid and press **Mark selected paid out**. The payments move to **Paid out** with the date, your email and the reference; the seller sees this in their history.

Filter **Payments** by status, seller and paid dates to look back at earlier payouts.

**Refund due to buyer.** If a buyer's online payment is confirmed after the order or booking was already cancelled (for example it was released for non-payment), or a seller cancels a paid online booking, the payment shows as **Refund due to buyer** and is not paid to the seller. Refund the buyer the full amount from the payment gateway's dashboard (PayFast or Peach), then choose status **Refund due to buyer**, tick the payments, enter the refund reference and press **Mark selected refunded**.

## Referrals

Open **Referrals** to see every referral with filters for referrer, referred business, status and dates, plus totals.

When a referred business pays its first subscription, the referrer earns a commission. Payouts are made outside the platform; press **Mark paid** once you have paid a referrer.

## Settings

Open **Settings** to manage:

- **Platform**: name and general settings
- **Subscription pricing (monthly)**: the Basic and Premium prices
- **Online payments**: the **payment gateway** behind the Card / Instant EFT option (**Automatic** uses PayFast when it is set up, otherwise Peach Payments; choosing one gateway turns online payments off if that gateway is not set up on the server) and the **platform fee on online orders** (percent kept from each online order or rental before the seller is paid out; default 0%). A new fee applies to payments confirmed after you save.
- **Referral programme**: the **commission %** and the **claim window** (days)
- **Order invoice (admin)**: look up any order's invoice
- **Maintenance**: housekeeping tools. Read the description of each one before running it.

## Security

- Wrong password **5 times** locks an account for **15 minutes**; a password reset unlocks it.
- Sign-up, sign-in, password reset and email codes are rate-limited to stop guessing attacks.
- Change your own password from **Profile**.
- Keep the platform owner account for owner tasks only, and create separate administrator and support accounts for daily work.

## Questions and problems

**A provider says buyers can't see their listings.**
Check the provider's status and subscription panel. If the trial ended and they have no plan, ask them to choose a plan, or (owner only) extend their trial.

**A provider paid but still shows "no active plan".**
Check **Orders & payments** for the payment. If the payment gateway (PayFast or Peach) confirmed it, the plan activates automatically within a minute. If needed, the platform owner can set the plan manually with a reason.

**How do I see who changed a provider's plan?**
The provider's Subscription panel shows the override history: who, when and why.
