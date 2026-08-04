Attribute VB_Name = "Módulo1"
Sub Firmado(trabajador As String, hoja As Worksheet)
    trabajador = Empleado(trabajador)
    hoja.Range("N61") = trabajador
    hoja.Range("AG61") = trabajador
    hoja.Range("N125") = trabajador
    hoja.Range("AG125") = trabajador
    
    'If hoja.Range("N61") <> "" And hoja.Range("N61") <> trabajador Then
    '    If hoja.Range("N62") <> "" And hoja.Range("N62") <> trabajador Then
    '        If hoja.Range("N63") <> "" And hoja.Range("N63") <> trabajador Then
    '            hoja.Range("N64") = trabajador
    '        Else
    '            hoja.Range("N63") = trabajador
    '        End If
    '    Else
    '        hoja.Range("N62") = trabajador
    '    End If
    'Else
    '    hoja.Range("N61") = trabajador
    'End If
End Sub
Sub ObtenerArticulo(ean As String)
    Dim consultaSQL As String
                    
    consultaSQL = "SELECT F_ART.CODART AS CODIGO, F_ART.EQUART AS EQUIVALENTE, F_ART.DESART AS DESCRIPCION, ' - ' AS TALLA, ' - ' AS SECTOR, F_ART.CP4ART AS FINCA, F_ART.CP5ART AS AUTORIZACION " & _
                    "FROM F_ART " & _
                    "WHERE F_ART.EANART='" & ean & "'; " & _
                    "Union " & _
                    "SELECT F_EAC.ARTEAC AS CODIGO, F_ART.EQUART AS EQUIVALENTE, F_ART.DESART AS DESCRIPCION, F_CE1.DESCE1 AS TALLA, F_CE2.DESCE2 AS SECTOR, F_ART.CP4ART AS FINCA, F_ART.CP5ART AS AUTORIZACION " & _
                    "FROM ((F_EAC LEFT JOIN F_CE1 ON F_EAC.CE1EAC = F_CE1.CODCE1) LEFT JOIN F_CE2 ON F_EAC.CE2EAC = F_CE2.CODCE2) INNER JOIN F_ART ON F_EAC.ARTEAC = F_ART.CODART " & _
                    "WHERE F_EAC.EANEAC='" & ean & "';"

    Set datos = cn.Execute(consultaSQL)
End Sub
Sub Articulo(ean As String, fila As Long, columna As Long, hoja As Worksheet, anyo As String)
    Dim autorizacion As String
    Dim otroAnyo As String
    
    Call AbrirConexion(anyo)
    
    Call ObtenerArticulo(ean)
    
    If datos.EOF Then
        Call CerrarConexion
        otroAnyo = anyo - 1
        Call AbrirConexion(otroAnyo)
        Call ObtenerArticulo(ean)
        If datos.EOF Then
            Call CerrarConexion
            otroAnyo = otroAnyo - 1
            Call AbrirConexion(otroAnyo)
            Call ObtenerArticulo(ean)
        End If
    End If
    
    If Not datos.EOF Then
        hoja.Cells(fila, columna + 2) = datos.Fields("CODIGO")
        hoja.Cells(fila, columna + 4) = datos.Fields("EQUIVALENTE")
        If otroAnyo <> "" Then
            hoja.Cells(fila, columna + 5) = datos.Fields("DESCRIPCION") & " [Anyo " & otroAnyo & "]"
            otroAnyo = ""
        Else
            hoja.Cells(fila, columna + 5) = datos.Fields("DESCRIPCION")
        End If
        If datos.Fields("TALLA") <> " - " Then
            hoja.Cells(fila, columna + 13) = datos.Fields("TALLA")
        End If
        If datos.Fields("SECTOR") <> " - " Then
            hoja.Cells(fila, columna + 15) = datos.Fields("SECTOR")
        End If
        hoja.Cells(fila, columna + 17) = datos.Fields("FINCA")
        
        ' Obtener el campo de autorización
        autorizacion = datos.Fields("AUTORIZACION")
        
        ' Verificar si el campo CP5ART contiene "NO"
        If InStr(autorizacion, "NO") > 0 Then
            ' Si contiene "NO", aplicar formato de negrita, cursiva y tachado al rango
            Set rango = hoja.Range(hoja.Cells(fila, columna + 2), hoja.Cells(fila, columna + 12))
            
            With rango
                .Font.Bold = True
                .Font.Italic = True
                .Font.Strikethrough = True
                .Font.Color = RGB(255, 0, 0) ' Cambiar color de la fuente a rojo
            End With
        End If
        
        datos.MoveNext
    End If
    
    Call CerrarConexion
