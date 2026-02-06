# QR Generator App (Java)

Pequena app en Java que genera codigos QR a partir del texto que le pases por linea de comandos. Usa Maven y la libreria ZXing.

Requisitos
- JDK 11 o superior
- Maven

Uso rapido
1. Ejecuta `run.bat` con el texto entre comillas.
2. Opcional: define el archivo de salida y el tamano en pixeles.

Ejemplos
- `run.bat "Hola mundo"`
- `run.bat "https://example.com" qr.png 512`

Parametros
- `text` (obligatorio): el texto para codificar.
- `output.png` (opcional): ruta del archivo PNG de salida. Por defecto `output.png`.
- `size` (opcional): tamano en pixeles. Por defecto `300`.

Salida
- Se genera un PNG con el QR en la ruta indicada.

Notas
- Si pasas una ruta con carpetas que no existen, se crean automaticamente.