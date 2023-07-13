package de.geolykt.starloader.transformers;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple transformer that inlines the proper values from the starplane-annotations string source, without remapping them
 * - that is it uses the references as-is.
 *
 * <p>Should only be used in a development environment as this transformer is unlikely to work in a production environment,
 * where a remapping step needs to be added on top.
 *
 * @since 4.0.0
 */
public class StarplaneAnnotationsInlineTransformer extends ASMTransformer {

    private static class MemberTriple {
        @NotNull
        final String owner;
        @NotNull
        final String name;
        @NotNull
        final String desc;

        @SuppressWarnings("null")
        public MemberTriple(String owner, String name, String desc) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }

        @Override
        public int hashCode() {
            return this.owner.hashCode() ^ this.name.hashCode() ^ this.desc.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof MemberTriple) {
                MemberTriple other = (MemberTriple) obj;
                return this.owner.equals(other.owner) && this.name.equals(other.name) && this.desc.equals(other.desc);
            }
            return false;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(StarplaneAnnotationsInlineTransformer.class);

    @Override
    public boolean accept(@NotNull ClassNode node) {
        Map<MemberTriple, MemberTriple> memberMapRequests = new HashMap<>();
        Map<MemberTriple, String> memberMapFormat = new HashMap<>();
        Map<MemberTriple, String> classMapRequests = new HashMap<>();
        boolean transformed = false;
        for (FieldNode field : node.fields) {
            Iterable<AnnotationNode> annotations = field.invisibleAnnotations;
            if (annotations == null) {
                continue;
            }
            Iterator<AnnotationNode> it = annotations.iterator();
            while (it.hasNext()) {
                AnnotationNode annotation = it.next();
                if (annotation.desc.equals("Lde/geolykt/starloader/starplane/annotations/StarplaneReobfuscateReference;")) {
                    it.remove();
                    transformed = true;
                    break;
                } else if (annotation.desc.equals("Lde/geolykt/starloader/starplane/annotations/RemapClassReference;")) {
                    it.remove();
                    transformed = true;
                    if (annotation.values == null || annotation.values.size() == 0) {
                        LOGGER.error("Field {}.{}:{} is annotated with de/geolykt/starloader/starplane/annotations/RemapClassReference, but neither the 'name' nor the 'type' value of the annotation is set.", node.name, field.name, field.desc);
                        break;
                    }
                    if (annotation.values.size() == 4) {
                        LOGGER.error("Field {}.{}:{} is annotated with de/geolykt/starloader/starplane/annotations/RemapClassReference, but both the 'name' and the 'type' value of the annotation is set. Consider only setting one of these values.", node.name, field.name, field.desc);
                        break;
                    }
                    String typeName;
                    if (annotation.values.get(0).equals("name")) {
                        typeName = ((String) annotation.values.get(0)).replace('.', '/');
                    } else if (annotation.values.get(0).equals("type")) {
                        typeName = ((Type) annotation.values.get(1)).getInternalName();
                    } else {
                        LOGGER.error("Erroneous annotation value: " + annotation.values.get(0) + " for RemapClassReference. Are you depending on the wrong starplane-annotations version?");
                        break;
                    }
                    classMapRequests.put(new MemberTriple(node.name, field.name, field.desc), typeName);
                } else if (annotation.desc.equals("Lde/geolykt/starloader/starplane/annotations/RemapMemberReference;")) {
                    it.remove();
                    if (annotation.values == null) {
                        LOGGER.error("Field {}.{}:{} is annotated with de/geolykt/starloader/starplane/annotations/RemapMemberReference, but does not define any of the required values.", node.name, field.name, field.desc);
                        break;
                    }
                    if (annotation.values.size() >= 10) {
                        LOGGER.error("Field {}.{}:{} is annotated with de/geolykt/starloader/starplane/annotations/RemapMemberReference, but more than the required values of the annotation is set. Consider removing duplicates.", node.name, field.name, field.desc);
                        break;
                    }
                    String typeName = null;
                    String memberName = null;
                    String memberDesc = null;
                    String format = null;
                    for (int i = 0; i < annotation.values.size(); i += 2) {
                        String valueName = ((String) annotation.values.get(i));
                        if (valueName.equals("ownerType")) {
                            typeName = ((Type) annotation.values.get(i + 1)).getInternalName();
                        } else if (valueName.equals("owner")) {
                            typeName = ((String) annotation.values.get(i + 1)).replace('.', '/');
                        } else if (valueName.equals("name")) {
                            memberName = (String) annotation.values.get(i + 1);
                        } else if (valueName.equals("desc")) {
                            if (memberDesc != null) {
                                LOGGER.error("Field {}.{}:{} is annotated with de/geolykt/starloader/starplane/annotations/RemapMemberReference, but multiple values contain descriptor-giving values. Consider removing duplicated.", node.name, field.name, field.desc);
                                break;
                            }
                            memberDesc = (String) annotation.values.get(i + 1);
                        } else if (valueName.equals("descType")) {
                            if (memberDesc != null) {
                                LOGGER.error("Field {}.{}:{} is annotated with de/geolykt/starloader/starplane/annotations/RemapMemberReference, but multiple values contain descriptor-giving values. Consider removing duplicated.", node.name, field.name, field.desc);
                                break;
                            }
                            memberDesc = ((Type) annotation.values.get(i + 1)).getDescriptor();
                        } else if (valueName.equals("methodDesc")) {
                            AnnotationNode methodDesc = (AnnotationNode) annotation.values.get(i + 1);
                            int args;
                            int ret;
                            if (methodDesc.values.get(0).equals("args")) {
                                args = 1;
                                ret = 3;
                            } else {
                                ret = 1;
                                args = 3;
                            }
                            String argDesc = "";
                            List<?> arglist = (List<?>) methodDesc.values.get(args);
                            for (int j = 0; j < arglist.size(); j++) {
                                Type arg = (Type) arglist.get(j);
                                if (arg == null) {
                                    throw new AssertionError();
                                }
                                argDesc += arg.getDescriptor();
                            }
                            if (memberDesc != null) {
                                LOGGER.error("Field {}.{}:{} is annotated with de/geolykt/starloader/starplane/annotations/RemapMemberReference, but multiple values contain descriptor-giving values. Consider removing duplicated.", node.name, field.name, field.desc);
                                break;
                            }
                            memberDesc = "(" + argDesc + ")" + ((Type) methodDesc.values.get(ret)).getDescriptor();
                        } else if (valueName.equals("format")) {
                            format = ((String[]) annotation.values.get(i + 1))[1];
                        } else {
                            LOGGER.error("Erroneous annotation value: {} for RemapMemberReference. Are you depending on the wrong starplane-annotations version?", valueName);
                            break;
                        }
                    }
                    if (typeName == null) {
                        LOGGER.error("Field {}.{}:{} is annotated with de/geolykt/starloader/starplane/annotations/RemapMemberReference, but neither the 'owner' nor the 'ownerType' value of the annotation is set. Consider setting one of these values.", node.name, field.name, field.desc);
                        break;
                    }
                    MemberTriple targetTriple = new MemberTriple(typeName, memberName, memberDesc);
                    MemberTriple fieldTriple = new MemberTriple(node.name, field.name, field.desc);
                    memberMapFormat.put(fieldTriple, format);
                    memberMapRequests.put(fieldTriple, targetTriple);
                }
            }
        }

        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
                    continue;
                }
                MethodInsnNode minsn = (MethodInsnNode) insn;
                if (!minsn.owner.equals("de/geolykt/starloader/starplane/annotations/ReferenceSource")) {
                    continue;
                }
                if (!minsn.name.equals("getStringValue") || !minsn.desc.equals("()Ljava/lang/String;")) {
                    continue;
                }
                AbstractInsnNode nextInsn = insn.getNext();
                while (nextInsn != null && (nextInsn.getOpcode() == -1 || nextInsn.getOpcode() == Opcodes.ALOAD)) {
                    nextInsn = nextInsn.getNext();
                }
                if (nextInsn == null) {
                    LOGGER.error("Method {}.{} {} contains a rouge ReferenceSource.getStringValue() call.", node.name, method.name, method.desc);
                    break;
                }
                if (nextInsn.getOpcode() != Opcodes.PUTSTATIC && nextInsn.getOpcode() != Opcodes.PUTFIELD) {
                    LOGGER.error("Method {}.{} {} contains a call to ReferenceSource.getStringValue() that is not immediately assigned to a field.", node.name, method.name, method.desc);
                    continue;
                }
                MemberTriple assignmentTriple = new MemberTriple(((FieldInsnNode) nextInsn).owner, ((FieldInsnNode) nextInsn).name, ((FieldInsnNode) nextInsn).desc);
                String cl = classMapRequests.get(assignmentTriple);
                String replacementLdc;
                if (cl != null) {
                    replacementLdc = cl;
                } else {
                    MemberTriple member = memberMapRequests.get(assignmentTriple);
                    String format = memberMapFormat.get(assignmentTriple);
                    if (member == null || format == null) {
                        LOGGER.error("Method {}.{} {} contains a call to ReferenceSource.getStringValue() that is assigned to {}.{} {} which is not annotated with a starplane remapping annotation. (Note: this feature does not work across classes!)", node.name, method.name, method.desc, assignmentTriple.owner, assignmentTriple.name, assignmentTriple.desc);
                        continue;
                    }
                    boolean isMethod = member.desc.codePointAt(0) == '(';
                    if (format.equals("OWNER")) {
                        replacementLdc = member.owner;
                    } else if (format.equals("NAME")) {
                        replacementLdc = member.name;
                    } else if (format.equals("DESCRIPTOR")) {
                        replacementLdc = member.desc;
                    } else if (format.equals("COMBINED_LEGACY")) {
                        if (isMethod) {
                            replacementLdc = member.owner + "." + member.name + member.desc;
                        } else {
                            replacementLdc = member.owner + "." + member.name + " " + member.desc;
                        }
                    } else {
                        LOGGER.error("Method {}.{} {} contains a call to ReferenceSource.getStringValue() that is assigned to {}.{} {} which uses an unsupported format. (Are you using the right version of starplane-annotations?)", node.name, method.name, method.desc, assignmentTriple.owner, assignmentTriple.name, assignmentTriple.desc);
                        continue;
                    }
                }
                transformed = true;
                method.instructions.set(insn, new LdcInsnNode(replacementLdc));
                insn = nextInsn;
            }
        }

        return transformed;
    }

    @Override
    public boolean isValidTarget(@NotNull String internalName) {
        return true;
    }

    @Override
    public int getPriority() {
        return -10_010;
    }
}
