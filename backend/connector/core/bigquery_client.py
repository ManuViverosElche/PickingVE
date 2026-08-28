import os
import tempfile

from google.cloud import bigquery
from google.cloud.exceptions import NotFound


def build_client(settings: dict) -> bigquery.Client:
    credentials_path = settings.get("bigquery", {}).get("credentials_path")
    if credentials_path:
        os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = credentials_path
    project = settings["bigquery"]["project_id"]
    location = settings["bigquery"].get("location", "US")
    return bigquery.Client(project=project, location=location)


def _schema_fields(table_cfg: dict) -> list[bigquery.SchemaField]:
    return [
        bigquery.SchemaField(
            f["name"],
            f["type"],
            mode=f.get("mode", "NULLABLE"),
            description=f.get("description", ""),
        )
        for f in table_cfg["schema"]
    ]


def ensure_table(client: bigquery.Client, table_cfg: dict, table_id: str) -> bool:
    try:
        client.get_table(table_id)
        return True
    except NotFound:
        client.create_table(bigquery.Table(table_id, schema=_schema_fields(table_cfg)))
        return False


def _load_jsonl(client, df, table_id, write_disposition, schema_fields):
    with tempfile.NamedTemporaryFile("w", suffix=".jsonl", delete=False, encoding="utf-8") as f:
        df.to_json(f, orient="records", lines=True, force_ascii=False, date_format="iso")
        path = f.name
    try:
        job_config = bigquery.LoadJobConfig(
            schema=schema_fields,
            source_format=bigquery.SourceFormat.NEWLINE_DELIMITED_JSON,
            write_disposition=write_disposition,
        )
        with open(path, "rb") as f:
            job = client.load_table_from_file(f, table_id, job_config=job_config)
        job.result()
        return job.output_rows
    finally:
        os.unlink(path)


def load_full(client, table_cfg, df, table_id) -> int:
    return _load_jsonl(
        client,
        df,
        table_id,
        bigquery.WriteDisposition.WRITE_TRUNCATE,
        _schema_fields(table_cfg),
    )


def load_append(client, table_cfg, df, table_id) -> int:
    return _load_jsonl(
        client,
        df,
        table_id,
        bigquery.WriteDisposition.WRITE_APPEND,
        _schema_fields(table_cfg),
    )


def load_merge(client, table_cfg, df, table_id) -> int:
    ensure_table(client, table_cfg, table_id)
    temp_table_id = f"{table_id}_TEMP"
    _load_jsonl(client, df, temp_table_id, bigquery.WriteDisposition.WRITE_TRUNCATE, _schema_fields(table_cfg))

    merge_key = table_cfg["merge_key"]
    cols = [f["name"] for f in table_cfg["schema"]]
    update_set = ", ".join(f"T.{c} = S.{c}" for c in cols if c != merge_key)
    insert_cols = ", ".join(cols)
    insert_vals = ", ".join(f"S.{c}" for c in cols)
    sql = f"""
        MERGE `{table_id}` T
        USING `{temp_table_id}` S
        ON T.{merge_key} = S.{merge_key}
        WHEN MATCHED THEN UPDATE SET {update_set}
        WHEN NOT MATCHED THEN INSERT ({insert_cols}) VALUES ({insert_vals})
        WHEN NOT MATCHED BY SOURCE AND T.TOTAL_ACOPIADO > 0 THEN UPDATE SET T.LINEA_ACTIVA = FALSE
        WHEN NOT MATCHED BY SOURCE AND T.TOTAL_ACOPIADO <= 0 THEN DELETE
    """
    client.query(sql).result()
    client.delete_table(temp_table_id)
    return len(df)
