#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
for tool in foxguard gitleaks zizmor; do
    command -v "$tool" >/dev/null || { echo "Missing security scanner: $tool" >&2; exit 1; }
done

foxguard .
gitleaks git --redact --no-banner
gitleaks dir --redact --no-banner .

# zizmor exits with an error when there are no auditable inputs.
if [[ -d .github/workflows ]] || [[ -n "$(rg --files --hidden -g '!.git' -g 'action.yml' -g 'action.yaml')" ]]; then
    zizmor --offline --no-progress .
else
    echo "zizmor: skipped (no GitHub Actions workflows or action definitions)."
fi
