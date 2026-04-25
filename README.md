# Core API & Guardrails

A Spring Boot microservice that manages social interactions with strict Redis-based guardrails to ensure concurrency safety and prevent bot abuse.

---

## Overview

This service simulates a social platform where users and bots interact with posts while enforcing:

* Bot interaction limits
* Thread depth control
* Cooldown restrictions
* Smart notification batching

---

## Tech Stack

* Java 17
* Spring Boot 3.x
* PostgreSQL
* Redis
* Flyway
* Docker

---

## Setup Instructions

### 1. Start dependencies

```bash
docker compose up -d
```

### 2. Run application

```bash
./mvnw spring-boot:run
```

---

## API Endpoints

* POST /api/posts
* POST /api/posts/{postId}/comments
* POST /api/posts/{postId}/like

---

## Key Concepts

* Redis used for:

    * Atomic counters
    * Rate limiting
    * Distributed locks

* PostgreSQL used as:

    * Source of truth

---

#### Note: Credentials are hardcoded for simplicity in this assignment. In production, environment variables or secret managers should be used.
