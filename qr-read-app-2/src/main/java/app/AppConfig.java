package app;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class AppConfig {
    private static final Path DEFAULT_VIDEO = Path.of("video", "qrs.mp4");
    private static final Path DEFAULT_FRAMES_DIR = Path.of("frames");
    private static final Path DEFAULT_ZIPS_DIR = Path.of("zips");
    private static final int DEFAULT_FPS = 6;
    private static final double DEFAULT_CHANGE_THRESHOLD = 0.10;
    private static final int DEFAULT_ANALYSIS_SIZE = 64;

    private final Path videoPath;
    private final Path framesOutputDir;
    private final Path zipsOutputDir;
    private final int fps;
    private final double changeThreshold;
    private final int analysisSize;

    private AppConfig(
            Path videoPath,
            Path framesOutputDir,
            Path zipsOutputDir,
            int fps,
            double changeThreshold,
            int analysisSize
    ) {
        this.videoPath = videoPath;
        this.framesOutputDir = framesOutputDir;
        this.zipsOutputDir = zipsOutputDir;
        this.fps = fps;
        this.changeThreshold = changeThreshold;
        this.analysisSize = analysisSize;
    }

    static AppConfig fromArgs(String[] args) {
        Path video = DEFAULT_VIDEO;
        Path frames = DEFAULT_FRAMES_DIR;
        Path zips = DEFAULT_ZIPS_DIR;
        int fps = DEFAULT_FPS;
        double threshold = DEFAULT_CHANGE_THRESHOLD;
        int analysisSize = DEFAULT_ANALYSIS_SIZE;

        List<String> positional = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--video":
                    video = Path.of(requireValue(args, ++i, "--video"));
                    break;
                case "--frames":
                    frames = Path.of(requireValue(args, ++i, "--frames"));
                    break;
                case "--zips":
                    zips = Path.of(requireValue(args, ++i, "--zips"));
                    break;
                case "--fps":
                    fps = Integer.parseInt(requireValue(args, ++i, "--fps"));
                    break;
                case "--threshold":
                    threshold = Double.parseDouble(requireValue(args, ++i, "--threshold"));
                    break;
                case "--analysis-size":
                    analysisSize = Integer.parseInt(requireValue(args, ++i, "--analysis-size"));
                    break;
                case "--help":
                    break;
                default:
                    if (arg.startsWith("--")) {
                        throw new IllegalArgumentException("Bandera no soportada: " + arg);
                    }
                    positional.add(arg);
                    break;
            }
        }

        if (!positional.isEmpty()) {
            video = Path.of(positional.get(0));
        }
        if (positional.size() > 1) {
            frames = Path.of(positional.get(1));
        }
        if (positional.size() > 2) {
            zips = Path.of(positional.get(2));
        }
        if (positional.size() > 3) {
            throw new IllegalArgumentException("Demasiados argumentos posicionales.");
        }

        if (fps <= 0) {
            throw new IllegalArgumentException("--fps debe ser > 0");
        }
        if (analysisSize < 8) {
            throw new IllegalArgumentException("--analysis-size debe ser >= 8");
        }
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("--threshold debe estar entre 0 y 1");
        }

        return new AppConfig(video, frames, zips, fps, threshold, analysisSize);
    }

    static boolean hasHelpFlag(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    static void printUsage() {
        System.out.println("Uso:");
        System.out.println("  java app.QrFrameChangeDetectorApp [video.mp4] [framesDir] [zipsDir]");
        System.out.println("  java app.QrFrameChangeDetectorApp --video video\\qrs.mp4 --frames frames --zips zips --fps 6 --threshold 0.10 --analysis-size 64");
    }

    private static String requireValue(String[] args, int index, String name) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Falta valor para " + name);
        }
        return args[index];
    }

    Path videoPath() {
        return videoPath;
    }

    Path framesOutputDir() {
        return framesOutputDir;
    }

    Path zipsOutputDir() {
        return zipsOutputDir;
    }

    int fps() {
        return fps;
    }

    double changeThreshold() {
        return changeThreshold;
    }

    int analysisSize() {
        return analysisSize;
    }
}
