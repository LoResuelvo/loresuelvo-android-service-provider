const SENSITIVE_PATTERNS = [
  // Private keys
  {
    regex: /-----BEGIN [A-Z0-9_-]+ PRIVATE KEY-----[\s\S]*?-----END [A-Z0-9_-]+ PRIVATE KEY-----/g,
    replace: () => "[REDACTED_PRIVATE_KEY]",
  },
  // JWT tokens: eyJ...
  {
    regex: /\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}(?:\.[A-Za-z0-9_-]+)?\b/g,
    replace: () => "[REDACTED_JWT]",
  },
  // Bearer tokens
  {
    regex: /(Bearer\s+)[A-Za-z0-9\-._~+/]+=*/gi,
    replace: (_match, prefix) => `${prefix}[REDACTED]`,
  },
  // GitHub tokens (classic and fine-grained)
  {
    regex: /\b(?:gh[pousr]_[A-Za-z0-9_]{16,}|github_pat_[A-Za-z0-9_]{22,})\b/g,
    replace: () => "[REDACTED_GITHUB_TOKEN]",
  },
  // AWS access key ID
  {
    regex: /\bAKIA[0-9A-Z]{16}\b/g,
    replace: () => "[REDACTED_AWS_KEY]",
  },
  // Generic API keys and OpenAI keys
  {
    regex: /\bsk-[a-zA-Z0-9_-]{20,}\b/g,
    replace: () => "[REDACTED_API_KEY]",
  },
  // Key-value pairs for passwords, secrets, tokens: password=secret123, api_key: "abc"
  {
    regex: /((?:password|passwd|secret|api[_-]?key|token|auth0_client_secret|auth0_token|private[_-]?key)\s*[:=]\s*["']?)([^"'\s,\r\n&]{6,})(["']?)/gi,
    replace: (_match, prefix, _val, suffix) => `${prefix}[REDACTED]${suffix || ""}`,
  },
];

/**
 * Redacts sensitive credentials, tokens, and secrets from text.
 * Suitable for both process logs and short summary lines.
 */
export function redactSecrets(input) {
  if (typeof input !== "string") return input;
  let result = input;
  for (const { regex, replace } of SENSITIVE_PATTERNS) {
    result = result.replace(regex, replace);
  }
  return result;
}
