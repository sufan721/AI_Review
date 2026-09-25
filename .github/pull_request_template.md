## What changed
<!-- Explain the problem solved by this change in one sentence. -->

## Related issue
Closes #

## How to verify
<!-- List local or CI verification commands and results. -->

## Self-check
- [ ] API responses use the shared `Result` type and do not expose entities
- [ ] ID-based operations are restricted to the current user
- [ ] Multi-table writes use `@Transactional`
- [ ] Schema changes are synchronized with `docs/sql/schema.sql`
- [ ] No debug code, `console.log`, or dead comments
- [ ] No passwords, keys, or other secrets are committed
