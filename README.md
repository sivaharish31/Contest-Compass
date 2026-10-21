# ContestRadar

Centralized competitive programming contest discovery platform.

## Prerequisites

- **Java 25** (JDK)
- **Maven 3.9+**
- **MySQL 8+** (tested with MySQL 26.7 Community Server)
- **Node.js 20+** and npm

## Database Setup

1. Connect to MySQL as a user with CREATE and GRANT privileges:

   ```
   mysql -u root -p
   ```

2. Run the setup script (edit `create-database.sql` first to set a real password):

   ```
   source backend/src/main/resources/db/create-database.sql
   ```

## Environment Variables

Copy `.env.example` to `.env` and fill in your values:

```
DATABASE_URL=jdbc:mysql://localhost:3306/contestradar?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
DATABASE_USERNAME=contestradar
DATABASE_PASSWORD=<your-password>
SERVER_PORT=8080
```

Never commit `.env`.

## Running the Backend

```bash
cd backend
DATABASE_PASSWORD=<your-password> mvn spring-boot:run
```

Flyway runs automatically and applies pending migrations on startup.

## Running Tests

```bash
# Backend (uses H2 in-memory — no MySQL needed)
cd backend
mvn clean test

# Frontend
cd frontend
npm run build   # type-check + production build
npm run lint    # oxlint
```

## Running the Frontend

```bash
cd frontend
npm install
npm run dev     # dev server at http://localhost:5173
```

The Vite dev server proxies `/api` requests to `http://localhost:8080`.

## Authentication

Session-based authentication using Spring Security with BCrypt password hashing. The frontend uses `credentials: 'include'` to send session cookies with all API requests.

- `POST /api/auth/register` — create account
- `POST /api/auth/login` — sign in (sets session cookie)
- `POST /api/auth/logout` — sign out
- `GET /api/auth/me` — current user

Public endpoints: `GET /api/contests/**`, `GET /api/contests/sync-status`

Protected endpoints (require login): saved contests, preferences, profiles, reminders, sync trigger.

## Features

- **Dashboard** — live/upcoming contests with countdown timers, platform filters, search
- **All Contests** — paginated, filterable contest browser with status filters
- **Saved Contests** — bookmark contests for quick access (per user)
- **Recommended** — contest recommendations based on preferred platforms
- **Connected Profiles** — link Codeforces/LeetCode/CodeChef/AtCoder accounts
- **Settings** — notification preferences, platform preferences
- **Authentication** — registration, login, logout with secure sessions
- **Contest Sync** — automatic sync from Codeforces API, with mock data for development
