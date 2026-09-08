import fs from "node:fs/promises";
import path from "node:path";
import { execFile, execFileSync } from "node:child_process";
import { promisify } from "node:util";
import { assertSafeRepoPath, findRepoRoot } from "./repo-root.mjs";
import { validateCiInspectionResult } from "./validate-schema.mjs";
import { redactSecrets } from "./redact-secrets.mjs";
import { summarizeFailureOutput } from "./execute-check.mjs";

const execFileAsync = promisify(execFile);

export const CI_RUNTIME_DIR = ".delivery/runtime/ci";

function repositoryFromRemote(repoRoot) {
  try {
    const remote = execFileSync("git", ["config", "--get", "remote.origin.url"], {
      cwd: repoRoot,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
    }).trim();
    const match = remote.match(/github\.com[/:]([^/]+\/[^/.]+)(?:\.git)?$/i);
    return match ? match[1] : null;
  } catch {
    return null;
  }
}

export function resolveRepository({ repo = null, repoRoot = null } = {}) {
  if (typeof repo === "string" && repo.trim()) return repo.trim();
  if (process.env.GITHUB_REPOSITORY?.trim()) return process.env.GITHUB_REPOSITORY.trim();
  return repositoryFromRemote(repoRoot || findRepoRoot());
}

export class CiProvider {
  async inspectCommit(sha, options = {}) {
    throw new Error("CiProvider.inspectCommit must be implemented by subclasses");
  }
}

export class MockCiProvider extends CiProvider {
  constructor(fixtures = {}) {
    super();
    this.fixtures = new Map(Object.entries(fixtures));
  }

  setFixture(sha, data) {
    this.fixtures.set(sha, data);
  }

  async inspectCommit(sha, { repoRoot } = {}) {
    const root = findRepoRoot(repoRoot);
    if (this.fixtures.has(sha)) {
      const fix = this.fixtures.get(sha);
      const res = {
        schemaVersion: 1,
        sha,
        workflow: fix.workflow || { id: 1001, name: "CI" },
        status: fix.status || "passed",
        failedJobs: fix.failedJobs || [],
        failure: fix.failure || null,
        url: fix.url || `https://github.com/${resolveRepository({ repoRoot: root }) || "LoResuelvo/loresuelvo-android-service-provider"}/actions/runs/${fix.workflow?.id || 1001}`,
        retryable: Boolean(
          fix.retryable ?? ["failed", "timed_out", "cancelled", "provider_error"].includes(fix.status)
        ),
      };

      if (res.failure?.excerpt) {
        await saveCiExcerpt({ repoRoot: root, sha, excerpt: res.failure.excerpt });
      }
      validateCiInspectionResult(res, root);
      return res;
    }

    const notFound = {
      schemaVersion: 1,
      sha,
      workflow: null,
      status: "not_found",
      failedJobs: [],
      failure: null,
      url: null,
      retryable: false,
    };
    validateCiInspectionResult(notFound, root);
    return notFound;
  }
}

export class GitHubActionsProvider extends CiProvider {
  constructor({ repo = null, repoRoot = null, token = null } = {}) {
    super();
    this.repo = resolveRepository({ repo, repoRoot });
    this.repoError = this.repo ? null : "Unable to resolve the GitHub repository. Set GITHUB_REPOSITORY or configure origin.";
    this.token = token || process.env.GITHUB_TOKEN || process.env.GH_TOKEN || null;
  }

  async inspectCommit(sha, { repoRoot } = {}) {
    const root = findRepoRoot(repoRoot);
    if (!this.repo) {
      return this.providerErrorResult(sha, this.repoError, root);
    }

    // Try gh CLI first
    let ghResult = null;
    try {
      ghResult = await this.queryViaGhCli(sha, root);
    } catch {
      // Fall back to the API when gh is unavailable or unauthenticated.
    }
    if (ghResult) {
      validateCiInspectionResult(ghResult, root);
      return ghResult;
    }

    // Try GitHub API via fetch if token available
    if (this.token) {
      try {
        const apiResult = await this.queryViaApi(sha, root);
        if (apiResult) {
          validateCiInspectionResult(apiResult, root);
          return apiResult;
        }
      } catch (err) {
        return this.providerErrorResult(sha, `GitHub API query failed: ${err.message}`, root);
      }
    }

    // If neither gh CLI nor token available, report provider_error without throwing
    return this.providerErrorResult(
      sha,
      "No GitHub credentials available (gh CLI not authenticated and GITHUB_TOKEN not set)",
      root
    );
  }

