package starloader.launcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.JSONObject;

import de.geolykt.starloader.launcher.JavaDownloader;

/**
 * Bootstrap for the Starloader-Launcher compiled with Java 8 compatibility.
 */
public class J8Boot {

    public static void main(String[] args) {
        try {
            String s = new String(Files.readAllBytes(Paths.get("config.json")), StandardCharsets.UTF_8);
            JSONObject cfg = new JSONObject(s);
            cfg.put("mainClass", "de.geolykt.starloader.launcher.CLILauncher");
            JavaDownloader.runMain(cfg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
