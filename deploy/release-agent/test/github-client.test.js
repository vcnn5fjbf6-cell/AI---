import assert from "node:assert/strict";
import test from "node:test";

import { GitHubClient } from "../src/github-client.js";
import { SHA } from "../test-support/helpers.js";

test("main promotion uses the Git ref API with force=false and the exact tested SHA", async () => {
  let request;
  const client = new GitHubClient({
    owner: "example-owner",
    repo: "example-repo",
    token: "test-token",
    fetchImpl: async (url, options) => {
      request = { url, options };
      return new Response(JSON.stringify({ object: { sha: SHA.release } }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    },
  });

  assert.equal(await client.updateBranchRefFastForward("main", SHA.release), SHA.release);
  assert.match(request.url, /\/git\/refs\/heads\/main$/);
  assert.equal(request.options.method, "PATCH");
  assert.deepEqual(JSON.parse(request.options.body), { sha: SHA.release, force: false });
});

test("release pull request is closed without merging it", async () => {
  let request;
  const client = new GitHubClient({
    owner: "example-owner",
    repo: "example-repo",
    token: "test-token",
    fetchImpl: async (url, options) => {
      request = { url, options };
      return new Response(JSON.stringify({ number: 42, state: "closed" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    },
  });

  await client.closePullRequest(42);
  assert.match(request.url, /\/pulls\/42$/);
  assert.equal(request.options.method, "PATCH");
  assert.deepEqual(JSON.parse(request.options.body), { state: "closed" });
});

test("candidate verification selects only the exact base-bound commit status", async () => {
  const expectedContext = `Verify pull request build @ ${SHA.main}`;
  const client = new GitHubClient({
    owner: "example-owner",
    repo: "example-repo",
    token: "test-token",
    fetchImpl: async () => new Response(JSON.stringify([
      { id: 8, context: expectedContext, state: "success" },
      { id: 9, context: "Verify pull request build", state: "success" },
      { id: 7, context: expectedContext, state: "failure" },
    ]), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }),
  });

  const status = await client.getHeadStatus(SHA.head, expectedContext);
  assert.equal(status.id, 8);
  assert.equal(status.state, "success");
});

test("workflow run listing filters by the deterministic batch display title", async () => {
  const expectedTitle = `Release test 123e4567-e89b-42d3-a456-426614174000 ${SHA.release}`;
  const client = new GitHubClient({
    owner: "example-owner",
    repo: "example-repo",
    token: "test-token",
    fetchImpl: async () => new Response(JSON.stringify({
      workflow_runs: [
        { id: 1, display_title: expectedTitle },
        { id: 2, display_title: "unrelated run" },
      ],
    }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }),
  });

  assert.deepEqual(await client.listWorkflowRuns("deploy-test.yml", "main", expectedTitle), [
    { id: 1, display_title: expectedTitle },
  ]);
});

test("pull request file listing follows pagination and returns exact paths", async () => {
  const requests = [];
  const firstPage = Array.from({ length: 100 }, (_, index) => ({ filename: `src/file-${index}.js` }));
  const client = new GitHubClient({
    owner: "example-owner",
    repo: "example-repo",
    token: "test-token",
    fetchImpl: async (url) => {
      requests.push(url);
      const page = new URL(url).searchParams.get("page");
      return new Response(JSON.stringify(page === "1" ? firstPage : [{
        filename: "docs/renamed-deploy.yml",
        previous_filename: ".github/workflows/deploy.yml",
      }]), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    },
  });

  const files = await client.listPullRequestFiles(7);
  assert.equal(files.length, 101);
  assert.deepEqual(files.at(-1), {
    filename: "docs/renamed-deploy.yml",
    previousFilename: ".github/workflows/deploy.yml",
  });
  assert.equal(requests.length, 2);
});
