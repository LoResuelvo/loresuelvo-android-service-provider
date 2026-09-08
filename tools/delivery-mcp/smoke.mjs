import assert from "node:assert/strict";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { server } from "./server.mjs";

async function runSmokeTest() {
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: "delivery-smoke-test", version: "1.0.0" });

  try {
    await Promise.all([server.connect(serverTransport), client.connect(clientTransport)]);
    const toolsResult = await client.listTools();
    const inspectTool = toolsResult.tools.find((tool) => tool.name === "delivery_inspect");
    const prepareTool = toolsResult.tools.find((tool) => tool.name === "delivery_prepare");
    const ciTool = toolsResult.tools.find((tool) => tool.name === "delivery_ci_inspect");
    const finalizeTool = toolsResult.tools.find((tool) => tool.name === "delivery_finalize");
    const waitTool = toolsResult.tools.find((tool) => tool.name === "delivery_job_wait");
    const cancelTool = toolsResult.tools.find((tool) => tool.name === "delivery_job_cancel");
    const verifyHeadTool = toolsResult.tools.find((tool) => tool.name === "delivery_verify_head");
    const testTool = toolsResult.tools.find((tool) => tool.name === "delivery_test");
    assert.ok(inspectTool, "delivery_inspect tool is registered");
    assert.ok(prepareTool, "delivery_prepare tool is registered");
    assert.ok(ciTool, "delivery_ci_inspect tool is registered");
    assert.ok(finalizeTool, "delivery_finalize tool is registered");
    assert.ok(waitTool, "delivery_job_wait tool is registered");
    assert.ok(cancelTool, "delivery_job_cancel tool is registered");
    assert.ok(verifyHeadTool, "delivery_verify_head tool is registered");
    assert.ok(testTool, "delivery_test tool is registered");
    assert.deepStrictEqual(testTool.inputSchema.properties.executionMode.enum, ["sync", "job", "auto"]);
    assert.strictEqual(testTool.inputSchema.properties.async.type, "boolean");

    const testCallResult = await client.callTool({
      name: "delivery_test",
      arguments: { mode: "affected", executionMode: "sync" },
    });
    assert.ok(testCallResult.content?.[0]?.text, "delivery_test result text present");
    const parsedTest = JSON.parse(testCallResult.content[0].text);
    assert.ok(
      ["passed", "failed", "error", "blocked", "timed_out", "no_changes"].includes(parsedTest.status),
      `Invalid test status: ${parsedTest.status}`
    );
    assert.strictEqual(parsedTest.mode, "affected");
    assert.strictEqual(typeof parsedTest.cached, "boolean");
    assert.strictEqual(typeof parsedTest.durationMs, "number");
    assert.ok(parsedTest.counts && typeof parsedTest.counts.passed === "number");

    const callResult = await client.callTool({
      name: "delivery_inspect",
      arguments: { intent: "prepare_commit" },
    });
    assert.ok(callResult.content?.[0]?.text, "Result text present");
    const parsed = JSON.parse(callResult.content[0].text);
    assert.strictEqual(parsed.schemaVersion, 1);
    assert.ok(
      ["ready", "review_required", "blocked", "needs_input", "no_changes"].includes(parsed.status),
      `Invalid status: ${parsed.status}`
    );
    assert.match(parsed.snapshotHash, /^[a-f0-9]{64}$/);
    assert.ok(parsed.repository && typeof parsed.repository.branch === "string");
    assert.ok(parsed.gate && Array.isArray(parsed.gate.checkIds));
    assert.ok(parsed.maintainability && Array.isArray(parsed.maintainability.filesReviewed));
    assert.ok(Array.isArray(parsed.diagnostics));

    console.log(`Smoke passed: tools=${toolsResult.tools.length} status=${parsed.status} gate=${parsed.gate.id}`);
  } finally {
    await client.close();
  }
}

runSmokeTest().catch((error) => {
  console.error(`Smoke failed: ${String(error.message || "unknown").split("\n")[0]}`);
  process.exitCode = 1;
});
