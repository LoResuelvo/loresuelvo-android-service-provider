import { execFile } from "node:child_process";
import { promisify } from "node:util";

const execute = promisify(execFile);

export async function checkAndroidDevice({ repoRoot, run = execute, serial = process.env.ANDROID_SERIAL } = {}) {
  const startedAt = performance.now();
  let code = "ANDROID_DEVICE_UNAVAILABLE";
  let message = "Connect and authorize an Android device or emulator before running this gate";
  let available = false;
  try {
    const { stdout } = await run("scripts/with-android-env.sh", ["adb", "devices"], {
      cwd: repoRoot, timeout: 10000, maxBuffer: 65536, encoding: "utf8",
    });
    const lines = stdout.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
    if (!lines.includes("List of devices attached")) throw new Error("Invalid ADB response");
    const devices = lines.filter((line) => !line.startsWith("List of devices attached") && !line.startsWith("*"))
      .map((line) => line.split(/\s+/));
    const selected = serial ? devices.filter(([id]) => id === serial) : devices;
    available = selected.length > 0 && selected.every((entry) => entry.length === 2 && entry[1] === "device");
  } catch {
    code = "ANDROID_DEVICE_QUERY_FAILED";
    message = "Cannot query Android devices through the repository toolchain wrapper";
  }
  return {
    id: "android_device", status: available ? "passed" : "blocked",
    durationMs: Math.round(performance.now() - startedAt), exitCode: null,
    summaryLines: available ? [] : [message], locations: [],
    diagnostic: available ? null : { code, checkId: "android_device", message, retryable: true },
  };
}
