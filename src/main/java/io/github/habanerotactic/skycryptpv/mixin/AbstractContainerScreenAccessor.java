package io.github.habanerotactic.skycryptpv.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

	@Accessor("leftPos")
	int skycryptpv$getLeftPos();

	@Accessor("topPos")
	int skycryptpv$getTopPos();

}
