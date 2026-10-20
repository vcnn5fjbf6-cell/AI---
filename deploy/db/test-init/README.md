# Test database bootstrap

The test stack intentionally does not mount `backend-springboot/sql` into MySQL. That directory is not a valid empty-database migration chain.

Place a verified, schema-only dump at `runtime/001_baseline.sql` on the test host. The dump must contain no `INSERT` or `REPLACE` statements and no production data. `010_seed_test.sql` then creates one synthetic administrator, and `999_safety.sql` disables and clears every external integration used by the test environment.

The `runtime/` directory is ignored by Git because the baseline is generated from the currently deployed schema and verified separately before use.
