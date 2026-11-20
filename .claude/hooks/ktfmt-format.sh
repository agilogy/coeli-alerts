#!/bin/bash
# Formats the Kotlin sources after Claude edits a .kt or .kts file.
file=$(jq -r '.tool_response.filePath // .tool_input.file_path // empty')
case "$file" in
  *.kt | *.kts) ;;
  *) exit 0 ;;
esac
cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}" || exit 0
# Without the configuration cache, Gradle doesn't need the cache keystore, which fails for some users.
./gradlew ktfmtFormat -q --no-configuration-cache
