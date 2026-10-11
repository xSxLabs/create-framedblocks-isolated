package dev.compat.diagnostics;

import java.nio.file.Path;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

/**
 * Read-only trace of NBT flow through Create's contraption implementation.
 * Usage: gradle runNbtTrace --args='/path/to/create.jar'
 */
public final class TraceNbtFlow {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected path to Create JAR");
        try (JarFile jar = new JarFile(Path.of(args[0]).toFile())) {
            String path = "com/simibubi/create/content/contraptions/Contraption.class";
            var entry = jar.getJarEntry(path);
            if (entry == null) throw new IllegalStateException("Missing " + path);
            ClassNode cls = new ClassNode();
            try (var in = jar.getInputStream(entry)) {
                new ClassReader(in).accept(cls, 0);
            }
            for (MethodNode method : cls.methods) {
                boolean interesting = false;
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof MethodInsnNode call &&
                        (call.owner.contains("NBTProcessors") ||
                         call.name.equals("loadWithComponents") ||
                         call.name.equals("loadStatic") ||
                         call.name.equals("readClient") ||
                         call.name.equals("saveWithFullMetadata"))) {
                        interesting = true;
                    }
                }
                if (!interesting) continue;
                System.out.println("\nMETHOD " + method.name + method.desc);
                int index = 0;
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof MethodInsnNode call) {
                        System.out.printf("%04d CALL %s.%s%s%n", index, call.owner, call.name, call.desc);
                    } else if (insn instanceof LdcInsnNode constant) {
                        if (String.valueOf(constant.cst).matches("(?i).*(camo|blockentity|lastknownpos|controller).*")) {
                            System.out.printf("%04d CONST %s%n", index, constant.cst);
                        }
                    }
                    index++;
                }
            }
        }
    }
}
