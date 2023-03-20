package de.geolykt.starloader.deobf.access;

import java.util.Locale;
import java.util.Optional;

import org.objectweb.asm.Opcodes;

public abstract class AccessFlagModifier {

    public static enum Type {
        CLASS,
        METHOD,
        FIELD;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public final Type type;
    public final boolean isCompileOnly;
    public final String clazz;
    public final Optional<String> name;
    public final Optional<String> descriptor;

    public AccessFlagModifier(Type type, String clazz, Optional<String> name, Optional<String> descriptor, boolean compileOnly) {
        this.type = type;
        this.clazz = clazz;
        this.name = name;
        this.descriptor = descriptor;
        if (type == Type.CLASS) {
            if (name.isPresent() || descriptor.isPresent()) {
                throw new IllegalArgumentException("Neither name nor descriptor may be present for the CLASS type.");
            }
        } else if (!name.isPresent() || !descriptor.isPresent()) {
            throw new IllegalArgumentException("Both name and descriptor must be present for anything but the CLASS type.");
        }
        this.isCompileOnly = compileOnly;
    }

    public abstract int apply(int oldAccessFlag);
    public abstract String toAccessWidenerString();

    public static class AccessibleModifier extends AccessFlagModifier {

        public AccessibleModifier(Type type, String clazz, Optional<String> name, Optional<String> descriptor, boolean compileOnly) {
            super(type, clazz, name, descriptor, compileOnly);
        }

        @Override
        public int apply(int oldAccessFlag) {
            return oldAccessFlag & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC;
        }

        @Override
        public String toAccessWidenerString() {
            if (type == Type.CLASS) {
                return "accessible class " + clazz;
            } else {
                return String.format("accessible %s %s %s %s", type.toString(), clazz, name.get(), descriptor.get());
            }
        }
    }

    public static class ExtendableModifier extends AccessFlagModifier {

        public ExtendableModifier(Type type, String clazz, Optional<String> name, Optional<String> descriptor, boolean compileOnly) {
            super(type, clazz, name, descriptor, compileOnly);
        }

        @Override
        public int apply(int oldAccessFlag) {
            int flag = oldAccessFlag & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_FINAL;
            if ((flag & Opcodes.ACC_PUBLIC) == 0) {
                return flag | Opcodes.ACC_PROTECTED;
            } else {
                return flag;
            }
        }

        @Override
        public String toAccessWidenerString() {
            if (type == Type.CLASS) {
                return "extendable class " + clazz;
            } else {
                return String.format("extendable %s %s %s %s", type.toString(), clazz, name.get(), descriptor.get());
            }
        }
    }

    public static class RemoveFlagModifier extends AccessFlagModifier {

        private final int flag;
        private final String awMode;

        public RemoveFlagModifier(Type type, String clazz, Optional<String> name, Optional<String> descriptor, int flag, String awMode, boolean compileOnly) {
            super(type, clazz, name, descriptor, compileOnly);
            this.flag = flag;
            this.awMode = awMode;
        }

        @Override
        public int apply(int oldAccessFlag) {
            return oldAccessFlag & ~flag;
        }

        @Override
        public String toAccessWidenerString() {
            if (type == Type.CLASS) {
                return awMode + " class " + clazz;
            } else {
                return String.format("%s %s %s %s %s", awMode, type.toString(), clazz, name.get(), descriptor.get());
            }
        }
    }
}
