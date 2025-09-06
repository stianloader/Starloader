package net.minestom.server.extras.selfmodification.mixins;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

/**
 * Global properties service for Mixin.
 */
public class GlobalPropertyServiceMinestom implements IGlobalPropertyService {

    private static class BasicProperty implements IPropertyKey {

        @NotNull
        private final String name;

        public BasicProperty(@NotNull String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof BasicProperty)) {
                return false;
            }

            return this.name.equals(((BasicProperty) o).name);
        }

        @Override
        public int hashCode() {
            return this.name.hashCode();
        }

        @Override
        public String toString() {
            return "BasicProperty{name='" + this.name + "'}";
        }
    }

    private final Map<String, IPropertyKey> keys = new HashMap<>();
    private final Map<IPropertyKey, Object> values = new HashMap<>();

    @Override
    public IPropertyKey resolveKey(String name) {
        if (name == null) {
            throw new NullPointerException("Argument 'name' should not be null as this implementation does not support keys with null names.");
        }

        IPropertyKey key = this.keys.get(name);

        if (key == null) {
            key = new BasicProperty(name);
            this.keys.put(name, key);
        }

        return key;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getProperty(IPropertyKey key) {
        return (T) this.values.get(key);
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        this.values.put(key, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        return (T) this.values.getOrDefault(key, defaultValue);
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        return (String) this.values.getOrDefault(key, defaultValue);
    }
}