  async queryViaGhCli(sha, repoRoot) {
    const args = [
      "run",
      "list",
      "--commit",
      sha,
      "--json",
      "databaseId,name,status,conclusion,url",
      "--limit",
      "1",
    ];

    const { stdout } = await execFileAsync("gh", args, {
      cwd: repoRoot,
      encoding: "utf8",
      timeout: 10000,
    });

    const parsed = JSON.parse(stdout || "[]");
    if (!parsed || parsed.length === 0) {
      return {
        schemaVersion: 1,
        sha,
        workflow: null,
        status: "not_found",
        failedJobs: [],
        failure: null,
        url: null,
        retryable: false,
      };
    }

    const run = parsed[0];
    const result = this.normalizeRun(sha, run, repoRoot);
    if (["failed", "timed_out"].includes(result.status)) {
      return this.enrichFailureViaGh(result, repoRoot);
    }
    return result;
  }

  async queryViaApi(sha, repoRoot) {
    const url = `https://api.github.com/repos/${this.repo}/actions/runs?head_sha=${sha}&per_page=1`;
    const response = await fetch(url, {
      headers: {
        Authorization: `token ${this.token}`,
        Accept: "application/vnd.github.v3+json",
        "User-Agent": "loresuelvo-delivery-ci",
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    const data = await response.json();
    if (!data.workflow_runs || data.workflow_runs.length === 0) {
      return {
        schemaVersion: 1,
        sha,
        workflow: null,
        status: "not_found",
        failedJobs: [],
        failure: null,
        url: null,
        retryable: false,
      };
    }

    const run = data.workflow_runs[0];
    const result = this.normalizeRun(
      sha,
      {
        databaseId: run.id,
        name: run.name,
        status: run.status,
        conclusion: run.conclusion,
        url: run.html_url,
      },
      repoRoot
    );
    if (["failed", "timed_out"].includes(result.status)) {
      return this.enrichFailureViaApi(result, repoRoot);
    }
    return result;
  }

  failureFromJobs(jobs = []) {
    const failingConclusions = new Set([
      "failure",
      "timed_out",
      "cancelled",
      "action_required",
      "startup_failure",
    ]);
    const failedJobs = jobs.filter((job) => failingConclusions.has(job.conclusion));
    if (failedJobs.length === 0) return null;

    const firstJob = failedJobs[0];
    const firstStep = (firstJob.steps || []).find((step) =>
      failingConclusions.has(step.conclusion)
    );
    const message = firstStep
      ? `Job '${firstJob.name}' failed at step '${firstStep.name}'`
      : `Job '${firstJob.name}' failed`;
    const excerpt = [
      `Job: ${firstJob.name}`,
      ...(firstStep ? [`Step: ${firstStep.name}`] : []),
      `Conclusion: ${firstStep?.conclusion || firstJob.conclusion || "failure"}`,
    ].join("\n");

    return {
      failedJobs: failedJobs.map((job) => job.name || `job-${job.databaseId || job.id}`),
      firstJobId: firstJob.databaseId || firstJob.id || null,
      failure: { message, excerpt },
    };
  }

  async enrichFailureViaGh(result, repoRoot) {
    try {
      const runId = String(result.workflow.id);
      const { stdout } = await execFileAsync("gh", ["run", "view", runId, "--json", "jobs"], {
        cwd: repoRoot,
        encoding: "utf8",
        timeout: 10000,
        maxBuffer: 2 * 1024 * 1024,
      });
      const details = this.failureFromJobs(JSON.parse(stdout || "{}").jobs || []);
      if (!details) return result;

      let excerpt = details.failure.excerpt;
      if (details.firstJobId) {
        try {
          const logResult = await execFileAsync(
            "gh",
            ["run", "view", runId, "--job", String(details.firstJobId), "--log-failed"],
            {
              cwd: repoRoot,
              encoding: "utf8",
              timeout: 15000,
              maxBuffer: 2 * 1024 * 1024,
            }
          );
          const lines = summarizeFailureOutput(logResult.stdout, 6);
          if (lines.length > 0) excerpt = lines.join("\n");
        } catch {
          // Job and step metadata still provide a bounded diagnostic.
        }
      }

      const enriched = {
        ...result,
        failedJobs: details.failedJobs,
        failure: { ...details.failure, excerpt: redactSecrets(excerpt) },
      };
      await saveCiExcerpt({ repoRoot, sha: result.sha, excerpt: enriched.failure.excerpt });
      return enriched;
    } catch {
      return result;
    }
  }

  async enrichFailureViaApi(result, repoRoot) {
    try {
      const jobsUrl = `https://api.github.com/repos/${this.repo}/actions/runs/${result.workflow.id}/jobs?filter=latest&per_page=100`;
      const jobsResponse = await fetch(jobsUrl, {
        headers: {
          Authorization: `token ${this.token}`,
          Accept: "application/vnd.github.v3+json",
          "User-Agent": "loresuelvo-delivery-ci",
        },
      });
      if (!jobsResponse.ok) return result;
      const details = this.failureFromJobs((await jobsResponse.json()).jobs || []);
      if (!details) return result;

      let excerpt = details.failure.excerpt;
      if (details.firstJobId) {
        const annotationsUrl = `https://api.github.com/repos/${this.repo}/check-runs/${details.firstJobId}/annotations?per_page=10`;
        const annotationsResponse = await fetch(annotationsUrl, {
          headers: {
            Authorization: `token ${this.token}`,
            Accept: "application/vnd.github.v3+json",
            "User-Agent": "loresuelvo-delivery-ci",
          },
        });
        if (annotationsResponse.ok) {
          const annotations = await annotationsResponse.json();
          const lines = annotations
            .filter((annotation) => annotation.annotation_level === "failure")
            .slice(0, 6)
            .map((annotation) =>
              `${annotation.path || "CI"}${annotation.start_line ? `:${annotation.start_line}` : ""}: ${annotation.message || annotation.title || "failure"}`
            );
          if (lines.length > 0) excerpt = lines.join("\n");
        }
      }

      const enriched = {
        ...result,
        failedJobs: details.failedJobs,
        failure: { ...details.failure, excerpt: redactSecrets(excerpt) },
      };
      await saveCiExcerpt({ repoRoot, sha: result.sha, excerpt: enriched.failure.excerpt });
      return enriched;
    } catch {
      return result;
    }
  }

  normalizeRun(sha, run, repoRoot) {
    const rawStatus = run.status;
    const rawConclusion = run.conclusion;
    let status = "in_progress";
    let retryable = false;

    if (rawStatus === "queued" || rawStatus === "waiting") {
      status = "queued";
    } else if (rawStatus === "in_progress") {
      status = "in_progress";
    } else if (rawStatus === "completed") {
      if (rawConclusion === "success") {
        status = "passed";
      } else if (rawConclusion === "failure") {
        status = "failed";
        retryable = true;
      } else if (rawConclusion === "cancelled") {
        status = "cancelled";
        retryable = true;
      } else if (rawConclusion === "timed_out") {
        status = "timed_out";
        retryable = true;
      } else {
        status = "failed";
        retryable = true;
      }
    }

    let failure = null;
    let failedJobs = [];
    if (status === "failed" || status === "timed_out") {
      failedJobs = [run.name || "ci"];
      const excerpt = `Run ${run.databaseId} concluded with '${rawConclusion || "failure"}'`;
      failure = {
        message: `Workflow '${run.name}' failed on commit ${sha.slice(0, 8)}`,
        excerpt,
      };
    }

    return {
      schemaVersion: 1,
      sha,
      workflow: run.databaseId ? { id: run.databaseId, name: run.name || "CI" } : null,
      status,
      failedJobs,
      failure,
      url: run.url || null,
      retryable,
    };
  }

  providerErrorResult(sha, message, repoRoot) {
    const safeMessage = redactSecrets(String(message || "CI provider unavailable")).split("\n")[0];
    const res = {
      schemaVersion: 1,
      sha,
      workflow: null,
      status: "provider_error",
      failedJobs: [],
      failure: {
        message: safeMessage,
        excerpt: safeMessage,
      },
      url: null,
      retryable: true,
    };
    validateCiInspectionResult(res, repoRoot);
    return res;
  }
}

export async function saveCiExcerpt({ repoRoot, sha, excerpt } = {}) {
  const root = findRepoRoot(repoRoot);
  const targetDir = path.resolve(root, CI_RUNTIME_DIR);
  assertSafeRepoPath(root, CI_RUNTIME_DIR, "CI runtime directory");

  await fs.mkdir(targetDir, { recursive: true, mode: 0o700 });
  const filePath = path.join(targetDir, `${sha}.log`);
  const safeExcerpt = redactSecrets(String(excerpt || "")).slice(0, 20000);
  await fs.writeFile(filePath, `${safeExcerpt}\n`, { mode: 0o600 });
  return filePath;
}

let activeProviderInstance = null;

export function setCiProvider(provider) {
  activeProviderInstance = provider;
}

export function getCiProvider({ repoRoot = null } = {}) {
  if (activeProviderInstance) return activeProviderInstance;
  return new GitHubActionsProvider({ repoRoot });
}

export async function inspectCi({ sha, repoRoot, provider = null } = {}) {
  const root = findRepoRoot(repoRoot);
  if (!sha || typeof sha !== "string") {
    throw new Error("Missing required commit SHA for CI inspection");
  }

  const ciProvider = provider || getCiProvider({ repoRoot: root });
  return ciProvider.inspectCommit(sha.trim().toLowerCase(), { repoRoot: root });
}
