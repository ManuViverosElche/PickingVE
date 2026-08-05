from contextlib import contextmanager
from typing import Iterator, Sequence

import pyodbc

from .config import load_settings


def build_connection_string(settings: dict | None = None) -> str:
    settings = settings or load_settings()
    access = settings["access"]
    driver = access["driver"]
    db_path = access["db_path"]
    read_only = access.get("read_only", True)
    mode = "1" if read_only else "0"
    return f"DRIVER={{{driver}}};DBQ={db_path};ReadOnly={mode}"


@contextmanager
def open_connection(settings: dict | None = None) -> Iterator[pyodbc.Connection]:
    conn = pyodbc.connect(build_connection_string(settings))
    try:
        yield conn
    finally:
        conn.close()


def read_query(conn: pyodbc.Connection, sql: str) -> tuple[list[str], list[Sequence]]:
    cursor = conn.cursor()
    cursor.execute(sql)
    columns = [column[0] for column in cursor.description]
    rows = cursor.fetchall()
    cursor.close()
    return columns, rows


def list_tables(conn: pyodbc.Connection) -> list[str]:
    cursor = conn.cursor()
    tables = [
        row.table_name
        for row in cursor.tables(tableType="TABLE")
        if row.table_name.startswith("F_")
    ]
    cursor.close()
    return sorted(tables)
