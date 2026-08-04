Attribute VB_Name = "Módulo2"
Sub RellenarInventario()
    Dim ruta As String
    Dim pedido() As String
    Dim aux() As String
    Dim archivo As String
    Dim inventario As String
    
    inventario = Hoja11.inventario_list.Value
    
    Hoja11.inventario_list.Clear
    
    ruta = "C:\Users\Usuario\Documents\Web\Inventario\*.xlsx"
    archivo = Dir(ruta, vbArchive)
    While archivo <> ""
        pedido = Split(archivo, "_")
        aux = Split(pedido(1), ".")
        Hoja11.inventario_list.AddItem (aux(0))
        archivo = Dir
    Wend
    
    If inventario <> "" Then
        Hoja11.inventario_list.Value = inventario
    End If
End Sub
Sub Articulo(ean As String)
    Dim consultaSQL As String
                
    consultaSQL = "SELECT F_ART.CODART AS CODIGO, F_ART.DESART AS DESCRIPCION, ' - ' AS TALLA, ' - ' AS SECTOR, F_STO.ACTSTO AS STOCK " & _
                    "FROM F_ART LEFT JOIN F_STO ON F_ART.CODART = F_STO.ARTSTO " & _
                    "WHERE F_ART.EANART='" & ean & "'; " & _
                    "Union " & _
                    "SELECT F_ART.CODART AS CODIGO, F_ART.DESART AS DESCRIPCION, F_CE1.DESCE1 AS TALLA, F_CE2.DESCE2 AS SECTOR, F_STC.ACTSTC AS STOCK " & _
                    "FROM ((F_STC INNER JOIN (F_EAC INNER JOIN F_ART ON F_EAC.ARTEAC = F_ART.CODART) ON (F_STC.ARTSTC = F_ART.CODART) AND (F_STC.CE1STC = F_EAC.CE1EAC) AND (F_STC.CE2STC = F_EAC.CE2EAC)) LEFT JOIN F_CE1 ON F_STC.CE1STC = F_CE1.CODCE1) LEFT JOIN F_CE2 ON F_STC.CE2STC = F_CE2.CODCE2 " & _
                    "WHERE F_EAC.EANEAC='" & ean & "';"

    Set datos = cn.Execute(consultaSQL)
End Sub
Sub RellenarArticulo(ean As String, fila As Long, hoja As Worksheet)
    Dim anyo As String
    Dim otroAnyo As String
    
    anyo = Year(Date)
    Call AbrirConexion(anyo)
    
    Call Articulo(ean)
    
    If datos.EOF Then
        Call CerrarConexion
        otroAnyo = anyo - 1
        Call AbrirConexion(otroAnyo)
        Call Articulo(ean)
        If datos.EOF Then
            Call CerrarConexion
            otroAnyo = otroAnyo - 1
            Call AbrirConexion(otroAnyo)
            Call Articulo(ean)
        End If
    End If
    
    If Not datos.EOF Then
        hoja.Cells(fila, 1) = datos.Fields("CODIGO")
        If otroAnyo <> "" Then
            hoja.Cells(fila, 2) = datos.Fields("DESCRIPCION") & " [Anyo " & otroAnyo & "]"
            otroAnyo = ""
        Else
            hoja.Cells(fila, 2) = datos.Fields("DESCRIPCION")
        End If
        If datos.Fields("TALLA") <> " - " Then
            hoja.Cells(fila, 3) = datos.Fields("TALLA")
        End If
        If datos.Fields("SECTOR") <> " - " Then
            hoja.Cells(fila, 4) = datos.Fields("SECTOR")
        End If
        hoja.Cells(fila, 6) = datos.Fields("STOCK")
        datos.MoveNext
    Else
        MsgBox "El EAN (" & ean & ") no existe en el año " & anyo
    End If
    
    Call CerrarConexion
End Sub
Sub LimpiarPedido(hoja As Worksheet)
    Dim ultimo As Long
    
    ultimo = hoja.Range("A" & Rows.Count).End(xlUp).Row
    
    If ultimo > 2 Then
        Call Limpiar(hoja.Range("A3:J" & ultimo))
        hoja.Range("A3:J" & ultimo).FormatConditions.Delete
    End If

