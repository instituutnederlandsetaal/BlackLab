# Improvement Tasks Checklist

A prioritized, actionable checklist to improve the architecture, code quality, performance, reliability, and developer experience of the BlackLab project. Each item can be checked off as it is completed.

## Architecture and Design

1. [ ] Write an up-to-date high-level architecture overview (components, data flow, boundaries between modules: util, common, content-store, engine, query-parser, core, server, wslib, solr, proxy, instrumentation). Include diagrams and responsibilities for each module.
2. [ ] Define and document clear public APIs vs. internal implementation details for core/engine packages; minimize leakage of internal classes across module boundaries.
3. [ ] Introduce an internal API stability policy and deprecation process aligned with semantic versioning (what is stable, how long deprecations remain, removal cadence).
4. [ ] Review and tighten module dependencies in all pom.xml files to avoid unnecessary transitive exposure (use dependencyManagement, reduce compile scope where possible, prefer test/runtime scopes).
5. [ ] Reconcile README vs. build configuration: README says "This version uses Lucene 8" while root pom.xml uses Lucene 9.11.1; update docs or dependencies so they are consistent.
6. [ ] Create per-module READMEs summarizing purpose, main packages, build/test commands, and how to run examples.
7. [ ] Define feature flags/config toggles for experimental modules (solr, proxy, instrumentation) and document how to enable/disable them in builds and runtime.
8. [ ] Establish configuration layering/precedence (env vars, system props, YAML) and document it for server and tools.
9. [ ] Capture architectural decisions as ADRs (docs/adr) for big choices (Solr integration, forward index design, index metadata model, tokenization pipeline, caching strategy).

## Build, Quality Gates, and Dependencies

10. [ ] Re-enable and standardize static analysis across modules (Checkstyle, PMD, SpotBugs/FindSecBugs); the root pom currently comments out build-tools; integrate shared rules via build-tools or maven aggregator.
11. [ ] Add JaCoCo test coverage reporting at the multi-module level; set a reasonable minimum coverage threshold per module.
12. [ ] Add OWASP Dependency-Check or Maven/OWASP plugin to flag vulnerable dependencies during CI.
13. [ ] Add Maven Enforcer rules for dependency convergence and banned duplicates; fail build on version conflicts.
14. [ ] Introduce Maven versions-maven-plugin to automate and document dependency upgrades; schedule periodic update checks.
15. [ ] Ensure reproducible builds (pin plugin versions, consider maven-dependency-plugin checksumPolicy, maven-compiler release already set to 17; verify all modules inherit it).
16. [ ] Create a Bill of Materials (BOM) for shared dependency versions or use the parent’s dependencyManagement consistently across all child modules.

## Continuous Integration / Delivery

17. [ ] Add a GitHub Actions (or alternative CI) workflow: build matrix for JDK 17 and 21, run mvn -B -q -T 1C verify, cache ~/.m2, and publish test reports and coverage.
18. [ ] Add a scheduled CI job (weekly) to run dependency checks and open automated PRs for safe upgrades.
19. [ ] Add a release workflow to create tagged releases, deploy artifacts to OSSRH, and publish Docker images (server, indexer) with proper version tags.

## Testing Strategy

20. [ ] Map existing tests per module (unit vs. integration) and identify gaps (e.g., query-parser edge cases, forward index persistence tests, server API contract tests).
21. [ ] Introduce API contract tests for BlackLab Server (OpenAPI/Swagger-based tests for request/response schemas and error codes).
22. [ ] Add performance regression tests or benchmarks for hot paths (hit grouping, KWIC generation, forward index lookups) using JMH.
23. [ ] Add property-based tests for text pattern parsing and serialization round-trips.
24. [ ] Create integration tests for Solr integration path (start embedded Solr or testcontainer, index small corpus, run distributed queries) guarded by a profile.
25. [ ] Add smoke tests for Docker images (server starts, endpoint health, simple search works) in CI.

## Performance and Scalability

26. [ ] Profile end-to-end query execution on representative corpora (JFR/JProfiler) and document hotspots; create issues for top findings.
27. [ ] Audit caching layers (caffeine usage, any custom caches): set sensible maximum sizes, lifetimes, and metrics; add cache hit/miss instrumentation.
28. [ ] Review memory usage of forward index implementations and terms dictionaries; consider off-heap options or compressed structures where beneficial.
29. [ ] Validate I/O patterns for content-store and forward index (sequential vs. random, mmap usage) and document recommended FS/OS settings.
30. [ ] Evaluate parallelism defaults and thread pools (search, indexing); make configurable and document safe ranges per hardware.

