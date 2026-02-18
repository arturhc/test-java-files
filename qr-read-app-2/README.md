# QR Read App 2 (Java + ffmpeg)

Lee QRs desde un video, reconstruye payload Base64 en un ZIP y lo descomprime.

## Flujo
1. Extrae frames con `ffmpeg` a una carpeta temporal.
2. Compara frames consecutivos para detectar cambios.
3. Guarda los frames detectados en `frames/`.
4. Lee cada QR detectado con ZXing.
5. Omite `__WARMUP__` y lo reporta en consola.
6. Concatena chunks Base64 y reconstruye un `.zip` en `zips/`.
7. Descomprime ese `.zip` en una carpeta hermana con el mismo nombre base.

## Requisitos
- JDK 11 o superior
- `ffmpeg` en `PATH`

## Dependencias locales
- `libs/core-3.5.4.jar`
- `libs/javase-3.5.4.jar`

## Uso rapido
1. Coloca el video en `video/qrs.mp4` o pasa ruta por parametro.
2. Ejecuta:
   - `run.bat`
   - o `run.bat --video video\qrs.mp4 --frames frames --zips zips --fps 6 --threshold 0.10 --analysis-size 64`

## Parametros opcionales
- `--video <ruta>`
- `--frames <directorio_salida>`
- `--zips <directorio_salida_zip>`
- `--fps <n>`
- `--threshold <0..1>`
- `--analysis-size <n>`

Tambien acepta posicionales:
- `run.bat <video.mp4> <framesDir> <zipsDir>`

## Salida
- `frames/change_XXXX_frame_YYYYYYYY.png`: frames donde se detecto cambio.
- `zips/dd-MM-yyyy_HH-mm-ss.zip`: ZIP reconstruido (zona horaria Mexico).
- `zips/dd-MM-yyyy_HH-mm-ss/`: contenido descomprimido del ZIP.

El nombre del ZIP se genera con fecha/hora de Mexico. Si ese nombre ya existe, agrega sufijo `_1`, `_2`, etc.
