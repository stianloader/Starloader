package de.geolykt.starloader.launcher;

import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.JSONObject;

public class JavaDownloader {

    private static void moveJRE(Path path) throws IOException {

        long fileCount = Files.list(path).count();
        Optional<Path> subfolder = Files.list(path).findFirst();

        if (fileCount == 1) {
            Files.walkFileTree(subfolder.get(), new FileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.equals(subfolder.get())) {
                        return FileVisitResult.CONTINUE;
                    }
                    Files.createDirectory(path.resolve(subfolder.get().relativize(dir)));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.move(file, path.resolve(subfolder.get().relativize(file)));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            System.out.println("JRE already installed");
        }
    }

    private static void downloadJRE(Path path) {
        String downloadLoc = SystemOS.getCurrentOS().getJDKDownloadPath();
        URI uri = URI.create(downloadLoc);
        System.out.println("Downloading JDK... please wait");
        try {
            URLConnection connection = uri.toURL().openConnection();
            connection.connect();
            long total = connection.getContentLengthLong();
            System.out.println("Download is " + total + " bytes long");
            Path packed = Files.createTempFile("jdkdownload", "tmp");
            Files.copy(new CompletionAnnounceInputStream(connection.getInputStream(), total), packed, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Actually transfered " + Files.size(packed) + " bytes. Starting to unpack now");

            if (SystemOS.getCurrentOS() == SystemOS.LINUX) {
                // We assume that they have tar installed.
                new ProcessBuilder("tar", "-xvzf", packed.toAbsolutePath().toString(), "-C", path.toAbsolutePath().toString())
                        .inheritIO()
                        .start()
                        .waitFor();
            } else if (SystemOS.getCurrentOS() == SystemOS.WINDOWS) {
                try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(packed))) {
                    for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                        if (entry.isDirectory()) {
                            zip.closeEntry();
                            continue;
                        }
                        String pathname = entry.getName();
                        if (pathname.charAt(0) == '/') {
                            pathname = pathname.substring(1);
                        }
                        Files.copy(zip, path.resolve(entry.getName()));
                        zip.closeEntry();
                    }
                }
            } else {
                throw new UnsupportedOperationException("Unsupported os: " + SystemOS.getCurrentOS());
            }

            moveJRE(path);
            System.out.println("Unpacked");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void downloadJRE17() {

        Path jre17path = Paths.get("jre17");

        if (!Files.exists(jre17path)) {

            if (SystemOS.getCurrentOS() == SystemOS.MAC) {
                System.err.println("You are running an unsupported system (MacOS/OSX). Consult a developer if you want to know workarounds");
                return;
            }
            if (SystemOS.getCurrentOS() == SystemOS.WINDOWS) {
                System.err.println("Please be aware that the windows integration is only partially supported.");
            }

            try {
                Files.createDirectory(jre17path);
            } catch (IOException e) {
                e.printStackTrace();
            }
            downloadJRE(jre17path);
        } else {
            try {
                moveJRE(jre17path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void runMain(JSONObject configFile) {
        //
        boolean isJ17 = false;
        try {
            Class.forName("java.util.random.RandomGenerator");
            isJ17 = true;
        } catch (ClassNotFoundException ignore) {
            // Nothing to do
        }

        if (isJ17) {
            // No need to download it
            try {
                Class.forName(configFile.getString("mainClass")).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            downloadJRE17();

            Path jre17path = Paths.get("jre17");

            String javaloc;

            if (SystemOS.getCurrentOS() == SystemOS.WINDOWS) {
                javaloc = jre17path.resolve("bin").resolve("java.exe").toAbsolutePath().toString();
            } else {
                javaloc = jre17path.resolve("bin").resolve("java").toAbsolutePath().toString();
            }

            String[] vmoptions = configFile.getJSONArray("vmArgs").toList().toArray(new String[0]);

            String[] cmd = new String[vmoptions.length + 4];

            cmd[0] = javaloc;
            System.arraycopy(vmoptions, 0, cmd, 1, vmoptions.length);
            cmd[cmd.length - 3] = "-cp";
            cmd[cmd.length - 2] = JavaDownloader.class.getProtectionDomain().getCodeSource().getLocation().toString();
            System.out.println("Source: " + cmd[cmd.length - 2]);
            cmd[cmd.length - 1] = configFile.getString("mainClass");

            try {
                new ProcessBuilder(cmd).inheritIO().start().waitFor();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
                System.err.println("Used command: " + Arrays.toString(cmd));
            }
        }
    }
}
