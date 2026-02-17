# QR Reader App (Java + ffmpeg)

Lector de codigos QR desde un video MP4.

Flujo:
1. Extrae frames del video con `ffmpeg`.
2. Calcula diferencia entre frames consecutivos.
3. Cuando detecta cambio radical, intenta decodificar QR con ZXing.
4. Guarda cada QR detectado y el payload combinado.

## Requisitos
- JDK 11 o superior
- `ffmpeg` en `PATH`

## Dependencias locales
- `libs/core-3.5.4.jar`
- `libs/javase-3.5.4.jar`

## Uso rapido
1. Coloca tu video en `video/all3.mp4` o pasa la ruta por parametro.
2. Ejecuta:
   - `run.bat`
   - o `run.bat video\all3.mp4 out`

## Parametros opcionales
- `--video <ruta>`
- `--out <directorio>`
- `--fps <n>`
- `--threshold <0..1>`
- `--analysis-size <n>`
- `--decode-window <n>`

Ejemplo:
`run.bat --video video\all3.mp4 --out out --fps 12 --threshold 0.18 --decode-window 8`

## Salida
- `out/decoded_chunks.txt`: texto de cada QR detectado.
- `out/combined_payload.txt`: concatenacion en orden de todos los QRs.
- `out/combined_payload.bin`: solo si el payload combinado parece Base64 valido.

Nota: si el video incluye un QR de arranque `__WARMUP__`, el lector lo ignora automaticamente.