End Sub
Sub pedido(nPedido As String, pagina As Integer, hoja As Worksheet, anyo As String)
    Dim consultaSQL As String
    
    Call AbrirConexion(anyo)
                    
    consultaSQL = "SELECT F_PCL.CODPCL, F_CLI.NOFCLI, F_CLI.NOCCLI, F_CLI.CODCLI, F_PCL.OB1PCL " & _
                    "FROM F_PCL INNER JOIN F_CLI ON F_PCL.CLIPCL = F_CLI.CODCLI " & _
                    "WHERE F_PCL.CODPCL= " & nPedido & ";"

    Set datos = cn.Execute(consultaSQL)
    
    If Not datos.EOF Then
        ' hoja 1
        hoja.Range("A8") = datos.Fields("CODCLI")
        hoja.Range("D8") = datos.Fields("NOFCLI")
        hoja.Range("D9") = datos.Fields("NOCCLI")
        hoja.Range("A62") = datos.Fields("OB1PCL")
        ' hoja 2
        hoja.Range("T8") = datos.Fields("CODCLI")
        hoja.Range("W8") = datos.Fields("NOFCLI")
        hoja.Range("W9") = datos.Fields("NOCCLI")
        hoja.Range("T62") = datos.Fields("OB1PCL")
        ' hoja 3
        hoja.Range("A72") = datos.Fields("CODCLI")
        hoja.Range("D72") = datos.Fields("NOFCLI")
        hoja.Range("D73") = datos.Fields("NOCCLI")
        hoja.Range("A126") = datos.Fields("OB1PCL")
        ' hoja 4
        hoja.Range("T72") = datos.Fields("CODCLI")
        hoja.Range("W72") = datos.Fields("NOFCLI")
        hoja.Range("W73") = datos.Fields("NOCCLI")
        hoja.Range("T126") = datos.Fields("OB1PCL")
    End If
    
    Call CerrarConexion
End Sub
Sub DatosPedido(tractora As String, remolque As String, finca As String, zona As String, nPedido As String, peso As Long, fecha As Date, pagina As Integer, hoja As Worksheet, anyo As String)
    ' hoja 1
    hoja.Range("M2") = finca
    hoja.Range("M4") = zona
    hoja.Range("A13") = tractora
    hoja.Range("G13") = remolque
    hoja.Range("N13") = nPedido
    hoja.Range("R13") = fecha
    hoja.Range("J64") = peso
    ' hoja 2
    hoja.Range("AF2") = finca
    hoja.Range("AF4") = zona
    hoja.Range("T13") = tractora
    hoja.Range("Z13") = remolque
    hoja.Range("AG13") = nPedido
    hoja.Range("AK13") = fecha
    'hoja.Range("AE13") = hoja.Range("L13")
    hoja.Range("AC64") = peso
    ' hoja 3
    hoja.Range("M66") = finca
    hoja.Range("M68") = zona
    hoja.Range("A77") = tractora
    hoja.Range("G77") = remolque
    hoja.Range("N77") = nPedido
    hoja.Range("R77") = fecha
    'hoja.Range("L77") = hoja.Range("L13")
    hoja.Range("J128") = peso
    ' hoja 4
    hoja.Range("AF66") = finca
    hoja.Range("AF68") = zona
    hoja.Range("T77") = tractora
    hoja.Range("Z77") = remolque
    hoja.Range("AG77") = nPedido
    hoja.Range("AK77") = fecha
    'hoja.Range("AE77") = hoja.Range("L13")
    hoja.Range("AC128") = peso
    
    Call pedido(nPedido, pagina, hoja, anyo)
    
    Select Case pagina
        Case 1
            hoja.Range("P13") = "1 de 1"
        Case 2
            hoja.Range("P13") = "1 de 2"
            hoja.Range("AI13") = "2 de 2"
        Case 3
            hoja.Range("P13") = "1 de 3"
            hoja.Range("AI13") = "2 de 3"
            hoja.Range("P77") = "3 de 3"
        Case 4
            hoja.Range("P13") = "1 de 2"
            hoja.Range("AI13") = "2 de 4"
            hoja.Range("P77") = "3 de 4"
            hoja.Range("AI77") = "4 de 4"
    End Select

End Sub
Sub LimpiarPedido(hoja As Worksheet)
    
    Call Limpiar(hoja.Range("A17:S57"))
    Call Limpiar(hoja.Range("T17:AL57"))
    Call Limpiar(hoja.Range("A81:S121"))
    Call Limpiar(hoja.Range("T81:AL121"))
    hoja.Range("AI13") = ""
    hoja.Range("P77") = ""
    hoja.Range("AI77") = ""
    hoja.Range("M61:S64") = ""
    hoja.Range("AG61:AL64") = ""
    hoja.Range("M125:S128") = ""
    hoja.Range("AG125:AL128") = ""
    hoja.Range("J64") = ""
    hoja.Range("AC64") = ""
    hoja.Range("J128") = ""
    hoja.Range("AC128") = ""

