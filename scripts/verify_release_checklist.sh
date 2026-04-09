#!/usr/bin/env bash
set -euo pipefail

checklist_file="${1:-docs/RELEASE_CHECKLIST.md}"

if [[ ! -f "${checklist_file}" ]]; then
  echo "Checklist file not found: ${checklist_file}" >&2
  exit 1
fi

unchecked_items="$(grep -nE '^- \[ \]' "${checklist_file}" || true)"

if [[ -n "${unchecked_items}" ]]; then
  echo "Release checklist contains unchecked required items:" >&2
  echo "${unchecked_items}" >&2
  exit 1
fi

echo "Release checklist passed: ${checklist_file}"
