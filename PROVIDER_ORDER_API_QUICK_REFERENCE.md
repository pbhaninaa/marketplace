# Provider Order API - Quick Reference

## Authentication
```
Authorization: Bearer {jwt_token}
```

## Endpoints Summary

| Method | Endpoint | Description | Payment Status Constraint |
|--------|----------|-------------|---------------------------|
| GET | `/api/provider/orders` | List all orders (paginated) | - |
| GET | `/api/provider/orders/{id}` | Get order with items | - |
| PUT | `/api/provider/orders/{id}/status` | Update order status | - |
| DELETE | `/api/provider/orders/{id}/cancel` | Cancel order | PENDING only |
| DELETE | `/api/provider/orders/{id}` | Delete order | PENDING only |
| DELETE | `/api/provider/orders/bulk` | Bulk delete orders | PENDING only |

---

## Payment Status Rules

### PENDING
- ✅ Can view order
- ✅ Can cancel order
- ✅ Can delete order
- ✅ Can update status to PAID
- Inventory: RESERVED

### PAID
- ✅ Can view order
- ❌ Cannot cancel order
- ❌ Cannot delete order
- ✅ Can update status to FULFILLED
- Inventory: DEDUCTED

---

## Order Status Flow

```
PENDING_PAYMENT → PAID → FULFILLED
       ↓            ↓
   CANCELLED    CANCELLED
```

---

## Example Requests

### List Orders
```bash
curl -X GET "http://localhost:8080/api/provider/orders?page=0&size=20" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Get Order Details
```bash
curl -X GET "http://localhost:8080/api/provider/orders/123" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Confirm Payment (PENDING → PAID)
```bash
curl -X PUT "http://localhost:8080/api/provider/orders/123/status" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "PAID"}'
```

### Mark as Fulfilled (PAID → FULFILLED)
```bash
curl -X PUT "http://localhost:8080/api/provider/orders/123/status" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "FULFILLED"}'
```

### Cancel Order (PENDING only)
```bash
curl -X DELETE "http://localhost:8080/api/provider/orders/123/cancel" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Delete Order (PENDING only)
```bash
curl -X DELETE "http://localhost:8080/api/provider/orders/123" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Bulk Delete
```bash
# Delete specific orders
curl -X DELETE "http://localhost:8080/api/provider/orders/bulk" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"orderIds": [123, 456, 789]}'

# Delete all PENDING orders
curl -X DELETE "http://localhost:8080/api/provider/orders/bulk" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

## Response Examples

### Order with Items
```json
{
  "id": 123,
  "providerId": 1,
  "guestName": "John Doe",
  "guestEmail": "john@example.com",
  "guestPhone": "0821234567",
  "deliveryOrPickup": "Pickup",
  "status": "PAID",
  "paymentStatus": "PAID",
  "totalAmount": 150.00,
  "deliveryFee": 0.00,
  "verificationCode": "ABC12345",
  "createdAt": "2025-01-15T10:30:00Z",
  "inventoryFinalized": true,
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

### Error Response
```json
{
  "errorCode": "CANNOT_CANCEL_PAID",
  "message": "Cannot cancel orders with paid status. Payment must be PENDING."
}
```

---

## Error Codes

| Code | Status | Meaning |
|------|--------|---------|
| `CANNOT_CANCEL_PAID` | 400 | Cannot cancel PAID orders |
| `CANNOT_DELETE_PAID` | 400 | Cannot delete PAID orders |
| `ORDER_NOT_FOUND` | 404 | Order doesn't exist |
| `ACCESS_DENIED` | 403 | Not your order |
| `INVALID_STATUS_TRANSITION` | 400 | Invalid status change |

---

## Important Notes

1. **Payment Status is Immutable**: Once PAID, you cannot cancel or delete
2. **Order Items Included**: All responses include the list of items
3. **Inventory Auto-Managed**: System handles stock reservation/deduction
4. **Authorization**: Can only access your own provider's orders
5. **Bulk Delete Safety**: Only deletes PENDING payment orders

---

## Frontend Checklist

- [ ] Show order items in order details view
- [ ] Disable cancel/delete buttons for PAID orders
- [ ] Display payment status badge (PENDING/PAID)
- [ ] Show inventory finalized indicator
- [ ] Handle error codes gracefully
- [ ] Implement pagination for order list
- [ ] Add confirmation dialog for delete operations
- [ ] Display total amount with delivery fee breakdown
