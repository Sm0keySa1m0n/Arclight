package io.izzel.arclight.common.mixin.bukkit;

import io.izzel.arclight.common.bridge.bukkit.CraftItemStackBridge;
import io.izzel.arclight.common.bridge.bukkit.ItemMetaBridge;
import io.izzel.arclight.common.bridge.item.ItemStackBridge;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v.inventory.CraftItemFactory;
import org.bukkit.craftbukkit.v.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v.legacy.CraftLegacy;
import org.bukkit.inventory.meta.ItemMeta;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Objects;

@Mixin(value = CraftItemStack.class, remap = false)
public abstract class CraftItemStackMixin implements CraftItemStackBridge {

    // @formatter:off
    @Shadow ItemStack handle;
    @Shadow public abstract Material getType();
    @Shadow public abstract short getDurability();
    @Shadow public abstract boolean hasItemMeta();
    @Shadow static Material getType(ItemStack item) { throw new RuntimeException(); }
    // @formatter:on

    @Inject(method = "getItemMeta(Lnet/minecraft/item/ItemStack;)Lorg/bukkit/inventory/meta/ItemMeta;", cancellable = true, at = @At(value = "INVOKE", target = "Lorg/bukkit/Material;ordinal()I"))
    private static void arclight$noTag(ItemStack item, CallbackInfoReturnable<ItemMeta> cir) {
        if (item.getTag() == null) {
            ItemMeta meta = CraftItemFactory.instance().getItemMeta(getType(item));
            ((ItemMetaBridge) meta).bridge$setForgeCaps(((ItemStackBridge) (Object) item).bridge$getForgeCaps());
            cir.setReturnValue(meta);
        }
    }

    @Inject(method = "getItemMeta(Lnet/minecraft/item/ItemStack;)Lorg/bukkit/inventory/meta/ItemMeta;", at = @At("RETURN"))
    private static void arclight$offerCaps(ItemStack item, CallbackInfoReturnable<ItemMeta> cir) {
        if (item == null) return;
        ItemMeta meta = cir.getReturnValue();
        CompoundNBT tag = item.getTag();
        if (tag != null) {
            ((ItemMetaBridge) meta).bridge$offerUnhandledTags(tag);
        }
        ((ItemMetaBridge) meta).bridge$setForgeCaps(((ItemStackBridge) (Object) item).bridge$getForgeCaps());
    }

    @Redirect(method = "setItemMeta(Lnet/minecraft/item/ItemStack;Lorg/bukkit/inventory/meta/ItemMeta;)Z", at = @At(value = "INVOKE", ordinal = 1, remap = true, target = "Lnet/minecraft/item/ItemStack;setTag(Lnet/minecraft/nbt/CompoundNBT;)V"))
    private static void arclight$setTagLater(ItemStack instance, CompoundNBT nbt) {
    }

    @Inject(method = "setItemMeta(Lnet/minecraft/item/ItemStack;Lorg/bukkit/inventory/meta/ItemMeta;)Z", locals = LocalCapture.CAPTURE_FAILHARD,
        at = @At(value = "INVOKE", ordinal = 1, remap = true, target = "Lnet/minecraft/item/ItemStack;getItem()Lnet/minecraft/item/Item;"))
    private static void arclight$setCaps(ItemStack item, ItemMeta itemMeta, CallbackInfoReturnable<Boolean> cir, Item oldItem, Item newItem, CompoundNBT tag) {
        if (!tag.isEmpty()) {
            item.setTag(tag);
        } else {
            item.setTag(null);
        }
        CompoundNBT forgeCaps = ((ItemMetaBridge) itemMeta).bridge$getForgeCaps();
        if (forgeCaps != null) {
            ((ItemStackBridge) (Object) item).bridge$setForgeCaps(forgeCaps.copy());
        }
    }

    /**
     * @author IzzelAliz
     * @reason
     */
    @Overwrite
    public boolean isSimilar(org.bukkit.inventory.ItemStack stack) {
        if (stack == null) {
            return false;
        }
        if (stack == (Object) this) {
            return true;
        }
        if (!(stack instanceof CraftItemStack)) {
            return stack.getClass() == org.bukkit.inventory.ItemStack.class && stack.isSimilar((org.bukkit.inventory.ItemStack) (Object) this);
        }

        CraftItemStack that = (CraftItemStack) stack;
        if (handle == ((CraftItemStackBridge) (Object) that).bridge$getHandle()) {
            return true;
        }
        if (handle == null || ((CraftItemStackBridge) (Object) that).bridge$getHandle() == null) {
            return false;
        }
        Material comparisonType = CraftLegacy.fromLegacy(that.getType()); // This may be called from legacy item stacks, try to get the right material
        if (!(comparisonType == this.getType() && getDurability() == that.getDurability())) {
            return false;
        }
        return hasItemMeta()
            ? (that.hasItemMeta()
            && Objects.equals(handle.getTag(), ((CraftItemStackBridge) (Object) that).bridge$getHandle().getTag())
            && Objects.equals(((ItemStackBridge) (Object) handle).bridge$getForgeCaps(), ((ItemStackBridge) (Object) ((CraftItemStackBridge) (Object) that).bridge$getHandle()).bridge$getForgeCaps()))
            : !that.hasItemMeta();
    }

    @Inject(method = "hasItemMeta(Lnet/minecraft/item/ItemStack;)Z", cancellable = true, at = @At("HEAD"))
    private static void arclight$hasMeta(ItemStack item, CallbackInfoReturnable<Boolean> cir) {
        if (item != null) {
            CompoundNBT forgeCaps = ((ItemStackBridge) (Object) item).bridge$getForgeCaps();
            if (forgeCaps != null && !forgeCaps.isEmpty()) {
                cir.setReturnValue(true);
            }
        }
    }

    @Override
    public ItemStack bridge$getHandle() {
        return handle;
    }
}
