import path from "node:path";

export function normalizePath(filePath) {
  return String(filePath || "").replaceAll("\\", "/").replace(/^\.\//, "");
}

export function isProductionSourceFile(normalizedPath) {
  const normalized = normalizePath(normalizedPath);
  if (!/^app\/src\/main\//.test(normalized)) return false;
  if (!/\.(?:kt|java)$/.test(normalized)) return false;
  if (/(?:^|\/)build\/|(?:^|\/)generated\//.test(normalized)) return false;
  return true;
}

const HUMAN_ONLY_PATTERNS = [/^\.github\/workflows(?:\/|$)/];

export function isHumanOnlyPath(filePath) {
  const normalized = normalizePath(filePath);
  return HUMAN_ONLY_PATTERNS.some((pattern) => pattern.test(normalized));
}

function matchesRule(normalized, match = {}) {
  if ((match.exact || []).includes(normalized)) return true;
  if ((match.prefixes || []).some((prefix) => normalized.startsWith(prefix))) return true;
  if ((match.extensions || []).some((extension) => normalized.endsWith(extension))) {
    return true;
  }
  return (match.patterns || []).some((pattern) => new RegExp(pattern).test(normalized));
}

function materializeClassification(definition, normalized) {
  const humanOnly = definition.category === "human_only" || isHumanOnlyPath(normalized);
  return {
    category: humanOnly ? "human_only" : definition.category,
    isGateCTrigger: Boolean(definition.isGateCTrigger),
    isGate0Trigger: Boolean(definition.isGate0Trigger),
    isHumanOnly: humanOnly,
    isProductSource:
      definition.productSource === "auto"
        ? isProductionSourceFile(normalized)
        : Boolean(definition.productSource),
  };
}

export function classifyFile(filePath, policy) {
  const normalized = normalizePath(filePath);
  const classification = policy?.classification;
  if (!classification?.rules || !classification.fallback) {
    throw new Error("Delivery policy does not define file classification rules");
  }

  const rule = classification.rules.find((candidate) => matchesRule(normalized, candidate.match));
  return materializeClassification(rule || classification.fallback, normalized);
}

export function isDeliveryControlPlanePath(filePath, policy) {
  return classifyFile(filePath, policy).category === "delivery_tooling";
}

export function classifyFiles(files = [], policy) {
  const result = {
    all: [],
    hasGateCTrigger: false,
    hasGate0Trigger: false,
    hasIsolatedProduction: false,
    hasDeliveryTooling: false,
    hasHumanOnly: false,
    hasOnlyGate0: false,
    hasOnlyDocsOrConfig: false,
    productFiles: [],
  };
  if (!files?.length) return result;

  let gate0Count = 0;
  let docsConfigCount = 0;
  for (const file of files) {
    const classification = classifyFile(file, policy);
    result.all.push({ file, ...classification });
    result.hasHumanOnly ||= classification.isHumanOnly;
    result.hasGateCTrigger ||= classification.isGateCTrigger;
    result.hasGate0Trigger ||= classification.isGate0Trigger;
    if (classification.isGate0Trigger) gate0Count += 1;
    if (classification.category === "isolated_domain_kotlin" || classification.category === "production_kotlin") {
      result.hasIsolatedProduction = true;
    }
    result.hasDeliveryTooling ||= classification.category === "delivery_tooling";
    if (classification.category === "non_code_docs_config") docsConfigCount += 1;
    if (classification.isProductSource) result.productFiles.push(file);
  }
  result.hasOnlyGate0 = gate0Count === files.length;
  result.hasOnlyDocsOrConfig = docsConfigCount === files.length;
  return result;
}
