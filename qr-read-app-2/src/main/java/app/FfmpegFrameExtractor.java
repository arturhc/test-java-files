package app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

final class FfmpegFrameExtractor {
    private FfmpegFrameExtractor() {
    }

    static void extractFrames(Path videoPath, Path tempFramesDir, int fps) throws IOException, InterruptedException {
        String fpsFilter = "fps=" + fps;
        Path outputPattern = tempFramesDir.resolve("frame_%08d.png");
        List<String> command = List.of(
                "ffmpeg",
                "-hide_banner",
                "-loglevel", "error",
                "-y",
                "-i", videoPath.toString(),
                "-vf", fpsFilter,
                outputPattern.toString()
        );

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("ffmpeg devolvio codigo " + exitCode + ". Salida: " + output);
        }
    }
}
