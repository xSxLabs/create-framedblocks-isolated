# Create × FramedBlocks — isolated investigation

An independent diagnostic project, **not a released fix**.

## Target
- Minecraft 26.1.2 / NeoForge 26.1.2.106
- Create Adapted 6.0.10; keep the working train-relocation patch
- FramedBlocks 11.4.0; FTB XMod Compat remains enabled

## Confirmed symptom

A FramedBlocks block entity at (270, 69, -1763) has:

Before assembly: `camo: {type: "framedblocks:block", state: {Name: "minecraft:cracked_polished_blackstone_bricks"}}`

After train disassembly: `camo: {type: "framedblocks:empty"}`

This confirms persistent data loss, **not** the exact point of loss. No fix has yet been validated.

## Build

GitHub Actions runs `gradle build` using Java 25 and archives the standalone bytecode-inspection tool. It is **not** a server-ready Create JAR.

To examine a Create JAR locally:

```sh
gradle run --args='/path/to/create-adapted.jar'
```

Do not upload mod binaries or private server/world files to this public repository.

## Investigation

1. Trace captured BlockEntity NBT during assembly.
2. Check whether NBT processors modify `camo`.
3. Check how block entities are reconstructed during disassembly.
4. Add a minimal, regression-tested change only after finding the loss point.
5. Preserve train relocation behavior and test both client/server before release.

## Patch verification (required)

The experimental patcher must be run against the **known-working relocation JAR**, not against a diagnostics JAR:

```sh
gradle patchFramedCamo --args='/path/to/working-relocation.jar /path/to/output.jar'
gradle verifyFramedCamo --args='/path/to/output.jar'
```

**Warning:** A previously distributed experimental file named `create-adapted-6.0.10-RELOCATE-FRAMEDCAMO-EXPERIMENTAL.jar` was inspected and found to **lack** the `framedCamoRestore` method. Do not treat it as patched. A green diagnostics build does not prove that a Create JAR has been patched or works in-game.
