package tk.jandev.donutauctions.mixin;


import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import tk.jandev.donutauctions.DonutAuctions;
import tk.jandev.donutauctions.scraper.cache.ItemCache;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Shadow public abstract boolean hasNbt();

    @Shadow public abstract int getCount();

    @Inject(
            method = "getTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NbtCompound;contains(Ljava/lang/String;I)Z",
                    shift = At.Shift.AFTER,
                    ordinal = 0
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    public void appendAfterLore(PlayerEntity player, TooltipContext context, CallbackInfoReturnable<List<Text>> cir, @Local(ordinal = 0) List<Text> tooltip) {
        if (player == null) return;
        if (DonutAuctions.getInstance().shouldRenderItem((ItemStack) (Object) this)) {
            tooltip.add(ItemCache.getInstance().getPrice((ItemStack) (Object) this).getMessage(getCount()));
        }
    }

    @Inject(
            method = "getTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;hasNbt()Z",
                    shift = At.Shift.AFTER,
                    ordinal = 0
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    public void appendAfterLoreNoNBT(PlayerEntity player, TooltipContext context, CallbackInfoReturnable<List<Text>> cir, @Local(ordinal = 0) List<Text> tooltip) {
        if (player == null) return;
        if (this.hasNbt()) return; // we've already appended our component after lore was appended
        if (DonutAuctions.getInstance().shouldRenderItem((ItemStack) (Object) this)) {
            tooltip.add(ItemCache.getInstance().getPrice((ItemStack) (Object) this).getMessage(getCount()));
        }
    }
}
