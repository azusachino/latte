#!/usr/bin/env bash

set -uo pipefail

base_url="${KONACHAN_URL:-https://konachan.com}"
headers_file="$(mktemp "${TMPDIR:-/tmp}/latte-konachan.XXXXXX")"
trap 'rm -f "$headers_file"' EXIT

probe() {
    local label="$1"
    local url="$2"
    shift 2

    : >"$headers_file"
    if ! curl \
        --silent \
        --show-error \
        --connect-timeout 10 \
        --max-time 15 \
        --dump-header "$headers_file" \
        --output /dev/null \
        "$@" \
        "$url"; then
        printf '%s status=transport-error\n' "$label"
        return
    fi

    local status
    local content_type
    local challenge
    local server
    status="$(awk '$1 ~ /^HTTP\// { print $2; exit }' "$headers_file")"
    content_type="$(awk 'tolower($1) == "content-type:" { sub(/\r$/, "", $2); print $2; exit }' "$headers_file")"
    challenge="$(awk 'tolower($1) == "cf-mitigated:" { sub(/\r$/, "", $2); print tolower($2); exit }' "$headers_file")"
    server="$(awk 'tolower($1) == "server:" { sub(/\r$/, "", $2); print tolower($2); exit }' "$headers_file")"

    local classification="ordinary-response"
    if [[ "$challenge" == "challenge" ]]; then
        classification="browser-required"
    elif [[ "$content_type" == text/html* && "$status" == 403 ]]; then
        classification="possible-browser-required"
    fi

    printf '%s status=%s content_type=%s cf_mitigated=%s server=%s classification=%s\n' \
        "$label" \
        "${status:-unknown}" \
        "${content_type:-unknown}" \
        "${challenge:-none}" \
        "${server:-unknown}" \
        "$classification"
}

printf '%s\n' "Konachan.com bounded probe: $base_url"
printf '%s\n' 'No cookies, redirects, challenge solving, proxy rotation, or retries are used.'
printf '%s\n' 'Each request has a 10s connect timeout and 15s total timeout.'

probe 'default-root' "$base_url/"
probe 'default-json' "$base_url/post.json?limit=1" \
    -H 'Accept: application/json'
probe 'android-ua-root' "$base_url/" \
    -H 'User-Agent: Mozilla/5.0 (Android; Mobile; Latte probe)'
probe 'android-ua-json' "$base_url/post.json?limit=1" \
    -H 'Accept: application/json' \
    -H 'User-Agent: Mozilla/5.0 (Android; Mobile; Latte probe)'
probe 'browser-ua-root' "$base_url/" \
    -H 'User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140.0.0.0 Safari/537.36'
probe 'browser-ua-json' "$base_url/post.json?limit=1" \
    -H 'Accept: application/json' \
    -H 'User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140.0.0.0 Safari/537.36'
