#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

line_of() {
  local file="$1"
  local pattern="$2"
  local match
  match="$(grep -nF -- "$pattern" "$file" | head -n 1)" ||
    fail "$file does not contain: $pattern"
  printf '%s\n' "${match%%:*}"
}

assert_before() {
  local file="$1"
  local first="$2"
  local second="$3"
  local first_line
  local second_line
  first_line="$(line_of "$file" "$first")"
  second_line="$(line_of "$file" "$second")"
  [ "$first_line" -lt "$second_line" ] ||
    fail "$first must appear before $second in $file"
}

backend_dockerfile="$PROJECT_ROOT/backend-springboot/Dockerfile"
backend_pom="$PROJECT_ROOT/backend-springboot/pom.xml"
frontend_dockerfile="$PROJECT_ROOT/frontend-vue/Dockerfile"
production_workflow="$PROJECT_ROOT/.github/workflows/deploy.yml"
test_workflow="$PROJECT_ROOT/.github/workflows/deploy-test.yml"
production_runner="$PROJECT_ROOT/deploy/deploy_local_runner.sh"
baseline_helper="$PROJECT_ROOT/deploy/verify_production_baseline.sh"
pull_helper="$PROJECT_ROOT/deploy/pull_immutable_images.sh"
archive_verifier="$PROJECT_ROOT/deploy/verify_candidate_image_archive.py"

bash -n "$baseline_helper"
bash -n "$pull_helper"
bash -n "$production_runner"
python3 -m py_compile "$archive_verifier"
python3 -m unittest "$PROJECT_ROOT/deploy/tests/test_verify_candidate_image_archive.py"

assert_before "$backend_dockerfile" "COPY pom.xml ." "mvn -B -ntp -DskipTests dependency:go-offline"
assert_before "$backend_dockerfile" "mvn -B -ntp -DskipTests dependency:go-offline" "COPY src ./src"
assert_before "$backend_dockerfile" "COPY src ./src" "mvn -B -ntp clean package"
grep -Fq -- "<project.build.outputTimestamp>" "$backend_pom" ||
  fail "backend build output timestamp is not deterministic"
if grep -Fq -- "--mount=type=cache" "$backend_dockerfile"; then
  fail "backend image requires BuildKit and cannot use the Gitee legacy builder cache"
fi
grep -Fq -- 'test "$layer_bytes" -le 33554432' "$backend_dockerfile" ||
  fail "backend image does not enforce the 32 MiB dependency bucket limit"
grep -Fq -- "touch -h -d '@0'" "$backend_dockerfile" ||
  fail "backend runtime layer timestamps are not normalized"
for bucket in a-l m-n o p-r s t-z; do
  grep -Fq -- "/app/dependency-layers/$bucket/" "$backend_dockerfile" ||
    fail "backend image is missing dependency bucket $bucket"
done
for layer in spring-boot-loader snapshot-dependencies application; do
  grep -Fq -- "/app/extracted/$layer/" "$backend_dockerfile" ||
    fail "backend image is missing the Spring Boot $layer layer"
done

assert_before "$frontend_dockerfile" "COPY package*.json ./" "RUN npm ci"
assert_before "$frontend_dockerfile" "RUN npm ci" "ARG VITE_BUILD_REF=local"
assert_before "$frontend_dockerfile" "ARG VITE_BUILD_REF=local" "RUN npm run build"

assert_before "$production_workflow" "name: Verify production baseline before image transfer" "name: Prepare verified candidate image cache"
assert_before "$production_workflow" "name: Prepare verified candidate image cache" "name: Restore verified candidate image cache"
assert_before "$production_workflow" "name: Restore verified candidate image cache" "name: Load verified candidate image cache"
assert_before "$production_workflow" "name: Load verified candidate image cache" "name: Pull exact tested images"
assert_before "$production_workflow" "name: Pull exact tested images" "name: Deploy exact tested release"
grep -Fq -- "bash deploy/verify_production_baseline.sh" "$production_workflow" ||
  fail "production workflow does not invoke the baseline helper"
