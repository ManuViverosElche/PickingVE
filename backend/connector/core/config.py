from pathlib import Path
from typing import Any

import yaml

CONFIG_DIR = Path(__file__).resolve().parent.parent / "config"

DEFAULTS_FILE = CONFIG_DIR / "settings.yaml"
LOCAL_FILE = CONFIG_DIR / "settings.local.yaml"
TABLES_FILE = CONFIG_DIR / "tables.yaml"


def _deep_merge(base: dict, override: dict) -> dict:
    for key, value in override.items():
        if isinstance(value, dict) and isinstance(base.get(key), dict):
            _deep_merge(base[key], value)
        else:
            base[key] = value
    return base


def load_settings() -> dict:
    if not DEFAULTS_FILE.exists():
        raise FileNotFoundError(f"No existe la plantilla de configuracion: {DEFAULTS_FILE}")
    settings = yaml.safe_load(DEFAULTS_FILE.read_text(encoding="utf-8")) or {}
    if LOCAL_FILE.exists():
        local = yaml.safe_load(LOCAL_FILE.read_text(encoding="utf-8")) or {}
        settings = _deep_merge(settings, local)
    return settings


def load_tables() -> list[dict]:
    if not TABLES_FILE.exists():
        raise FileNotFoundError(f"No existe el maestro de tablas: {TABLES_FILE}")
    data = yaml.safe_load(TABLES_FILE.read_text(encoding="utf-8")) or {}
    return data.get("tables", [])
