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
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the body of {@code ScaledResolution(Minecraft)} with a call to
 * {@link ScaleHooks#compute(net.minecraft.client.Minecraft)} and a few field stores.
 * <p>
 * Fields are located by descriptor/declaration order instead of name, so the patch works
 * both in the development workspace (MCP names) and in production (SRG names).
 */
public class ScaleTransformer implements IClassTransformer {

    private static final Logger LOGGER = LogManager.getLogger("guiscalefix");

    private static final String TARGET = "net.minecraft.client.gui.ScaledResolution";
    // Notch name of ScaledResolution in 1.12.2; only needed if no deobf name is supplied.
    private static final String TARGET_OBF = "bit";
    private static final String CTOR_DESC = "(Lnet/minecraft/client/Minecraft;)V";
    private static final String HOOKS = "com/andypeng/guiscalefix/core/ScaleHooks";
    private static final String HOOKS_DESC = "(Lnet/minecraft/client/Minecraft;)[D";

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
            LOGGER.info("Patched ScaledResolution GUI scale calculation");
            return patched;
        } catch (Throwable t) {
            // Never break the game because of this mod.
            LOGGER.error("Failed to patch ScaledResolution, falling back to vanilla scaling", t);
            return basicClass;
        }
    }

    private static byte[] patch(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        MethodNode ctor = null;
        for (MethodNode method : classNode.methods) {
            if ("<init>".equals(method.name) && CTOR_DESC.equals(method.desc)) {
                ctor = method;
                break;
            }
        }
        if (ctor == null) {
            LOGGER.warn("ScaledResolution constructor not found, skipping patch");
            return basicClass;
        }

        // Vanilla layout (declaration order):
        //   private final double scaledWidthD, scaledHeightD;
        //   private int scaledWidth, scaledHeight, scaleFactor;
        List<FieldNode> doubles = new ArrayList<>(2);
        List<FieldNode> ints = new ArrayList<>(3);
        for (FieldNode field : classNode.fields) {
            if ((field.access & Opcodes.ACC_STATIC) != 0) {
                continue;
            }
            if ("D".equals(field.desc)) {
                doubles.add(field);
            } else if ("I".equals(field.desc)) {
                ints.add(field);
            }
        }

        if (doubles.size() != 2 || ints.size() != 3) {
            LOGGER.warn("Unexpected ScaledResolution layout ({} doubles, {} ints), skipping patch",
                    doubles.size(), ints.size());
            return basicClass;
        }

        FieldNode scaledWidthD = doubles.get(0);
        FieldNode scaledHeightD = doubles.get(1);
        FieldNode scaledWidth = ints.get(0);
        FieldNode scaledHeight = ints.get(1);
        FieldNode scaleFactor = ints.get(2);

        InsnList body = buildBody(classNode.name, scaledWidthD, scaledHeightD, scaledWidth, scaledHeight, scaleFactor);

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

    private static InsnList buildBody(String owner, FieldNode scaledWidthD, FieldNode scaledHeightD,
                                      FieldNode scaledWidth, FieldNode scaledHeight, FieldNode scaleFactor) {
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

    private static void storeDouble(InsnList il, String owner, FieldNode field, int arrayIndex) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(intConstant(arrayIndex));
        il.add(new InsnNode(Opcodes.DALOAD));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, owner, field.name, field.desc));
    }

    private static void storeInt(InsnList il, String owner, FieldNode field, int arrayIndex) {
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
