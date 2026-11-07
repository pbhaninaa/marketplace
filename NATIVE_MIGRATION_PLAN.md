# Native React Native Migration Plan
## Agri Marketplace - WebView to Native Migration

**Date**: 2026-07-22
**Current State**: WebView-based mobile app wrapping Vue.js web application
**Target State**: Full native React Native application with feature parity
**Estimated Timeline**: 3-4 weeks for full implementation

---

## Executive Summary

This document outlines the complete migration from a WebView-based mobile application to a fully native React Native implementation. The migration will maintain 100% feature parity with the current web application while providing improved performance, better UX, and native mobile capabilities.

---

## Current Application Analysis

### Web Application Stack
- **Framework**: Vue.js 3.4.21
- **State Management**: Pinia 2.1.7
- **Routing**: Vue Router 4.3.0
- **HTTP Client**: Axios 1.6.8
- **Build Tool**: Vite 8.0.10
- **Components**: 56 Vue components
- **Views**: 35+ screens

### Key Features Inventory

#### Customer-Facing Features (Priority 1)
1. **Marketplace/Browse**
   - Listing grid/list view
   - Category filters
   - Provider filters
   - Price range filters
   - Location filters
   - Search functionality
   - Rent/Buy toggle
   - Listing detail modal

2. **Shopping Cart**
   - Add/remove items
   - Quantity adjustment
   - Cart badge counter
   - Cart persistence
   - Provider grouping

3. **Checkout**
   - Multi-step checkout flow
   - Address input
   - Delivery/pickup selection
   - Payment integration (Peach Payments)
   - Order confirmation

4. **Authentication**
   - Login (email/password)
   - Registration
   - Client OTP verification
   - Forgot password
   - Reset password
   - Session persistence

5. **Orders**
   - Order history lookup
   - Invoice viewing/download
   - Order status tracking

6. **Settings**
   - Account management
   - Theme toggle (light/dark)
   - Help center access
   - Logout

7. **Help Center**
   - Quick start guide
   - FAQ sections
   - Contact information

#### Admin/Provider Features (Priority 2)
8. **Provider Dashboard**
9. **Listings Management**
10. **Orders Management**
11. **Team Management**
12. **Admin Panel**
13. **Support Tools**

---

## Migration Architecture

### Technology Stack

#### Core Framework
```
React Native 0.73+
TypeScript 5.0+
React 18.2+
```

#### Navigation
```
@react-navigation/native 6.x
@react-navigation/bottom-tabs
@react-navigation/stack
@react-navigation/native-stack
```

#### State Management
```
@reduxjs/toolkit 2.0+ (or Zustand 4.x for lighter approach)
react-redux 9.x
redux-persist (for persistence)
```

#### API & Networking
```
axios 1.6+
react-query (TanStack Query) 5.x
```

#### UI Components
```
react-native-paper (Material Design)
react-native-vector-icons
react-native-maps
@react-native-picker/picker
react-native-gesture-handler
react-native-reanimated
```

#### Forms & Validation
```
react-hook-form 7.x
zod (for validation)
```

#### Storage
```
@react-native-async-storage/async-storage
react-native-encrypted-storage (for tokens)
```

#### Utilities
```
date-fns (date formatting)
react-native-device-info
@notifee/react-native (push notifications - already installed)
```

---

## Phase-by-Phase Implementation Plan

### Phase 1: Project Setup & Foundation (Days 1-3)

#### Day 1: Project Structure
- [ ] Clean up current mobile directory
- [ ] Set up new folder structure:
  ```
  mobile/
  ├── src/
  │   ├── api/           # API client & endpoints
  │   ├── components/    # Reusable components
  │   ├── screens/       # Screen components
  │   ├── navigation/    # Navigation configuration
  │   ├── store/         # Redux store & slices
  │   ├── hooks/         # Custom hooks
  │   ├── utils/         # Utility functions
  │   ├── types/         # TypeScript types
  │   ├── constants/     # Constants & config
  │   ├── theme/         # Theme configuration
  │   └── assets/        # Images, fonts, etc.
  ```

