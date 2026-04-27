# Provider Order Management Integration Guide

## Overview

This document describes the provider order management system integration, including CRUD endpoints, payment status constraints, and order lifecycle management.

## Payment Status Model

The system uses a simplified 2-state payment model:

- **PENDING**: Payment not confirmed. Orders can be cancelled or deleted.
- **PAID**: Payment confirmed. Orders CANNOT be cancelled or deleted.

## API Endpoints

### Base URL
```
/api/provider/orders
```

### Authentication
All endpoints require JWT authentication with provider role.
Include token in header: `Authorization: Bearer {token}`

---

## Endpoints

### 1. List Provider Orders (Paginated)
```http
GET /api/provider/orders?page=0&size=20
```

**Response:**
```json
{
  "content": [
    {
      "id": 123,
      "providerId": 1,
      "guestName": "John Doe",
      "guestEmail": "john@example.com",
      "guestPhone": "0821234567",
      "deliveryOrPickup": "Pickup",
      "status": "PENDING_PAYMENT",
      "paymentStatus": "PENDING",
      "totalAmount": 150.00,
      "deliveryFee": 0.00,
      "verificationCode": "ABC12345",
      "createdAt": "2025-01-15T10:30:00Z",
      "inventoryFinalized": false,
      "items": [
        {
          "listingId": 456,
          "listingTitle": "Fresh Tomatoes",
          "unitPrice": 15.00,
          "quantity": 10,
          "lineTotal": 150.00
        }
      ]
    }
  ],
  "pageable": {...},
  "totalElements": 1,
  "totalPages": 1
}
```

---

### 2. Get Order by ID
```http
GET /api/provider/orders/{orderId}
```

**Response:**
```json
{
  "id": 123,
  "providerId": 1,
  "guestName": "John Doe",
  "guestEmail": "john@example.com",
  "guestPhone": "0821234567",
  "deliveryOrPickup": "Pickup",
  "status": "PENDING_PAYMENT",
  "paymentStatus": "PENDING",
  "totalAmount": 150.00,
  "deliveryDistanceKm": null,
  "deliveryFee": 0.00,
  "verificationCode": "ABC12345",
  "verifiedAt": null,
  "createdAt": "2025-01-15T10:30:00Z",
  "inventoryFinalized": false,
  "items": [
    {
      "listingId": 456,
      "listingTitle": "Fresh Tomatoes",
      "unitPrice": 15.00,
      "quantity": 10,
      "lineTotal": 150.00
    }
  ]
}
```

---

### 3. Update Order Status
```http
PUT /api/provider/orders/{orderId}/status
Content-Type: application/json

{
  "status": "PAID"
}
```

**Valid Status Transitions:**
- PENDING_PAYMENT → PAID
- PENDING_PAYMENT → CANCELLED
- PAID → FULFILLED
- PAID → CANCELLED

**Response:**
```json
{
  "id": 123,
  "status": "PAID",
  "paymentStatus": "PAID",
  "inventoryFinalized": true,
  ...
}
```

---

### 4. Cancel Order
```http
DELETE /api/provider/orders/{orderId}/cancel
```

**Constraints:**
- ✅ Allowed only if `paymentStatus == PENDING`
- ❌ Returns `400 CANNOT_CANCEL_PAID` if payment is PAID

**Response:**
```json
{
  "message": "Order cancelled successfully",
  "orderId": "123"
}
```

---

### 5. Delete Order
```http
DELETE /api/provider/orders/{orderId}
```

**Constraints:**
- ✅ Allowed only if `paymentStatus == PENDING`
- ✅ Allowed only if `status == CANCELLED` or `PENDING_PAYMENT`
- ❌ Returns `400 CANNOT_DELETE_PAID` if payment is PAID

**Response:**
```json
{
  "message": "Order deleted successfully",
  "orderId": "123"
}
```

---

### 6. Bulk Delete Orders
```http
DELETE /api/provider/orders/bulk
Content-Type: application/json

{
  "orderIds": [123, 456, 789]
}
```

Or delete all PENDING orders:
```http
DELETE /api/provider/orders/bulk
```
(Empty body deletes all PENDING payment orders)

**Response:**
```json
{
  "message": "Orders deleted successfully",
  "deletedCount": 3
}
```

---

## Order Lifecycle

### 1. Order Created (Checkout)
```
Status: PENDING_PAYMENT
PaymentStatus: PENDING
Inventory: RESERVED (stockReserved +2)
```

### 2. Provider Confirms Payment
```http
PUT /api/provider/orders/123/status
{ "status": "PAID" }
```

```
Status: PAID
PaymentStatus: PAID
Inventory: DEDUCTED (stockReserved -2, stockQuantity -2)
inventoryFinalized: true
```

### 3. Order Fulfilled
```http
PUT /api/provider/orders/123/status
{ "status": "FULFILLED" }
```

```
Status: FULFILLED
PaymentStatus: PAID
Inventory: No change (already deducted)
```