End Sub
Sub inventario()
    Dim consultaSQL As String
    Dim ultimo As Long
    Dim cont As Long
    Dim n_linea As Long
    
    Dim csvLibro As Workbook
    Dim csvHoja As Worksheet
    Dim inventarioLibro As Workbook
    Dim inventarioHoja As Worksheet
    Dim ruta As String
    Dim n_archivo As String
    
    Dim ean As String
    Dim fila As Long
    Dim finca As String
    Dim sector As String
    
    If Hoja11.inventario_list.Value = "" Then
        End
    Else
        n_archivo = Hoja11.inventario_list.Value
    End If
    
    ruta = "C:\Users\Usuario\Documents\Web\Inventario\inventory_" & n_archivo & ".xlsx"
    
    Set inventarioLibro = Workbooks(ThisWorkbook.Name)
    Set inventarioHoja = inventarioLibro.Worksheets("Inventario")
    
    Set csvLibro = Workbooks.Open(ruta)
    Set csvHoja = csvLibro.Worksheets(1)
    
    Call LimpiarPedido(inventarioHoja)
    
    ultimo = csvHoja.Range("A" & Rows.Count).End(xlUp).Row
    If ultimo < 3 Then
        End
    End If
    
    finca = Localizacion(csvHoja.Cells(1, 2))
    sector = Localizacion(csvHoja.Cells(1, 3))
    inventarioHoja.Cells(1, 3) = Localizacion(csvHoja.Cells(1, 4))
    
    For i = 3 To ultimo
        ' ean
        ean = csvHoja.Cells(i, 2)
        fila = i
        Call RellenarArticulo(ean, fila, inventarioHoja)
        ' Cantidad
        inventarioHoja.Cells(fila, 5) = csvHoja.Cells(i, 3)
        ' Fecha
        inventarioHoja.Cells(fila, 7) = csvHoja.Cells(i, 4)
        ' Empleado
        inventarioHoja.Cells(fila, 8) = Empleado(csvHoja.Cells(i, 1))
        ' Finca
        inventarioHoja.Cells(fila, 9) = finca
        ' Sector
        inventarioHoja.Cells(fila, 10) = sector
    Next i
    
    Call Setear(fila, inventarioHoja)
    
    inventarioHoja.Activate
    
    'csvLibro.Close
    
End Sub
Sub Setear(fila As Long, hoja As Worksheet)
    ' Configura el tamaño de fuente en el rango A3:J
    With hoja.Range("A3:J" & fila).Font
        .Size = 8
    End With

    ' Aplica formato en el rango G3:G
    With hoja.Range("G3:G" & fila).Font
        .Italic = True
    End With

    ' Aplica formato en el rango E3:E
    With hoja.Range("E3:E" & fila).Font
        .Size = 10
        .Bold = True
        .Italic = True
    End With

    ' Configura borde grueso en el borde derecho del rango H2:H
    With hoja.Range("H2:H" & fila).Borders(xlEdgeRight)
        .Weight = xlThick
        .LineStyle = xlContinuous
    End With

    ' Añade formato condicional para celdas en A3:J que cumplen la fórmula $E3<$F3
    With hoja.Range("A3:D" & fila).FormatConditions.Add(Type:=xlExpression, Formula1:="=$E3<$F3")
        .Interior.ColorIndex = 3    ' Fondo rojo
        .Font.ColorIndex = 2        ' Texto blanco
    End With
    
    ' Añade formato condicional para celdas en A3:J que cumplen la fórmula $E3<$F3
    With hoja.Range("A3:J" & fila).FormatConditions.Add(Type:=xlExpression, Formula1:="=$E3=$F3")
        .Font.Bold = True
    End With

    ' Añade formato condicional para subrayar, poner en cursiva y negrita en B si contiene "[anyo"
    With hoja.Range("B3:B" & fila).FormatConditions.Add(Type:=xlTextString, String:="[anyo", TextOperator:=xlContains)
        .Font.Underline = xlUnderlineStyleSingle
        .Font.Italic = True
        .Font.Bold = True
    End With
End Sub
