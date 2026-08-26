import sys
import os

connector_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, connector_dir)

from core.config import load_settings
from core.bigquery_client import build_client
from google.cloud import bigquery

def main():
    settings = load_settings()
    client = build_client(settings)
    project_id = settings["bigquery"]["project_id"]
    dataset_name = settings["bigquery"]["analytics_dataset"]
    dataset_id = f"{project_id}.{dataset_name}"
    
    print(f"Creando dataset {dataset_id} en region EU...")
    dataset = bigquery.Dataset(dataset_id)
    dataset.location = "EU"
    try:
        client.create_dataset(dataset, exists_ok=True)
        print(f"Dataset {dataset_id} creado/verificado exitosamente.")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    main()
