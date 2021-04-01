package de.geolykt.starloader.transformers;

import java.util.ArrayList;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minestom.server.extras.selfmodification.CodeModifier;

/**
 * Starloader's implementation of EnumWideners, based on research and code by GrossFabricHackers.
 *
 * @author Geolykt
 */
public class EnumWidenerTransformer extends CodeModifier {

    protected final static Logger LOGGER = LoggerFactory.getLogger(EnumWidenerTransformer.class);

    protected ArrayList<String> targets;

    public EnumWidenerTransformer(@NotNull ArrayList<String> targetClasses) {
        targets = targetClasses;
    }

    @Override
    public boolean transform(ClassNode source) {
        if (targets.contains(source.name)) {
            if ((source.access & Opcodes.ACC_ENUM) != 0) {
                // Enum flag set
                source.access = source.access ^ Opcodes.ACC_ENUM; // invert the enum flag
                return true;
            } else {
                // Enum flag not set
                LOGGER.warn("Class {} is not an enum, despite it being a candidate for enum widening.", source.name);
                return false;
            }
        }
        return false;
    }

    @Override
    public String getNamespace() {
        return null; // I honestly do not know what a namespace is in this case, I should look at it more closely one day
        // I return null however so I will fully make use of that.
    }
}
