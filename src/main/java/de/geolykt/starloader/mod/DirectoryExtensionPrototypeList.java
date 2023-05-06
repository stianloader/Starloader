package de.geolykt.starloader.mod;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.geolykt.starloader.util.JarFilter;
import de.geolykt.starloader.util.JavaInterop;

public class DirectoryExtensionPrototypeList extends ArrayList<@NotNull NamedExtensionPrototype> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryExtensionPrototypeList.class);
    private static final long serialVersionUID = -9023683363717314711L;

    private final File extensionFolder;
    private final Map<String, List<NamedExtensionPrototype>> extensionsByName = new HashMap<>();

    public DirectoryExtensionPrototypeList(File extensionFolder) {
        this.extensionFolder = extensionFolder;
        File[] jarFiles = extensionFolder.listFiles(JarFilter.INSTANCE);
        if (jarFiles == null) {
            LOGGER.warn("Unable to list files at {}", extensionFolder);
            return;
        }
        for (File jarFile : jarFiles) {
            try (JarFile jar = new JarFile(jarFile)) {
                ZipEntry entry = jar.getEntry("extension.json");
                if (entry == null) {
                    continue;
                }
                JSONObject jsonObj;
                try (InputStream is = jar.getInputStream(entry)) {
                    if (is == null) {
                        throw new AssertionError();
                    }
                    jsonObj = new JSONObject(new String(JavaInterop.readAllBytes(is), StandardCharsets.UTF_8));
                }
                addPrototype(new NamedExtensionPrototype(Collections.singletonList(jarFile.toURI().toURL()), jsonObj.getString("name"), jsonObj.optString("version", "unkown")));
            } catch (IOException e) {
                LOGGER.warn("Failed to load potential extension {}: {}", jarFile.getPath(), e);
                continue;
            }
        }
    }

    @Override
    public void add(int index, @NotNull NamedExtensionPrototype element) {
        super.add(index, element);
        addPrototype(element);
    }

    @Override
    public boolean add(@NotNull NamedExtensionPrototype e) {
        if (super.add(e)) {
            addPrototype(e);
            return true;
        }
        return false;
    }

    @Override
    public boolean addAll(Collection<@NotNull ? extends NamedExtensionPrototype> c) {
        c.forEach(this::add);
        return true;
    }

    @Override
    public boolean addAll(int index, Collection<@NotNull ? extends NamedExtensionPrototype> c) {
        c.forEach(this::addPrototype);
        return super.addAll(index, c);
    }

    private void addPrototype(NamedExtensionPrototype prototype) {
        extensionsByName.compute(prototype.name, (key, list) -> {
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(prototype);
            return list;
        });
    }

    @Override
    public void clear() {
        super.clear();
        extensionsByName.clear();
    }

    public File getFolder() {
        return this.extensionFolder;
    }

    public List<NamedExtensionPrototype> getPrototypes(String s) {
        List<NamedExtensionPrototype> l = extensionsByName.get(s);
        if (l == null) {
            return new ArrayList<>();
        }
        return l;
    }

    @Override
    @NotNull
    public NamedExtensionPrototype remove(int index) {
        throw new UnsupportedOperationException("remove");
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("remove");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("remove");
    }

    @Override
    public boolean removeIf(Predicate<? super NamedExtensionPrototype> filter) {
        throw new UnsupportedOperationException("remove");
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException("remove");
    }

    @Override
    public void replaceAll(UnaryOperator<@NotNull NamedExtensionPrototype> operator) {
        throw new UnsupportedOperationException("remove");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("remove");
    }
}
