#!/usr/bin/env bash

# Run a command with Node.js 24 discovered from an explicit override, the
# repository sibling toolchain, or PATH.  The wrapper always executes the
# original argument vector and never evaluates a command string.

set -euo pipefail

readonly SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly REPOSITORY_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd -P)"

die() {
  printf 'node-24: error: %s\n' "$*" >&2
  exit 1
}

node_major_version() {
  local node_binary="$1"
  local output major

  output="$("$node_binary" --version 2>&1)" || return 1
  if [[ "$output" =~ (^|[[:space:]])v?([0-9]+)([.][0-9]+)*([[:space:]]|$) ]]; then
    major="${BASH_REMATCH[2]}"
  else
    return 1
  fi

  [[ "$major" =~ ^[0-9]+$ ]] || return 1
  printf '%s' "$major"
}

resolve_executable() {
  local candidate="$1"
  local resolved

  if [[ "$candidate" == */* ]]; then
    [[ -f "$candidate" && -x "$candidate" ]] || return 1
    resolved="$(readlink -f -- "$candidate" 2>/dev/null || true)"
    [[ -n "$resolved" && -x "$resolved" ]] || resolved="$candidate"
  else
    resolved="$(command -v "$candidate" 2>/dev/null || true)"
    [[ -n "$resolved" && -x "$resolved" ]] || return 1
    resolved="$(readlink -f -- "$resolved" 2>/dev/null || printf '%s' "$resolved")"
  fi

  [[ -f "$resolved" && -x "$resolved" ]] || return 1
  printf '%s' "$resolved"
}

describe_node_failure() {
  local candidate="$1"
  local resolved major

  if ! resolved="$(resolve_executable "$candidate")"; then
    if [[ "$candidate" == */* ]]; then
      die "DELIVERY_NODE points to '$candidate', but that path is not an executable file. Node.js 24 is required."
    fi
    die "Node.js command '$candidate' was not found on PATH. Node.js 24 is required."
  fi

  if ! major="$(node_major_version "$resolved")"; then
    die "Node.js at '$resolved' did not report a parseable version. Node.js 24 is required."
  fi
  die "Node.js $major was found at '$resolved'; Node.js 24 is required."
}

add_unique_candidate() {
  local candidate="$1"
  local existing

  [[ -n "$candidate" ]] || return 0
  for existing in "${CANDIDATES[@]}"; do
    [[ "$existing" == "$candidate" ]] && return 0
  done
  CANDIDATES+=("$candidate")
}

discover_node() {
  local override="${1:-}"
  local candidate resolved major path_entry first_path_node
  local -a path_entries

  if [[ -n "$override" ]]; then
    if ! resolved="$(resolve_executable "$override")"; then
      describe_node_failure "$override"
    fi
    if ! major="$(node_major_version "$resolved")"; then
      die "Node.js at '$resolved' did not report a parseable version. Node.js 24 is required."
    fi
    [[ "$major" == "24" ]] || die "Node.js $major was found at '$resolved'; Node.js 24 is required."
    printf '%s' "$resolved"
    return 0
  fi

  CANDIDATES=()

  # Prefer a repository sibling so a checkout can use its provisioned
  # toolchain even when an older Node is first on the user's PATH.
  for candidate in "$REPOSITORY_ROOT/../.toolchains"/node-v24*/bin/node; do
    [[ -x "$candidate" ]] || continue
    add_unique_candidate "$candidate"
  done

  # Search every PATH entry.  This allows a later Node 24 entry to win over an
  # older Node installation while still producing a useful wrong-major error
  # when PATH contains no compatible binary.
  first_path_node=""
  IFS=: read -r -a path_entries <<< "${PATH:-}"
  for path_entry in "${path_entries[@]}"; do
    [[ -n "$path_entry" ]] || path_entry="."
    candidate="$path_entry/node"
    [[ -x "$candidate" ]] || continue
    [[ -n "$first_path_node" ]] || first_path_node="$candidate"
    add_unique_candidate "$candidate"
  done

  for candidate in "${CANDIDATES[@]}"; do
    resolved="$(resolve_executable "$candidate" 2>/dev/null || true)"
    [[ -n "$resolved" ]] || continue
    if major="$(node_major_version "$resolved" 2>/dev/null || true)"; then
      if [[ "$major" == "24" ]]; then
        printf '%s' "$resolved"
        return 0
      fi
    fi
  done

  if [[ -n "$first_path_node" ]]; then
    describe_node_failure "$first_path_node"
  fi

  die "Node.js 24 was not found. Put Node.js 24 on PATH or place it under '../.toolchains/node-v24*/bin/node'."
}

prepend_node_path() {
  local node_binary="$1"
  local node_bin_dir

  node_bin_dir="$(CDPATH= cd -- "$(dirname -- "$node_binary")" && pwd -P)"
  if [[ -n "${PATH:-}" ]]; then
    PATH="$node_bin_dir:$PATH"
  else
    PATH="$node_bin_dir"
  fi
  export PATH
}

validate_command() {
  local command_name="$1"
  local resolved major

  if [[ "$command_name" == */* ]]; then
    [[ -f "$command_name" && -x "$command_name" ]] || die "command '$command_name' is not an executable file."
    resolved="$(readlink -f -- "$command_name" 2>/dev/null || printf '%s' "$command_name")"
  else
    resolved="$(command -v "$command_name" 2>/dev/null || true)"
    [[ -n "$resolved" ]] || die "command '$command_name' was not found on PATH."
    resolved="$(readlink -f -- "$resolved" 2>/dev/null || printf '%s' "$resolved")"
  fi

  # Make and hooks normally invoke `node` by name.  Validate an explicitly
  # supplied node path too, so a wrong-major DELIVERY_NODE cannot slip past
  # the discovery check.
  case "$(basename -- "$resolved")" in
    node|nodejs)
      if ! major="$(node_major_version "$resolved")"; then
        die "Node.js at '$resolved' did not report a parseable version. Node.js 24 is required."
      fi
      [[ "$major" == "24" ]] || die "Node.js $major was found at '$resolved'; Node.js 24 is required."
      ;;
  esac
}

override="${DELIVERY_NODE:-}"
if [[ "${1:-}" == "--node" ]]; then
  [[ $# -ge 2 ]] || die "--node requires a Node.js executable or command name."
  override="$2"
  shift 2
fi
if [[ "${1:-}" == "--" ]]; then
  shift
fi

if [[ $# -eq 0 ]]; then
  printf 'Usage: %s [--node <path-or-command>] <command> [argument ...]\n' "$(basename "$0")" >&2
  printf 'Run a command with Node.js 24 discovery.\n' >&2
  exit 64
fi

NODE_BINARY="$(discover_node "$override")"
prepend_node_path "$NODE_BINARY"
validate_command "$1"

# Preserve every argument as an argv element.  In particular, strings such as
# '$(touch /tmp/file)' and 'a; rm -rf ...' are never interpreted by a shell.
exec -- "$@"