#### Day 2: Core Infrastructure
- [ ] Set up TypeScript configuration
- [ ] Configure Redux Toolkit store
- [ ] Create API client with Axios
- [ ] Set up environment configuration
- [ ] Create theme system (light/dark)
- [ ] Set up navigation structure

#### Day 3: Authentication System
- [ ] Build auth slice (Redux)
- [ ] Create API endpoints for auth
- [ ] Implement token storage
- [ ] Build Login screen
- [ ] Build Register screen
- [ ] Build Forgot Password screen
- [ ] Build Reset Password screen
- [ ] Add session persistence

---

### Phase 2: Core Customer Screens (Days 4-10)

#### Days 4-5: Marketplace/Browse
- [ ] Create listing types/interfaces
- [ ] Build listings API client
- [ ] Create listing card component
- [ ] Build marketplace grid view
- [ ] Implement filters component
  - Category filter
  - Provider filter
  - Price range filter
  - Location filter
  - Search bar
- [ ] Add rent/buy toggle
- [ ] Implement pull-to-refresh
- [ ] Build listing detail modal
- [ ] Add image carousel
- [ ] Implement infinite scroll/pagination

#### Days 6-7: Shopping Cart & Checkout
- [ ] Create cart slice (Redux)
- [ ] Build cart API endpoints
- [ ] Create cart screen
- [ ] Build cart item component
- [ ] Implement add to cart functionality
- [ ] Create cart badge component
- [ ] Build checkout screen (multi-step)
  - Step 1: Review items
  - Step 2: Delivery info
  - Step 3: Payment
  - Step 4: Confirmation
- [ ] Integrate Peach Payments
- [ ] Add address input component
- [ ] Implement geolocation for delivery

#### Days 8-9: Orders & Invoices
- [ ] Create orders slice
- [ ] Build orders API client
- [ ] Create order lookup screen
- [ ] Build order history list
- [ ] Create order detail view
- [ ] Add invoice viewer
- [ ] Implement PDF download/share

#### Day 10: Settings & Help
- [ ] Build settings screen
- [ ] Implement theme toggle
- [ ] Create help/FAQ screen
- [ ] Add account management
- [ ] Build profile screen (if logged in)

---

### Phase 3: UI Components Library (Days 11-13)

#### Reusable Components
- [ ] Button component (variants: primary, secondary, outline, ghost)
- [ ] Input component (text, email, password, number)
- [ ] Select/Picker component
- [ ] Textarea component
- [ ] Checkbox component
- [ ] Radio button component
- [ ] Switch/Toggle component
- [ ] Modal/Dialog component
- [ ] Alert/Toast component
- [ ] Loading spinner/skeleton
- [ ] Empty state component
- [ ] Error boundary component
- [ ] Badge component
- [ ] Card component
- [ ] Tabs component
- [ ] Accordion component
- [ ] Bottom sheet component

---

### Phase 4: Advanced Features (Days 14-18)

#### Days 14-15: Provider Features
- [ ] Provider dashboard screen
- [ ] Listings management
- [ ] Orders management
- [ ] Profile/settings
- [ ] Team management (if applicable)

#### Days 16-17: Admin Features
- [ ] Admin dashboard
- [ ] Users management
- [ ] Providers management
- [ ] System settings
- [ ] Reports/analytics

#### Day 18: Additional Features
- [ ] Push notifications handling
- [ ] Deep linking
- [ ] Offline mode support
- [ ] Image caching
- [ ] Error tracking integration

---

### Phase 5: Testing & Optimization (Days 19-21)

#### Day 19: Unit Testing
- [ ] Test Redux slices
- [ ] Test API client
- [ ] Test utility functions
- [ ] Test custom hooks

#### Day 20: Integration Testing
- [ ] Test navigation flows
- [ ] Test authentication flow
- [ ] Test checkout flow
- [ ] Test cart operations

