# Contributing to PredictiveEdge

First of all, thank you for your interest in contributing to PredictiveEdge!

PredictiveEdge is an open-source AI Trading Intelligence Platform built by the community for the community. Every contribution—whether code, documentation, testing, design, or ideas—helps improve the project.

---

# Before You Start

Please read the following documents before contributing:

* README.md
* docs/00-constitution/PredictiveEdge-Constitution-v1.0.md
* ARCHITECTURE.md
* CODE_OF_CONDUCT.md

All contributions should align with the project's Constitution and Architecture Decision Records (ADRs).

---

# Ways to Contribute

You can contribute by:

* Developing new features
* Fixing bugs
* Improving documentation
* Creating diagrams
* Writing tests
* Developing plugins
* Integrating brokers
* Building AI providers
* Creating technical indicators
* Improving performance
* Reviewing pull requests

---

# Development Principles

Every contribution should follow these principles:

* Keep modules small and focused.
* Prefer composition over inheritance.
* Follow Domain-Driven Design (DDD).
* Follow Hexagonal Architecture.
* Keep business logic independent of infrastructure.
* Write readable, maintainable code.
* Prefer interfaces over implementations.
* Avoid unnecessary dependencies.
* Document significant architectural decisions.

---

# Branching Strategy

Use the following branch naming convention:

```text
main

feature/<feature-name>

bugfix/<issue-name>

docs/<document-name>

refactor/<module-name>

release/<version>
```

Examples:

```text
feature/trade-planner

feature/fyers-plugin

docs/repository-architecture

bugfix/order-validation
```

---

# Commit Message Convention

Follow Conventional Commits.

Examples:

```text
feat: add broker adapter framework

fix: resolve order validation issue

docs: update architecture documentation

refactor: simplify AI provider loading

test: add unit tests for trade planner

build: upgrade Spring Boot version

ci: add GitHub Actions workflow

security: encrypt stored broker credentials
```

---

# Pull Request Guidelines

Before opening a Pull Request:

* Rebase on the latest `main` branch.
* Ensure the project builds successfully.
* Ensure all tests pass.
* Update documentation if required.
* Include relevant ADR updates for architectural changes.
* Keep pull requests focused on a single topic whenever possible.

Pull requests should include:

* Summary of changes
* Motivation
* Related issue (if applicable)
* Testing performed
* Documentation updates

---

# Coding Standards

General expectations:

* Java 21
* Maven multi-module project
* Follow project formatting rules
* Write meaningful class and method names
* Keep methods concise
* Avoid duplicated logic
* Prefer immutable objects where practical

Additional coding standards will be documented separately.

---

# Testing

Contributors are encouraged to include:

* Unit tests
* Integration tests (where appropriate)
* Regression tests for bug fixes

New features should include appropriate test coverage whenever feasible.

---

# Documentation

Documentation is a first-class citizen.

Whenever applicable, update:

* README
* Architecture documents
* ADRs
* API documentation
* Diagrams

Documentation should evolve alongside the code.

---

# Architecture Decision Records (ADR)

Major architectural decisions must be documented using an ADR.

An ADR should explain:

* Context
* Decision
* Alternatives considered
* Consequences

This ensures future contributors understand why decisions were made.

---

# Plugin Development

PredictiveEdge is designed to be extensible.

Contributors are encouraged to build plugins for:

* Brokers
* AI Providers
* Market Data Providers
* Indicators
* Strategies
* Risk Models
* Notifications
* Scanners

Plugins should depend only on the appropriate Plugin SDK modules.

---

# Community Values

We value:

* Respectful communication
* Constructive feedback
* Transparency
* Collaboration
* Continuous learning
* Long-term maintainability
* High engineering standards

---

# Questions

If you're unsure about an implementation or architectural decision, start a discussion before investing significant development effort.

---

Thank you for helping build PredictiveEdge.

Together, we can create the world's most trusted open-source AI Trading Intelligence Platform.

**Trade Smarter. Stay in Control.**
