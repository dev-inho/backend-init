# Requirements — jpa-dsl-extension

## Goal
Extend the storage-side JPA generation DSL while keeping `core:domain` completely annotation-free and persistence-agnostic.

## P0 — Must Have
- R001: `core:domain` remains free of Spring, JPA, MyBatis, KSP, and project persistence annotations.
- R002: `storage:jpa` DSL supports nullable column policy per generated field.
- R003: `storage:jpa` DSL supports enum fields with explicit persistence strategy.
- R004: `storage:jpa` DSL supports simple value-object fields through explicit converter declarations.
- R005: Generated `*JpaEntity`, `*JpaRepository`, and `*JpaMapper` compile from a clean build and preserve existing `Sample` behavior.
- R006: Generator failures are explicit when a domain field type or DSL declaration is unsupported.

## P1 — Should Have
- R101: DSL supports relation declarations for `many-to-one` and `one-to-many` without adding annotations to domain models.
- R102: Relation generation includes clear mapper behavior and avoids accidental eager graph loading by default.
- R103: Tests cover generated nullable, enum, value-object, and relation examples.
- R104: Documentation shows how to add a new domain model to the JPA DSL.

## P2 — Nice To Have
- R201: Generator supports column naming overrides independently from Kotlin property names.
- R202: Generator supports generated ID strategy for storage entities when domain ID policy allows it.
- R203: Generator output is stable enough for diff inspection when generated into a temporary directory during tests.
- R204: Developer-facing error messages include the domain class and property that failed generation.

## Non-Goals
- N001: Do not introduce JPA annotations into `core:domain`.
- N002: Do not replace Spring Data JPA itself.
- N003: Do not implement arbitrary object graph persistence in the first phase.
- N004: Do not commit generated sources under `src/main/kotlin`.
