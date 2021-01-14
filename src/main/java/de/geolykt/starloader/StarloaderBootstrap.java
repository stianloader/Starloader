package de.geolykt.starloader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.CommandLineOptions;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.transformer.Config;

import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;
import net.minestom.server.extras.selfmodification.mixins.MixinCodeModifier;
import net.minestom.server.extras.selfmodification.mixins.MixinServiceMinestom;

// Credits to the minestom team for providing the base for this class
public class StarloaderBootstrap {

    public static void bootstrap(String mainClassFullName, String[] args) {
        try {
            ClassLoader classLoader = MinestomRootClassLoader.getInstance();
            startMixin(args);
            MinestomRootClassLoader.getInstance().addCodeModifier(new MixinCodeModifier());

            MixinServiceMinestom.gotoPreinitPhase();
            // ensure extensions are loaded when starting the server
            Class<?> serverClass = classLoader.loadClass("de.geolykt.starloader.Starloader");
            Method init = serverClass.getMethod("init");
            init.invoke(null);
            MixinServiceMinestom.gotoInitPhase();

            MixinServiceMinestom.gotoDefaultPhase();

            Class<?> mainClass = classLoader.loadClass(mainClassFullName);
            Method main = mainClass.getDeclaredMethod("main", String[].class);
            main.setAccessible(true);
            main.invoke(null, new Object[] { args });
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static void startMixin(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // hacks required to pass custom arguments
        Method start = MixinBootstrap.class.getDeclaredMethod("start");
        start.setAccessible(true);
        if (! ((boolean)start.invoke(null)) ) {
            return;
        }

        Method doInit = MixinBootstrap.class.getDeclaredMethod("doInit", CommandLineOptions.class);
        doInit.setAccessible(true);
        doInit.invoke(null, CommandLineOptions.ofArgs(Arrays.asList(args)));

        MixinBootstrap.getPlatform().inject();
        Mixins.getConfigs().forEach(c -> MinestomRootClassLoader.getInstance().protectedPackages.add(c.getConfig().getMixinPackage()));
    }
}
