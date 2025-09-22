package org.stianloader.sll;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import de.geolykt.starloader.util.JavaInterop;

/**
 * Obtain metadata information about this SLL (<b>s</b>tian<b>l</b>oader-<b>l</b>auncher) environment.
 *
 * <p>This mainly focuses on traits about the environment that might differ greatly between installations.
 * Though in truth the chance of there being significant difference is low as users will in general not
 * use their own SLL install but use whatever installation is already provided for them (i.e. through modpacks
 * or by the game itself).
 *
 * <p>Thus the traits are largely focused on the Mixin capabilities of SLL, alongside vendor information.
 *
 * @since 4.0.0-a20250922
 */
@AvailableSince("4.0.0-a20250922")
public class SLLEnvironment {

    @NotNull
    private static final String MIXIN_COMPILED_ARTIFACT_ID;
    @NotNull
    private static final String MIXIN_COMPILED_GROUP_ID;
    @NotNull
    private static final String MIXIN_COMPILED_VERSION;

    private static final boolean MIXIN_SPONGELIKE;

    @NotNull
    private static final String SLL_ARTIFACT_ID;
    @NotNull
    private static final String SLL_GROUP_ID;
    @NotNull
    private static final String SLL_VERSION;

    static {
        String sllGroup = null;
        String sllArtifact = null;
        String sllVersion = null;
        String mixinGroup = null;
        String mixinArtifact = null;
        String mixinVersion = null;
        String mixinSpongelike = null;
        try (InputStream is = SLLEnvironment.class.getResourceAsStream("/sll-environment-meta.json")) {
            if (is == null) {
                throw new IOException("Resource not found.");
            }
            JSONObject jsonObject = new JSONObject(new String(JavaInterop.readAllBytes(is), StandardCharsets.UTF_8));
            sllGroup = jsonObject.optString("sll_groupid", null);
            sllArtifact = jsonObject.optString("sll_artifactid", null);
            mixinGroup = jsonObject.optString("mixin_groupid", null);
            mixinArtifact = jsonObject.optString("mixin_artifact", null);
            mixinVersion = jsonObject.optString("mixin_version", null);
            mixinSpongelike = jsonObject.optString("mixin_spongelike", null);

            // Nightly-paperpusher declares 
            readPomMeta:
            try (InputStream is2 = SLLEnvironment.class.getResourceAsStream("/META-INF/maven/" + sllGroup + "/" + sllArtifact + "/pom.properties")) {
                if (is2 == null) {
                    sllVersion = jsonObject.optString("sll_version_fallback", "unknown");
                    break readPomMeta;
                }
                Properties properties = new Properties();
                properties.load(is);
                String reportedVersion = properties.getProperty("version");
                sllVersion = reportedVersion == null ? jsonObject.optString("sll_version_fallback", "unknown") : reportedVersion;
            } catch (IOException ignored) { }
        } catch (IOException e) {
            LoggerFactory.getLogger(SLLEnvironment.class).warn("Failed to fetch environment metadata.", e);
        }

        if (mixinSpongelike == null) {
            try {
                Class.forName("org.spongepowered.asm.mixin.Mixins", false, SLLEnvironment.class.getClassLoader());
                mixinSpongelike = "true";
            } catch (ClassNotFoundException cnfe) {
                mixinSpongelike = "false";
            }
        }

        MIXIN_COMPILED_ARTIFACT_ID = mixinArtifact == null ? "unknown" : mixinArtifact;
        MIXIN_COMPILED_GROUP_ID = mixinGroup == null ? "unknown" : mixinGroup;
        MIXIN_COMPILED_VERSION = mixinVersion == null ? "unknown" : mixinVersion;
        MIXIN_SPONGELIKE = Boolean.parseBoolean(mixinSpongelike);
        SLL_GROUP_ID = sllGroup == null ? "unknown" : sllGroup;
        SLL_ARTIFACT_ID = sllArtifact == null ? "unknown" : sllArtifact;
        SLL_VERSION = sllVersion == null ? "unknown" : sllVersion;
    }

    /**
     * Obtains the artifactId of the mixin implementation this version of SLL
     * was compiled against. This gives a good idea about what the runtime
     * mixin implementation is based on. In practice it doesn't mean much though,
     * but is useful information for the developer, especially as it highlights the
     * capabilities of the mixin implementation.
     *
     * <p>Please note that the artifactId alone does not suffice to completely
     * distinguish mixin implementations. For example, the stianloader organisation
     * as well as fabric both offer "sponge-mixin" under the "org.stianloader" and
     * "net.fabricmc" groupIds respectively. Hence, artifactIds and groupIds
     * are tightly linked together.
     *
     * <p>Also note that it is generally possible in the java ecosystem to swap out
     * singular classes or even entire packages or jars. As such, it is possible (albeit
     * unlikely outside of a modular environment) that the reported data does not match
     * the actual runtime data.
     *
     * @return The groupId of the mixin implementation the SLL environment
     * was built against.
     * @since 4.0.0-a20250922
     */
    @AvailableSince("4.0.0-a20250922")
    @NotNull
    @Contract(pure = true)
    public static String getMixinCompiledArtifactId() {
        return SLLEnvironment.MIXIN_COMPILED_ARTIFACT_ID;
    }

