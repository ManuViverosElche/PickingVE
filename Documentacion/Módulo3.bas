Attribute VB_Name = "Módulo3"
Option Explicit
Public contador As Integer
Public datos As Object
Public cn As Object
Public anyo As Date
Public stock As String
' --- Manejo de errores en funciones existentes ---
Sub AbrirConexion(anyo As String)
    On Error GoTo ErrorHandler
    Dim ruta As String
    Dim conexion As String
    
    ruta = "\\nasviveroselche\FACTURACION\FactuSOL 2017\Datos\FS\014"
    conexion = "Provider=Microsoft.ACE.OLEDB.12.0;Data Source=" & ruta & anyo & ".accdb"
    Set cn = CreateObject("ADODB.Connection")
    cn.Open conexion
    Exit Sub
ErrorHandler:
    Debug.Print "Error al abrir la conexión: " & Err.Description
    MsgBox "No se pudo abrir la conexión a la base de datos.", vbCritical
    Resume Next
End Sub
Sub CerrarConexion()
    On Error Resume Next
    If Not datos Is Nothing Then datos.Close
    Set datos = Nothing
    If Not cn Is Nothing Then cn.Close
    Set cn = Nothing
End Sub
Sub Limpiar(rango As Range)
    On Error Resume Next
    rango.ClearContents
    rango.Font.Bold = False
    rango.Font.Italic = False
    rango.Font.Strikethrough = False
    rango.Font.Size = 9
    rango.Font.ColorIndex = 1
    rango.Interior.ColorIndex = 0
    rango.Borders(xlInsideHorizontal).LineStyle = xlNone
    rango.Borders(xlInsideVertical).LineStyle = xlNone
    rango.Borders(xlEdgeBottom).LineStyle = xlNone
    rango.Borders(xlEdgeLeft).LineStyle = xlNone
    rango.Borders(xlEdgeRight).LineStyle = xlNone
End Sub
' --- Función para obtener la finca optimizada ---
Function finca() As String
    On Error GoTo ErrorHandler
    Dim fincas As Object
    Set fincas = CreateObject("Scripting.Dictionary")
    ' Definir las fincas en el diccionario
    fincas.Add "Borisa", "BORISA"
    fincas.Add "Aspe", "ASPE"
    fincas.Add "Carrefour", "CARREFOUR"
    fincas.Add "Crevillente", "CREVILLENTE"
    fincas.Add "100 Tahullas", "100 TAHULLAS"
    fincas.Add "Canalillo", "CANALILLO"
    fincas.Add "Derramador", "DERRAMADOR"
    fincas.Add "La Ermita", "LA ERMITA"
    fincas.Add "El Surdet", "EL SURDET"
    fincas.Add "Gari", "GARI"
    fincas.Add "Jose Lopez", "JOSE LOPEZ"
    fincas.Add "La Balsa", "LA BALSA"
    fincas.Add "La Hoya", "LA HOYA"
    fincas.Add "La L", "LA L"
    fincas.Add "La Romana", "LA ROMANA"
    fincas.Add "Matola", "MATOLA"
    fincas.Add "Oficina", "OFICINA"
    fincas.Add "Puçol", "PUÇOL"
    fincas.Add "Sector AT", "SECTOR AT"
    fincas.Add "Suegra", "SUEGRA"
    fincas.Add "Ulises", "ULISES"
    
    ' Retornar la finca correspondiente
    finca = IIf(fincas.exists(ActiveSheet.Name), fincas(ActiveSheet.Name), "")
    Exit Function
ErrorHandler:
    finca = "ERROR"
    Debug.Print "Error en la función finca: " & Err.Description
End Function
Function Mayor(n1 As Long, n2 As Long, n3 As Long) As Long
    If n1 > n2 Then
        If n1 > n3 Then
            Mayor = n1
        Else
            Mayor = n3
        End If
    Else
        If n2 > n3 Then
            Mayor = n2
        Else
            Mayor = n3
        End If
    End If
End Function
Sub Stock_0(v As Boolean)
    If v Then
        stock = ""
    Else
        stock = "F_STC.ACTSTC <> 0 And "
    End If
End Sub
' --- Función para obtener el empleado optimizada ---
Function Empleado(email As String) As String
    On Error GoTo ErrorHandler
    Dim empleados As Object
    Set empleados = CreateObject("Scripting.Dictionary")
    ' Definir empleados en el diccionario
    empleados.Add "administracion@viveroselche.es", "Manu"
    empleados.Add "info@viveroselche.es", "Mari Carmen"
    empleados.Add "export@viveroselche.es", "Rubén"
    empleados.Add "test@test.es", "Test"
    empleados.Add "manu.mirgra@gmail.com", "Manu prueba"
    empleados.Add "emeter79@gmail.com", "Emeterio"
    empleados.Add "inventario@viveroselche.es", "David"
    empleados.Add "carlosjaviergarlito@gmail.com", "Carlos"
    empleados.Add "tecnico@viveroselche.es", "Antonio"
    empleados.Add "pickingviveroselche@gmail.com", "Mariano"
    empleados.Add "fedecm90@gmail.com", "Fede"
    empleados.Add "lauuriitaa98@gmail.com", "Laura"
    
    ' Retornar el empleado correspondiente
    Empleado = IIf(empleados.exists(email), empleados(email), "No asignado")
    Exit Function
ErrorHandler:
    Empleado = "ERROR"
    Debug.Print "Error en la función Empleado: " & Err.Description
End Function
Function Localizacion(zona As String) As String
    Dim lista() As String
    lista = Split(zona, ": ")
    Localizacion = lista(1)
End Function
