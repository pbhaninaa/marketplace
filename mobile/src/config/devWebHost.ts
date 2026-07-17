/**
 * SIT/local only — host where Vite + Spring Boot run on your PC.
 *
 * - Android emulator: `10.0.2.2` (maps to host localhost; no adb reverse needed for web)
 * - Physical device on same Wi‑Fi: your PC LAN IP (e.g. `10.85.80.158`)
 * - adb reverse alternative: set to `localhost` and run `adb reverse tcp:5173 tcp:5173`
 */
export const SIT_DEV_HOST = '10.0.2.2';