grep -Fq -- "bash deploy/pull_immutable_images.sh" "$production_workflow" ||
  fail "production workflow does not invoke the immutable image pull helper"
grep -Fq -- 'bash "$PROJECT_ROOT/deploy/verify_production_baseline.sh"' "$production_runner" ||
  fail "production runner does not reuse the baseline helper"
for protected_path in \
  "deploy/verify_production_baseline.sh" \
  "deploy/pull_immutable_images.sh" \
  "deploy/verify_candidate_image_archive.py"; do
  grep -Fq -- "$protected_path" "$test_workflow" ||
    fail "$protected_path is not protected from release-batch changes"
done

assert_before "$test_workflow" "name: Publish immutable candidate images" "name: Package optional candidate image archive"
assert_before "$test_workflow" "name: Package optional candidate image archive" "name: Save optional candidate image archive cache"
assert_before "$test_workflow" "name: Verify trusted test runner guard" "name: Prepare optional candidate image archive cache"
assert_before "$test_workflow" "name: Prepare optional candidate image archive cache" "name: Restore optional candidate image archive cache"
assert_before "$test_workflow" "name: Restore optional candidate image archive cache" "name: Load verified optional candidate image archive"
assert_before "$test_workflow" "name: Load verified optional candidate image archive" "name: Pull immutable candidate images"
assert_before "$test_workflow" "name: Pull immutable candidate images" "name: Deploy isolated test environment with trusted runner"

grep -Fq -- "candidate_archive_sha256: \${{ steps.archive.outputs.archive_sha256 }}" "$test_workflow" ||
  fail "test workflow does not expose the validated archive checksum"
grep -Fq -- "candidate_backend_image_id: \${{ steps.publish.outputs.backend_image_id }}" "$test_workflow" ||
  fail "test workflow does not expose the published backend image ID"
grep -Fq -- "candidate_frontend_image_id: \${{ steps.publish.outputs.frontend_image_id }}" "$test_workflow" ||
  fail "test workflow does not expose the published frontend image ID"
grep -Fq -- "actions/cache/save@0400d5f644dc74513175e3cd8d07132dd4860809" "$test_workflow" ||
  fail "test workflow does not use the pinned cache save action"
grep -Fq -- "actions/cache/restore@0400d5f644dc74513175e3cd8d07132dd4860809" "$test_workflow" ||
  fail "test runner does not use the pinned cache restore action"
grep -Fq -- "actions/cache/restore@0400d5f644dc74513175e3cd8d07132dd4860809" "$production_workflow" ||
  fail "production runner does not use the pinned cache restore action"
if grep -Fq -- "actions/upload-artifact@" "$test_workflow"; then
  fail "candidate image transport must not consume Actions Artifact quota"
fi
if grep -Fq -- "actions/download-artifact@" "$production_workflow"; then
  fail "production must restore the exact candidate cache instead of an Artifact copy"
fi

grep -Fq -- 'key: candidate-images-${{ github.run_id }}-${{ github.run_attempt }}-${{ steps.identity.outputs.candidate_sha }}' "$test_workflow" ||
  fail "test build cache key is not bound to the exact run, attempt, and candidate SHA"
grep -Fq -- 'key: candidate-images-${{ github.run_id }}-${{ github.run_attempt }}-${{ needs.build.outputs.candidate_sha }}' "$test_workflow" ||
  fail "test runner cache key is not bound to the exact run, attempt, and candidate SHA"
grep -Fq -- 'key: candidate-images-${{ inputs.test_run_id }}-${{ needs.authorize.outputs.test_run_attempt }}-${{ inputs.release_sha }}' "$production_workflow" ||
  fail "production cache key is not bound to the authorized test run, attempt, and release SHA"
[ "$(grep -Fc -- 'path: .release-cache/procurement-candidate-images-${{ github.run_id }}-${{ github.run_attempt }}' "$test_workflow")" = "2" ] ||
  fail "test cache save and restore must use the same workspace-relative path"
if grep -Fq -- "path: /tmp/procurement-candidate-images-" "$test_workflow" "$production_workflow"; then
  fail "absolute cache paths cannot be restored across runners with different workspace depths"