End Sub
Sub Punteo_Inicial()
    Dim csvLibro As Workbook
    Dim csvHoja As Worksheet
    Dim punteoLibro As Workbook
    Dim punteoHoja As Worksheet
    Dim ruta As String
    Dim n_archivo As String
    
    If Hoja11.inicial_list.Value = "" Then
        End
    Else
        n_archivo = Hoja11.inicial_list.Value
    End If
    ruta = "C:\Users\Usuario\Documents\Web\Punteo\picking_" & n_archivo & "_I.xlsx"
    
    Set punteoLibro = Workbooks(ThisWorkbook.Name)
    Set punteoHoja = punteoLibro.Worksheets("Punteo Inicial")
    
    Set csvLibro = Workbooks.Open(ruta)
    Set csvHoja = csvLibro.Worksheets(1)
    
    'punteoHoja.Range("L13") = "Punteo Inicial"
    
    Call Punteo(csvHoja, punteoHoja)
    
    csvLibro.Close
    
    punteoHoja.Select
    punteoHoja.Range("L10").Select
End Sub
Sub Punteo_Final()
    Dim csvLibro As Workbook
    Dim csvHoja As Worksheet
    Dim punteoLibro As Workbook
    Dim punteoHoja As Worksheet
    Dim punteoFinal As Worksheet
    Dim ruta As String
    Dim n_archivo As String
    
    If Hoja11.final_list.Value = "" Then
        End
    Else
        n_archivo = Hoja11.final_list.Value
    End If
    
    ' comprobamos si existe punteo inicial
    ruta = "C:\Users\Usuario\Documents\Web\Punteo\picking_" & n_archivo & "_I.xlsx"
    
    If ruta <> "" Then
    
        Set punteoLibro = Workbooks(ThisWorkbook.Name)
        Set punteoFinal = punteoLibro.Worksheets("Punteo Inicial")
        
        Set csvLibro = Workbooks.Open(ruta)
        Set csvHoja = csvLibro.Worksheets(1)
        
        Call Punteo(csvHoja, punteoFinal)
        
        Set punteoFinal = punteoLibro.Worksheets("Punteo")
        
        Call Punteo(csvHoja, punteoFinal)
        
        csvLibro.Close
        
        punteoFinal.Select
        punteoFinal.Range("L10").Select
    Else
        MsgBox "No hay punteo inicial"
    End If
    
    ruta = "C:\Users\Usuario\Documents\Web\Punteo\picking_" & n_archivo & "_F.xlsx"
    
    Set punteoLibro = Workbooks(ThisWorkbook.Name)
    Set punteoHoja = punteoLibro.Worksheets("Punteo Final")
    
    Set csvLibro = Workbooks.Open(ruta)
    Set csvHoja = csvLibro.Worksheets(1)
    
    'punteoHoja.Range("L13") = "Restos Carga"
    
    Call Punteo(csvHoja, punteoHoja, punteoFinal)
    
    csvLibro.Close
