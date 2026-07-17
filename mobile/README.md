# Agri Marketplace (mobile)

React Native WebView shell that loads the Marketplace Vue web app — same UX as the browser, with native bottom navigation for shoppers.

## Environments

| Flavor | WebView | API | App ID |
|--------|---------|-----|--------|
| **sit** | `http://10.0.2.2:5173` | `http://10.0.2.2:8080` | `com.agrimarketplace.sit` |
| **uat** / **prod** | Set in `src/config/environments.ts` | same | `com.agrimarketplace[.uat]` |

Update `HOSTED_WEB_APP_URL` and `HOSTED_API_BASE_URL` in `src/config/environments.ts` to your Vercel + Railway URLs before UAT/PROD builds.

## Commands

```bash
npm install
npm run android:sit
npm run android:uat
npm run android:prod
```

## Roles

- **Guests / CLIENT** — native bottom tabs (Shop, Cart, Orders, Help, Account)
- **Provider / Admin / Support** — web hamburger / side-nav inside the WebView

Payments (Cash, Manual EFT, Peach) and all other features run in the web app — identical to the mobile browser.
