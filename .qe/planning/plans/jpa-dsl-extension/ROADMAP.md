# Roadmap — jpa-dsl-extension

## Phase 1 — Scalar Mapping DSL
Goal: Make generated JPA code handle common single-field persistence concerns without touching `core:domain`.

Requirements: R001, R002, R003, R004, R005, R006, R201, R204

### Wave 1 — DSL Shape
- Define a typed-ish Groovy DSL structure for fields, nullability, column names, enums, and converters.
- Preserve the current minimal `domainClass/table/id` declaration as the simple path.
- Add validation rules for unsupported Kotlin property forms.

### Wave 2 — Generator Expansion
- Generate `@Column(nullable = ...)` from DSL policy.
- Generate enum storage according to explicit strategy.
- Generate mapper calls for declared value-object converters.
- Keep generated class names and packages compatible with existing adapters.

### Wave 3 — Tests and Compatibility
- Add generator-focused tests using sample scalar fields.
- Re-run domain purity, `storage:jpa`, and persistence wiring tests.
- Update storage JPA docs with scalar DSL examples.

Success Criteria:
- Clean build generates and compiles scalar-enhanced JPA sources.
- Existing sample repository behavior remains green.
- Unsupported field cases fail at generation time with actionable errors.

## Phase 2 — Relation Mapping DSL
Goal: Add relation declarations in `storage:jpa` while preserving domain plainness and explicit mapper behavior.

Requirements: R001, R005, R006, R101, R102, R103

### Wave 1 — Relation Policy
- Define supported relation types: `manyToOne` and `oneToMany`.
- Require explicit foreign-key and referenced-domain declarations.
- Default relation fetch behavior to lazy where JPA supports it.

### Wave 2 — Relation Generation
- Generate relation fields and join column metadata in JPA entities.
- Generate mapper behavior that avoids unbounded graph recursion.
- Add explicit failure paths for unsupported relation cycles.

### Wave 3 — Relation Tests
- Add small domain fixtures for parent/child relation examples.
- Verify generated JPA sources compile and basic persistence tests pass.
- Document safe relation patterns and known limitations.

Success Criteria:
- A simple parent-child relation can be generated and tested through JPA.
- Domain models remain annotation-free.
- Recursive mapping risk is documented and guarded.

## Phase 3 — Hardening and Documentation
Goal: Make the generator maintainable enough for future domain growth.

Requirements: R001, R005, R006, R103, R104, R203, R204

### Wave 1 — Generator Structure
- Extract generation logic out of ad hoc Gradle script sections if complexity now warrants it.
- Keep Gradle integration simple and deterministic.
- Add stable generated-source snapshot or compile-based verification strategy.

### Wave 2 — Documentation
- Expand `storage:jpa/README.md` with DSL recipes.
- Update `docs/MODULE_GUIDE.md` with relation/value-object conventions.
- Record the domain-plain generation decision in the decision log if not already captured.

### Wave 3 — Verification
- Run full relevant Gradle test matrix.
- Confirm `core:domain` purity test still rejects persistence leakage.
- Produce a concise implementation/verification summary.

Success Criteria:
- The DSL has documented recipes for new scalar, enum, value-object, and relation fields.
- Full relevant test matrix passes.
- Future unsupported cases fail clearly rather than generating invalid Kotlin silently.
