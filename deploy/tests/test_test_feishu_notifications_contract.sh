#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
compose_file="$PROJECT_ROOT/deploy/docker-compose.test.yml"
configure_script="$PROJECT_ROOT/deploy/configure_test_feishu_notifications.sh"
reconcile_script="$PROJECT_ROOT/deploy/prepare_test_gitee_reconciliation.sh"
deploy_script="$PROJECT_ROOT/deploy/deploy_test_runner.sh"
clean_test_script="$PROJECT_ROOT/deploy/verify_release_batch_clean_db.sh"
workflow_file="$PROJECT_ROOT/.github/workflows/deploy-release-control.yml"
test_workflow_file="$PROJECT_ROOT/.github/workflows/deploy-test.yml"
proxy_config="$PROJECT_ROOT/frontend-vue/deploy/nginx/test-feishu-egress.conf"
release_service="$PROJECT_ROOT/deploy/release-agent/src/release-service.js"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

grep -Fq 'FEISHU_APP_ID: ${TEST_FEISHU_APP_ID:-}' "$compose_file" ||
  fail "test App ID is not sourced from the host-managed test environment"
grep -Fq 'FEISHU_APP_SECRET: ${TEST_FEISHU_APP_SECRET:-}' "$compose_file" ||
  fail "test App Secret is not sourced from the host-managed test environment"
grep -Fq 'TEST_FEISHU_TEST_CARD_ENABLED: ${TEST_FEISHU_TEST_CARD_ENABLED:-false}' "$compose_file" ||
  fail "test card enablement is not host managed"
grep -Fq 'FEISHU_APPROVAL_CARD_TEST_RECIPIENT_EMAIL: ${TEST_FEISHU_RECIPIENT_EMAIL:-}' "$compose_file" ||
  fail "test recipient is not injected into the dedicated test-card setting"
if grep -Fq "SET email = '\$\${recipient_email}'" "$compose_file"; then
  fail "test recipient configuration must not overwrite a system user email"
fi
grep -Fq 'FEISHU_APPROVAL_CARD_ENABLED: "false"' "$compose_file" ||
  fail "test business notifications must remain disabled"
grep -Fq 'FEISHU_APPROVAL_CARD_HEARTBEAT_ENABLED: "false"' "$compose_file" ||
  fail "test approval heartbeat must remain disabled"
grep -Fq 'FEISHU_APPROVAL_CARD_LONG_CONNECTION_ENABLED: "false"' "$compose_file" ||
  fail "test long connection must remain disabled"
grep -Fq 'FEISHU_BOT_ENABLED: "false"' "$compose_file" ||
  fail "test inbound bot must remain disabled"

grep -Fq 'location ^~ /open-apis/' "$proxy_config" ||
  fail "test egress proxy must restrict requests to the Feishu API prefix"
grep -Fq 'proxy_pass https://open.feishu.cn;' "$proxy_config" ||
  fail "test egress proxy must have one fixed Feishu upstream"
grep -Fq 'gw_priority: 1' "$compose_file" ||
  fail "test egress network must be the proxy container default gateway"
grep -Fq '    user: "101:101"' "$compose_file" ||
  fail "test egress proxy must run as the nginx image user"
grep -Fq '/var/cache/nginx:uid=101,gid=101,mode=0755' "$compose_file" ||
  fail "test egress proxy temp storage must be owned by the nginx image user"
test "$(grep -Fc '/var/cache/nginx:uid=101,gid=101,mode=0755' "$compose_file")" = "1" ||
  fail "unprivileged nginx temp ownership must apply only to the test egress proxy"
if grep -Eq 'proxy_pass[[:space:]]+\$|resolver.*[[:space:]][^;]*8\.8\.8\.8' "$proxy_config"; then
  fail "test egress proxy must not use a caller-controlled upstream or public resolver"
fi

grep -Fq 'TEST_FEISHU_CONFIG_B64: ${{ secrets.TEST_FEISHU_CONFIG_B64 }}' "$workflow_file" ||
  fail "trusted workflow does not consume the one-time Feishu configuration secret"
grep -Fq 'bash deploy/configure_test_feishu_notifications.sh' "$workflow_file" ||
  fail "trusted workflow does not apply the host-managed Feishu configuration"
grep -Fq 'reconcile_test_gitee_deployment:' "$workflow_file" ||
  fail "trusted workflow does not expose an explicit Gitee reconciliation gate"
grep -Fq 'bash deploy/gitee_test_deploy_bootstrap.sh' "$workflow_file" ||
  fail "trusted workflow cannot reconcile the Gitee test deployment before configuration"
grep -Fq 'bash deploy/prepare_test_gitee_reconciliation.sh' "$workflow_file" ||
  fail "trusted workflow does not disable the test card before Gitee reconciliation"
grep -Fq 'procurement-test-feishu-egress-1' "$workflow_file" ||
  fail "trusted workflow does not verify the Gitee reconciliation postconditions"
grep -Fq 'TEST_FEISHU_TEST_CARD_ENABLED": "false"' "$reconcile_script" ||
  fail "Gitee reconciliation does not fail closed for the test card"
grep -Fq 'deploy/configure_test_feishu_notifications.sh|\' "$test_workflow_file" ||
  fail "release batches do not protect the host configuration script"
