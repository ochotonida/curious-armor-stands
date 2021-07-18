package curiousarmorstands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.SlotTypeMessage;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.Optional;

@Mod(CuriousArmorStands.MODID)
@SuppressWarnings("unused")
public class CuriousArmorStands {

    public static final String MODID = "curious_armor_stands";

    public static final String SLOT = "armor_stand_curio";

    @Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            EntityRenderer<?> renderer = Minecraft.getInstance().getRenderManager().renderers.get(EntityType.ARMOR_STAND);
            if (renderer instanceof ArmorStandRenderer) {
                ((ArmorStandRenderer) renderer).addLayer(new CuriosLayer<>((ArmorStandRenderer) renderer));
            }
        }
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {

        @SubscribeEvent
        public static void enqueueIMC(final InterModEnqueueEvent event) {
            InterModComms.sendTo("curios", SlotTypeMessage.REGISTER_TYPE, () -> new SlotTypeMessage.Builder(SLOT).cosmetic().size(0).build());
        }
    }

    @Mod.EventBusSubscriber(modid = CuriousArmorStands.MODID)
    public static class Events {

        @SubscribeEvent
        public static void entityJoinWorld(EntityJoinWorldEvent event) {
            if (!event.getWorld().isRemote && event.getEntity() instanceof ArmorStandEntity) {
                CuriosApi.getSlotHelper().setSlotsForType(SLOT, (LivingEntity) event.getEntity(), 8);
            }
        }

        @SubscribeEvent
        public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof ArmorStandEntity) {
                event.addCapability(CuriosCapability.ID_INVENTORY, CurioInventoryCapability.createProvider((ArmorStandEntity) event.getObject()));
            }
        }

        @SubscribeEvent
        public static void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
            if (!(event.getTarget() instanceof ArmorStandEntity)) {
                return;
            }
            ArmorStandEntity armorStand = (ArmorStandEntity) event.getTarget();
            ItemStack stackInHand = event.getItemStack();

            if (!stackInHand.isEmpty()) {
                equipItem(armorStand, stackInHand, event);
            } else if (canUnequipCurio(event.getLocalPos(), armorStand)){
                unequipItem(armorStand, event);
            }
        }

        public static void equipItem(ArmorStandEntity armorStand, ItemStack stackInHand, PlayerInteractEvent.EntityInteractSpecific event) {
            if (CuriosApi.getCuriosHelper().getCurioTags(stackInHand.getItem()).isEmpty()) {
                return;
            }

            if (armorStand.world.isRemote) {
                event.setCancellationResult(ActionResultType.SUCCESS);
                event.setCanceled(true);
                return;
            }

            CuriosApi.getCuriosHelper().getCuriosHandler(armorStand).ifPresent(
                    handler -> handler.getStacksHandler(SLOT).ifPresent(
                            stacksHandler -> {
                                IDynamicStackHandler cosmeticStacks = stacksHandler.getCosmeticStacks();
                                Optional<ICurio> curio = CuriosApi.getCuriosHelper().getCurio(stackInHand).resolve();

                                for (int slot = 0; slot < cosmeticStacks.getSlots(); slot++) {
                                    if (cosmeticStacks.getStackInSlot(slot).isEmpty() && (!curio.isPresent() || curio.get().canEquip(SLOT, armorStand))) {
                                        cosmeticStacks.setStackInSlot(slot, stackInHand.copy());

                                        if (curio.isPresent()) {
                                            // noinspection deprecation
                                            curio.get().playRightClickEquipSound(armorStand);
                                        } else {
                                            armorStand.world.playSound(
                                                    null,
                                                    armorStand.getPosition(),
                                                    SoundEvents.ITEM_ARMOR_EQUIP_GENERIC,
                                                    armorStand.getSoundCategory(),
                                                    1,
                                                    1
                                            );
                                        }

                                        enableArmorStandArms(armorStand, stackInHand.getItem());

                                        if (!event.getPlayer().isCreative()) {
                                            int count = stackInHand.getCount();
                                            stackInHand.shrink(count);
                                        }

                                        event.setCancellationResult(ActionResultType.SUCCESS);
                                        event.setCanceled(true);
                                        return;
                                    }
                                }
                            }
                    )
            );
        }

        public static void unequipItem(ArmorStandEntity armorStand, PlayerInteractEvent.EntityInteractSpecific event) {
            CuriosApi.getCuriosHelper().getCuriosHandler(armorStand).ifPresent(handler -> handler.getStacksHandler(SLOT).ifPresent(stacksHandler -> {
                IDynamicStackHandler cosmeticStacks = stacksHandler.getCosmeticStacks();
                for (int slot = cosmeticStacks.getSlots() - 1; slot >= 0; slot--) {
                    ItemStack stackInSlot = cosmeticStacks.getStackInSlot(slot);
                    if (!stackInSlot.isEmpty()) {
                        if (!armorStand.world.isRemote()) {
                            event.getPlayer().setHeldItem(event.getHand(), stackInSlot);
                            cosmeticStacks.setStackInSlot(slot, ItemStack.EMPTY);
                        }
                        event.setCancellationResult(ActionResultType.SUCCESS);
                        event.setCanceled(true);
                        return;
                    }
                }
            }));
        }

        private static void enableArmorStandArms(ArmorStandEntity entity, Item curioItem) {
            if (CuriosApi.getCuriosHelper().getCurioTags(curioItem).contains("hands") || CuriosApi.getCuriosHelper().getCurioTags(curioItem).contains("ring") || CuriosApi.getCuriosHelper().getCurioTags(curioItem).contains("bracelet")) {
                CompoundNBT nbt = entity.writeWithoutTypeId(new CompoundNBT());
                nbt.putBoolean("ShowArms", true);
                entity.read(nbt);
            }
        }

        private static boolean canUnequipCurio(Vector3d localPos, ArmorStandEntity entity) {
            boolean isSmall = entity.isSmall();
            double y = isSmall ? localPos.y * 2 : localPos.y;
            return !(entity.hasItemInSlot(EquipmentSlotType.FEET) && y >= 0.1 && y < 0.1 + (isSmall ? 0.8 : 0.45))
                    && !(entity.hasItemInSlot(EquipmentSlotType.CHEST) && y >= 0.9 + (isSmall ? 0.3 : 0) && y < 0.9 + (isSmall ? 1 : 0.7))
                    && !(entity.hasItemInSlot(EquipmentSlotType.LEGS) && y >= 0.4 && y < 0.4 + (isSmall ? 1.0 : 0.8))
                    && !(entity.hasItemInSlot(EquipmentSlotType.HEAD) && y >= 1.6)
                    && !entity.hasItemInSlot(EquipmentSlotType.MAINHAND)
                    && !entity.hasItemInSlot(EquipmentSlotType.OFFHAND);
        }
    }
}
