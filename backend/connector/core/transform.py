import re
from datetime import datetime

import pandas as pd


def _extraer_coste_nota(texto):
    if not texto:
        return 0.0
    match = re.search(r"(?:transporte:?|precio:?)\s*([\d.,]+)", str(texto).lower())
    if match:
        try:
            return float(match.group(1).replace(".", "").replace(",", "."))
        except ValueError:
            return 0.0
    return 0.0


def _extraer_tags(texto_sucio):
    if not texto_sucio:
        return {"LIMPIA": "", "MARCADO": False, "MARCA": "", "FINCA": "", "SECTOR_R": "", "UBI": "", "PRIO": "NORMAL", "OBS": ""}
    texto = str(texto_sucio).replace("\n", " ").replace("\r", " ").strip()
    res = {"MARCA": "", "FINCA": "", "SECTOR_R": "", "UBI": "", "OBS": ""}
    prio = "PRIORITARIO" if "[PRIO]" in texto.upper() else ("NO PRIORIDAD" if "[NO_PRIO]" in texto.upper() else "NORMAL")
    marcado = bool(re.search(r"\[M\]|\[M:.*?\]", texto, re.IGNORECASE))
    bloques = re.findall(r"\[(.*?)\]", texto)
    freno = r"(?=\s*-\s*(?:F|S|M|UBI|OBS):|$)"
    for b in bloques:
        s_m = re.search(r"(?:^|(?<=\s-\s))(?<![A-Z])S:\s*(.*?)" + freno, b, re.IGNORECASE)
        if s_m:
            res["SECTOR_R"] = s_m.group(1).strip()
        o_m = re.search(r"(?:^|(?<=\s-\s))OBS:\s*(.*?)" + freno, b, re.IGNORECASE)
        if o_m:
            res["OBS"] = o_m.group(1).strip()
        u_m = re.search(r"(?:^|(?<=\s-\s))UBI:\s*(.*?)" + freno, b, re.IGNORECASE)
        if u_m:
            res["UBI"] = u_m.group(1).strip()
        f_m = re.search(r"(?:^|(?<=\s-\s))F:\s*(.*?)" + freno, b, re.IGNORECASE)
        if f_m:
            res["FINCA"] = f_m.group(1).strip()
        m_m = re.search(r"(?:^|(?<=\s-\s))M:\s*(.*?)" + freno, b, re.IGNORECASE)
        if m_m:
            res["MARCA"] = m_m.group(1).strip()
            marcado = True
    limpia = re.sub(r"\[.*?\]", "", texto).strip()
    return {"LIMPIA": re.sub(r"\s+", " ", limpia), "MARCADO": marcado, **res, "PRIO": prio}


def _sumar_documento(df, table_cfg):
    for col in [f["name"] for f in table_cfg["schema"] if f["name"].startswith("FECHA")]:
        if col in df.columns:
            df[col] = pd.to_datetime(df[col], errors="coerce").dt.strftime("%Y-%m-%d")
    for c in [f["name"] for f in table_cfg["schema"]]:
        if f"{c}_10" in df.columns and f"{c}_EXENTO" in df.columns:
            df[f"{c}_10"] = pd.to_numeric(df[f"{c}_10"], errors="coerce")
            df[f"{c}_EXENTO"] = pd.to_numeric(df[f"{c}_EXENTO"], errors="coerce")
            df[c] = df[f"{c}_10"] + df[f"{c}_EXENTO"]
    return df


def _transform_pedidos(df, table_cfg):
    df["FECHA_CARGA"] = df["FECHA_CARGA"].astype(str).str.strip()
    mask_carga = df["FECHA_CARGA"].str.match(r"^\d{2}/\d{2}/\d{4}$", na=False)
    df.loc[~mask_carga, "FECHA_CARGA"] = None
    df["FECHA_CARGA"] = pd.to_datetime(df["FECHA_CARGA"], format="%d/%m/%Y", errors="coerce")

    df["FECHA_PEDIDO"] = pd.to_datetime(df["FECHA_PEDIDO"], errors="coerce")
    df["FECHA_MODIFICACION"] = pd.to_datetime(df["FECHA_MODIFICACION"], errors="coerce")
    df["FECHA_CREACION_RAW"] = pd.to_datetime(df["FECHA_CREACION"], errors="coerce")

    df["FECHA_CREACION"] = df.apply(
        lambda x: pd.Timestamp.combine(x["FECHA_PEDIDO"].date(), x["FECHA_CREACION_RAW"].time())
        if pd.notnull(x["FECHA_PEDIDO"]) and pd.notnull(x["FECHA_CREACION_RAW"]) else x["FECHA_PEDIDO"],
        axis=1,
    )

    df["COSTE_TRANSPORTE_COMPRA"] = df["NOTA_PRIVADA"].apply(_extraer_coste_nota)
    df["TRANSPORTE_INCLUIDO_30"] = df.apply(
        lambda x: (float(x["TOTAL_PEDIDO"]) * 0.30) if x["MODO_PORTES"] == 0
        else (float(x["PORTES_VENTA_10"]) + float(x["PORTES_VENTA_EXENTO"])),
        axis=1,
    )
    df["MARGEN_LOGISTICO_NETO"] = df["TRANSPORTE_INCLUIDO_30"] - df["COSTE_TRANSPORTE_COMPRA"]
    df["REPERCUSION_TRANSPORTE_PCT"] = df.apply(
        lambda x: (float(x["COSTE_TRANSPORTE_COMPRA"]) / float(x["TOTAL_PEDIDO"]) * 100) if x["TOTAL_PEDIDO"] > 0 else 0,
        axis=1,
    )

    for col in [f["name"] for f in table_cfg["schema"] if f["type"] in ("NUMERIC", "INT64")]:
        if col in df.columns:
            df[col] = pd.to_numeric(df[col], errors="coerce").fillna(0).round(2)

    df["FECHA_PEDIDO"] = df["FECHA_PEDIDO"].dt.strftime("%Y-%m-%d").astype(str).str[:10]
    df["FECHA_CARGA"] = df["FECHA_CARGA"].dt.strftime("%Y-%m-%d").astype(str).str[:10]
    df["FECHA_MODIFICACION"] = df["FECHA_MODIFICACION"].dt.strftime("%Y-%m-%d %H:%M:%S")
    df["FECHA_CREACION"] = df["FECHA_CREACION"].dt.strftime("%Y-%m-%d %H:%M:%S")

    df = df.replace(["NaT", "nan", "None", "NaN"], None)

    for col in [f["name"] for f in table_cfg["schema"] if f["type"] == "STRING"]:
        if col in df.columns:
            df[col] = df[col].fillna("").astype(str).str.replace(r"[\n\r\"]", " ", regex=True).str.strip()
    return df


