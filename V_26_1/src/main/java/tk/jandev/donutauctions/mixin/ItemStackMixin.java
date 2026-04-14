package tk.jandev.donutauctions.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import tk.jandev.donutauctions.DonutAuctions;
import tk.jandev.donutauctions.ItemCache;

import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

@Mixin(ItemStack.class)
@Environment(EnvType.CLIENT)
public abstract class ItemStackMixin {
    @Shadow public abstract int getCount();

    @Inject(
            method = "addDetailsToTooltip",
            at = @At(
                    value = "INVOKE", // inject into the place where LORE is applied!
                    target = "Lnet/minecraft/world/item/ItemStack;addToTooltip(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/component/TooltipDisplay;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V",
                    shift = At.Shift.AFTER,
                    ordinal = 18
            )
    )
    public void appendAfterLore(Item.TooltipContext context, TooltipDisplay displayComponent, Player player, TooltipFlag type, Consumer<Component> textConsumer, CallbackInfo ci) {
        if (player == null) return;
        final DonutAuctions donutAuctions = DonutAuctions.getInstance();

        if (donutAuctions.shouldRenderFor(this)) {
            appendTooltip(textConsumer);
        }
    }


    @Unique
    private void appendTooltip(Consumer<Component> consumer) {
        final DonutAuctions donutAuctions = DonutAuctions.getInstance();

        ItemCache.CacheResult cacheResult = donutAuctions.getItemCache().getPrice(this);
        int count = this.getCount();

        int maxCount = ((ItemStack) (Object) this).getMaxStackSize();

        List<Component> messages = donutAuctions.getTextFor(cacheResult, count, maxCount);

        messages.forEach(consumer);
    }
}
