import fs from "node:fs";
import path from "node:path";
import { findRepoRoot } from "./repo-root.mjs";

const schemaCache = new Map();

function loadSchema(repoRoot, schemaFileName) {
  const absolutePath = path.resolve(repoRoot, ".delivery", "schemas", schemaFileName);
  const cacheKey = `${path.resolve(repoRoot)}:${schemaFileName}`;
  try {
    const stat = fs.statSync(absolutePath);
    const cached = schemaCache.get(cacheKey);
    if (cached && cached.mtimeMs === stat.mtimeMs) return cached.parsed;
    const content = fs.readFileSync(absolutePath, "utf8");
    const parsed = JSON.parse(content);
    schemaCache.set(cacheKey, { mtimeMs: stat.mtimeMs, parsed });
    return parsed;
  } catch {
    const content = fs.readFileSync(absolutePath, "utf8");
    const parsed = JSON.parse(content);
    schemaCache.set(cacheKey, { mtimeMs: 0, parsed });
    return parsed;
  }
}

function checkType(value, expectedType) {
  if (Array.isArray(expectedType)) {
    return expectedType.some((t) => checkType(value, t));
  }
  if (expectedType === "null") return value === null;
  if (expectedType === "array") return Array.isArray(value);
  if (expectedType === "integer") return typeof value === "number" && Number.isInteger(value);
  if (expectedType === "string") return typeof value === "string";
  if (expectedType === "boolean") return typeof value === "boolean";
  if (expectedType === "object") return value !== null && typeof value === "object" && !Array.isArray(value);
  return true;
}

