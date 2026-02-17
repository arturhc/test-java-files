# QR Generator App (Java)

App en Java con interfaz Swing que genera codigos QR a partir del texto que pegues en un area de texto. Usa JARs locales de ZXing en `libs/`.

Requisitos
- JDK 11 o superior

Uso rapido
1. Ejecuta `run.bat`.
2. Elige si quieres usar **Texto** o **Archivo (Base64)**.
3. Si eliges archivo, selecciona uno con el boton **Seleccionar archivo**.
4. Haz clic en **Generar QR**.
5. La app agrega un primer QR de arranque (`__WARMUP__`) para estabilizar lectura por video.
6. Antes de mostrar, pre-genera todos los QRs en una carpeta temporal.
7. Los muestra cada 500 ms y al terminar elimina automaticamente esos archivos temporales.
8. Durante la pre-generacion muestra progreso `X/Y` en pantalla y logs en consola.

Dependencias locales
- `libs/core-3.5.4.jar`
- `libs/javase-3.5.4.jar`
