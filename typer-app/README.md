# Typer App

Pequeña app en Java Swing que **emula escritura** con `java.awt.Robot` (sin usar pegar/portapapeles).

## Requisitos

- JDK instalado (Java 8+)
- Windows con teclado numérico (Num Lock activado)

## Uso

1. Ejecuta el `.bat`:

```bat
run.bat
```

2. Escribe/pega el texto en el área.
3. Presiona el botón.
4. Tienes 5 segundos para enfocar la ventana donde quieras que se escriba el texto.

## Notas

- La escritura comienza después de 5 segundos.
- Si el texto está vacío, no se hace nada.
- Letras, números y espacios se teclean directo.
- Tildes y símbolos (¿? ¡! {} [] () etc) se emulan con Alt+Códigos (numpad).
- Para Unicode fuera de 0-255 se requeriría `SendInput` en modo Unicode.