fi

grep -Fq -- "python3 trusted/deploy/verify_candidate_image_archive.py" "$test_workflow" ||
  fail "test runner does not use the shared candidate archive verifier"
grep -Fq -- "python3 deploy/verify_candidate_image_archive.py" "$production_workflow" ||
  fail "production runner does not use the shared candidate archive verifier"
for expected_arg in \
  '--expected-archive-sha256 "$EXPECTED_ARCHIVE_SHA256"' \
  '--expected-backend-image-id "$EXPECTED_BACKEND_IMAGE_ID"' \
  '--expected-frontend-image-id "$EXPECTED_FRONTEND_IMAGE_ID"'; do
  grep -Fq -- "$expected_arg" "$test_workflow" ||
    fail "test runner is missing trusted verifier argument: $expected_arg"
done
grep -Fq -- 'docker save "$BACKEND_TAG" "$FRONTEND_TAG"' "$test_workflow" ||
  fail "test workflow does not package both exact candidate image tags"
grep -Fq -- "verify_registered_digest() {" "$test_workflow" ||
  fail "test runner lacks the immutable digest registration gate"
grep -Fq -- "steps.archive_restore.outputs.cache-hit == 'true'" "$test_workflow" ||
  fail "test runner may not load an archive unless the exact cache key was restored"
grep -Fq -- "steps.candidate_cache_restore.outputs.cache-hit == 'true'" "$production_workflow" ||
  fail "production may not load an archive unless the exact cache key was restored"

grep -Fq -- "name: Verify successful test workflow identity" "$production_workflow" ||
  fail "production does not validate the referenced successful test run"
grep -Fq -- 'run.path !== ".github/workflows/deploy-test.yml"' "$production_workflow" ||
  fail "production does not bind the test run to the trusted test workflow"
grep -Fq -- 'core.setOutput("test_run_attempt", String(run.run_attempt))' "$production_workflow" ||
  fail "production does not resolve the exact test run attempt"
grep -Fq -- 'if [ "$elapsed_seconds" -gt 5400 ]' "$production_workflow" ||
  fail "production preparation no longer has the 90-minute maintenance-window guard"

test_deploy_block="$(sed -n '/^  deploy:/,/^  cleanup_failed_cache:/p' "$test_workflow")"
printf '%s\n' "$test_deploy_block" | grep -Fq -- "actions: read" ||
  fail "the procurement-151 job cannot restore the candidate cache"
if printf '%s\n' "$test_deploy_block" | grep -Fq -- "actions: write"; then
  fail "the procurement-151 job must not receive Actions write permission"
fi

failed_cleanup_block="$(sed -n '/^  cleanup_failed_cache:/,$p' "$test_workflow")"
printf '%s\n' "$failed_cleanup_block" | grep -Fq -- "needs.deploy.result != 'success'" ||
  fail "a successful test cache must be retained for production"
printf '%s\n' "$failed_cleanup_block" | grep -Fq -- "getActionsCacheList" ||
  fail "failed test cleanup does not resolve the exact cache ID"
printf '%s\n' "$failed_cleanup_block" | grep -Fq -- "deleteActionsCacheById" ||
  fail "failed test cleanup does not delete cache entries by ID"

production_cleanup_block="$(sed -n '/^  cleanup_successful_cache:/,$p' "$production_workflow")"
printf '%s\n' "$production_cleanup_block" | grep -Fq -- "needs.deploy.result == 'success'" ||
  fail "production cache cleanup must run only after successful deployment"
printf '%s\n' "$production_cleanup_block" | grep -Fq -- "getActionsCacheList" ||
  fail "production cleanup does not resolve the exact cache ID"
printf '%s\n' "$production_cleanup_block" | grep -Fq -- "deleteActionsCacheById" ||
  fail "production cleanup does not delete cache entries by ID"
printf '%s\n' "$production_cleanup_block" | grep -Fq -- "actions: write" ||
  fail "isolated production cleanup cannot delete the released cache"

echo "release performance contract tests: PASS"
