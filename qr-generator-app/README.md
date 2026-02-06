# QR Generator App (Java)

App en Java con interfaz Swing que genera codigos QR a partir del texto que pegues en un area de texto. Usa JARs locales de ZXing en `libs/`.

Requisitos
- JDK 11 o superior

Uso rapido
1. Ejecuta `run.bat`.
2. Elige si quieres usar **Texto** o **Archivo (Base64)**.
3. Si eliges archivo, selecciona uno con el boton **Seleccionar archivo**.
4. Haz clic en **Generar QR**.
5. Si el texto supera 2000 caracteres, se divide en partes y se muestran en secuencia cada 2 segundos.

Dependencias locales
- `libs/core-3.5.4.jar`
- `libs/javase-3.5.4.jar`