#### Day 21: Performance Optimization
- [ ] Optimize re-renders
- [ ] Implement memoization
- [ ] Optimize images
- [ ] Reduce bundle size
- [ ] Test on low-end devices

---

### Phase 6: Polish & Deployment (Days 22-28)

#### Days 22-24: UI/UX Polish
- [ ] Fine-tune animations
- [ ] Improve loading states
- [ ] Polish error messages
- [ ] Accessibility improvements
- [ ] Test on various screen sizes

#### Days 25-26: Android Build
- [ ] Configure build.gradle
- [ ] Set up signing configs
- [ ] Generate app icons
- [ ] Update app name/branding
- [ ] Build release APK
- [ ] Test on physical devices

#### Days 27-28: iOS Build (if applicable)
- [ ] Configure Xcode project
- [ ] Set up provisioning profiles
- [ ] Generate app icons
- [ ] Build release IPA
- [ ] Test on physical devices

---

## Data Models & Type Definitions

### Core Types to Implement

```typescript
// User & Auth
interface User {
  id: string;
  email: string;
  name: string;
  phone?: string;
  role: 'client' | 'provider' | 'admin' | 'support';
  providerId?: string;
}

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  loading: boolean;
}

// Listings
interface Listing {
  id: string;
  title: string;
  description: string;
  price: number;
  rentPrice?: number;
  forSale: boolean;
  forRent: boolean;
  categoryId: string;
  categoryName: string;
  providerId: string;
  providerName: string;
  location: string;
  images: string[];
  stock: number;
  createdAt: string;
}

// Cart
interface CartItem {
  listingId: string;
  listing: Listing;
  quantity: number;
  isRental: boolean;
  rentalDays?: number;
}

interface Cart {
  items: CartItem[];
  totalItems: number;
  totalPrice: number;
}

// Orders
interface Order {
  id: string;
  orderNumber: string;
  status: 'pending' | 'confirmed' | 'completed' | 'cancelled';
  items: CartItem[];
  total: number;
  deliveryAddress?: string;
  createdAt: string;
  invoiceUrl?: string;
}
```

---

## API Integration Plan

### Base API Configuration

```typescript
// src/api/client.ts
import axios from 'axios';
import { getToken } from '../utils/storage';

const API_BASE_URL = 'https://marketplace-production-129b.up.railway.app';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add auth token interceptor
apiClient.interceptors.request.use(async (config) => {
  const token = await getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
```

### API Endpoints to Implement

```typescript
// Auth
POST /api/auth/login
POST /api/auth/register
POST /api/auth/forgot-password
POST /api/auth/reset-password
POST /api/auth/verify-otp
GET  /api/auth/me

// Listings
GET  /api/listings
GET  /api/listings/:id
GET  /api/categories
GET  /api/providers

// Cart
GET  /api/cart
POST /api/cart/items
PUT  /api/cart/items/:id
DELETE /api/cart/items/:id
DELETE /api/cart

// Orders
POST /api/orders
GET  /api/orders
GET  /api/orders/:id
GET  /api/orders/:id/invoice

// Client
POST /api/client/verify-email
POST /api/client/verify-otp
```

---

## State Management Structure

### Redux Store Slices

```
store/
├── slices/
│   ├── authSlice.ts      # Authentication state
│   ├── cartSlice.ts      # Shopping cart state
│   ├── listingsSlice.ts  # Listings & filters
│   ├── ordersSlice.ts    # Orders state
│   ├── themeSlice.ts     # Theme preferences
│   └── uiSlice.ts        # UI state (modals, loading)
├── index.ts              # Store configuration
└── hooks.ts              # Typed hooks
```

---

## Navigation Structure

