# Android App Setup

## Prerequisites

- Node.js 18+
- Android Studio with SDK
- Backend running (see `apps/backend/`)

## Configure API URL

Before running, set your backend URL in `src/config/index.ts`:

- **Android Emulator**: `http://10.0.2.2:8080/api` (10.0.2.2 = host's localhost)
- **Physical device**: Use your computer's IP, e.g. `http://192.168.1.5:8080/api`
- Find your IP: `ipconfig` (Windows) or `ifconfig` (Mac/Linux)

## Run the app

```bash
cd apps/mobile
npm install
npx react-native run-android
```

## Backend must be running

Start the Spring Boot backend on port 8080 before using the app.
