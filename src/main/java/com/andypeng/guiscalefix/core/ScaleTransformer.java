package com.andypeng.guiscalefix.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Replaces the body of {@code ScaledResolution(Minecraft)} with a call to
 * {@link ScaleHooks#compute(net.minecraft.client.Minecraft)} and a few field stores.
 * <p>
 * The transformer runs before Forge's deobfuscation transformer, so in production the
 * bytecode contains notch names ({@code bit}, {@code bib}, methods {@code a..e}) while in
 * the development workspace it contains MCP names. Fields are therefore located through
 * the getter methods' {@code GETFIELD} instructions (accepting MCP, SRG and notch names)
 * instead of relying on field names or declaration order.
 */
public class ScaleTransformer implements IClassTransformer {

    private static final Logger LOGGER = LogManager.getLogger("guiscalefix");

    private static final String TARGET = "net.minecraft.client.gui.ScaledResolution";
    // Notch name of ScaledResolution in 1.12.2; only needed if no deobf name is supplied.
    private static final String TARGET_OBF = "bit";
    private static final String HOOKS = "com/andypeng/guiscalefix/core/ScaleHooks";
    private static final String HOOKS_DESC = "(Lnet/minecraft/client/Minecraft;)[D";

    // Getter names in MCP (dev), SRG and notch (production) form.
    private static final String[] WIDTH_I = {"getScaledWidth", "func_78326_a", "a"};
    private static final String[] HEIGHT_I = {"getScaledHeight", "func_78328_b", "b"};
    private static final String[] WIDTH_D = {"getScaledWidth_double", "func_78327_c", "c"};
    private static final String[] HEIGHT_D = {"getScaledHeight_double", "func_78324_d", "d"};
    private static final String[] SCALE_I = {"getScaleFactor", "func_78325_e", "e"};

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }

        boolean matches = TARGET.equals(transformedName) || TARGET.equals(name) || TARGET_OBF.equals(name);
        if (!matches) {
            return basicClass;
        }

        try {
            byte[] patched = patch(basicClass);
            if (patched != null) {
                LOGGER.info("Patched ScaledResolution GUI scale calculation");
                return patched;
            }
        } catch (Throwable t) {
            // Never break the game because of this mod.
            LOGGER.error("Failed to patch ScaledResolution, falling back to vanilla scaling", t);
        }
        return basicClass;
    }

    /**
     * @return patched class bytes, or {@code null} if the class did not have the expected shape
     */
    private static byte[] patch(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        MethodNode ctor = findConstructor(classNode);
        if (ctor == null) {
            LOGGER.warn("ScaledResolution constructor not found in {}", classNode.name);
            return null;
        }

        FieldInsnNode scaledWidth = findFieldReturnedBy(classNode, "()I", WIDTH_I);
        FieldInsnNode scaledHeight = findFieldReturnedBy(classNode, "()I", HEIGHT_I);
        FieldInsnNode scaledWidthD = findFieldReturnedBy(classNode, "()D", WIDTH_D);
        FieldInsnNode scaledHeightD = findFieldReturnedBy(classNode, "()D", HEIGHT_D);
        FieldInsnNode scaleFactor = findFieldReturnedBy(classNode, "()I", SCALE_I);

        if (scaledWidth == null || scaledHeight == null || scaledWidthD == null
                || scaledHeightD == null || scaleFactor == null) {
            LOGGER.warn("Could not locate ScaledResolution fields in {} (width={}, height={}, widthD={}, heightD={}, scale={})",
                    classNode.name, scaledWidth != null, scaledHeight != null,
                    scaledWidthD != null, scaledHeightD != null, scaleFactor != null);
            return null;
        }

        InsnList body = buildBody(classNode.name, scaledWidth, scaledHeight, scaledWidthD, scaledHeightD, scaleFactor);

        ctor.instructions.clear();
        ctor.tryCatchBlocks.clear();
        ctor.localVariables = null;
        ctor.instructions.add(body);
        ctor.maxStack = 8;
        ctor.maxLocals = 3;

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode findConstructor(ClassNode classNode) {
        MethodNode found = null;
        for (MethodNode method : classNode.methods) {
            if (!"<init>".equals(method.name) || "()V".equals(method.desc) || !method.desc.endsWith(")V")) {
                continue;
            }
            if (found != null) {
                // Unexpected overload; refuse to guess.
                return null;
            }
            found = method;
        }
        return found;
    }

    private static FieldInsnNode findFieldReturnedBy(ClassNode classNode, String desc, String[] methodNames) {
        for (MethodNode method : classNode.methods) {
            if (!desc.equals(method.desc) || !matchesAny(method.name, methodNames)) {
                continue;
            }
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.GETFIELD) {
                    FieldInsnNode field = (FieldInsnNode) insn;
                    if (classNode.name.equals(field.owner)) {
                        return field;
                    }
                }
            }
        }
        return null;
    }

    private static boolean matchesAny(String value, String[] candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static InsnList buildBody(String owner, FieldInsnNode scaledWidth, FieldInsnNode scaledHeight,
                                      FieldInsnNode scaledWidthD, FieldInsnNode scaledHeightD,
                                      FieldInsnNode scaleFactor) {
        InsnList il = new InsnList();

        // super()
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));

        // double[] values = ScaleHooks.compute(minecraftClient);
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "compute", HOOKS_DESC, false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 2));

        storeDouble(il, owner, scaledWidthD, 1);
        storeDouble(il, owner, scaledHeightD, 2);
        storeInt(il, owner, scaledWidth, 3);
        storeInt(il, owner, scaledHeight, 4);
        storeInt(il, owner, scaleFactor, 0);

        il.add(new InsnNode(Opcodes.RETURN));
        return il;
    }

    private static void storeDouble(InsnList il, String owner, FieldInsnNode field, int arrayIndex) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(intConstant(arrayIndex));
        il.add(new InsnNode(Opcodes.DALOAD));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, owner, field.name, field.desc));
    }

    private static void storeInt(InsnList il, String owner, FieldInsnNode field, int arrayIndex) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(intConstant(arrayIndex));
        il.add(new InsnNode(Opcodes.DALOAD));
        il.add(new InsnNode(Opcodes.D2I));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, owner, field.name, field.desc));
    }

    private static AbstractInsnNode intConstant(int value) {
        if (value >= -1 && value <= 5) {
            return new InsnNode(Opcodes.ICONST_0 + value);
        }
        return new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, value);
    }
}
