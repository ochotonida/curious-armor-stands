/*
 * See https://github.com/TheIllusiveC4/Curios/blob/1.16.x-forge/src/main/java/top/theillusivec4/curios/common/capability/CurioInventoryCapability.java
 */

package curiousarmorstands;

import net.minecraft.entity.LivingEntity;
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
import top.theillusivec4.curios.api.SlotContext;
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

        CurioInventoryWrapper(LivingEntity entity) {
            wearer = entity;
            reset();
        }

        @Override
        public void reset() {
            if (!wearer.getEntityWorld().isRemote()) {
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
                    loseStacks(stackHandler.getStacks(), identifier, toShrink);
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

        private void loseStacks(IDynamicStackHandler stackHandler, String identifier, int amount) {
            if (wearer != null && !wearer.getEntityWorld().isRemote()) {
                List<ItemStack> drops = new ArrayList<>();

                for (int i = stackHandler.getSlots() - amount; i < stackHandler.getSlots(); i++) {
                    ItemStack stack = stackHandler.getStackInSlot(i);
                    drops.add(stackHandler.getStackInSlot(i));
                    SlotContext slotContext = new SlotContext(identifier, wearer, i);

                    if (!stack.isEmpty()) {
                        UUID uuid = UUID.nameUUIDFromBytes((identifier + i).getBytes());
                        wearer.getAttributeManager().removeModifiers(CuriosApi.getCuriosHelper().getAttributeModifiers(slotContext, uuid, stack));
                        final int index = i;
                        // noinspection deprecation
                        CuriosApi.getCuriosHelper().getCurio(stack).ifPresent(curio -> curio.onUnequip(identifier, index, wearer));
                    }
                    stackHandler.setStackInSlot(i, ItemStack.EMPTY);
                }
                drops.forEach(drop -> dropStack(wearer, drop));
            }
        }

        private void dropStack(LivingEntity entity, ItemStack stack) {
            if (!entity.world.isRemote) {
                ItemEntity itemEntity = new ItemEntity(entity.world, entity.getPosX(), entity.getPosY() + 0.5, entity.getPosZ(), stack);
                entity.world.addEntity(itemEntity);
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
        public void setEnchantmentBonuses(Tuple<Integer, Integer> tuple) {

        }
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
            CompoundNBT result = new CompoundNBT();
            ListNBT slotTypes = new ListNBT();
            instance.getCurios().forEach((key, stacksHandler) -> {
                CompoundNBT tag = new CompoundNBT();
                tag.put("StacksHandler", stacksHandler.serializeNBT());
                tag.putString("Identifier", key);
                slotTypes.add(tag);
            });
            result.put("Curios", slotTypes);
            return result;
        }

        @Override
        public void deserializeNBT(INBT nbt) {
            ListNBT slotTypes = ((CompoundNBT) nbt).getList("Curios", Constants.NBT.TAG_COMPOUND);
            ISlotHelper slotHelper = CuriosApi.getSlotHelper();

            if (!slotTypes.isEmpty() && slotHelper != null) {
                Map<String, ICurioStacksHandler> curios = new LinkedHashMap<>();
                SortedMap<ISlotType, ICurioStacksHandler> sortedCurios = slotHelper.createSlots();

                for (int slotTypeIndex = 0; slotTypeIndex < slotTypes.size(); slotTypeIndex++) {
                    CompoundNBT curioTag = slotTypes.getCompound(slotTypeIndex);
                    String identifier = curioTag.getString("Identifier");
                    CurioStacksHandler prevStacksHandler = new CurioStacksHandler();
                    prevStacksHandler.deserializeNBT(curioTag.getCompound("StacksHandler"));

                    Optional<ISlotType> optionalSlotType = CuriosApi.getSlotHelper().getSlotType(identifier);
                    optionalSlotType.ifPresent(slotType -> {
                        CurioStacksHandler newStacksHandler = new CurioStacksHandler(slotType.getSize(), prevStacksHandler.getSizeShift(), slotType.isVisible(), slotType.hasCosmetic());
                        
                        int slotIndex = 0;
                        while (slotIndex < newStacksHandler.getSlots() && slotIndex < prevStacksHandler.getSlots()) {
                            ItemStack prevStack = prevStacksHandler.getStacks().getStackInSlot(slotIndex);
                            if (!prevStack.isEmpty()) {
                                newStacksHandler.getStacks().setStackInSlot(slotIndex, prevStack);
                            }

                            ItemStack prevCosmetic = prevStacksHandler.getCosmeticStacks().getStackInSlot(slotIndex);
                            if (!prevCosmetic.isEmpty()) {
                                newStacksHandler.getCosmeticStacks().setStackInSlot(slotIndex, prevStacksHandler.getCosmeticStacks().getStackInSlot(slotIndex));
                            }
                            
                            slotIndex++;
                        }

                        while (slotIndex < prevStacksHandler.getSlots()) {
                            instance.loseInvalidStack(prevStacksHandler.getStacks().getStackInSlot(slotIndex));
                            instance.loseInvalidStack(prevStacksHandler.getCosmeticStacks().getStackInSlot(slotIndex));
                            slotIndex++;
                        }
                        
                        sortedCurios.put(slotType, newStacksHandler);
                    });

                    if (!optionalSlotType.isPresent()) {
                        IDynamicStackHandler stackHandler = prevStacksHandler.getStacks();
                        IDynamicStackHandler cosmeticStackHandler = prevStacksHandler.getCosmeticStacks();

                        for (int removedSlotIndex = 0; removedSlotIndex < stackHandler.getSlots(); removedSlotIndex++) {
                            ItemStack stack = stackHandler.getStackInSlot(removedSlotIndex);
                            if (!stack.isEmpty()) {
                                instance.loseInvalidStack(stack);
                            }

                            ItemStack cosmeticStack = cosmeticStackHandler.getStackInSlot(removedSlotIndex);
                            if (!cosmeticStack.isEmpty()) {
                                instance.loseInvalidStack(cosmeticStack);
                            }
                        }
                    }
                }
                sortedCurios.forEach((slotType, stacksHandler) -> curios.put(slotType.getIdentifier(), stacksHandler));
                instance.setCurios(curios);
            }
        }
    }
}