def _transform_linea_pedido(df, table_cfg, logger=None):
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    records = []
    seen = set()
    for _, f in df.iterrows():
        t = _extraer_tags(f["DESCRIPCION_SISTEMA"])
        serie = str(f["SERIE_PEDIDO"])
        numero = str(f["NUMERO_PEDIDO"])
        posicion = str(f["POSICION_PEDIDO"])
        huella = str(f["HUELLA_DIGITAL"])
        if not huella or huella == "None":
            huella = f"HUELLA-{serie}-{numero}-{posicion}"
        if huella in seen:
            huella = f"{huella}-X"
        seen.add(huella)
        records.append({
            "HUELLA_DIGITAL": huella,
            "SERIE_PEDIDO": str(f["SERIE_PEDIDO"]),
            "NUMERO_PEDIDO": str(f["NUMERO_PEDIDO"]),
            "POSICION_PEDIDO": int(f["POSICION_PEDIDO"]),
            "REFERENCIA_ARTICULO": str(f["REFERENCIA_ARTICULO"]),
            "DESCRIPCION_ARTICULO": t["LIMPIA"],
            "UNIDADES": float(f["UNIDADES"]),
            "UNIDADES_PENDIENTES": float(f["UNIDADES_PENDIENTES"] if f["UNIDADES_PENDIENTES"] else 0),
            "CODIGO_LITRAJE": str(f["CODIGO_LITRAJE"]),
            "CODIGO_SECTOR": str(f["CODIGO_SECTOR"]),
            "PRECIO": float(f["PRECIO"]),
            "MARCADO": bool(t["MARCADO"]),
            "MARCA": t["MARCA"],
            "FINCA_RELEVADA": t["FINCA"],
            "SECTOR_RELEVADO": t["SECTOR_R"],
            "UBICACION_EXTRA": t["UBI"],
            "PRIORIDAD": t["PRIO"],
            "ACCION_LOGISTICA": t["OBS"],
            "NOTA_LINEA_PEDIDO": str(f["NOTA_LINEA_PEDIDO"])[:500] if f["NOTA_LINEA_PEDIDO"] else "",
            "DESCRIPCION_SISTEMA": str(f["DESCRIPCION_SISTEMA"]),
            "TOTAL_ACOPIADO": 0.0,
            "LINEA_ACTIVA": True,
            "IMPRIMIR_LINEA": int(f["IMPRIMIR_LINEA"] if f["IMPRIMIR_LINEA"] else 0),
            "ULTIMA_SINCRONIZACION": now,
        })
    return pd.DataFrame(records)


def _transform_precios_venta(df, table_cfg):
    df["PRECIO_VENTA"] = df["PRECIO_VENTA"].fillna(0)
    return df


def _clean_text(df):
    df = df.replace({r"[\r\n]+": " "}, regex=True)
    for col in df.select_dtypes(include="object"):
        df[col] = df[col].map(lambda x: x.strip() if isinstance(x, str) else x)
    return df


def apply_transform(df, table_cfg, logger=None):
    transform = table_cfg.get("transform")
    if transform == "sumar_documento":
        return _sumar_documento(df, table_cfg)
    if transform == "pedidos":
        return _transform_pedidos(df, table_cfg)
    if transform == "linea_pedido":
        return _transform_linea_pedido(df, table_cfg, logger)
    if transform == "precios_venta":
        return _transform_precios_venta(df, table_cfg)
    return _clean_text(df)


def finalize(df, table_cfg):
    out_cols = [f["name"] for f in table_cfg["schema"]]
    for c in out_cols:
        if c not in df.columns:
            df[c] = None
    df = df.replace(["NaT", "nan", "None", "NaN"], None)
    df = df.where(pd.notnull(df), None)
    return df[out_cols]