function validateNode(value, schema, pathStr = "") {
  const errors = [];
  if (!schema || typeof schema !== "object") return errors;

  if (Array.isArray(schema.oneOf)) {
    const candidates = schema.oneOf.map((candidate) => validateNode(value, candidate, pathStr));
    const matchingCandidates = candidates.filter((candidateErrors) => candidateErrors.length === 0);
    if (matchingCandidates.length !== 1) {
      errors.push(
        `${pathStr || "root"}: expected exactly one oneOf schema to match, got ${matchingCandidates.length}`
      );
      return errors;
    }
  }

  if (Array.isArray(schema.anyOf)) {
    const hasMatch = schema.anyOf.some(
      (candidate) => validateNode(value, candidate, pathStr).length === 0
    );
    if (!hasMatch) {
      errors.push(`${pathStr || "root"}: no anyOf schema matched`);
      return errors;
    }
  }

  if (Object.prototype.hasOwnProperty.call(schema, "const") && value !== schema.const) {
    errors.push(`${pathStr || "root"}: expected constant ${JSON.stringify(schema.const)}`);
  }

  if (schema.type) {
    if (!checkType(value, schema.type)) {
      errors.push(`${pathStr || "root"}: expected type ${JSON.stringify(schema.type)}, got ${value === null ? "null" : typeof value}`);
      return errors;
    }
  }

  if (schema.enum && !schema.enum.includes(value)) {
    errors.push(`${pathStr || "root"}: value ${JSON.stringify(value)} is not in enum ${JSON.stringify(schema.enum)}`);
  }

  if (typeof value === "number") {
    if (schema.minimum !== undefined && value < schema.minimum) {
      errors.push(`${pathStr || "root"}: ${value} is less than minimum ${schema.minimum}`);
    }
    if (schema.maximum !== undefined && value > schema.maximum) {
      errors.push(`${pathStr || "root"}: ${value} is greater than maximum ${schema.maximum}`);
    }
  }

  if (typeof value === "string") {
    if (schema.minLength !== undefined && value.length < schema.minLength) {
      errors.push(`${pathStr || "root"}: length ${value.length} is less than minLength ${schema.minLength}`);
    }
    if (schema.maxLength !== undefined && value.length > schema.maxLength) {
      errors.push(`${pathStr || "root"}: length ${value.length} exceeds maxLength ${schema.maxLength}`);
    }
    if (schema.pattern !== undefined && !new RegExp(schema.pattern).test(value)) {
      errors.push(`${pathStr || "root"}: value does not match pattern ${schema.pattern}`);
    }
  }

  if (Array.isArray(value)) {
    if (schema.maxItems !== undefined && value.length > schema.maxItems) {
      errors.push(`${pathStr || "root"}: array length ${value.length} exceeds maxItems ${schema.maxItems}`);
    }
    if (schema.minItems !== undefined && value.length < schema.minItems) {
      errors.push(`${pathStr || "root"}: array length ${value.length} is less than minItems ${schema.minItems}`);
    }
    if (schema.uniqueItems) {
      const serialized = value.map((item) => JSON.stringify(item));
      if (new Set(serialized).size !== serialized.length) {
        errors.push(`${pathStr || "root"}: array items must be unique`);
      }
    }
    if (schema.items) {
      for (let i = 0; i < value.length; i++) {
        errors.push(...validateNode(value[i], schema.items, `${pathStr}[${i}]`));
      }
    }
  }

  if (value && typeof value === "object" && !Array.isArray(value)) {
    const objectKeys = Object.keys(value);
    if (schema.minProperties !== undefined && objectKeys.length < schema.minProperties) {
      errors.push(
        `${pathStr || "root"}: property count ${objectKeys.length} is less than minProperties ${schema.minProperties}`
      );
    }
    if (schema.maxProperties !== undefined && objectKeys.length > schema.maxProperties) {
      errors.push(
        `${pathStr || "root"}: property count ${objectKeys.length} exceeds maxProperties ${schema.maxProperties}`
      );
    }

    if (schema.required) {
      for (const requiredKey of schema.required) {
        if (!(requiredKey in value)) {
          errors.push(`${pathStr || "root"}: missing required property '${requiredKey}'`);
        }
      }
    }
    if (schema.properties) {
      for (const [key, propSchema] of Object.entries(schema.properties)) {
        if (key in value && value[key] !== undefined) {
          errors.push(...validateNode(value[key], propSchema, pathStr ? `${pathStr}.${key}` : key));
        }
      }
    }

    const declaredProperties = new Set(Object.keys(schema.properties || {}));
    const patternEntries = Object.entries(schema.patternProperties || {}).map(
      ([pattern, childSchema]) => [new RegExp(pattern), childSchema]
    );

    for (const key of objectKeys) {
      if (declaredProperties.has(key)) continue;
      const matchingPatterns = patternEntries.filter(([regex]) => regex.test(key));
      if (matchingPatterns.length > 0) {
        for (const [, childSchema] of matchingPatterns) {
          errors.push(
            ...validateNode(value[key], childSchema, pathStr ? `${pathStr}.${key}` : key)
          );
        }
        continue;
      }

      if (schema.additionalProperties === false) {
        errors.push(`${pathStr || "root"}: unexpected property '${key}'`);
      } else if (
        schema.additionalProperties &&
        typeof schema.additionalProperties === "object"
      ) {
        errors.push(
          ...validateNode(
            value[key],
            schema.additionalProperties,
            pathStr ? `${pathStr}.${key}` : key
          )
        );
      }
    }
  }

  return errors;
}

export function validateAgainstSchema(value, schemaFileName, repoRoot = findRepoRoot()) {
  const schema = loadSchema(repoRoot, schemaFileName);
  const errors = validateNode(value, schema);
  if (errors.length > 0) {
    throw new Error(`Schema validation failed against ${schemaFileName}:\n  ${errors.slice(0, 10).join("\n  ")}`);
  }
  return true;
}

export function validateInspectionResult(result, repoRoot) {
  return validateAgainstSchema(result, "inspection-result.schema.json", repoRoot);
}

export function validateExecutionResult(result, repoRoot) {
  return validateAgainstSchema(result, "execution-result.schema.json", repoRoot);
}

export function validateDeliveryContextResult(result, repoRoot) {
  return validateAgainstSchema(result, "delivery-context.schema.json", repoRoot);
}

export function validateCiInspectionResult(result, repoRoot) {
  return validateAgainstSchema(result, "ci-inspection-result.schema.json", repoRoot);
}
