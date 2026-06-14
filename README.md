# marketplace

Agricultural Marketplace Platform - Connecting agricultural providers with clients for product sales and equipment rentals.

## Features

- **Real-time Inventory Management** - Prevents overselling with pessimistic locking
- **Order Processing** - Automated validation and stock management
- **Rental System** - Schedule-based equipment rental
- **Provider Dashboard** - Complete order and inventory control
- **Payment Integration** - Secure payment processing

## Branches

- **main** - Main development branch
- **SIT** - System Integration Testing environment
- **UAT** - User Acceptance Testing environment  
- **PROD** - Production environment

## Documentation

See [USER_MANUAL.md](USER_MANUAL.md) for complete documentation.  
Deployment: [DEPLOYMENT.md](DEPLOYMENT.md) · Environment variables: [ENV.md](ENV.md).

## Technology Stack

- **Frontend**: Vue.js 3 + Vite
- **Backend**: Spring Boot (Java 17)
- **Database**: PostgreSQL (UAT/PROD) · MySQL (local dev) · H2 (SIT tests)
- **Build Tools**: Maven, npm

## Getting Started

### Backend
```bash
cd backend
mvn clean install
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Frontend
```bash
cd frontend
npm install
npm run dev
```

## Project Structure

```
MarketPlace/
├── backend/          # Spring Boot API
├── frontend/         # Vue.js application
├── USER_MANUAL.md    # Complete user documentation
└── README.md         # This file
```
