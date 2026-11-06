#!/usr/bin/env python3
"""
Automated Version Bump, Bundle Build, and Google Play Publishing Script for CGM Glance.

Usage examples:
  1. Bump versions and build both signed .aab bundles into release/:
     python scripts/publish_play.py --bump both --build

  2. Build and upload both Phone & Wear OS bundles to Internal Testing:
     python scripts/publish_play.py --bump both --build --upload internal

  3. Build and upload both Phone & Wear OS bundles to Closed Testing (Alpha):
     python scripts/publish_play.py --bump both --build --upload alpha

  4. Upload already-built bundles to Internal Testing without bumping:
     python scripts/publish_play.py --bump none --upload internal
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

PACKAGE_NAME = "ro.smakeru.cgmglance"
ROOT_DIR = Path(__file__).resolve().parent.parent
PHONE_GRADLE = ROOT_DIR / "phone" / "app" / "build.gradle.kts"
WEAR_GRADLE = ROOT_DIR / "wear" / "app" / "build.gradle.kts"
RELEASE_DIR = ROOT_DIR / "release"
DEFAULT_KEY_FILE = ROOT_DIR / "play-service-account.json"


def read_version_info(gradle_file: Path) -> tuple[int, str]:
    text = gradle_file.read_text(encoding="utf-8")
    code_match = re.search(r"versionCode\s*=\s*([0-9_]+)", text)
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not code_match or not name_match:
        raise RuntimeError(f"Could not parse versionCode/versionName in {gradle_file}")
    code = int(code_match.group(1).replace("_", ""))
    name = name_match.group(1)
    return code, name


def bump_version_code(gradle_file: Path, is_wear: bool = False) -> int:
    text = gradle_file.read_text(encoding="utf-8")
    code_match = re.search(r"(versionCode\s*=\s*)([0-9_]+)", text)
    if not code_match:
        raise RuntimeError(f"Could not find versionCode in {gradle_file}")
    current_code = int(code_match.group(2).replace("_", ""))
    next_code = current_code + 1
    if is_wear and next_code >= 1_000_000:
        formatted = f"{next_code:,}".replace(",", "_")
    else:
        formatted = str(next_code)
    updated = (
        text[: code_match.start(2)]
        + formatted
        + text[code_match.end(2) :]
    )
    gradle_file.write_text(updated, encoding="utf-8", newline="\n")
    return next_code


def build_bundles(target: str) -> None:
    gradlew = str(ROOT_DIR / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    tasks = ["spotlessApply"]
    if target in ("phone", "both"):
        tasks.append(":phone:app:bundleRelease")
    if target in ("wear", "both"):
        tasks.append(":wear:app:bundleRelease")

    print(f"==> Running Gradle tasks: {' '.join(tasks)}")
    subprocess.run([gradlew, *tasks], cwd=ROOT_DIR, check=True)

    RELEASE_DIR.mkdir(parents=True, exist_ok=True)

    if target in ("phone", "both"):
        p_code, p_name = read_version_info(PHONE_GRADLE)
        src = ROOT_DIR / "phone" / "app" / "build" / "outputs" / "bundle" / "release" / "app-release.aab"
        dst_versioned = RELEASE_DIR / f"CgmGlance-phone-{p_name}-v{p_code}.aab"
        dst_latest = RELEASE_DIR / f"CgmGlance-phone-{p_name}.aab"
        shutil.copy2(src, dst_versioned)
        shutil.copy2(src, dst_latest)
        print(f"==> Phone bundle ready: {dst_versioned}")

    if target in ("wear", "both"):
        w_code, w_name = read_version_info(WEAR_GRADLE)
        src = ROOT_DIR / "wear" / "app" / "build" / "outputs" / "bundle" / "release" / "app-release.aab"
        dst_versioned = RELEASE_DIR / f"CgmGlance-wear-{w_name}-v{w_code}.aab"
        dst_latest = RELEASE_DIR / f"CgmGlance-wear-{w_name}.aab"
        shutil.copy2(src, dst_versioned)
        shutil.copy2(src, dst_latest)
        print(f"==> Wear OS bundle ready: {dst_versioned}")


def get_access_token(service_account_path: Path) -> str:
    """Obtain an OAuth2 access token for androidpublisher using google-auth or cryptography."""
    try:
        from google.oauth2 import service_account
        import google.auth.transport.requests

        creds = service_account.Credentials.from_service_account_file(
            str(service_account_path),
            scopes=["https://www.googleapis.com/auth/androidpublisher"],
        )
        creds.refresh(google.auth.transport.requests.Request())
        return creds.token
    except ImportError:
        print(
            "Installing google-auth for Google Play API authentication...",
            file=sys.stderr,
        )
        subprocess.run(
            [sys.executable, "-m", "pip", "install", "--quiet", "google-auth", "requests"],
            check=True,
        )
        from google.oauth2 import service_account
        import google.auth.transport.requests

        creds = service_account.Credentials.from_service_account_file(
            str(service_account_path),
            scopes=["https://www.googleapis.com/auth/androidpublisher"],
        )
        creds.refresh(google.auth.transport.requests.Request())
        return creds.token


def api_request(
    method: str,
    url: str,
    token: str,
    body: bytes | None = None,
    content_type: str = "application/json",
) -> dict:
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": content_type,
    }
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=300) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as err:
        err_body = err.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Google Play API error ({err.code}) on {method} {url}:\n{err_body}") from err


def upload_to_play(track: str, target: str, key_file: Path) -> None:
    if not key_file.exists():
        raise FileNotFoundError(
            f"Service account JSON not found at: {key_file}\n"
            "Save your Google Cloud Service Account key as 'play-service-account.json' in the project root."
        )

    print(f"==> Authenticating with Google Play Developer API using {key_file.name}...")
    token = get_access_token(key_file)
    base_url = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE_NAME}"
    upload_base_url = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE_NAME}"

    # 1. Create a new Edit session
    edit = api_request("POST", f"{base_url}/edits", token, b"{}")
    edit_id = edit["id"]
    print(f"==> Created Google Play edit session: {edit_id}")

    existing_bundles_resp = api_request("GET", f"{base_url}/edits/{edit_id}/bundles", token)
    existing_codes = {
        int(b["versionCode"]) for b in existing_bundles_resp.get("bundles", [])
    }

    existing_tracks_resp = api_request("GET", f"{base_url}/edits/{edit_id}/tracks", token)
    existing_tracks = {
        t["track"] for t in existing_tracks_resp.get("tracks", [])
    }

    # 2. Upload Phone bundle (if not already in Play library) and assign to track
    if target in ("phone", "both"):
        p_code, p_name = read_version_info(PHONE_GRADLE)
        if p_code in existing_codes:
            print(f"==> Phone versionCode={p_code} already exists in Play library; assigning to '{track}'...")
            uploaded_code = p_code
        else:
            p_aab = RELEASE_DIR / f"CgmGlance-phone-{p_name}-v{p_code}.aab"
            if not p_aab.exists():
                p_aab = ROOT_DIR / "phone" / "app" / "build" / "outputs" / "bundle" / "release" / "app-release.aab"
            print(f"==> Uploading Phone AAB ({p_aab.name}, versionCode={p_code})...")
            bundle_resp = api_request(
                "POST",
                f"{upload_base_url}/edits/{edit_id}/bundles?uploadType=media",
                token,
                p_aab.read_bytes(),
                content_type="application/octet-stream",
            )
            uploaded_code = bundle_resp["versionCode"]
        track_body = {
            "track": track,
            "releases": [
                {
                    "name": f"{p_name} ({uploaded_code})",
                    "versionCodes": [str(uploaded_code)],
                    "status": "completed",
                }
            ],
        }
        api_request(
            "PUT",
            f"{base_url}/edits/{edit_id}/tracks/{track}",
            token,
            json.dumps(track_body).encode("utf-8"),
        )
        print(f"==> Assigned Phone versionCode {uploaded_code} to track '{track}'")

    # 3. Upload Wear OS bundle (if not already in Play library) and assign to wear:{track}
    if target in ("wear", "both"):
        w_code, w_name = read_version_info(WEAR_GRADLE)
        if w_code in existing_codes:
            print(f"==> Wear OS versionCode={w_code} already exists in Play library; assigning to 'wear:{track}'...")
            uploaded_code = w_code
        else:
            w_aab = RELEASE_DIR / f"CgmGlance-wear-{w_name}-v{w_code}.aab"
            if not w_aab.exists():
                w_aab = ROOT_DIR / "wear" / "app" / "build" / "outputs" / "bundle" / "release" / "app-release.aab"
            print(f"==> Uploading Wear OS AAB ({w_aab.name}, versionCode={w_code})...")
            bundle_resp = api_request(
                "POST",
                f"{upload_base_url}/edits/{edit_id}/bundles?uploadType=media",
                token,
                w_aab.read_bytes(),
                content_type="application/octet-stream",
            )
            uploaded_code = bundle_resp["versionCode"]
        wear_track = f"wear:{track}"
        if wear_track not in existing_tracks and f"wear: beta" not in existing_tracks:
            pass
        if wear_track not in existing_tracks:
            print(
                f"==> Note: Track '{wear_track}' does not exist in Play Console yet "
                f"(available tracks: {sorted(existing_tracks)}). Skipping '{wear_track}'."
            )
        else:
            track_body = {
                "track": wear_track,
                "releases": [
                    {
                        "name": f"{w_name} ({uploaded_code})",
                        "versionCodes": [str(uploaded_code)],
                        "status": "completed",
                    }
                ],
            }
            api_request(
                "PUT",
                f"{base_url}/edits/{edit_id}/tracks/{wear_track}",
                token,
                json.dumps(track_body).encode("utf-8"),
            )
            print(f"==> Assigned Wear OS versionCode {uploaded_code} to track '{wear_track}'")

    # 4. Commit Edit session
    api_request("POST", f"{base_url}/edits/{edit_id}:commit", token, b"")
    print("==> Successfully committed release to Google Play Console!")


def list_tracks_status(key_file: Path) -> None:
    token = get_access_token(key_file)
    base_url = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE_NAME}"
    edit = api_request("POST", f"{base_url}/edits", token, b"{}")
    edit_id = edit["id"]
    tracks_resp = api_request("GET", f"{base_url}/edits/{edit_id}/tracks", token)
    print("==> Active tracks in Google Play Console:")
    for t in tracks_resp.get("tracks", []):
        track_name = t.get("track")
        releases = t.get("releases", [])
        rel_summary = [
            f"name={r.get('name')}, versionCodes={r.get('versionCodes')}, status={r.get('status')}"
            for r in releases
        ]
        print(f"  - {track_name}: {'; '.join(rel_summary) if rel_summary else 'empty'}")



def main() -> None:
    parser = argparse.ArgumentParser(description="Build and publish CGM Glance to Google Play")
    parser.add_argument(
        "--bump",
        choices=["phone", "wear", "both", "none"],
        default="none",
        help="Which module versionCode to increment before building",
    )
    parser.add_argument(
        "--target",
        choices=["phone", "wear", "both"],
        default="both",
        help="Which module(s) to build/upload",
    )
    parser.add_argument(
        "--build",
        action="store_true",
        help="Build signed release AAB bundles and copy to release/",
    )
    parser.add_argument(
        "--upload",
        choices=["internal", "alpha", "beta", "production"],
        default=None,
        help="Google Play track to upload to (sets both <track> and wear:<track>)",
    )
    parser.add_argument(
        "--status",
        action="store_true",
        help="List all active tracks and versions currently in Google Play Console",
    )
    parser.add_argument(
        "--key-file",
        type=Path,
        default=Path(os.environ.get("PLAY_SERVICE_ACCOUNT_FILE", str(DEFAULT_KEY_FILE))),
        help="Path to play-service-account.json",
    )
    args = parser.parse_args()

    if args.status:
        list_tracks_status(args.key_file)
        return

    if args.bump in ("phone", "both"):
        new_p = bump_version_code(PHONE_GRADLE, is_wear=False)
        print(f"==> Bumped Phone versionCode to {new_p}")
    if args.bump in ("wear", "both"):
        new_w = bump_version_code(WEAR_GRADLE, is_wear=True)
        print(f"==> Bumped Wear OS versionCode to {new_w}")

    if args.build:
        build_bundles(args.target)

    if args.upload:
        upload_to_play(args.upload, args.target, args.key_file)


if __name__ == "__main__":
    main()
