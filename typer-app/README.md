# Typer App

App en Java Swing que **emula escritura** con `java.awt.Robot` (sin usar pegar/portapapeles).

## Requisitos

- JDK instalado (Java 8+)
- Windows con teclado numérico (Num Lock activado)

## Uso

1. Ejecuta el `.bat`:

```bat
run.bat
```

2. Para texto: escribe/pega en el área y selecciona `Texto`.
3. Para archivo: selecciona `Archivo (Base64)` y elige un archivo.
4. Presiona el botón.
5. Tienes 5 segundos para enfocar la ventana donde quieras que se escriba el texto.

## Notas

- La escritura comienza después de 5 segundos.
- Si no hay texto/archivo, no se hace nada.
- Letras, números y espacios se teclean directo.
- Tildes y símbolos (¿? ¡! {} [] () etc) se emulan con Alt+Códigos (numpad).
- El archivo se convierte a Base64 y luego se teclea.
- Para Unicode fuera de 0-255 se requeriría `SendInput` en modo Unicode.