## Concurrency and Correctness

31. [ ] Perform a thread-safety audit for forward index and associated classes (e.g., AnnotationForwardIndex, Terms, Collators); ensure @ThreadSafe annotations reflect reality and guard shared mutable state.
32. [ ] Replace ad-hoc synchronization with well-defined concurrency primitives (locks, read-write locks) where it improves throughput; document invariants.
33. [ ] Adopt explicit nullability annotations (e.g., SpotBugs or JetBrains @Nullable/@NonNull) in public APIs and enforce with static analysis.
34. [ ] Standardize equals/hashCode/compareTo implementations for value types (e.g., PropertyValue, Results keys); add tests to prevent comparator contract violations.

## Logging, Monitoring, and Observability

35. [ ] Standardize logging (Log4j2) configuration across modules; define logger categories, levels, and JSON layout option for server.
36. [ ] Ensure sensitive data are never logged; add SpotBugs/FindSecBugs rule checks.
37. [ ] Add basic metrics (Micrometer) for key operations (index open, query exec time, cache hit rate, forward index reads); expose via JMX and optionally Prometheus for server.
38. [ ] Add structured request logging and trace IDs to BlackLab Server; propagate via MDC.

## API and Backward Compatibility

39. [ ] Generate and publish an OpenAPI spec for BlackLab Server; align error model and document status codes.
40. [ ] Provide migration notes for breaking changes between major versions; automate changelog generation from PR labels.
41. [ ] Add serialization compatibility tests for any persisted formats (terms dictionaries, forward index entries) to prevent silent format drift.

## Security and Hardening

42. [ ] Run a dependency vulnerability scan regularly; track CVEs and define an SLA for remediation.
43. [ ] Review input validation for server endpoints (query parameters, limits, timeouts) to prevent DoS and injection vectors; add sane defaults and upper bounds.
44. [ ] Validate XML parsing is safe (Saxon, XML parsers): disable external entity resolution where applicable, enforce secure-processing features.
45. [ ] Review classpath scanning (org.reflections) for tight filters to minimize attack surface and startup cost.

## Documentation and Developer Experience

46. [ ] Consolidate developer docs in doc/ and link clearly from README; add a "Getting Started for Devs" with mvn commands, test profiles, and common pitfalls.
47. [ ] Add module diagrams and sequence diagrams for query execution and indexing in doc/technical.
48. [ ] Provide ready-to-run sample corpora and companion scripts to exercise core features locally.
49. [ ] Enhance Docker docs: resource requirements, bind mounts, troubleshooting common errors, and multi-arch builds.
50. [ ] Create contributor guide (coding style, commit message conventions, branching strategy, code review checklist).

## Code Health and Refactoring

51. [ ] Identify and remove dead code, obsolete adapters, and duplicate utilities across util/common/engine.
52. [ ] Reduce overly broad visibility; prefer package-private for internal classes, sealed interfaces/classes where applicable (Java 17+) for key hierarchies.
53. [ ] Replace raw types and unchecked casts with generics; add @SuppressWarnings only with justification.
54. [ ] Normalize exception handling strategy (checked vs. runtime), map exceptions to server HTTP errors consistently, and document.
55. [ ] Introduce small, focused interfaces for complex components (e.g., forward index readers/writers, collators) to ease testing and substitution.
56. [ ] Add immutability where possible (records for simple data carriers, unmodifiable collections) to reduce bugs.

## Release Management and Packaging

57. [ ] Verify Maven Central publishing pipeline (OSSRH) including javadoc/source jars; fix signing if needed.
58. [ ] Produce and test reproducible Docker images; include SBOMs (CycloneDX/Syft) and image scanning in CI.
59. [ ] Tag and publish versioned CLI tools; provide checksum files and release notes per version.

## Solr/Distributed Search Track

60. [ ] Finalize and document distributed search design (doc/technical/design/plan-distributed.md references) with concrete milestones and acceptance criteria.
61. [ ] Prototype a minimal distributed search flow (index shards, query fanout, result merge); add integration tests and benchmarks.
62. [ ] Define failure-handling and retry semantics for distributed searches (timeouts, partial results policy) and document user-facing behavior.

