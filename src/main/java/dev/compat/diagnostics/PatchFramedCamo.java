package dev.compat.diagnostics;

import java.io.IOException;
import java.nio.file.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Experimental server-side patch. Requires a separately supplied, legally obtained Create JAR.
 * Restores FramedBlocks BE NBT when disassembling a contraption.
 * Not validated in a live Minecraft server.
 */
public final class PatchFramedCamo {
    private static final String CONTRAPTION = "com/simibubi/create/content/contraptions/Contraption";
    private static final String PROCESSORS = "net/createmod/catnip/api/nbt/NBTProcessors";
    private static final String FRAMED = "io/github/xfacthd/framedblocks/api/block/blockentity/FramedBlockEntity";
    private static final String BLOCK_ENTITY = "net/minecraft/world/level/block/entity/BlockEntity";
    private static final String TAG = "net/minecraft/nbt/CompoundTag";
    private static final String STATE = "net/minecraft/world/level/block/state/BlockState";
    private static final String PROVIDER = "net/minecraft/core/HolderLookup$Provider";
    private static final String LEVEL = "net/minecraft/world/level/Level";
    private static final String DESC = "(L" + STATE + ";L" + BLOCK_ENTITY + ";L" + TAG + ";Z)L" + TAG + ";";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: PatchFramedCamo <working-relocation-create.jar> <output.jar>");
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        if (Files.isSameFile(input, output)) throw new IllegalArgumentException("Input and output must differ");
        boolean[] patched = {false};
        try (JarFile jar = new JarFile(input.toFile());
             JarOutputStream out = new JarOutputStream(Files.newOutputStream(output))) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                // Signed JAR metadata becomes invalid after modifying a class.
                String name = entry.getName();
                if (name.matches("(?i)META-INF/[^/]+\\.(SF|RSA|DSA|EC)")) continue;
                JarEntry dest = new JarEntry(name);
                dest.setTime(entry.getTime());
                out.putNextEntry(dest);
                if (!entry.isDirectory()) {
                    byte[] bytes;
                    try (var in = jar.getInputStream(entry)) { bytes = in.readAllBytes(); }
                    if (name.equals(CONTRAPTION + ".class")) {
                        bytes = patch(bytes);
                        patched[0] = true;
                    }
                    out.write(bytes);
                }
                out.closeEntry();
            }
        }
        if (!patched[0]) { Files.deleteIfExists(output); throw new IOException("Contraption.class not found"); }
        System.out.println("EXPERIMENTAL patch generated: " + output);
    }

    private static byte[] patch(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (node.methods.stream().anyMatch(m -> m.name.equals("framedCamoRestore"))) {
            throw new IllegalStateException("Already patched");
        }
        int count = 0;
        for (MethodNode m : node.methods) {
            if (!m.name.equals("addBlocksToWorld")) continue;
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode call &&
                    call.getOpcode() == Opcodes.INVOKESTATIC &&
                    call.owner.equals(PROCESSORS) && call.name.equals("process") && call.desc.equals(DESC)) {
                    call.owner = CONTRAPTION;
                    call.name = "framedCamoRestore";
                    count++;
                }
            }
        }
        if (count != 1) throw new IllegalStateException("Expected one NBTProcessors.process call in addBlocksToWorld; got " + count);
        MethodNode bridge = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "framedCamoRestore", DESC, null, null);
        InsnList i = bridge.instructions;
        // Retain standard processing for all blocks.
        i.add(new VarInsnNode(Opcodes.ALOAD, 0));
        i.add(new VarInsnNode(Opcodes.ALOAD, 1));
        i.add(new VarInsnNode(Opcodes.ALOAD, 2));
        i.add(new VarInsnNode(Opcodes.ILOAD, 3));
        i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROCESSORS, "process", DESC, false));
        i.add(new VarInsnNode(Opcodes.ASTORE, 4));
        // Restore the *original* NBT to FramedBlocks; processor may have stripped camo.
        LabelNode skip = new LabelNode();
        i.add(new VarInsnNode(Opcodes.ALOAD, 1));
        i.add(new TypeInsnNode(Opcodes.INSTANCEOF, FRAMED));
        i.add(new JumpInsnNode(Opcodes.IFEQ, skip));
        i.add(new VarInsnNode(Opcodes.ALOAD, 1));
        i.add(new VarInsnNode(Opcodes.ALOAD, 2));
        i.add(new VarInsnNode(Opcodes.ALOAD, 1));
        i.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCK_ENTITY, "getLevel", "()L" + LEVEL + ";", false));
        i.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, LEVEL, "registryAccess", "()Lnet/minecraft/core/RegistryAccess;", false));
        i.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCK_ENTITY, "loadWithComponents",
            "(L" + TAG + ";L" + PROVIDER + ";)V", false));
        i.add(skip);
        i.add(new FrameNode(Opcodes.F_APPEND, 1, new Object[]{TAG}, 0, null));
        i.add(new VarInsnNode(Opcodes.ALOAD, 4));
        i.add(new InsnNode(Opcodes.ARETURN));
        bridge.maxLocals = 5;
        bridge.maxStack = 4;
        node.methods.add(bridge);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
