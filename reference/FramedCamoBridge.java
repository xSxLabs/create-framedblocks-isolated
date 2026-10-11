package dev.compat;

/**
 * Compatibility bridge for Minecraft 26.1.2's ValueInput-based block entity API.
 * Called instead of NBTProcessors.process from Contraption.addBlocksToWorld.
 *
 * IMPORTANT: This source is intended to compile against the real Minecraft
 * 26.1.2 and FramedBlocks 11.4.0 APIs; it is not a Minecraft runtime test.
 */
public final class FramedCamoBridge {
    private FramedCamoBridge() {}

    public static net.minecraft.nbt.CompoundTag process(
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.block.entity.BlockEntity be,
            net.minecraft.nbt.CompoundTag tag,
            boolean flag) {
        net.minecraft.nbt.CompoundTag result =
                net.createmod.catnip.api.nbt.NBTProcessors.process(state, be, tag, flag);

        if (be instanceof io.github.xfacthd.framedblocks.api.block.blockentity.FramedBlockEntity
                && tag != null && be.getLevel() != null) {
            try {
                be.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                        net.minecraft.util.ProblemReporter.DISCARDING,
                        be.getLevel().registryAccess(),
                        tag));
                be.setChanged();
            } catch (Throwable ignored) {
                // Restoration must never abort train disassembly.
            }
        }
        return result;
    }
}