End Sub
Sub Punteo(csvHoja As Worksheet, punteoHoja As Worksheet, Optional punteoFinal As Worksheet)
    Dim ultimo As Long
    Dim cont As Long
    Dim n_linea As Long
    Dim anyo As String
    Dim anyoPedido As String
    
    Dim pagina As Integer
    Dim columna As Long
    Dim fila As Long
    Dim punteos As Integer
    Dim j As Long
    
    Dim tractora As String
    Dim remolque As String
    Dim finca As String
    Dim zona As String
    Dim nPedido As String
    Dim ean As String
    Dim cantidad As Long
    Dim peso As Long
    Dim fecha As Date
    Dim trabajador As String
    
    Call LimpiarPedido(punteoHoja)
    
    ultimo = csvHoja.Range("A" & Rows.Count).End(xlUp).Row
    If ultimo < 3 Then
        End
    End If
    
    tractora = RTrim(Mid(csvHoja.Cells(1, 2), 21))
    remolque = RTrim(Mid(csvHoja.Cells(1, 3), 23))
    finca = RTrim(Mid(csvHoja.Cells(1, 4), 7))
    zona = RTrim(Mid(csvHoja.Cells(1, 5), 6))
    If Len(csvHoja.Cells(1, 6)) > 18 Then
        peso = RTrim(Mid(csvHoja.Cells(1, 6), 18))
    Else
        peso = 0
    End If
    trabajador = csvHoja.Cells(3, 1)
    nPedido = csvHoja.Cells(3, 2)
    fecha = csvHoja.Cells(3, 5)
    
    pagina = 1
    columna = 0
    fila = 17
    punteos = 1
    j = 1
    anyoPedido = "20" & Left(nPedido, 2)
    anyo = Year(Date)
    
    Call Firmado(trabajador, punteoHoja)
    
    For i = 3 To ultimo
        If fila = 58 Then
            If pagina = 1 Then
                fila = 17
                columna = 19
            Else
                fila = 81
                columna = 0
            End If
            pagina = pagina + 1
        End If
        If fila = 122 Then
            pagina = 4
            fila = 81
            columna = 19
        End If
        punteoHoja.Cells(fila, columna + 1) = i - j - 1
        ean = csvHoja.Cells(i, 3)
        Call Articulo(ean, fila, columna, punteoHoja, anyo)
        punteoHoja.Cells(fila, columna + 19) = csvHoja.Cells(i, 4)
        If Not punteoFinal Is Nothing Then
            Call Restar(punteoHoja.Cells(fila, columna + 2), punteoHoja.Cells(fila, columna + 13), punteoHoja.Cells(fila, columna + 15), punteoHoja.Cells(fila, columna + 19), punteoFinal)
        End If
        fila = fila + 1
    Next i
    
    Call DatosPedido(tractora, remolque, UCase(finca), UCase(zona), nPedido, peso, fecha, pagina, punteoHoja, anyoPedido)
    
    Call Setear(punteoHoja)
End Sub
Sub Restar(codigo As String, talla As String, sector As String, cantidad As Long, punteoFinal As Worksheet)
    Dim fila As Long
    Dim pagina As Long
    Dim columna As Long
    
    pagina = 1
    columna = 0
    
    For i = 17 To 121
        If i = 58 Then
            If pagina = 1 Then
                i = 17
                columna = 19
            Else
                i = 81
                columna = 0
            End If
            pagina = pagina + 1
        End If
        If i = 122 Then
            pagina = 4
            i = 81
            columna = 19
        End If
        If punteoFinal.Cells(i, columna + 2) = codigo And punteoFinal.Cells(i, columna + 13) = talla And punteoFinal.Cells(i, columna + 15) = sector Then
            punteoFinal.Cells(i, columna + 19) = punteoFinal.Cells(i, columna + 19) - cantidad
            Exit For
        End If
    Next i
End Sub
Sub Setear(hoja As Worksheet)
    ' hoja 1
    hoja.Range("A17:S57").Font.Size = 8
    hoja.Range("O17:O57").Font.Bold = True
    hoja.Range("O17:O57").Font.Italic = True
    hoja.Range("N61").Font.FontStyle = "Cooper Black"
    ' hoja 2
    hoja.Range("T17:AL57").Font.Size = 8
    hoja.Range("AH17:AH57").Font.Bold = True
    hoja.Range("AH17:AH57").Font.Italic = True
    hoja.Range("AG61").Font.FontStyle = "Cooper Black"
    ' hoja 3
    hoja.Range("A81:S121").Font.Size = 8
    hoja.Range("O81:O121").Font.Bold = True
    hoja.Range("O81:O121").Font.Italic = True
    hoja.Range("N125").Font.FontStyle = "Cooper Black"
    ' hoja 4
    hoja.Range("T81:AL121").Font.Size = 8
    hoja.Range("AH81:AH121").Font.Bold = True
    hoja.Range("AH81:AH121").Font.Italic = True
    hoja.Range("AG125").Font.FontStyle = "Cooper Black"
End Sub
Sub RellenarComoboBox()
    Dim ruta As String
    Dim pedido() As String
    Dim archivo As String
    Dim inicial As String
    Dim final As String
    
    final = Hoja11.final_list.Value
    inicial = Hoja11.inicial_list.Value
    
    Hoja11.inicial_list.Clear
    Hoja11.final_list.Clear
    ruta = "C:\Users\Usuario\Documents\Web\Punteo\*.xlsx"
    archivo = Dir(ruta, vbArchive)
    While archivo <> ""
        pedido = Split(archivo, "_")
        If Left(pedido(2), 1) = "I" Then
            Hoja11.inicial_list.AddItem (pedido(1))
        ElseIf Left(pedido(2), 1) = "F" Then
            Hoja11.final_list.AddItem (pedido(1))
        End If
        archivo = Dir
    Wend
    
    If final <> "" Then
        Hoja11.final_list.Value = final
    End If
    If inicial <> "" Then
        Hoja11.inicial_list.Value = inicial
    End If
End Sub
