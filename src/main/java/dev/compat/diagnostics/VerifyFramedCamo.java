package dev.compat.diagnostics;

import java.nio.file.Path;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

/** Fails unless a produced Create JAR actually contains the camo restoration hook. */
public final class VerifyFramedCamo {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: VerifyFramedCamo <patched-create.jar>");
        try (JarFile jar = new JarFile(Path.of(args[0]).toFile())) {
            var entry = jar.getJarEntry("com/simibubi/create/content/contraptions/Contraption.class");
            if (entry == null) throw new IllegalStateException("Missing Contraption.class");
            var node = new ClassNode();
            try (var in = jar.getInputStream(entry)) { new ClassReader(in).accept(node, 0); }
            boolean bridge = false, redirect = false, restore = false;
            for (MethodNode m : node.methods) {
                if (m.name.equals("framedCamoRestore")) {
                    bridge = true;
                    for (AbstractInsnNode n : m.instructions)
                        if (n instanceof MethodInsnNode c && c.name.equals("loadWithComponents")) restore = true;
                }
                if (m.name.equals("addBlocksToWorld")) {
                    for (AbstractInsnNode n : m.instructions)
                        if (n instanceof MethodInsnNode c && c.owner.equals(node.name) && c.name.equals("framedCamoRestore")) redirect = true;
                }
            }
            if (!bridge || !redirect || !restore)
                throw new IllegalStateException("NOT PATCHED: bridge=" + bridge + ", redirect=" + redirect + ", restore=" + restore);
            System.out.println("Verified patch hook present. This does NOT prove Minecraft runtime compatibility.");
        }
    }
}
