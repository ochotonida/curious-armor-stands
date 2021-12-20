/*
 * See https://github.com/TheIllusiveC4/Curios/blob/1.16.x-forge/src/main/java/top/theillusivec4/curios/common/capability/CurioInventoryCapability.java
 */

package curiousarmorstands;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
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
        Set<ICurioStacksHandler> updates = new HashSet<>();

        CurioInventoryWrapper(ArmorStand entity) {
            wearer = entity;
            reset();
        }

        @Override
        public void reset() {
            ISlotHelper slotHelper = CuriosApi.getSlotHelper();

            if (slotHelper != null && this.wearer != null && !this.wearer.getCommandSenderWorld().isClientSide()) {
                this.curios.clear();
                this.invalidStacks.clear();
                SortedSet<ISlotType> sorted = new TreeSet<>(slotHelper.getSlotTypes(this.wearer));

                for (ISlotType slotType : sorted) {
                    this.curios.put(slotType.getIdentifier(), new CurioStacksHandler(
                            this, slotType.getIdentifier(), slotType.getSize(), 0, slotType.isVisible(), slotType.hasCosmetic())
                    );
                }
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
        @SuppressWarnings("deprecation")
        public void growSlotType(String identifier, int amount) {
            if (amount > 0) {
                getStacksHandler(identifier).ifPresent(stackHandler -> stackHandler.grow(amount));
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public void shrinkSlotType(String identifier, int amount) {
            if (amount > 0) {
                this.getStacksHandler(identifier).ifPresent(stackHandler -> stackHandler.shrink(amount));
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
        public ListTag saveInventory(boolean clear) {
            ListTag taglist = new ListTag();

            for (Map.Entry<String, ICurioStacksHandler> entry : curios.entrySet()) {
                CompoundTag tag = new CompoundTag();
                ICurioStacksHandler stacksHandler = entry.getValue();
                IDynamicStackHandler stacks = stacksHandler.getStacks();
                IDynamicStackHandler cosmetics = stacksHandler.getCosmeticStacks();
                tag.put("Stacks", stacks.serializeNBT());
                tag.put("Cosmetics", cosmetics.serializeNBT());
                tag.putString("Identifier", entry.getKey());
                taglist.add(tag);

                if (clear) {

                    for (int i = 0; i < stacks.getSlots(); i++) {
                        stacks.setStackInSlot(i, ItemStack.EMPTY);
                    }

                    for (int i = 0; i < cosmetics.getSlots(); i++) {
                        cosmetics.setStackInSlot(i, ItemStack.EMPTY);
                    }
                }
            }
            return taglist;
        }

        @Override
        public void loadInventory(ListTag data) {

            if (data != null) {

                for (int i = 0; i < data.size(); i++) {
                    CompoundTag tag = data.getCompound(i);
                    String identifier = tag.getString("Identifier");
                    ICurioStacksHandler stacksHandler = curios.get(identifier);

                    if (stacksHandler != null) {
                        CompoundTag stacksData = tag.getCompound("Stacks");
                        ItemStackHandler loaded = new ItemStackHandler();
                        IDynamicStackHandler stacks = stacksHandler.getStacks();

                        if (!stacksData.isEmpty()) {
                            loaded.deserializeNBT(stacksData);
                            loadStacks(stacksHandler, loaded, stacks);
                        }
                        stacksData = tag.getCompound("Cosmetics");

                        if (!stacksData.isEmpty()) {
                            loaded.deserializeNBT(stacksData);
                            stacks = stacksHandler.getCosmeticStacks();
                            loadStacks(stacksHandler, loaded, stacks);
                        }
                    }
                }
            }
        }

        @Override
        public Set<ICurioStacksHandler> getUpdatingInventories() {
            return this.updates;
        }

        @Override
        public void addTransientSlotModifiers(Multimap<String, AttributeModifier> modifiers) {

            for (Map.Entry<String, Collection<AttributeModifier>> entry : modifiers.asMap().entrySet()) {
                String id = entry.getKey();

                for (AttributeModifier attributeModifier : entry.getValue()) {
                    ICurioStacksHandler stacksHandler = this.curios.get(id);

                    if (stacksHandler != null) {
                        stacksHandler.addTransientModifier(attributeModifier);
                    }
                }
            }
        }

        @Override
        public void addPermanentSlotModifiers(Multimap<String, AttributeModifier> modifiers) {

            for (Map.Entry<String, Collection<AttributeModifier>> entry : modifiers.asMap().entrySet()) {
                String id = entry.getKey();

                for (AttributeModifier attributeModifier : entry.getValue()) {
                    ICurioStacksHandler stacksHandler = this.curios.get(id);

                    if (stacksHandler != null) {
                        stacksHandler.addPermanentModifier(attributeModifier);
                    }
                }
            }
        }

        @Override
        public void removeSlotModifiers(Multimap<String, AttributeModifier> modifiers) {

            for (Map.Entry<String, Collection<AttributeModifier>> entry : modifiers.asMap().entrySet()) {
                String id = entry.getKey();

                for (AttributeModifier attributeModifier : entry.getValue()) {
                    ICurioStacksHandler stacksHandler = this.curios.get(id);

                    if (stacksHandler != null) {
                        stacksHandler.removeModifier(attributeModifier.getId());
                    }
                }
            }
        }

        @Override
        public void clearSlotModifiers() {

            for (Map.Entry<String, ICurioStacksHandler> entry : this.curios.entrySet()) {
                entry.getValue().clearModifiers();
            }
        }

        @Override
        public void clearCachedSlotModifiers() {

            for (Map.Entry<String, ICurioStacksHandler> entry : this.curios.entrySet()) {
                entry.getValue().clearCachedModifiers();
            }
        }

        @Override
        public Multimap<String, AttributeModifier> getModifiers() {
            Multimap<String, AttributeModifier> result = HashMultimap.create();

            for (Map.Entry<String, ICurioStacksHandler> entry : this.curios.entrySet()) {
                result.putAll(entry.getKey(), entry.getValue().getModifiers().values());
            }
            return result;
        }

        private void loadStacks(ICurioStacksHandler stacksHandler, ItemStackHandler loaded,
                                IDynamicStackHandler stacks) {

            for (int j = 0; j < stacksHandler.getSlots() && j < loaded.getSlots(); j++) {
                ItemStack stack = stacks.getStackInSlot(j);
                ItemStack loadedStack = loaded.getStackInSlot(j);

                if (stack.isEmpty()) {
                    stacks.setStackInSlot(j, loadedStack);
                } else {
                    this.loseInvalidStack(stack);
                }
            }
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
            ListTag slotTypes = ((CompoundTag) tag).getList("Curios", Tag.TAG_COMPOUND);
            ISlotHelper slotHelper = CuriosApi.getSlotHelper();

            if (!slotTypes.isEmpty() && slotHelper != null) {
                Map<String, ICurioStacksHandler> curios = new LinkedHashMap<>();
                SortedMap<ISlotType, ICurioStacksHandler> sortedCurios = new TreeMap<>();
                SortedSet<ISlotType> sorted = new TreeSet<>(CuriosApi.getSlotHelper().getSlotTypes(this.wearer));

                for (ISlotType slotType : sorted) {
                    sortedCurios.put(slotType, new CurioStacksHandler(
                            this, slotType.getIdentifier(), slotType.getSize(), 0, slotType.isVisible(), slotType.hasCosmetic()
                    ));
                }

                for (int i = 0; i < slotTypes.size(); i++) {
                    CompoundTag curioTag = slotTypes.getCompound(i);
                    String identifier = curioTag.getString("Identifier");
                    CurioStacksHandler prevStacksHandler = new CurioStacksHandler(this, identifier);
                    prevStacksHandler.deserializeNBT(curioTag.getCompound("StacksHandler"));

                    Optional<ISlotType> optionalType = CuriosApi.getSlotHelper().getSlotType(identifier);
                    optionalType.ifPresent(type -> {
                        CurioStacksHandler newStacksHandler = new CurioStacksHandler(
                                this, type.getIdentifier(), type.getSize(), prevStacksHandler.getSizeShift(), type.isVisible(), type.hasCosmetic()
                        );
                        newStacksHandler.copyModifiers(prevStacksHandler);
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

                        for (int j = 0; j < newStacksHandler.getRenders().size() && j < prevStacksHandler.getRenders().size(); j++) {
                            newStacksHandler.getRenders().set(j, prevStacksHandler.getRenders().get(j));
                        }
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
                sortedCurios.forEach((slotType, stacksHandler) -> curios.put(slotType.getIdentifier(), stacksHandler));
                setCurios(curios);
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
