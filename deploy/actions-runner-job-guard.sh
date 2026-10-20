#!/usr/bin/env bash
set -euo pipefail

readonly EXPECTED_REPOSITORY="dongfanfan94-droid/enterprise-procurement-platform"
role="${PROCUREMENT_RUNNER_ROLE:-}"
workflow="${GITHUB_WORKFLOW:-}"
workflow_ref="${GITHUB_WORKFLOW_REF:-}"
event_name="${GITHUB_EVENT_NAME:-}"
ref="${GITHUB_REF:-}"
repository="${GITHUB_REPOSITORY:-}"

if [ "$repository" != "$EXPECTED_REPOSITORY" ]; then
  echo "Runner guard rejected an unexpected repository" >&2
  exit 1
fi
if [ "$event_name" != "workflow_dispatch" ] || [ "$ref" != "refs/heads/main" ]; then
  echo "Runner guard only accepts manual workflows from main" >&2
  exit 1
fi

expected_workflow_file=""
case "$role:$workflow" in
  test:Deploy\ Release\ Batch\ to\ Test|test:Deploy\ Trusted\ Release\ Control)
    if [ "$workflow" = "Deploy Release Batch to Test" ]; then
      expected_workflow_file=".github/workflows/deploy-test.yml"
    else
      expected_workflow_file=".github/workflows/deploy-release-control.yml"
    fi
    ;;
  production:Deploy\ Production\ \(Manual\))
    expected_workflow_file=".github/workflows/deploy.yml"
    ;;
  *)
    echo "Runner guard rejected workflow '$workflow' for role '$role'" >&2
    exit 1
    ;;
esac

expected_workflow_ref="$EXPECTED_REPOSITORY/$expected_workflow_file@refs/heads/main"
if [ "$workflow_ref" != "$expected_workflow_ref" ]; then
  echo "Runner guard rejected workflow ref '$workflow_ref'" >&2
  exit 1
fi

echo "Runner guard accepted the trusted $role workflow from main"
