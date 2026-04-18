# Agricultural Marketplace - User Manual

## Table of Contents
1. [Introduction](#introduction)
2. [System Overview](#system-overview)
3. [User Roles](#user-roles)
4. [Client Guide](#client-guide)
5. [Provider Guide](#provider-guide)
6. [Inventory & Stock Management](#inventory--stock-management)
7. [Order Management](#order-management)
8. [API Reference](#api-reference)
9. [Error Handling](#error-handling)
10. [Troubleshooting](#troubleshooting)

---

## Introduction

Welcome to the Agricultural Marketplace platform! This system connects agricultural providers with clients, enabling the sale and rental of agricultural products and equipment.

### Key Features
- **Real-time Inventory Management** - Prevents overselling with pessimistic locking
- **Order Processing** - Automated validation and stock management
- **Rental System** - Schedule-based equipment rental
- **Provider Dashboard** - Complete order and inventory control
- **Payment Integration** - Secure payment processing

---

## System Overview

### Architecture
- **Frontend**: Vue.js single-page application
- **Backend**: Spring Boot REST API
- **Database**: MySQL with JPA/Hibernate

### Core Entities
- **Listings**: Products/equipment for sale or rent
- **Purchase Orders**: Sale transactions
- **Rental Bookings**: Equipment rental reservations
- **Payments**: Payment records for transactions

---

## User Roles

### 1. **Clients (Customers)**
- Browse listings
- Add items to cart
- Place orders
- Rent equipment

### 2. **Providers (Sellers)**
- Manage listings (CRUD)
- Manage orders (View, Update, Cancel, Delete)
- Track inventory
- View dashboard statistics
- Manage team members
- Process payments

### 3. **Administrators**
- System-wide monitoring
- Provider approval
- Platform management

---

## Client Guide

### Browsing Products

1. **View Available Listings**
   - Products are displayed with:
     - Title and description
     - Price (sale) or rental rates (hourly/daily/weekly)
     - Available stock quantity
     - Images
     - Provider information

2. **Filter by Category**
   - Use category filters to find specific product types
   - View only active listings from approved providers

### Placing Orders

#### Step 1: Add to Cart
- Select desired quantity
- For rentals: choose start and end dates
- Items are added to your session cart

#### Step 2: Review Cart
- Verify quantities and dates
- Check total amount
- All items must be from the same provider

#### Step 3: Checkout
```
POST /api/public/checkout/guest
{
  "guestName": "John Doe",
  "guestEmail": "john@example.com",
  "guestPhone": "+1234567890",
  "deliveryOrPickup": "Delivery to 123 Farm Road",
  "paymentMethod": "CARD"
}
```

### Order Validation

The system automatically validates:

✅ **Stock Availability**
- Checks if requested quantity is available
- Prevents ordering out-of-stock items

✅ **Rental Conflicts**
- Ensures equipment isn't double-booked
- Validates date availability

### Error Messages You May See

| Error Code | Message | Action Required |
|------------|---------|-----------------|
| `OUT_OF_STOCK` | Item is sold out | Choose different item or wait for restock |
| `INSUFFICIENT_STOCK` | Not enough stock available | Reduce quantity to available amount |
| `RENTAL_CONFLICT` | Equipment unavailable for selected dates | Choose different dates |
| `LISTING_INACTIVE` | Product no longer available | Item has been removed by provider |
| `PROVIDER_INACTIVE` | Provider cannot accept orders | Contact provider or choose different seller |

**Example Error Response:**
```json
{
  "code": "INSUFFICIENT_STOCK",
  "message": "Insufficient stock for 'Organic Fertilizer 50kg'. Available: 5, Requested: 10"
}
```

**What to do:** Update your order to 5 units or less.

---

## Provider Guide

### Getting Started

1. **Register as Provider**
   - Submit provider application
   - Await admin approval
   - Activate subscription

2. **Access Provider Portal**
   - Login with provider credentials
   - Navigate to `/provider/me/`

### Managing Listings

#### Create New Listing

**Endpoint:** `POST /api/provider/me/listings`

```json
{
  "categoryId": 1,
  "listingType": "SALE",
  "title": "Organic Fertilizer 50kg",
  "description": "Premium organic fertilizer",
  "unitPrice": 45.00,
  "stockQuantity": 100,
  "active": true
}
```

**With Images:**
```
POST /api/provider/me/listing-with-images
Content-Type: multipart/form-data

listing: { ... JSON data ... }
files: [image1.jpg, image2.jpg]
```

#### Update Listing

**Endpoint:** `PUT /api/provider/me/listings/{id}`

```json
{
  "stockQuantity": 75,
  "unitPrice": 47.50,
  "active": true
}
```

#### Delete Listing

**Endpoint:** `DELETE /api/provider/me/listings/{id}`

**Note:** Cannot delete listings with pending orders.

### Listing Types

#### 1. **SALE** - Products for purchase
Required fields:
- `unitPrice`: Price per unit
- `stockQuantity`: Available inventory

#### 2. **RENT** - Equipment for rental
Required fields:
- `rentPriceHourly`: Hourly rate (optional)
- `rentPriceDaily`: Daily rate (optional)
- `rentPriceWeekly`: Weekly rate (optional)

---

## Inventory & Stock Management

### How Stock Tracking Works

#### Automatic Inventory Reduction
When a client completes an order:
1. System locks the listing (prevents concurrent modifications)
2. Validates requested quantity ≤ available stock
3. Deducts quantity from `stockQuantity`
4. Completes transaction

**Example:**
```
Initial Stock: 100 units
Client Orders: 15 units
New Stock: 85 units (automatic)
```

#### Stock Validation Rules

✅ **Before Order Creation:**
- Listing must be active
- Provider must be active
- Stock must be tracked (`stockQuantity` not null)
- Requested quantity ≤ available stock
- Stock > 0

❌ **Order Rejected If:**
- `stockQuantity` is null (not tracked)
- Requested > Available
- Stock is 0 (sold out)

### Preventing Overselling

The system uses **pessimistic locking** to prevent race conditions:

```java
// Locks listings in sorted order (prevents deadlock)
// Multiple simultaneous orders cannot exceed stock
@Lock(PESSIMISTIC_WRITE)
Optional<Listing> findByIdWithLock(Long id);
```

**Scenario:**
- Stock: 10 units
- Client A orders: 8 units (at 10:00:00)
- Client B orders: 5 units (at 10:00:01)

**Result:**
- Client A: ✅ Success (2 units remaining)
- Client B: ❌ Error "Insufficient stock. Available: 2, Requested: 5"

### Updating Stock Manually

```
PUT /api/provider/me/listings/{id}
{
  "stockQuantity": 150
}
```

**Best Practices:**
- Update stock when receiving new inventory
- Set to 0 to mark as out of stock
- Set `active: false` to temporarily hide listing

---

## Order Management

### Viewing Orders

#### List All Orders
```
GET /api/provider/me/orders/purchases?page=0&size=20
```

**Response:**
```json
{
  "content": [
    {
      "id": 1234,
      "guestEmail": "client@example.com",
      "status": "PAID",
      "total": 225.00,
      "createdAt": "2026-04-18T10:30:00Z"
    }
  ],
  "totalPages": 5,
  "totalElements": 100
}
```

#### Get Order Details
```
GET /api/provider/me/orders/purchases/{orderId}
```

**Response:**
```json
{
  "id": 1234,
  "provider": { "id": 1, "businessName": "Green Farms" },
  "guestName": "John Doe",
  "guestEmail": "john@example.com",
  "guestPhone": "+1234567890",
  "deliveryOrPickup": "Delivery to 123 Farm Road",
  "status": "PAID",
  "totalAmount": 225.00,
  "createdAt": "2026-04-18T10:30:00Z",
  "lines": [
    {
      "listing": { "id": 5, "title": "Organic Fertilizer 50kg" },
      "quantity": 5,
      "unitPrice": 45.00
    }
  ]
}
```

### Order Status Workflow

```
PENDING_PAYMENT → PAID → FULFILLED
                     ↓
                 CANCELLED
```

#### Status Definitions

| Status | Description | Client Action | Provider Action |
|--------|-------------|---------------|-----------------|
| `PENDING_PAYMENT` | Awaiting payment | Complete payment | Monitor |
| `PAID` | Payment received | Wait for fulfillment | Prepare order |
| `FULFILLED` | Order completed | Receive goods | Mark complete |
| `CANCELLED` | Order cancelled | Refund processed | Restock inventory |

### Updating Order Status

```
PUT /api/provider/me/orders/purchases/{orderId}/status?status=FULFILLED
```

**Valid Transitions:**
- `PENDING_PAYMENT` → `PAID` or `CANCELLED`
- `PAID` → `FULFILLED` or `CANCELLED`
- `FULFILLED` → (no changes allowed)
- `CANCELLED` → (no changes allowed)

**Example Error:**
```json
{
  "code": "INVALID_STATUS_TRANSITION",
  "message": "Cannot transition from FULFILLED to PAID"
}
```

### Cancelling Orders

```
POST /api/provider/me/orders/purchases/{orderId}/cancel
```

**Rules:**
- ✅ Can cancel `PENDING_PAYMENT` or `PAID` orders
- ❌ Cannot cancel `FULFILLED` orders
- ❌ Cannot cancel already `CANCELLED` orders

**Response:**
```json
{
  "message": "Order cancelled successfully"
}
```

**Note:** Cancelled orders do NOT automatically restock inventory. Update listing stock manually if needed.

### Deleting Orders

```
DELETE /api/provider/me/orders/purchases/{orderId}
```

**Rules:**
- ✅ Can delete `CANCELLED` orders
- ✅ Can delete `PENDING_PAYMENT` orders
- ❌ Cannot delete `PAID` or `FULFILLED` orders

**Use Cases:**
- Clean up old cancelled orders
- Remove abandoned carts (pending payment after timeout)

---

## API Reference

### Provider Endpoints

#### Listings Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/provider/me/listings` | List all provider listings |
| POST | `/api/provider/me/listings` | Create new listing (JSON) |
| POST | `/api/provider/me/listing-with-images` | Create listing with images |
| PUT | `/api/provider/me/listings/{id}` | Update listing |
| PUT | `/api/provider/me/listings/{id}/with-images` | Update with images |
| DELETE | `/api/provider/me/listings/{id}` | Delete listing |

#### Order Management (New)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/provider/me/orders/purchases` | List all orders (paginated) |
| GET | `/api/provider/me/orders/purchases/{id}` | Get order details |
| PUT | `/api/provider/me/orders/purchases/{id}/status` | Update order status |
| POST | `/api/provider/me/orders/purchases/{id}/cancel` | Cancel order |
| DELETE | `/api/provider/me/orders/purchases/{id}` | Delete order |

#### Rental Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/provider/me/orders/rentals` | List rental bookings |

#### Dashboard

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/provider/me/dashboard/stats` | Get statistics (sales, rentals) |

### Client Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/public/listings` | Browse all listings |
| POST | `/api/public/cart/add` | Add item to cart |
| POST | `/api/public/checkout/guest` | Complete purchase |

---

## Error Handling

### Common Error Codes

#### Client Errors (400)

| Code | Cause | Solution |
|------|-------|----------|
| `INSUFFICIENT_STOCK` | Requested > Available | Reduce quantity |
| `OUT_OF_STOCK` | Stock = 0 | Wait for restock |
| `STOCK_NOT_TRACKED` | Listing has no stock tracking | Contact provider |
| `LISTING_INACTIVE` | Product disabled | Choose different product |
| `RENTAL_CONFLICT` | Date overlap | Select different dates |
| `INVALID_STATUS_TRANSITION` | Invalid status change | Follow status workflow |
| `CANNOT_CANCEL` | Order already fulfilled | Cannot modify |
| `CANNOT_DELETE` | Order status prevents deletion | Only delete cancelled/pending |

#### Authorization Errors (403)

| Code | Cause | Solution |
|------|-------|----------|
| `ACCESS_DENIED` | Not your order | Can only manage own orders |

#### Not Found Errors (404)

| Code | Cause | Solution |
|------|-------|----------|
| `ORDER_NOT_FOUND` | Invalid order ID | Check order ID |
| `LISTING_MISSING` | Listing deleted | Product no longer exists |

### Error Response Format

```json
{
  "code": "ERROR_CODE",
  "message": "Human-readable description with context"
}
```

---

## Troubleshooting

### For Clients

#### "Cannot add to cart"
**Possible causes:**
- Item is from different provider (cart already has items from another provider)
- Listing is inactive
- Stock is 0

**Solution:** Clear cart or complete current order first.

#### "Order failed at checkout"
**Check:**
1. Is the item still in stock?
2. Is the provider active?
3. For rentals: Are your dates still available?

**Solution:** Review cart and update quantities/dates.

### For Providers

#### "Cannot update order status"
**Cause:** Invalid status transition

**Solution:** Follow the status workflow:
- Only move forward: `PENDING_PAYMENT` → `PAID` → `FULFILLED`
- Can cancel from `PENDING_PAYMENT` or `PAID`
- Cannot modify `FULFILLED` or `CANCELLED` orders

#### "Stock not reducing automatically"
**Check:**
1. Is `stockQuantity` set (not null)?
2. Is the listing active?
3. Did the order complete successfully?

**Solution:** Stock only reduces on successful `PAID` orders.

#### "Multiple clients ordered more than available stock"
**This should NOT happen.** The system prevents this with pessimistic locking.

**If it occurs:**
1. Report as critical bug
2. Check database transaction isolation level
3. Verify no direct database modifications

### System Performance

#### "Orders are slow during high traffic"
**Cause:** Pessimistic locking serializes concurrent orders on same listing.

**This is expected behavior** - it prevents overselling.

**Optimization tips:**
- Ensure database indexes on `listing_id`
- Monitor lock wait timeouts
- Consider horizontal scaling for database

---

## Best Practices

### For Providers

1. **Keep Stock Updated**
   - Update `stockQuantity` when receiving inventory
   - Set to 0 when out of stock (don't delete listing)

2. **Process Orders Promptly**
   - Move `PAID` orders to `FULFILLED` quickly
   - Cancel orders that cannot be fulfilled

3. **Use Meaningful Titles**
   - Clear, descriptive listing names
   - Include key details (size, weight, quantity)

4. **Set Accurate Prices**
   - Price updates affect new orders only
   - Existing orders keep original price

5. **Monitor Dashboard**
   - Track sales trends
   - Identify popular products
   - Plan inventory accordingly

### For Clients

1. **Check Stock Before Ordering**
   - Verify available quantity
   - Don't wait too long in checkout (stock may change)

2. **Review Cart Carefully**
   - Confirm quantities and dates
   - Check delivery/pickup details

3. **Complete Checkout Quickly**
   - Items in cart are NOT reserved
   - Stock is only locked during checkout

---

## Technical Notes

### Concurrency Control

The system implements **pessimistic locking** to prevent race conditions:

```java
// Locks are acquired in sorted order by listing ID
// This prevents deadlocks when multiple orders involve same listings
List<Long> listingIds = lines.stream()
    .map(line -> line.getListing().getId())
    .sorted()  // Critical: consistent ordering
    .distinct()
    .toList();

for (Long listingId : listingIds) {
    Listing listing = listingRepository.findByIdWithLock(listingId);
    // Listing is locked until transaction commits
}
```

**Key Points:**
- Locks are held until transaction completes
- Sorted locking prevents circular wait (deadlock prevention)
- Other orders wait for lock release (serialization)

### Transaction Boundaries

All checkout operations are in a single transaction:
```java
@Transactional
public Map<String, Object> guestCheckout(...) {
    // 1. Lock listings
    // 2. Validate stock
    // 3. Create order
    // 4. Reduce stock
    // 5. Process payment
    // All or nothing - ACID guaranteed
}
```

**Benefits:**
- Atomicity: All steps succeed or all fail
- Consistency: Stock always accurate
- Isolation: No partial visibility
- Durability: Changes persisted immediately

---

## Support

### Getting Help

- **Provider Issues:** Contact platform administrators
- **Technical Bugs:** Report through issue tracking system
- **Feature Requests:** Submit via provider portal

### System Status

Monitor at: `/api/public/health`

---

**Version:** 1.0  
**Last Updated:** April 18, 2026  
**Platform:** Agricultural Marketplace