### 4. Order Cancelled (PENDING only)
```http
DELETE /api/provider/orders/123/cancel
```

```
Status: CANCELLED
PaymentStatus: PENDING
Inventory: RELEASED (stockReserved -2)
```

---

## Error Codes

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `CANNOT_CANCEL_PAID` | 400 | Cannot cancel order with PAID payment status |
| `CANNOT_DELETE_PAID` | 400 | Cannot delete order with PAID payment status |
| `ORDER_NOT_FOUND` | 404 | Order not found |
| `ACCESS_DENIED` | 403 | Provider doesn't own this order |
| `INVALID_STATUS_TRANSITION` | 400 | Invalid order status transition |

---

## Inventory Management

### Payment Status → Inventory State

| Payment Status | Inventory State | Can Cancel? | Can Delete? |
|----------------|-----------------|-------------|-------------|
| PENDING | Reserved (stockReserved) | ✅ Yes | ✅ Yes |
| PAID | Deducted (stockQuantity) | ❌ No | ❌ No |

### Inventory Operations

**On Checkout:**
- Reserves items: `stockReserved += quantity`

**On Payment Confirmation (PENDING → PAID):**
- Finalizes deduction: `stockReserved -= quantity`, `stockQuantity -= quantity`

**On Cancellation (PENDING only):**
- Releases reservation: `stockReserved -= quantity`

---

## Frontend Integration Example

```javascript
// Fetch provider orders
async function getProviderOrders(page = 0, size = 20) {
  const response = await fetch(
    `/api/provider/orders?page=${page}&size=${size}`,
    {
      headers: {
        'Authorization': `Bearer ${providerToken}`
      }
    }
  );
  return await response.json();
}

// Confirm payment
async function confirmPayment(orderId) {
  const response = await fetch(
    `/api/provider/orders/${orderId}/status`,
    {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${providerToken}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ status: 'PAID' })
    }
  );
  return await response.json();
}

// Cancel order (only if PENDING)
async function cancelOrder(orderId) {
  const response = await fetch(
    `/api/provider/orders/${orderId}/cancel`,
    {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${providerToken}`
      }
    }
  );

  if (!response.ok) {
    const error = await response.json();
    if (error.errorCode === 'CANNOT_CANCEL_PAID') {
      alert('Cannot cancel paid orders');
    }
  }

  return await response.json();
}

// Delete order (only if PENDING)
async function deleteOrder(orderId) {
  const response = await fetch(
    `/api/provider/orders/${orderId}`,
    {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${providerToken}`
      }
    }
  );

  if (!response.ok) {
    const error = await response.json();
    if (error.errorCode === 'CANNOT_DELETE_PAID') {
      alert('Cannot delete paid orders');
    }
  }

  return await response.json();
}
```

---

## Testing

Run integration tests:
```bash
cd backend
mvn test -Dtest=ProviderOrderCrudIntegrationTest
```

Test coverage includes:
- ✅ List orders with items
- ✅ Get order by ID
- ✅ Update order status
- ✅ Cancel PENDING orders
- ✅ Prevent cancelling PAID orders
- ✅ Delete PENDING orders
- ✅ Prevent deleting PAID orders
- ✅ Payment status tracking

---

## Security

- All endpoints require authentication
- Providers can only access their own orders
- Authorization check: `order.getProvider().getId() == currentProviderId`
- Role required: `PROVIDER_OWNER` or `PROVIDER_STAFF`

---

## Database Schema

### Orders Table
```sql
CREATE TABLE orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_id BIGINT NOT NULL,
    guest_name VARCHAR(255) NOT NULL,
    guest_email VARCHAR(255) NOT NULL,
    guest_phone VARCHAR(20) NOT NULL,
    delivery_or_pickup TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(14,2) NOT NULL,
    delivery_distance_km DECIMAL(10,2),
    delivery_fee DECIMAL(10,2),
    verification_code VARCHAR(9) NOT NULL UNIQUE,
    verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    inventory_finalized BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (provider_id) REFERENCES providers(id)
);
```

### Order Lines (CartLine) Table
```sql
CREATE TABLE cart_lines (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT,
    listing_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (listing_id) REFERENCES listings(id)
);
```

---

## Change Summary

### Modified Files
1. `PaymentStatus.java` - Simplified to PENDING/PAID
2. `OrderManagementService.java` - Added payment status validation
3. `OrderPaymentService.java` - Updated to use new status values
4. `PurchaseInventoryService.java` - Inventory finalization logic
5. `ProviderOrderController.java` - New CRUD controller
6. `OrderResponse.java` - New DTO with items list

### New Features
- ✅ Full CRUD for provider orders
- ✅ Order items included in all responses
- ✅ Payment status constraints enforced
- ✅ Bulk delete operations
- ✅ Comprehensive error handling
- ✅ Integration tests

---

## Support

For issues or questions:
- Check error codes in API responses
- Review integration tests for examples
- Verify authentication token is valid
- Ensure provider has permission to access order
