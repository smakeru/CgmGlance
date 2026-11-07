#!/usr/bin/env python3
"""
Deploy the 'site/' folder directly to Google Firebase Hosting (https://cgm-glance-play.web.app)
using the existing play-service-account.json key via the official Firebase Hosting REST API.
100% Free Tier (Spark Plan) — no billing account required.
"""

from __future__ import annotations

import gzip
import hashlib
import json
import urllib.error
import urllib.request
from pathlib import Path

from google.oauth2 import service_account
import google.auth.transport.requests

ROOT_DIR = Path(__file__).resolve().parent.parent
SITE_DIR = ROOT_DIR / "site"
KEY_FILE = ROOT_DIR / "play-service-account.json"
SITE_ID = "cgm-glance-play"
BASE_URL = "https://firebasehosting.googleapis.com/v1beta1"


def get_token() -> str:
    creds = service_account.Credentials.from_service_account_file(
        str(KEY_FILE),
        scopes=[
            "https://www.googleapis.com/auth/cloud-platform",
            "https://www.googleapis.com/auth/firebase",
        ],
    )
    creds.refresh(google.auth.transport.requests.Request())
    return creds.token


def api_call(method: str, url: str, token: str, body: bytes | None = None, content_type: str = "application/json") -> dict:
    req = urllib.request.Request(
        url,
        data=body,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": content_type,
        },
        method=method,
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as err:
        err_text = err.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {err.code} on {method} {url}:\n{err_text}") from err


def deploy() -> None:
    token = get_token()

    # 1. Collect and gzip all files in site/
    file_hashes: dict[str, str] = {}
    gzipped_by_hash: dict[str, bytes] = {}

    for path in sorted(SITE_DIR.rglob("*")):
        if path.is_file():
            rel_path = "/" + path.relative_to(SITE_DIR).as_posix()
            gz_bytes = gzip.compress(path.read_bytes(), mtime=0)
            sha = hashlib.sha256(gz_bytes).hexdigest()
            file_hashes[rel_path] = sha
            gzipped_by_hash[sha] = gz_bytes

    print(f"==> Prepared {len(file_hashes)} files from {SITE_DIR}")

    # 2. Create a new Hosting Version
    ver = api_call(
        "POST",
        f"{BASE_URL}/sites/{SITE_ID}/versions",
        token,
        json.dumps({"config": {"cleanUrls": True}}).encode("utf-8"),
    )
    ver_name = ver["name"]
    print(f"==> Created version: {ver_name}")

    # 3. Populate files
    pop = api_call(
        "POST",
        f"{BASE_URL}/{ver_name}:populateFiles",
        token,
        json.dumps({"files": file_hashes}).encode("utf-8"),
    )
    upload_url = pop.get("uploadUrl")
    required_hashes = pop.get("uploadRequiredHashes", [])

    # 4. Upload required gzipped files
    for sha in required_hashes:
        print(f"==> Uploading blob {sha[:12]}...")
        api_call(
            "POST",
            f"{upload_url}/{sha}",
            token,
            gzipped_by_hash[sha],
            content_type="application/octet-stream",
        )

    # 5. Finalize version
    api_call(
        "PATCH",
        f"{BASE_URL}/{ver_name}?update_mask=status",
        token,
        json.dumps({"status": "FINALIZED"}).encode("utf-8"),
    )
    print("==> Version finalized.")

    # 6. Release version live
    rel = api_call(
        "POST",
        f"{BASE_URL}/sites/{SITE_ID}/releases?versionName={ver_name}",
        token,
        b"{}",
    )
    print(f"==> Deployed successfully! Live URL: https://{SITE_ID}.web.app")


if __name__ == "__main__":
    deploy()
