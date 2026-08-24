#!/bin/bash
set -e

FLAVOR="${1:-Dev}"

echo "======================================="
echo "Running Acceptance Tests (flavor: $FLAVOR)"
echo "======================================="

./gradlew "connected${FLAVOR}DebugAndroidTest" \
  -Pandroid.testInstrumentationRunnerArguments.package=com.loresuelvo.serviceprovider.acceptance

echo ""
echo "Acceptance Tests Completed"
