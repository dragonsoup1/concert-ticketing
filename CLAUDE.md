# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Concert ticketing backend — a Spring Boot 4.1.0 / Java 17 portfolio project demonstrating large-scale traffic handling, concurrency control, data consistency, idempotency, and availability patterns.

## Repository Structure

```
concert-ticketing/
├── backend/          ← Spring Boot application (see backend/CLAUDE.md)
├── docker-compose.yml
└── CLAUDE.md
```

## Local Infrastructure

Start PostgreSQL, Redis, and Kafka before running the application:

```bash
docker compose up -d
```
