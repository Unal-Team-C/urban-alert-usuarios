# Functional Specification — UrbanAlert Users Service

**Version:** 1.0.0
**Date:** 2026-10-03
**Author:** UrbanAlert Team C
**Status:** Approved

---

## Table of Contents

1. [Overview](#1-overview)
2. [Actors](#2-actors)
3. [User Stories](#3-user-stories)
4. [Business Rules](#4-business-rules)
5. [API Contract](#5-api-contract)
6. [Error Catalog](#6-error-catalog)

---

## 1. Overview

The **Users Service** is a core microservice of the UrbanAlert platform responsible for managing the complete lifecycle of user accounts. It stores identity data — name, email address, and role — and exposes a RESTful API consumed by other internal services (e.g., the Reports Service reads `userId` to attribute reports to citizens, the API Gateway delegates role-based access control decisions to this service's data).

The service is not responsible for authentication or session management — those concerns belong to the external Identity Provider (IDaaS). The Users Service is the **system of record** for user profile data and role assignments.

### Scope

| In scope | Out of scope |
|---|---|
| CRUD operations on user accounts | Password management |
| Role assignment (USER / ADMIN) | Authentication / token issuance |
| Email uniqueness enforcement | Session management |
| Protection of the last administrator | Permission evaluation at the gateway |
| Seed data (default admin on first boot) | Audit log of user actions (Audit Service) |

---

## 2. Actors

| Actor | Description |
|---|---|
| **Citizen** | Registered user with role `USER`. Can create reports. Has no access to admin-only endpoints. |
| **Administrator** | Registered user with role `ADMIN`. Can manage users, assign roles, and perform privileged operations across the platform. |
| **Internal Service** | Any other microservice (e.g., Reports) that queries user data via REST to validate the existence of a `userId`. |

---

## 3. User Stories

---

### US-001 — Create a User Account

**As an** administrator,
**I want to** register a new user in the system by providing their name, email address, and role,
**So that** the user can be associated to reports and platform activity.

#### Acceptance Criteria

**Scenario 1 — Successful creation with explicit role**
```
Given  a valid payload with name "Ana Torres", email "ana@example.com", and role "ADMIN"
When   the client sends POST /api/v1/users
Then   the service responds with HTTP 201
And    the body contains the generated UUID and the message "User created"
And    the user is persisted with the provided name, email, and role ADMIN
And    the assigned ID follows the UUID version 7 format
```

**Scenario 2 — Successful creation with default role**
```
Given  a valid payload with name "Luis Gómez" and email "luis@example.com" but no role field
When   the client sends POST /api/v1/users
Then   the service responds with HTTP 201
And    the persisted user has role USER (applied as the system default)
```

**Scenario 3 — Duplicate email**
```
Given  a user with email "ana@example.com" already exists in the system
When   the client sends POST /api/v1/users with the same email
Then   the service responds with HTTP 409
And    the body contains code 409 and a message indicating the email is already registered
And    no new user record is created
```

**Scenario 4 — Invalid email format**
```
Given  a payload where the email field is "not-an-email"
When   the client sends POST /api/v1/users
Then   the service responds with HTTP 400
And    the body contains code 400 and a message identifying the invalid email field
```

**Scenario 5 — Missing required fields**
```
Given  a payload missing the "name" field
When   the client sends POST /api/v1/users
Then   the service responds with HTTP 400
And    the error message identifies "name" as the missing field
```

**Scenario 6 — Name exceeds maximum length**
```
Given  a payload where the name is a string of 201 characters
When   the client sends POST /api/v1/users
Then   the service responds with HTTP 400
And    the error message states the name cannot exceed 200 characters
```

**Scenario 7 — Email exceeds maximum length**
```
Given  a payload where the email is a valid-format string exceeding 300 characters
When   the client sends POST /api/v1/users
Then   the service responds with HTTP 400
```

**Scenario 8 — Invalid role value**
```
Given  a payload with role "SUPERUSER" (not a recognized role)
When   the client sends POST /api/v1/users
Then   the service responds with HTTP 400
And    the error message states the role is not valid
```

---

### US-002 — List All Users

**As an** administrator,
**I want to** retrieve the full list of registered users,
**So that** I can audit the user base and manage roles.

#### Acceptance Criteria

**Scenario 1 — Users exist**
```
Given  the system has three registered users
When   the client sends GET /api/v1/users
Then   the service responds with HTTP 200
And    the body is a JSON array with three elements
And    each element contains the fields: id, name, email, role
And    no sensitive fields (e.g., internal hashes) are exposed
```

**Scenario 2 — No users exist (empty system)**
```
Given  the system has no registered users (excluding seed data)
When   the client sends GET /api/v1/users
Then   the service responds with HTTP 200
And    the body is an empty JSON array []
```

**Scenario 3 — Seed admin is always present**
```
Given  the service has just started for the first time
When   the client sends GET /api/v1/users
Then   the response contains at least one user with email "admin@urbanalert.com" and role ADMIN
```

---

### US-003 — Get a User by ID

**As an** internal service or administrator,
**I want to** retrieve the full profile of a specific user by their unique identifier,
**So that** I can display user details or validate user existence before associating them to a report.

#### Acceptance Criteria

**Scenario 1 — User exists**
```
Given  a user with ID "018f4c2a-1234-7abc-8def-000000000001" exists
When   the client sends GET /api/v1/users/018f4c2a-1234-7abc-8def-000000000001
Then   the service responds with HTTP 200
And    the body contains id, name, email, and role matching that user
```

**Scenario 2 — User does not exist**
```
Given  no user exists with ID "00000000-0000-0000-0000-000000000099"
When   the client sends GET /api/v1/users/00000000-0000-0000-0000-000000000099
Then   the service responds with HTTP 404
And    the body contains code 404 and the message "User not found"
```

**Scenario 3 — Malformed UUID in path**
```
Given  the path parameter is "not-a-uuid"
When   the client sends GET /api/v1/users/not-a-uuid
Then   the service responds with HTTP 400
And    the body indicates an invalid path parameter format
```

---

### US-004 — Update a User Profile

**As an** administrator,
**I want to** update a user's name, email address, and role in a single operation,
**So that** I can correct user data or promote/demote users as needed.

#### Acceptance Criteria

**Scenario 1 — Successful full update**
```
Given  a user with ID "X" exists with email "old@example.com"
And    no other user has email "new@example.com"
When   the client sends PUT /api/v1/users/X with name "New Name", email "new@example.com", role "ADMIN"
Then   the service responds with HTTP 200
And    the user record is updated with the new values
```

**Scenario 2 — Email collision with another user**
```
Given  user A has email "a@example.com" and user B has email "b@example.com"
When   a PUT on user A provides email "b@example.com"
Then   the service responds with HTTP 409
And    user A's record is not changed
```

**Scenario 3 — Same email as current (no conflict)**
```
Given  user A has email "a@example.com"
When   a PUT on user A provides the same email "a@example.com" with a new name
Then   the service responds with HTTP 200
And    only the name is updated; no conflict is raised
```

**Scenario 4 — User not found**
```
Given  no user exists with the provided ID
When   the client sends PUT /api/v1/users/{id}
Then   the service responds with HTTP 404
```

**Scenario 5 — Validation failure**
```
Given  a PUT body with an invalid email format
When   the client sends PUT /api/v1/users/{id}
Then   the service responds with HTTP 400
And    the user record is unchanged
```

---

### US-005 — Update a User's Role

**As an** administrator,
**I want to** change only the role of a specific user,
**So that** I can promote a citizen to administrator or revoke administrator privileges without touching other profile fields.

#### Acceptance Criteria

**Scenario 1 — Promote USER to ADMIN**
```
Given  a user with role USER
When   the client sends PATCH /api/v1/users/{id}/role with body { "role": "ADMIN" }
Then   the service responds with HTTP 200
And    the user's role is updated to ADMIN
And    name and email remain unchanged
```

**Scenario 2 — Demote ADMIN to USER (allowed when not the last admin)**
```
Given  two users with role ADMIN exist
When   the client sends PATCH on one of them with role "USER"
Then   the service responds with HTTP 200
And    that user's role changes to USER
```

**Scenario 3 — Invalid role value**
```
Given  a PATCH body with role "MODERATOR"
When   the client sends PATCH /api/v1/users/{id}/role
Then   the service responds with HTTP 400
```

**Scenario 4 — User not found**
```
Given  no user exists with the provided ID
When   the client sends PATCH /api/v1/users/{id}/role
Then   the service responds with HTTP 404
```

---

### US-006 — Delete a User

**As an** administrator,
**I want to** permanently remove a user from the system,
**So that** deactivated or incorrectly created accounts are removed from the platform.

#### Acceptance Criteria

**Scenario 1 — Successful deletion**
```
Given  a user with role USER exists with ID "X"
When   the client sends DELETE /api/v1/users/X
Then   the service responds with HTTP 204 No Content
And    a subsequent GET /api/v1/users/X returns HTTP 404
```

**Scenario 2 — User not found**
```
Given  no user exists with the provided ID
When   the client sends DELETE /api/v1/users/{id}
Then   the service responds with HTTP 404
```

**Scenario 3 — Attempt to delete the last administrator**
```
Given  only one user with role ADMIN exists in the system
When   the client sends DELETE on that user
Then   the service responds with HTTP 409
And    the body contains a message stating the last administrator cannot be deleted
And    the user record is not removed
```

**Scenario 4 — Delete one of several admins (allowed)**
```
Given  three users with role ADMIN exist
When   the client sends DELETE on one of them
Then   the service responds with HTTP 204
And    the remaining two ADMIN users are unaffected
```

---

### US-007 — Default Admin on First Boot (Seed Data)

**As a** platform operator,
**I want** the system to automatically create a default administrator account when the database is empty,
**So that** the platform is immediately operable after deployment without manual intervention.

#### Acceptance Criteria

**Scenario 1 — First-time startup**
```
Given  the users table is empty
When   the service starts up
Then   a user with name "Administrator", email "admin@urbanalert.com", and role ADMIN is present
And    the ID of the seeded user is stable across restarts (deterministic UUID)
```

**Scenario 2 — Restart with existing data**
```
Given  users already exist in the database
When   the service restarts
Then   no duplicate seed user is inserted
And    the existing data is unaffected
```

---

## 4. Business Rules

| ID | Rule | Enforcement point |
|---|---|---|
| BR-001 | Email address must be unique across all users | Service layer before persistence |
| BR-002 | Default role is `USER` when not explicitly provided | Domain model constructor |
| BR-003 | The system must always have at least one `ADMIN` user | Service layer on delete |
| BR-004 | Name must be 1–200 characters, non-blank | Domain model constructor + Bean Validation |
| BR-005 | Email must be 1–300 characters and a valid RFC 5322 format | Domain model constructor + Bean Validation |
| BR-006 | Role must be one of the defined enum values (`USER`, `ADMIN`) | Bean Validation on request DTO |
| BR-007 | IDs are assigned by the system (UUID v7); clients cannot specify them | Domain model constructor |
| BR-008 | A PUT operation that provides the same email as the current record is not a conflict | Service layer, filter by different ID |

---

## 5. API Contract

### Base URL
```
/api/v1/users
```

### `POST /api/v1/users` — Create user

**Request body:**
```json
{
  "name": "Ana Torres",
  "email": "ana@example.com",
  "role": "USER"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `name` | String | Yes | 1–200 chars, non-blank |
| `email` | String | Yes | 1–300 chars, valid email format |
| `role` | String (enum) | No | `USER` or `ADMIN`; defaults to `USER` |

**Response 201:**
```json
{
  "id": "018f4c2a-7e3b-7abc-8def-1a2b3c4d5e6f",
  "message": "User created"
}
```

---

### `GET /api/v1/users` — List users

**Response 200:**
```json
[
  {
    "id": "018f4c2a-7e3b-7abc-8def-1a2b3c4d5e6f",
    "name": "Ana Torres",
    "email": "ana@example.com",
    "role": "USER"
  }
]
```

---

### `GET /api/v1/users/{id}` — Get user by ID

**Path parameter:** `id` — UUID v7

**Response 200:**
```json
{
  "id": "018f4c2a-7e3b-7abc-8def-1a2b3c4d5e6f",
  "name": "Ana Torres",
  "email": "ana@example.com",
  "role": "USER"
}
```

---

### `PUT /api/v1/users/{id}` — Update user

**Request body:** same structure as POST (all fields required).

**Response 200:** empty body.

---

### `PATCH /api/v1/users/{id}/role` — Update role

**Request body:**
```json
{
  "role": "ADMIN"
}
```

**Response 200:** empty body.

---

### `DELETE /api/v1/users/{id}` — Delete user

**Response 204:** no body.

---

## 6. Error Catalog

| HTTP Status | Code field | Trigger | Message (example) |
|---|---|---|---|
| 400 | 400 | Validation failure (missing field, bad format, max length) | `"name is required"` |
| 400 | 400 | Invalid enum value for role | `"Invalid role value"` |
| 400 | 400 | Malformed UUID in path parameter | `"Invalid path parameter format"` |
| 404 | 404 | User not found by ID | `"User not found"` |
| 409 | 409 | Email already registered | `"The email address is already registered"` |
| 409 | 409 | Attempt to delete the last administrator | `"Cannot delete the last administrator"` |
| 500 | 500 | Unexpected server error | `"An unexpected error occurred. Please try again later."` |

**Error response structure:**
```json
{
  "code": 409,
  "message": "The email address is already registered"
}
```
