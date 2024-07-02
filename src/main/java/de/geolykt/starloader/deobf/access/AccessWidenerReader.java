package de.geolykt.starloader.deobf.access;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;

import de.geolykt.starloader.deobf.access.AccessFlagModifier.Type;

@Deprecated
@ScheduledForRemoval(inVersion = "5.0.0")
public class AccessWidenerReader implements AutoCloseable {

    @Deprecated
    @ScheduledForRemoval(inVersion = "5.0.0")
    public static class IllegalHeaderException extends IOException {

        /**
         * serialVersionUID.
         */
        private static final long serialVersionUID = 2674486982880918940L;

        public IllegalHeaderException(String message) {
            super(message);
        }
    }

    private final AccessTransformInfo atInfo;
    private final BufferedReader br;
    private final boolean runtime;

    public AccessWidenerReader(AccessTransformInfo atInfo, InputStream stream, boolean runtime) {
        this.br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        this.atInfo = atInfo;
        this.runtime = runtime;
    }

    public void readHeader() throws IllegalHeaderException, IOException {
        for (String ln = br.readLine(); ln != null; ln = br.readLine()) {
            int indexOfCommentSymbol = ln.indexOf('#');
            String pureLine = indexOfCommentSymbol == -1 ? ln : ln.substring(0, indexOfCommentSymbol);
            String trimLine = pureLine.trim();
            if (!trimLine.isEmpty()) {
                String[] blocks = trimLine.split("\\s+");
                if (blocks.length != 3) {
                    throw new IllegalHeaderException("Header must be in the format of \"accessWidener v2 intermediary\"");
                }
                if (!blocks[0].equalsIgnoreCase("accessWidener")) {
                    throw new IllegalHeaderException("Header must be in the format of \"accessWidener v2 intermediary\"");
                }
                if (!(blocks[1].equals("v1") || blocks[1].equals("v2"))) {
                    throw new IllegalHeaderException("Cannot read version: " + blocks[1]);
                }
                if (!blocks[2].equals("intermediary")) {
                    throw new UnsupportedOperationException("As of know only the intermediary namespace is supported for access wideners.");
                }
                return; // Checks passed. Do not fall back to the throw below
            }
        }
        throw new IllegalHeaderException("Unable to find header.");
    }

    public boolean readLn() throws IOException {
        String ln = br.readLine();
        if (ln == null) {
            return false;
        }
        int indexOfCommentSymbol = ln.indexOf('#');
        String pureLine = indexOfCommentSymbol == -1 ? ln : ln.substring(0, indexOfCommentSymbol);
        String trimLine = pureLine.trim();
        if (trimLine.isEmpty()) {
            return true;
        }
        @NotNull String[] blocks = trimLine.split("\\s+");

        boolean compileOnly = false;
        if (blocks.length != 0 && (compileOnly = blocks[0].equalsIgnoreCase("compileOnly"))) {
            @NotNull String[] copy = new @NotNull String[blocks.length - 1];
            System.arraycopy(blocks, 1, copy, 0, copy.length);
            blocks = copy;
        }

        if (blocks.length != 3 && blocks.length != 5) {
            throw new IOException("Illegal block count. Got " + blocks.length + " expected 3 or 5 blocks. Line content: " + pureLine);
        }

        String targetClass = blocks[2].replace('.', '/');
        String operation = blocks[0];
        String typeName = blocks[1];

        Optional<String> name;
        Optional<String> desc;
        Type memberType = null;
        switch (typeName.toLowerCase(Locale.ROOT)) {
        case "class":
            if (blocks.length != 3) {
                throw new IOException("Illegal block count. Got " + blocks.length
                        + " but expected 3 due to the CLASS modifier. Line: " + pureLine);
            }
            memberType = Type.CLASS;
            name = Optional.empty();
            desc = Optional.empty();
            break;
        case "field":
            memberType = Type.FIELD;
            // Fall-through intended
        case "method":
            if (memberType == null) {
                memberType = Type.METHOD;
            }
            if (blocks.length != 5) {
                throw new IOException("Illegal block count. Got " + blocks.length
                        + " but expected 5 due to the METHOD or FIELD modifier. Line: " + pureLine);
            }
            name = Optional.of(blocks[3]);
            desc = Optional.of(blocks[4]);
            break;
        default:
            throw new IOException();
        }

        AccessFlagModifier modifier;

        switch (operation.toLowerCase(Locale.ROOT)) {
        case "accessible":
            modifier = new AccessFlagModifier.AccessibleModifier(memberType, targetClass, name, desc, compileOnly);
            break;
        case "extendable":
            modifier = new AccessFlagModifier.ExtendableModifier(memberType, targetClass, name, desc, compileOnly);
            break;
        case "mutable":
            modifier = new AccessFlagModifier.RemoveFlagModifier(memberType, targetClass, name, desc, Opcodes.ACC_FINAL, "mutable", compileOnly);
            break;
        case "natural":
            modifier = new AccessFlagModifier.RemoveFlagModifier(memberType, targetClass, name, desc, Opcodes.ACC_SYNTHETIC, "natural", compileOnly);
            break;
        case "denumerised":
            modifier = new AccessFlagModifier.RemoveFlagModifier(memberType, targetClass, name, desc, Opcodes.ACC_ENUM, "denumerised", compileOnly);
            break;
        default:
            throw new UnsupportedOperationException("Unknown mode: " + operation);
        }

        if (!(runtime && compileOnly)) {
            atInfo.modifiers.add(modifier);
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        br.close();
    }
}
