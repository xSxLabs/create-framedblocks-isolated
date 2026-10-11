package dev.compat.diagnostics;

import java.nio.file.Path;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

/** Read-only inspection: never modifies the input Create JAR. */
public final class InspectCreate {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: gradle run --args='/path/to/create.jar'");
            System.exit(2);
        }
        try (var zip = new JarFile(Path.of(args[0]).toFile())) {
            String target = "com/simibubi/create/content/contraptions/Contraption.class";
            var entry = zip.getJarEntry(target);
            if (entry == null) throw new IllegalStateException("Missing " + target);
            var cls = new ClassNode();
            try (var stream = zip.getInputStream(entry)) {
                new ClassReader(stream).accept(cls, 0);
            }
            for (MethodNode method : cls.methods) {
                if (!(method.name.toLowerCase().contains("block") || method.name.toLowerCase().contains("nbt"))) continue;
                System.out.println("\n" + method.name + method.desc);
                for (var insn : method.instructions) {
                    if (insn instanceof MethodInsnNode call) {
                        if (call.name.toLowerCase().contains("save") ||
                            call.name.toLowerCase().contains("load") ||
                            call.name.toLowerCase().contains("process") ||
                            call.name.toLowerCase().contains("blockentity")) {
                            System.out.println("  CALL " + call.owner + "." + call.name + call.desc);
                        }
                    }
                }
            }
        }
    }
}
