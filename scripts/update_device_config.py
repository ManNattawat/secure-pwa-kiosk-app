#!/usr/bin/env python3
"""Update runtime configuration files during CI build.

This script injects Supabase / PWA values into provisioning config and
network security config using environment variables supplied via GitHub Secrets.
"""

from __future__ import annotations

import json
import os
import re
from pathlib import Path
from typing import List

ROOT = Path(__file__).resolve().parents[1]
STRINGS_XML = ROOT / "android" / "app" / "src" / "main" / "res" / "values" / "strings.xml"
NETWORK_SECURITY_CONFIG = ROOT / "android" / "app" / "src" / "main" / "res" / "xml" / "network_security_config.xml"
PROVISIONING_JSON = ROOT / "provisioning" / "device_owner_config.json"

ENV_SUPABASE_URL = "SECURE_KIOSK_SUPABASE_URL"
ENV_SUPABASE_KEY = "SECURE_KIOSK_SUPABASE_KEY"
ENV_PWA_URL = "SECURE_KIOSK_PWA_URL"
ENV_CERT_PINS = "SECURE_KIOSK_CERT_PINS"


def _read_env(name: str) -> str | None:
    value = os.getenv(name)
    return value.strip() if value else None


def _parse_cert_pins(raw: str | None) -> List[str]:
    if not raw:
        return []
    tokens = re.split(r"[\n,]", raw)
    pins = [token.strip() for token in tokens if token.strip()]
    return pins


def update_network_security_config(supabase_url: str) -> None:
    if not NETWORK_SECURITY_CONFIG.exists():
        return
    host = _extract_host(supabase_url)
    text = NETWORK_SECURITY_CONFIG.read_text(encoding="utf-8")
    updated = re.sub(r"YOUR_PROJECT\.supabase\.co", host, text)
    NETWORK_SECURITY_CONFIG.write_text(updated, encoding="utf-8")


def update_provisioning_config(
    supabase_url: str | None,
    supabase_key: str | None,
    pwa_url: str | None,
    cert_pins: List[str],
) -> None:
    if not PROVISIONING_JSON.exists():
        return

    data = json.loads(PROVISIONING_JSON.read_text(encoding="utf-8"))
    extras = data.setdefault("android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE", {})

    if supabase_url:
        extras["supabase_base_url"] = supabase_url
    if supabase_key:
        extras["supabase_service_key"] = supabase_key
    if pwa_url:
        extras["pwa_url"] = pwa_url
    if cert_pins:
        extras["certificate_pins"] = cert_pins
        extras["certificate_pin"] = cert_pins[0]

    PROVISIONING_JSON.write_text(
        json.dumps(data, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def _extract_host(url: str) -> str:
    match = re.match(r"https?://([^/]+)", url)
    return match.group(1) if match else url


def main() -> None:
    supabase_url = _read_env(ENV_SUPABASE_URL)
    supabase_key = _read_env(ENV_SUPABASE_KEY)
    pwa_url = _read_env(ENV_PWA_URL)
    cert_pins = _parse_cert_pins(_read_env(ENV_CERT_PINS))

    if supabase_url:
        update_network_security_config(supabase_url)
    update_provisioning_config(supabase_url, supabase_key, pwa_url, cert_pins)


if __name__ == "__main__":
    main()
