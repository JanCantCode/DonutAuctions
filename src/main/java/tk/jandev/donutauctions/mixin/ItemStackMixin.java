package tk.jandev.donutauctions.mixin;


import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tk.jandev.donutauctions.DonutAuctions;
import tk.jandev.donutauctions.scraper.cache.ItemCache;

import java.util.List;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Shadow public abstract int getCount();

    @Inject(method={"getTooltip(Lnet/minecraft/item/Item$TooltipContext;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/tooltip/TooltipType;)Ljava/util/List;"}, at={@At(value="INVOKE", target="Lnet/minecraft/item/ItemStack;appendTooltip(Lnet/minecraft/component/ComponentType;Lnet/minecraft/item/Item$TooltipContext;Ljava/util/function/Consumer;Lnet/minecraft/item/tooltip/TooltipType;)V", shift=At.Shift.AFTER, ordinal=5)})
    public void appendAfterLore(Item.TooltipContext context, PlayerEntity player, TooltipType type, CallbackInfoReturnable<List<Text>> cir, @Local(index = 6) Consumer<Text> textConsumer) {
        if (DonutAuctions.getInstance().shouldRenderItem((ItemStack) (Object) this)) {
            textConsumer.accept(ItemCache.getInstance().getPrice((ItemStack) (Object) this).getMessage(getCount()));
        }
    }
}