```
RootNavigator
├── AuthStack (if not authenticated)
│   ├── LoginScreen
│   ├── RegisterScreen
│   ├── ForgotPasswordScreen
│   └── ResetPasswordScreen
│
└── MainTabs (if authenticated or guest)
    ├── ShopTab
    │   ├── MarketplaceScreen
    │   └── ListingDetailModal
    │
    ├── CartTab (conditional)
    │   └── CartScreen
    │       └── CheckoutStack
    │           ├── ReviewScreen
    │           ├── DeliveryScreen
    │           ├── PaymentScreen
    │           └── ConfirmationScreen
    │
    ├── OrdersTab
    │   ├── OrderLookupScreen
    │   └── OrderDetailScreen
    │
    └── SettingsTab
        ├── SettingsScreen
        ├── HelpScreen
        └── ProfileScreen
```

---

## Migration Checklist

### Customer Features
- [ ] Browse marketplace
- [ ] Search listings
- [ ] Filter by category
- [ ] Filter by provider
- [ ] Filter by price
- [ ] Filter by location
- [ ] View listing details
- [ ] Add to cart
- [ ] View cart
- [ ] Remove from cart
- [ ] Adjust quantities
- [ ] Checkout flow
- [ ] Payment integration
- [ ] Order confirmation
- [ ] View orders
- [ ] View invoices
- [ ] Download invoices
- [ ] Login
- [ ] Register
- [ ] Forgot password
- [ ] Reset password
- [ ] OTP verification
- [ ] Logout
- [ ] Theme toggle
- [ ] Help center
- [ ] Settings

### Technical Requirements
- [ ] API client setup
- [ ] Authentication flow
- [ ] Token management
- [ ] State persistence
- [ ] Error handling
- [ ] Loading states
- [ ] Empty states
- [ ] Offline support
- [ ] Push notifications
- [ ] Deep linking
- [ ] Image caching
- [ ] Form validation
- [ ] Input sanitization
- [ ] Accessibility
- [ ] Performance optimization

### Quality Assurance
- [ ] Unit tests
- [ ] Integration tests
- [ ] E2E tests
- [ ] Manual testing
- [ ] Device compatibility
- [ ] Performance testing
- [ ] Security audit
- [ ] Code review

---

## Risk Assessment & Mitigation

### High Risk
1. **Payment Integration Complexity**
   - Mitigation: Test thoroughly, maintain WebView fallback initially

2. **API Compatibility Issues**
   - Mitigation: Review all endpoints, test with actual API

3. **Performance on Low-End Devices**
   - Mitigation: Optimize early, test on target devices

### Medium Risk
1. **State Management Complexity**
   - Mitigation: Keep store structure simple, document well

2. **Image Loading Performance**
   - Mitigation: Implement caching, lazy loading, image optimization

3. **Navigation Complexity**
   - Mitigation: Plan navigation structure carefully

### Low Risk
1. **UI Component Consistency**
   - Mitigation: Build component library early

2. **TypeScript Type Safety**
   - Mitigation: Define types early, use strict mode

---

## Success Criteria

### Functional
- [ ] 100% feature parity with web app
- [ ] All user flows working end-to-end
- [ ] No critical bugs

### Performance
- [ ] App launch time < 3 seconds
- [ ] Screen transition time < 300ms
- [ ] API response handling < 500ms
- [ ] Smooth 60fps animations

### Quality
- [ ] 80%+ code coverage
- [ ] 0 critical accessibility issues
- [ ] < 5 open bugs at launch
- [ ] Positive user feedback

---

## Next Steps

1. **Review & Approve Plan** - Stakeholder sign-off
2. **Begin Phase 1** - Project setup and foundation
3. **Weekly Check-ins** - Progress review and adjustments
4. **Milestone Demos** - After each phase completion
5. **Beta Testing** - Week 3 with select users
6. **Production Release** - Week 4

---

## Notes

- This is an aggressive timeline; adjust as needed
- Consider starting with customer features only
- Admin/Provider features can be kept in WebView temporarily
- Prioritize core shopping flow (browse → cart → checkout)
- Regular testing throughout development is critical

---

**Plan Status**: DRAFT
**Last Updated**: 2026-07-22
**Next Review**: After Phase 1 completion
