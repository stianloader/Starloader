package de.geolykt.starloader.launcher;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.CommandLineOptions;
import org.spongepowered.asm.mixin.Mixins;

import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;
import net.minestom.server.extras.selfmodification.mixins.MixinCodeModifier;
import net.minestom.server.extras.selfmodification.mixins.MixinServiceMinestom;

public final class SLMouseListener implements MouseListener {

    private static final String GALIMULATOR_JAR_LOC = "jar/galimulator-desktop.jar";

    private final boolean launchWithMods;
    private final String[] args;

    public SLMouseListener(boolean b, String...args) {
        launchWithMods = b;
        this.args = args;
    }

    private static void addToCPJ8 (File file) throws ReflectiveOperationException, SecurityException, IllegalArgumentException, MalformedURLException {
        URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
        method.invoke(sysloader, new Object[]{file.toURI().toURL()});
    }

    private void startMain(Class<?> className) {
        try {
            Method main = className.getDeclaredMethod("main", String[].class);
            main.setAccessible(true);
            main.invoke(null, new Object[] { args });
        } catch (Exception e) {
            throw new RuntimeException("Error while invoking main class!", e);
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        StarloaderLauncher.preloaderFrame.setVisible(false);
        StarloaderLauncher.preloaderFrame = null;
        if (StarloaderLauncher.isJava9()) {
            MinestomRootClassLoader cl = MinestomRootClassLoader.getInstance();
            try {
                cl.addURL(new File(GALIMULATOR_JAR_LOC).toURI().toURL());
            } catch (MalformedURLException e1) {
                throw new RuntimeException("Something went wrong while adding the galimulator Jar to the Classpath", e1);
            }
            try {
                if (launchWithMods) {
                    startMixin(args);
                    MinestomRootClassLoader.getInstance().addCodeModifier(new MixinCodeModifier());
                    MixinServiceMinestom.gotoPreinitPhase();
                    // ensure extensions are loaded when starting the server
                    Class<?> serverClass = cl.loadClass("de.geolykt.starloader.Starloader");
                    Method init = serverClass.getMethod("init");
                    init.invoke(null);
                    MixinServiceMinestom.gotoInitPhase();
                    MixinServiceMinestom.gotoDefaultPhase();
                }

                startMain(cl.loadClass("com.example.Main"));
            } catch (Exception e1) {
                throw new RuntimeException("Something went wrong while bootstrapping.", e1);
            }
        } else {
            try {
                addToCPJ8(new File(GALIMULATOR_JAR_LOC));
            } catch (SecurityException | IllegalArgumentException | MalformedURLException
                    | ReflectiveOperationException e1) {
                throw new RuntimeException("Something went wrong while adding the galimulator Jar to the Classpath", e1);
            }
            try {
                startMain(Class.forName("com.example.Main"));
            } catch (ClassNotFoundException e1) {
                throw new RuntimeException("Unable to locate Main class!", e1);
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {}

    @Override
    public void mouseReleased(MouseEvent e) {}

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}

    private static void startMixin(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // hacks required to pass custom arguments
        Method start = MixinBootstrap.class.getDeclaredMethod("start");
        start.setAccessible(true);
        if (!((boolean)start.invoke(null))) {
            return;
        }

        Method doInit = MixinBootstrap.class.getDeclaredMethod("doInit", CommandLineOptions.class);
        doInit.setAccessible(true);
        doInit.invoke(null, CommandLineOptions.ofArgs(Arrays.asList(args)));

        MixinBootstrap.getPlatform().inject();
        Mixins.getConfigs().forEach(c -> MinestomRootClassLoader.getInstance().protectedPackages.add(c.getConfig().getMixinPackage()));
    }

}
