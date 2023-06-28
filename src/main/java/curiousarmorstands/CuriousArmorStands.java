package curiousarmorstands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.client.render.CuriosLayer;

import java.util.Optional;
import java.util.Set;

@Mod(CuriousArmorStands.MODID)
public class CuriousArmorStands {

    public static final String MODID = "curious_armor_stands";

    public static final String SLOT = "curio";

    @Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().renderers.get(EntityType.ARMOR_STAND);
            if (renderer instanceof ArmorStandRenderer armorStandRenderer) {
                armorStandRenderer.addLayer(new CuriosLayer<>(armorStandRenderer));
                armorStandRenderer.addLayer(new ArmorStandCuriosDisplayLayer<>(armorStandRenderer));
            }
        }
    }

    @Mod.EventBusSubscriber(modid = CuriousArmorStands.MODID)
    public static class Events {

        @SubscribeEvent
        public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof ArmorStand armorStand) {
                CuriosApi.getSlotHelper().setSlotsForType(SLOT, armorStand, 8);
            }
        }

        @SubscribeEvent
        public static void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
            if (event.getTarget() instanceof ArmorStand armorStand) {
                ItemStack stack = event.getItemStack();

                if (!stack.isEmpty()) {
                    equipItem(armorStand, stack, event);
                } else if (canUnequipCurio(event.getLocalPos(), armorStand)) {
                    unequipItem(armorStand, event);
                }
            }
        }

        public static void equipItem(ArmorStand armorStand, ItemStack stack, PlayerInteractEvent.EntityInteractSpecific event) {
            if (CuriosApi.getCuriosHelper().getCurioTags(stack.getItem()).isEmpty()) {
                return;
            }

            if (armorStand.level().isClientSide()) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }

            CuriosApi.getCuriosHelper().getCuriosHandler(armorStand).ifPresent(
                    handler -> handler.getStacksHandler(SLOT).ifPresent(
                            stacksHandler -> {
                                IDynamicStackHandler cosmetics = stacksHandler.getCosmeticStacks();
                                Optional<ICurio> curio = CuriosApi.getCuriosHelper().getCurio(stack).resolve();

                                for (int slot = 0; slot < cosmetics.getSlots(); slot++) {
                                    SlotContext slotContext = new SlotContext(SLOT, armorStand, slot, true, true);
                                    if (cosmetics.getStackInSlot(slot).isEmpty() && (curio.isEmpty() || curio.get().canEquip(slotContext))) {
                                        cosmetics.setStackInSlot(slot, stack.copy());

                                        if (curio.isPresent()) {
                                            // noinspection deprecation
                                            curio.get().playRightClickEquipSound(armorStand);
                                        } else {
                                            armorStand.level().playSound(
                                                    null,
                                                    armorStand.blockPosition(),
                                                    SoundEvents.ARMOR_EQUIP_GENERIC,
                                                    armorStand.getSoundSource(),
                                                    1,
                                                    1
                                            );
                                        }

                                        enableArmorStandArms(armorStand, stack.getItem());

                                        if (!event.getEntity().isCreative()) {
                                            int count = stack.getCount();
                                            stack.shrink(count);
                                        }

                                        event.setCancellationResult(InteractionResult.SUCCESS);
                                        event.setCanceled(true);
                                        return;
                                    }
                                }
                            }
                    )
            );
        }

        public static void unequipItem(ArmorStand armorStand, PlayerInteractEvent.EntityInteractSpecific event) {
            CuriosApi.getCuriosHelper().getCuriosHandler(armorStand).ifPresent(handler -> handler.getStacksHandler(SLOT).ifPresent(stacksHandler -> {
                IDynamicStackHandler cosmetics = stacksHandler.getCosmeticStacks();
                for (int slot = cosmetics.getSlots() - 1; slot >= 0; slot--) {
                    ItemStack stackInSlot = cosmetics.getStackInSlot(slot);
                    if (!stackInSlot.isEmpty()) {
                        if (!armorStand.level().isClientSide()) {
                            event.getEntity().setItemInHand(event.getHand(), stackInSlot);
                            cosmetics.setStackInSlot(slot, ItemStack.EMPTY);
                        }
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        event.setCanceled(true);
                        return;
                    }
                }
            }));
        }

        private static void enableArmorStandArms(ArmorStand entity, Item item) {
            Set<String> tags = CuriosApi.getCuriosHelper().getCurioTags(item);
            if (tags.contains("hands") || tags.contains("ring") || tags.contains("bracelet")) {
                entity.setShowArms(true);
            }
        }

        private static boolean canUnequipCurio(Vec3 localPos, ArmorStand entity) {
            boolean isSmall = entity.isSmall();
            double y = isSmall ? localPos.y * 2 : localPos.y;
            return !(entity.hasItemInSlot(EquipmentSlot.FEET) && y >= 0.1 && y < 0.1 + (isSmall ? 0.8 : 0.45))
                    && !(entity.hasItemInSlot(EquipmentSlot.CHEST) && y >= 0.9 + (isSmall ? 0.3 : 0) && y < 0.9 + (isSmall ? 1 : 0.7))
                    && !(entity.hasItemInSlot(EquipmentSlot.LEGS) && y >= 0.4 && y < 0.4 + (isSmall ? 1.0 : 0.8))
                    && !(entity.hasItemInSlot(EquipmentSlot.HEAD) && y >= 1.6)
                    && !entity.hasItemInSlot(EquipmentSlot.MAINHAND)
                    && !entity.hasItemInSlot(EquipmentSlot.OFFHAND);
        }
    }
}
