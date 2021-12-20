/*
 * See https://github.com/TheIllusiveC4/Curios/blob/1.16.x-forge/src/main/java/top/theillusivec4/curios/common/capability/CurioInventoryCapability.java
 */

package curiousarmorstands;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.NonNullList;
import net.minecraft.util.Tuple;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.LazyOptional;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.api.type.util.ISlotHelper;
import top.theillusivec4.curios.common.inventory.CurioStacksHandler;

import javax.annotation.Nonnull;
import java.util.*;

public class CurioInventoryCapability {

    public static ICapabilityProvider createProvider(LivingEntity entity) {
        return new Provider(entity);
    }

    public static class CurioInventoryWrapper implements ICuriosItemHandler {

        Map<String, ICurioStacksHandler> curios = new LinkedHashMap<>();
        NonNullList<ItemStack> invalidStacks = NonNullList.create();
        LivingEntity wearer;
        Set<ICurioStacksHandler> updates = new HashSet<>();

        CurioInventoryWrapper(LivingEntity entity) {
            wearer = entity;
            reset();
        }

        @Override
        public void reset() {
            ISlotHelper slotHelper = CuriosApi.getSlotHelper();

            if (slotHelper != null && this.wearer != null && !this.wearer.getEntityWorld().isRemote()) {
                this.curios.clear();
                this.invalidStacks.clear();
                SortedSet<ISlotType> sorted = new TreeSet<>(slotHelper.getSlotTypes(this.wearer));

                for (ISlotType slotType : sorted) {
                    this.curios.put(slotType.getIdentifier(),
                            new CurioStacksHandler(this, slotType.getIdentifier(), slotType.getSize(), 0,
                                    slotType.isVisible(), slotType.hasCosmetic()));
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
        public Set<String> getLockedSlots() {
            return Collections.emptySet();
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
        public void unlockSlotType(String identifier, int amount, boolean visible, boolean cosmetic) { }

        @Override
        public void lockSlotType(String identifier) { }

        @Override
        public void processSlots() { }

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
                getStacksHandler(identifier).ifPresent(stackHandler -> stackHandler.shrink(amount));
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

        private void dropStack(LivingEntity entity, ItemStack stack) {
            if (!entity.world.isRemote) {
                ItemEntity itemEntity = new ItemEntity(entity.world, entity.getPosX(), entity.getPosY() + 0.5, entity.getPosZ(), stack);
                entity.world.addEntity(itemEntity);
            }
        }

        @Override
        public Set<ICurioStacksHandler> getUpdatingInventories() {
            return updates;
        }

        @Override
        public ListNBT saveInventory(boolean clear) {
            return new ListNBT();
        }

        @Override
        public void loadInventory(ListNBT data) {

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
                        stacksHandler.removeModifier(attributeModifier.getID());
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

        @Override
        public CompoundNBT writeNBT() {
            CompoundNBT compound = new CompoundNBT();

            ListNBT taglist = new ListNBT();
            this.getCurios().forEach((key, stacksHandler) -> {
                CompoundNBT tag = new CompoundNBT();
                tag.put("StacksHandler", stacksHandler.serializeNBT());
                tag.putString("Identifier", key);
                taglist.add(tag);
            });
            compound.put("Curios", taglist);

            return compound;
        }

        @Override
        public void readNBT(CompoundNBT compoundNBT) {
            ListNBT tagList = compoundNBT.getList("Curios", Constants.NBT.TAG_COMPOUND);
            ISlotHelper slotHelper = CuriosApi.getSlotHelper();

            if (!tagList.isEmpty() && slotHelper != null) {
                Map<String, ICurioStacksHandler> curios = new LinkedHashMap<>();
                SortedMap<ISlotType, ICurioStacksHandler> sortedCurios = new TreeMap<>();
                SortedSet<ISlotType> sorted =
                        new TreeSet<>(CuriosApi.getSlotHelper().getSlotTypes(this.wearer));

                for (ISlotType slotType : sorted) {
                    sortedCurios.put(slotType,
                            new CurioStacksHandler(this, slotType.getIdentifier(), slotType.getSize(), 0,
                                    slotType.isVisible(), slotType.hasCosmetic()));
                }

                for (int i = 0; i < tagList.size(); i++) {
                    CompoundNBT tag = tagList.getCompound(i);
                    String identifier = tag.getString("Identifier");
                    CurioStacksHandler prevStacksHandler = new CurioStacksHandler(this, identifier);
                    prevStacksHandler.deserializeNBT(tag.getCompound("StacksHandler"));

                    Optional<ISlotType> optionalType = CuriosApi.getSlotHelper().getSlotType(identifier);
                    optionalType.ifPresent(type -> {
                        CurioStacksHandler newStacksHandler = new CurioStacksHandler(
                                this, type.getIdentifier(), type.getSize(),
                                prevStacksHandler.getSizeShift(), type.isVisible(), type.hasCosmetic());
                        newStacksHandler.copyModifiers(prevStacksHandler);
                        int index = 0;

                        while (index < newStacksHandler.getSlots() && index < prevStacksHandler.getSlots()) {

                            ItemStack prevStack = prevStacksHandler.getStacks().getStackInSlot(index);
                            if (!prevStack.isEmpty()) {
                                newStacksHandler.getStacks().setStackInSlot(index, prevStack);
                            }

                            ItemStack prevCosmetic = prevStacksHandler.getCosmeticStacks().getStackInSlot(index);
                            if (!prevCosmetic.isEmpty()) {
                                newStacksHandler.getCosmeticStacks().setStackInSlot(index, prevStacksHandler.getCosmeticStacks().getStackInSlot(index));
                            }

                            index++;
                        }

                        while (index < prevStacksHandler.getSlots()) {
                            this.loseInvalidStack(prevStacksHandler.getStacks().getStackInSlot(index));
                            this.loseInvalidStack(prevStacksHandler.getCosmeticStacks().getStackInSlot(index));
                            index++;
                        }

                        sortedCurios.put(type, newStacksHandler);

                        for (int j = 0;
                             j < newStacksHandler.getRenders().size() &&
                                     j < prevStacksHandler.getRenders()
                                             .size(); j++) {
                            newStacksHandler.getRenders().set(j, prevStacksHandler.getRenders().get(j));
                        }
                    });

                    if (!optionalType.isPresent()) {
                        IDynamicStackHandler stackHandler = prevStacksHandler.getStacks();
                        IDynamicStackHandler cosmeticStackHandler = prevStacksHandler.getCosmeticStacks();

                        for (int j = 0; j < stackHandler.getSlots(); j++) {
                            ItemStack stack = stackHandler.getStackInSlot(j);

                            if (!stack.isEmpty()) {
                                this.loseInvalidStack(stack);
                            }

                            ItemStack cosmeticStack = cosmeticStackHandler.getStackInSlot(j);

                            if (!cosmeticStack.isEmpty()) {
                                this.loseInvalidStack(cosmeticStack);
                            }
                        }
                    }
                }
                sortedCurios.forEach((slotType, stacksHandler) -> curios.put(slotType.getIdentifier(), stacksHandler));
                this.setCurios(curios);
            }
        }

        @Override
        public int getFortuneBonus() {
            return 0;
        }

        @Override
        public int getLootingBonus() {
            return 0;
        }

        @Override
        public void setEnchantmentBonuses(Tuple<Integer, Integer> tuple) { }
    }

    public static class Provider implements ICapabilitySerializable<INBT> {

        final LazyOptional<ICuriosItemHandler> optional;
        final ICuriosItemHandler instance;

        Provider(LivingEntity entity) {
            instance = new CurioInventoryWrapper(entity);
            optional = LazyOptional.of(() -> instance);
        }

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, Direction facing) {
            return CuriosCapability.INVENTORY.orEmpty(capability, optional);
        }

        @Override
        public INBT serializeNBT() {
            return CuriosCapability.INVENTORY.writeNBT(this.instance, null);
        }

        @Override
        public void deserializeNBT(INBT nbt) {
            CuriosCapability.INVENTORY.readNBT(this.instance, null, nbt);
        }
    }
}