grep -Fq 'frontend-vue/deploy/nginx/test-feishu-egress.conf|\' "$test_workflow_file" ||
  fail "release batches do not protect the fixed Feishu proxy configuration"
grep -Fq '"deploy/configure_test_feishu_notifications.sh"' "$release_service" ||
  fail "release control does not classify the host configuration script as protected"
grep -Fq '"frontend-vue/deploy/nginx/test-feishu-egress.conf"' "$release_service" ||
  fail "release control does not classify the fixed Feishu proxy as protected"
grep -Fq 'test Feishu credentials were rejected' "$configure_script" ||
  fail "configuration script does not validate the test app credentials"
grep -Fq 'flock -w "${TEST_DEPLOY_LOCK_WAIT_SECONDS:-600}" 9' "$configure_script" ||
  fail "configuration script does not share the test deployment lock"
grep -Fq 'current-gitee-build-ref' "$configure_script" ||
  fail "configuration script does not validate the audited Gitee test image marker"
grep -Fq 'procurement-test-gitee/current-sha' "$configure_script" ||
  fail "configuration script does not bind Gitee images to the host deployment state"
grep -Fq 'does not match running image SHA' "$configure_script" ||
  fail "configuration script does not report non-sensitive Gitee state mismatches"
grep -Fq 'Restored the previous test Feishu environment after configuration failure' "$configure_script" ||
  fail "configuration script does not restore the host environment after failure"
grep -Fq 'egress_was_running' "$configure_script" ||
  fail "configuration rollback does not preserve the previous egress state"
grep -Fq 'Test Feishu recipient database configuration failed' "$configure_script" ||
  fail "configuration script does not report recipient database failures"
grep -Fq 'com.company.procurement.source' "$configure_script" ||
  fail "configuration script does not validate the immutable Gitee image source labels"
grep -Fq 'com.company.procurement.commit' "$configure_script" ||
  fail "configuration script does not validate the immutable Gitee image commit labels"
grep -Fq 'Restored missing Gitee test image audit marker' "$configure_script" ||
  fail "configuration script does not safely restore a missing Gitee audit marker"
grep -Fq 'procurement-backend:gitee-test-([0-9a-f]{40})' "$configure_script" ||
  fail "configuration script does not bind Gitee images to an exact commit"
grep -Fq 'test "$(docker inspect "$egress_container" --format' "$configure_script" ||
  fail "configuration script does not preserve the frontend image used by the egress proxy"
grep -Fq 'test "$(docker inspect "$backend_container" --format' "$configure_script" ||
  fail "configuration script does not preserve the backend image across recreation"
grep -Fq 'Test Feishu configuration failed at line %s' "$configure_script" ||
  fail "configuration script does not report a safe failure location"
if grep -Eq 'BASH_COMMAND|failed command|CONFIG_B64.*>&2|app_secret.*>&2' "$configure_script"; then
  fail "configuration diagnostics may not print credential-bearing commands or values"
fi
grep -Fq "'</dev/tcp/1.1.1.1/443'" "$configure_script" ||
  fail "configuration script does not verify direct backend egress remains blocked"
grep -Fq "'</dev/tcp/feishu-egress/8080'" "$configure_script" ||
  fail "configuration script does not verify the narrow proxy route"
grep -Fq 'test "$egress_members" = "procurement-test-feishu-egress-1"' "$configure_script" ||
  fail "configuration script does not enforce exclusive external-network membership"
grep -Fq 'test "$egress_members" = "procurement-test-feishu-egress-1"' "$deploy_script" ||
  fail "normal test deployment does not enforce exclusive external-network membership"
grep -Fq "docker inspect procurement-test-feishu-egress-1 --format '{{.State.Health.Status}}'" "$PROJECT_ROOT/deploy/gitee_test_deploy_bootstrap.sh" ||
  fail "Gitee deployment health checks do not include the Feishu egress proxy"
grep -Fq 'export TEST_FEISHU_APP_SECRET=""' "$clean_test_script" ||
  fail "clean candidate verification does not remove the test App Secret"
grep -Fq 'test -z "$egress_container"' "$clean_test_script" ||
  fail "clean candidate verification does not keep the Feishu proxy stopped"

diagnostic_root="$(mktemp -d)"
trap 'rm -rf "$diagnostic_root"' EXIT
mkdir -p \
  "$diagnostic_root/deploy" \
  "$diagnostic_root/frontend-vue/deploy/nginx"
diagnostic_marker="DO_NOT_PRINT_TEST_FEISHU_SECRET"
if diagnostic_output="$(
  TEST_DEPLOY_ROOT="$diagnostic_root" \
  TEST_FEISHU_CONFIG_B64="$diagnostic_marker" \
    bash "$configure_script" 2>&1
)"; then
  fail "configuration diagnostics fixture unexpectedly succeeded"
fi
grep -Eq '^Test Feishu configuration failed at line [0-9]+$' <<<"$diagnostic_output" ||
  fail "configuration diagnostics did not report a safe line number"
if grep -Fq "$diagnostic_marker" <<<"$diagnostic_output"; then
  fail "configuration diagnostics leaked the one-time configuration"
fi

echo "Test Feishu notification isolation contract passed"