    /**
     * Obtains the groupId of the mixin implementation. This gives a
     * good idea on who the author of the mixin implementation is,
     * especially useful for assigning blame. In practice it only
     * hints at who is vendoring the mixin implementation binary.
     *
     * <p>Please note that the groupId alone does not suffice to distinguish
     * between mixin implementations. For example, the stianloader organisation
     * offers both "micromixin-transformer" and "sponge-mixin" under the
     * "org.stianloader" groupId.
     *
     * <p>Also note that it is generally possible in the java ecosystem to swap out
     * singular classes or even entire packages or jars. As such, it is possible (albeit
     * unlikely outside of a modular environment) that the reported data does not match
     * the actual runtime data.
     *
     * @return The groupId of the mixin implementation the SLL environment
     * was built against.
     * @since 4.0.0-a20250922
     */
    @AvailableSince("4.0.0-a20250922")
    @NotNull
    @Contract(pure = true)
    public static String getMixinCompiledGroupId() {
        return SLLEnvironment.MIXIN_COMPILED_GROUP_ID;
    }

    /**
     * Obtains the version of the mixin implementation this SLL binary was compiled
     * against. Note that it is generally possible in the java ecosystem to swap out
     * singular classes or even entire packages or jars. As such, it is possible (albeit
     * unlikely outside of a modular environment) that the reported data does not match
     * the actual runtime data.
     *
     * <p>Mixin implementation versions can be extremely chaotic. Sponge's Mixins and
     * micromixin-transformer, as well as other reimplementations will more likely than not
     * stay with a "sane" X.Y.Z release format. However, the version naming scheme of
     * forks will be a bit chaotic by nature. For example, stianloader's mixin fork
     * (but not micromixin-transformer) has version names such as "0.16.4-0.8.7-1-a20250906".
     *
     * @return The version string of the mixin implementation binary
     * @since 4.0.0-a20250922
     */
    @AvailableSince("4.0.0-a20250922")
    @NotNull
    @Contract(pure = true)
    public static String getMixinCompiledVersion() {
        return SLLEnvironment.MIXIN_COMPILED_VERSION;
    }

    /**
     * Obtains the artifactId of the SLL environment.
     *
     * <p>This gives a good hint as to what the SLL binary is being vendored as.
     * It also gives a hint to the intended mixin environment. For example, the
     * launcher binary using micromixin has the artifactId of launcher-micromixin.
     * However finding out information about the mixin environment through that way
     * is prone to failure in the future.
     *
     * @return SLL's artifact groupId.
     * @since 4.0.0-a20250922
     */
    @AvailableSince("4.0.0-a20250922")
    @NotNull
    @Contract(pure = true)
    public static String getSLLArtifactId() {
        return SLLEnvironment.SLL_ARTIFACT_ID;
    }

    /**
     * Obtains the groupId of the SLL environment. This gives a good hint at who the vendor of
     * the compiled SLL binary is.
     *
     * @return SLL's artifact groupId.
     * @since 4.0.0-a20250922
     */
    @AvailableSince("4.0.0-a20250922")
    @NotNull
    @Contract(pure = true)
    public static String getSLLGroupId() {
        return SLLEnvironment.SLL_GROUP_ID;
    }

    /**
     * Obtains the the version string of the SLL binary used in this environment.
     *
     * <p>Please note that the returned string is generally not semver-compliant.
     * Instead, should be interpreted as a maven version string. Tools to compare
     * such versions are provided by picoresolve (which SLL already depends on),
     * though other libraries exist for the same result.
     *
     * @return The version string.
     * @since 4.0.0-a20250922
     */
    @AvailableSince("4.0.0-a20250922")
    @NotNull
    @Contract(pure = true)
    public static String getSLLVersion() {
        return SLLEnvironment.SLL_VERSION;
    }

    /**
     * Query whether the used Mixin implementation is similar to
     * sponge's original Mixin implementation. In other words,
     * this method returns <code>true</code> if the mixin implementation
     * is Mixin or a fork of Mixin, whilst it returns <code>false</code>
     * if the implementation happens to be a rewrite of the mixin
     * API that may or may not have been written from scratch.
     *
     * @return Whether the mixin implementation is "sponge-like",
     * using the definitions defined above.
     * @since 4.0.0-a20250922
     */
    @AvailableSince("4.0.0-a20250922")
    @Contract(pure = true)
    public static boolean isMixinSpongelike() {
        return SLLEnvironment.MIXIN_SPONGELIKE;
    }
}
