#!/usr/bin/env bash

# Run an Android/Gradle command with the repository's preferred local
# toolchains.  The wrapper deliberately uses exec with the original argument
# vector; it never evaluates a command string.

set -euo pipefail

readonly SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly REPOSITORY_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd -P)"

die() {
  printf 'android-env: error: %s\n' "$*" >&2
  exit 1
}

trim_whitespace() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

java_major_version() {
  local output="$1"
  local version major

  if [[ "$output" =~ version[[:space:]]+\"([^\"]+)\" ]]; then
    version="${BASH_REMATCH[1]}"
  elif [[ "$output" =~ \"([^\"]+)\" ]]; then
    version="${BASH_REMATCH[1]}"
  elif [[ "$output" =~ version[[:space:]]+([^[:space:]]+) ]]; then
    version="${BASH_REMATCH[1]}"
  elif [[ "$output" =~ (^|[[:space:]])([0-9]+([.][0-9]+)*)([[:space:]]|$) ]]; then
    version="${BASH_REMATCH[2]}"
  else
    return 1
  fi

  if [[ "$version" =~ ^1\.([0-9]+)(\.|$) ]]; then
    major="${BASH_REMATCH[1]}"
  else
    major="${version%%.*}"
  fi

  [[ "$major" =~ ^[0-9]+$ ]] || return 1
  printf '%s' "$major"
}

is_java_17_home() {
  local candidate="$1"
  local java_binary="$candidate/bin/java"
  local version_output major_version

  [[ -d "$candidate" && -x "$java_binary" ]] || return 1
  version_output="$("$java_binary" -version 2>&1)" || return 1
  major_version="$(java_major_version "$version_output")" || return 1
  [[ "$major_version" == "17" ]]
}

describe_java_home_failure() {
  local candidate="$1"
  local java_binary="$candidate/bin/java"
  local version_output major_version

  if [[ ! -e "$candidate" ]]; then
    die "JAVA_HOME points to '$candidate', but that directory does not exist."
  fi
  if [[ ! -d "$candidate" ]]; then
    die "JAVA_HOME points to '$candidate', but that path is not a directory."
  fi
  if [[ ! -x "$java_binary" ]]; then
    die "JAVA_HOME points to '$candidate', but '$java_binary' is not executable."
  fi

  if ! version_output="$("$java_binary" -version 2>&1)"; then
    die "JAVA_HOME points to '$candidate', but '$java_binary -version' failed."
  fi
  if ! major_version="$(java_major_version "$version_output")"; then
    die "JAVA_HOME points to '$candidate', but '$java_binary -version' did not report a parseable Java version. Java 17 is required."
  fi
  die "JAVA_HOME points to Java $major_version at '$candidate'; Java 17 is required."
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

absolute_existing_path() {
  CDPATH= cd -- "$1" && pwd -P
}

discover_java_home() {
  local candidate java_command java_command_dir

  if [[ -v JAVA_HOME ]]; then
    [[ -n "$JAVA_HOME" ]] || die "JAVA_HOME is set but empty; set it to a Java 17 installation or unset it."
    if is_java_17_home "$JAVA_HOME"; then
      printf '%s' "$JAVA_HOME"
      return 0
    fi
    describe_java_home_failure "$JAVA_HOME"
  fi

  CANDIDATES=()

  # Prefer the repository sibling used by local development.  The path is
  # derived from this script, so no developer-specific absolute path is
  # committed to the repository.
  for candidate in \
    "$REPOSITORY_ROOT/../.toolchains"/jdk-17* \
    "$REPOSITORY_ROOT/.toolchains"/jdk-17* \
    "${HOME:-}"/.sdkman/candidates/java/17* \
    "${HOME:-}"/.jdks/jdk-17* \
    "${HOME:-}"/.gradle/jdks/jdk-17* \
    "${HOME:-}"/.local/share/android-studio/jbr \
    "${HOME:-}"/android-studio/jbr \
    /usr/lib/jvm/java-17* \
    /usr/lib/jvm/jdk-17* \
    /opt/java/jdk-17* \
    /opt/android-studio/jbr \
    /usr/lib/android-studio/jbr \
    /Library/Java/JavaVirtualMachines/jdk-17*.jdk/Contents/Home \
    "${HOME:-}"/Library/Java/JavaVirtualMachines/jdk-17*.jdk/Contents/Home; do
    [[ -d "$candidate" ]] && add_unique_candidate "$candidate"
  done

  # A Java 17 installation can also be exposed through PATH without a
  # conventional directory name.  Resolve one symlink before deriving its
  # JAVA_HOME, and only accept it after checking the reported major version.
  java_command="$(command -v java 2>/dev/null || true)"
  if [[ -n "$java_command" && -x "$java_command" ]]; then
    local resolved_java_command
    resolved_java_command="$(readlink -f -- "$java_command" 2>/dev/null || printf '%s' "$java_command")"
    java_command_dir="$(CDPATH= cd -- "$(dirname -- "$resolved_java_command")" 2>/dev/null && pwd -P || true)"
    [[ -n "$java_command_dir" ]] && add_unique_candidate "$(CDPATH= cd -- "$java_command_dir/.." 2>/dev/null && pwd -P || true)"
  fi

  for candidate in "${CANDIDATES[@]}"; do
    if is_java_17_home "$candidate"; then
      absolute_existing_path "$candidate"
      return 0
    fi
  done

  die "Java 17 was not found. Set JAVA_HOME to a Java 17 installation or place one under '../.toolchains/jdk-17*'."
}

read_local_sdk_dir() {
  local properties_file="$REPOSITORY_ROOT/local.properties"
  local line key value

  [[ -f "$properties_file" ]] || return 1
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^[[:space:]]*([#\!]|$) ]] && continue
    [[ "$line" == *=* ]] || continue
    key="${line%%=*}"
    value="${line#*=}"
    key="$(trim_whitespace "$key")"
    [[ "$key" == "sdk.dir" ]] || continue
    value="$(trim_whitespace "$value")"
    [[ -n "$value" ]] || return 1
    printf '%s' "$value"
    return 0
  done < "$properties_file"
  return 1
}

resolve_project_path() {
  local candidate="$1"
  case "$candidate" in
    /*|[A-Za-z]:[\\/]*) printf '%s' "$candidate" ;;
    *) printf '%s/%s' "$REPOSITORY_ROOT" "$candidate" ;;
  esac
}

is_android_sdk_directory() {
  [[ -d "$1" ]]
}

discover_android_sdk() {
  local candidate local_properties_sdk sdk_command sdk_command_dir

  if [[ -v ANDROID_SDK_ROOT ]]; then
    [[ -n "$ANDROID_SDK_ROOT" ]] || die "ANDROID_SDK_ROOT is set but empty; set it to an Android SDK directory or unset it."
    if [[ ! -e "$ANDROID_SDK_ROOT" ]]; then
      die "ANDROID_SDK_ROOT points to '$ANDROID_SDK_ROOT', but that directory does not exist."
    fi
    is_android_sdk_directory "$ANDROID_SDK_ROOT" || die "ANDROID_SDK_ROOT points to '$ANDROID_SDK_ROOT', but that path is not a directory."
    printf '%s' "$ANDROID_SDK_ROOT"
    return 0
  fi

  if [[ -v ANDROID_HOME ]]; then
    [[ -n "$ANDROID_HOME" ]] || die "ANDROID_HOME is set but empty; set it to an Android SDK directory or unset it."
    if [[ ! -e "$ANDROID_HOME" ]]; then
      die "ANDROID_HOME points to '$ANDROID_HOME', but that directory does not exist."
    fi
    is_android_sdk_directory "$ANDROID_HOME" || die "ANDROID_HOME points to '$ANDROID_HOME', but that path is not a directory."
    printf '%s' "$ANDROID_HOME"
    return 0
  fi

  if local_properties_sdk="$(read_local_sdk_dir)"; then
    local_properties_sdk="$(resolve_project_path "$local_properties_sdk")"
    if is_android_sdk_directory "$local_properties_sdk"; then
      absolute_existing_path "$local_properties_sdk"
      return 0
    fi
  fi

  CANDIDATES=()
  for candidate in \
    "$REPOSITORY_ROOT/../.toolchains/android-sdk" \
    "$REPOSITORY_ROOT/.toolchains/android-sdk" \
    "${HOME:-}"/Android/Sdk \
    "${HOME:-}"/Android/sdk \
    "${HOME:-}"/Library/Android/sdk \
    "${HOME:-}"/.android/sdk \
    "${LOCALAPPDATA:-}"/Android/Sdk \
    /opt/android-sdk \
    /usr/local/android-sdk; do
    [[ -d "$candidate" ]] && add_unique_candidate "$candidate"
  done

  # If adb is already on PATH, its parent is platform-tools and the parent of
  # that directory is the SDK root.
  sdk_command="$(command -v adb 2>/dev/null || true)"
  if [[ -n "$sdk_command" && -x "$sdk_command" ]]; then
    local resolved_sdk_command
    resolved_sdk_command="$(readlink -f -- "$sdk_command" 2>/dev/null || printf '%s' "$sdk_command")"
    sdk_command_dir="$(CDPATH= cd -- "$(dirname -- "$resolved_sdk_command")" 2>/dev/null && pwd -P || true)"
    [[ -n "$sdk_command_dir" ]] && add_unique_candidate "$(CDPATH= cd -- "$sdk_command_dir/.." 2>/dev/null && pwd -P || true)"
  fi

  for candidate in "${CANDIDATES[@]}"; do
    if is_android_sdk_directory "$candidate"; then
      absolute_existing_path "$candidate"
      return 0
    fi
  done

  die "Android SDK was not found. Set ANDROID_SDK_ROOT or place one at '../.toolchains/android-sdk'."
}

prepend_toolchain_path() {
  local entry
  local entries=(
    "$JAVA_HOME/bin"
    "$ANDROID_SDK_ROOT/platform-tools"
    "$ANDROID_SDK_ROOT/emulator"
    "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin"
    "$ANDROID_SDK_ROOT/cmdline-tools/bin"
    "$ANDROID_SDK_ROOT/tools/bin"
  )
  for entry in "$ANDROID_SDK_ROOT"/cmdline-tools/*/bin; do
    [[ -d "$entry" ]] || continue
    [[ "$entry" == "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin" ]] && continue
    entries+=("$entry")
  done

  # Build the path in reverse so JAVA_HOME/bin and SDK tools take precedence
  # over an older installation already present in PATH.
  local original_path="${PATH:-}"
  PATH="$original_path"
  for ((i = ${#entries[@]} - 1; i >= 0; i--)); do
    entry="${entries[i]}"
    [[ -d "$entry" ]] || continue
    if [[ -n "$PATH" ]]; then
      PATH="$entry:$PATH"
    else
      PATH="$entry"
    fi
  done
  export PATH
}

discover_optional_android_directories() {
  local candidate

  if [[ ! -v ANDROID_AVD_HOME ]]; then
    candidate="$REPOSITORY_ROOT/../.toolchains/android-avd"
    if [[ -d "$candidate" ]]; then
      ANDROID_AVD_HOME="$(absolute_existing_path "$candidate")"
      export ANDROID_AVD_HOME
    fi
  fi

  if [[ ! -v ANDROID_USER_HOME ]]; then
    candidate="$REPOSITORY_ROOT/../.toolchains/android-user-home"
    if [[ -d "$candidate" ]]; then
      ANDROID_USER_HOME="$(absolute_existing_path "$candidate")"
      export ANDROID_USER_HOME
    fi
  fi
}

validate_command() {
  local command_name="$1"

  if [[ "$command_name" == */* ]]; then
    [[ -f "$command_name" && -x "$command_name" ]] || die "command '$command_name' is not an executable file."
  else
    command -v "$command_name" >/dev/null 2>&1 || die "command '$command_name' was not found on PATH."
  fi
}

if [[ $# -eq 0 ]]; then
  printf 'Usage: %s <command> [argument ...]\n' "$(basename "$0")" >&2
  printf 'Run a command with Java 17 and Android SDK environment discovery.\n' >&2
  exit 64
fi

JAVA_HOME="$(discover_java_home)"
ANDROID_SDK_ROOT="$(discover_android_sdk)"
export JAVA_HOME ANDROID_SDK_ROOT

# Keep an existing ANDROID_HOME untouched.  Populate it only when callers did
# not provide it, so older Android tools can consume the discovered SDK too.
if [[ ! -v ANDROID_HOME ]]; then
  ANDROID_HOME="$ANDROID_SDK_ROOT"
  export ANDROID_HOME
fi

discover_optional_android_directories
prepend_toolchain_path
validate_command "$1"

# Preserve every argument as an argv element.  In particular, arguments such
# as '$(touch /tmp/file)' or 'a; rm -rf ...' are never interpreted by a shell.
exec -- "$@"
