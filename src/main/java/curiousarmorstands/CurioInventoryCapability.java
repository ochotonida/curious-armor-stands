/*
 * See https://github.com/TheIllusiveC4/Curios/blob/1.16.x-forge/src/main/java/top/theillusivec4/curios/common/capability/CurioInventoryCapability.java
 */

package curiousarmorstands;

import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.LazyOptional;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.api.type.util.ISlotHelper;
import top.theillusivec4.curios.common.inventory.CurioStacksHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class CurioInventoryCapability {

    public static ICapabilityProvider createProvider(ArmorStand entity) {
        return new Provider(entity);
    }

    public static class CurioInventoryWrapper implements ICuriosItemHandler {

        Map<String, ICurioStacksHandler> curios = new LinkedHashMap<>();
        NonNullList<ItemStack> invalidStacks = NonNullList.create();
        ArmorStand wearer;

        CurioInventoryWrapper(ArmorStand entity) {
            wearer = entity;
            reset();
        }

        @Override
        public void reset() {
            if (!wearer.level.isClientSide()) {
                curios.clear();
                invalidStacks.clear();
                CuriosApi.getSlotHelper().createSlots().forEach(
                        (slotType, stacksHandler) -> curios.put(slotType.getIdentifier(), stacksHandler)
                );
            }
        }

        @Override
        public int getSlots() {
            int totalSlots = 0;

            for (ICurioStacksHandler stacks : curios.values()) {
                totalSlots += stacks.getSlots();
            }
            return totalSlots;
        }

        @Override
        public int getVisibleSlots() {
            int totalSlots = 0;

            for (ICurioStacksHandler stacks : curios.values()) {
                if (stacks.isVisible()) {
                    totalSlots += stacks.getSlots();
                }
            }
            return totalSlots;
        }

        @Override
        public Optional<ICurioStacksHandler> getStacksHandler(String identifier) {
            return Optional.ofNullable(curios.get(identifier));
        }

        @Override
        public Map<String, ICurioStacksHandler> getCurios() {
            return Collections.unmodifiableMap(curios);
        }

        @Override
        public void setCurios(Map<String, ICurioStacksHandler> curios) {
            this.curios = curios;
        }

        @Override
        public void growSlotType(String identifier, int amount) {
            if (amount > 0) {
                getStacksHandler(identifier).ifPresent(stackHandler -> stackHandler.grow(amount));
            }
        }

        @Override
        public void shrinkSlotType(String identifier, int amount) {
            if (amount > 0) {
                getStacksHandler(identifier).ifPresent(stackHandler -> {
                    int toShrink = Math.min(stackHandler.getSlots() - 1, amount);
                    loseStacks(stackHandler.getStacks(), identifier, toShrink, stackHandler);
                    stackHandler.shrink(amount);
                });
            }
        }

        @Override
        public LivingEntity getWearer() {
            return wearer;
        }

        @Override
        public void loseInvalidStack(ItemStack stack) {
            invalidStacks.add(stack);
        }

        @Override
        public void handleInvalidStacks() {
            if (wearer != null && !invalidStacks.isEmpty()) {
                invalidStacks.forEach(drop -> dropStack(wearer, drop));
                invalidStacks = NonNullList.create();
            }
        }

        @Override
        public int getFortuneLevel(@Nullable LootContext lootContext) {
            return 0;
        }

        @Override
        public int getLootingLevel(DamageSource source, LivingEntity target, int baseLooting) {
            return 0;
        }

        @Override
        public Tag writeTag() {
            CompoundTag result = new CompoundTag();
            ListTag slotTypes = new ListTag();

            getCurios().forEach((key, stacksHandler) -> {
                CompoundTag tag = new CompoundTag();
                tag.put("StacksHandler", stacksHandler.serializeNBT());
                tag.putString("Identifier", key);
                slotTypes.add(tag);
            });

            result.put("Curios", slotTypes);
            return result;
        }

        @Override
        public void readTag(Tag tag) {
            ListTag slotTypes = ((CompoundTag) tag).getList("Curios", Constants.NBT.TAG_COMPOUND);
            ISlotHelper slotHelper = CuriosApi.getSlotHelper();

            if (!slotTypes.isEmpty() && slotHelper != null) {
                Map<String, ICurioStacksHandler> curios = new LinkedHashMap<>();
                SortedMap<ISlotType, ICurioStacksHandler> sortedCurios = slotHelper.createSlots();

                for (int i = 0; i < slotTypes.size(); i++) {
                    CompoundTag curioTag = slotTypes.getCompound(i);
                    String identifier = curioTag.getString("Identifier");
                    CurioStacksHandler prevStacksHandler = new CurioStacksHandler();
                    prevStacksHandler.deserializeNBT(curioTag.getCompound("StacksHandler"));

                    Optional<ISlotType> optionalType = CuriosApi.getSlotHelper().getSlotType(identifier);
                    optionalType.ifPresent(type -> {
                        CurioStacksHandler newStacksHandler = new CurioStacksHandler(
                                type.getSize(), prevStacksHandler.getSizeShift(), type.isVisible(), type.hasCosmetic()
                        );
                        int slotIndex = 0;

                        while (slotIndex < newStacksHandler.getSlots() && slotIndex < prevStacksHandler.getSlots()) {
                            ItemStack prevStack = prevStacksHandler.getStacks().getStackInSlot(slotIndex);

                            if (!prevStack.isEmpty()) {
                                newStacksHandler.getStacks().setStackInSlot(slotIndex, prevStack);
                            }
                            ItemStack prevCosmetic = prevStacksHandler.getCosmeticStacks().getStackInSlot(slotIndex);

                            if (!prevCosmetic.isEmpty()) {
                                newStacksHandler.getCosmeticStacks().setStackInSlot(
                                        slotIndex, prevStacksHandler.getCosmeticStacks().getStackInSlot(slotIndex)
                                );
                            }
                            slotIndex++;
                        }

                        while (slotIndex < prevStacksHandler.getSlots()) {
                            this.loseInvalidStack(prevStacksHandler.getStacks().getStackInSlot(slotIndex));
                            this.loseInvalidStack(prevStacksHandler.getCosmeticStacks().getStackInSlot(slotIndex));
                            slotIndex++;
                        }

                        sortedCurios.put(type, newStacksHandler);
                    });

                    if (optionalType.isEmpty()) {
                        IDynamicStackHandler stackHandler = prevStacksHandler.getStacks();
                        IDynamicStackHandler cosmeticStackHandler = prevStacksHandler.getCosmeticStacks();

                        for (int removedSlotIndex = 0; removedSlotIndex < stackHandler.getSlots(); removedSlotIndex++) {
                            ItemStack stack = stackHandler.getStackInSlot(removedSlotIndex);
                            if (!stack.isEmpty()) {
                                loseInvalidStack(stack);
                            }

                            ItemStack cosmeticStack = cosmeticStackHandler.getStackInSlot(removedSlotIndex);
                            if (!cosmeticStack.isEmpty()) {
                                loseInvalidStack(cosmeticStack);
                            }
                        }
                    }
                }

                sortedCurios.forEach(
                        (slotType, stacksHandler) -> curios.put(slotType.getIdentifier(), stacksHandler)
                );

                setCurios(curios);
            }
        }

        private void loseStacks(IDynamicStackHandler stackHandler, String identifier, int amount, ICurioStacksHandler curioStacks) {
            if (wearer != null && !wearer.level.isClientSide()) {
                List<ItemStack> drops = new ArrayList<>();

                for (int i = stackHandler.getSlots() - amount; i < stackHandler.getSlots(); i++) {
                    ItemStack stack = stackHandler.getStackInSlot(i);
                    SlotContext slotContext = new SlotContext(identifier, wearer, i, false, curioStacks.getRenders().get(i));
                    drops.add(stack);

                    if (!stack.isEmpty()) {
                        UUID uuid = UUID.nameUUIDFromBytes((identifier + i).getBytes());
                        wearer.getAttributes().removeAttributeModifiers(
                                CuriosApi.getCuriosHelper().getAttributeModifiers(slotContext, uuid, stack)
                        );
                        CuriosApi.getCuriosHelper().getCurio(stack).ifPresent(curio -> curio.onUnequip(slotContext, stack));
                    }
                    stackHandler.setStackInSlot(i, ItemStack.EMPTY);
                }
                drops.forEach(drop -> dropStack(wearer, drop));
            }
        }

        private void dropStack(LivingEntity entity, ItemStack stack) {
            if (!entity.level.isClientSide()) {
                ItemEntity itemEntity = new ItemEntity(entity.level, entity.getX(), entity.getY() + 0.5, entity.getZ(), stack);
                entity.level.addFreshEntity(itemEntity);
            }
        }
    }

    public static class Provider implements ICapabilitySerializable<Tag> {

        final LazyOptional<ICuriosItemHandler> optional;
        final ICuriosItemHandler handler;

        private Provider(ArmorStand entity) {
            handler = new CurioInventoryWrapper(entity);
            optional = LazyOptional.of(() -> handler);
        }

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, Direction facing) {
            return CuriosCapability.INVENTORY.orEmpty(capability, optional);
        }

        @Override
        public Tag serializeNBT() {
            return handler.writeTag();
        }

        @Override
        public void deserializeNBT(Tag tag) {
             handler.readTag(tag);
        }
    }
}
