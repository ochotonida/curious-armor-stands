package curiousarmorstands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.client.render.CuriosLayer;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Mod(CuriousArmorStands.MODID)
public class CuriousArmorStands {

    public static final String MODID = "curious_armor_stands";

    public static final String SLOT = "curio";

    public static final UUID ATTRIBUTE_MODIFIER_UUID = UUID.fromString("c18b7612-ccbd-4766-a0dd-166fb0e13505");

    @EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class ClientModEvents {

        @SubscribeEvent
        @SuppressWarnings("unused")
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            ResourceLocation strawStatueId = new ResourceLocation("strawstatues:straw_statue");
            if (BuiltInRegistries.ENTITY_TYPE.containsKey(strawStatueId)) {
                addLayer(BuiltInRegistries.ENTITY_TYPE.get(strawStatueId));
            }
            addLayer(EntityType.ARMOR_STAND);
        }

        private static void addLayer(EntityType<?> type) {
            EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().renderers.get(type);
            try {
                addLayers(cast(renderer));
            } catch (ClassCastException ignored) {

            }
        }

        private static <E extends LivingEntity, M extends HumanoidModel<E>> void addLayers(LivingEntityRenderer<E, M> renderer) {
            renderer.addLayer(new CuriosLayer<>(renderer));
            renderer.addLayer(new ArmorStandCuriosDisplayLayer<>(renderer));
        }

        private static <T> T cast(Object object) {
            // noinspection unchecked
            return (T) object;
        }
    }

    @EventBusSubscriber(modid = CuriousArmorStands.MODID)
    public static class Events {

        private static void createAttributeModifier(ArmorStand armorStand) {
            CuriosApi.getCuriosInventory(armorStand)
                    .flatMap(inv -> inv.getStacksHandler(SLOT))
                    .filter(stacks -> !stacks.getModifiers().containsKey(ATTRIBUTE_MODIFIER_UUID))
                    .ifPresent(stacks -> stacks.addPermanentModifier(new AttributeModifier(ATTRIBUTE_MODIFIER_UUID, "curious_armor_stands:slots", 8 - 1, AttributeModifier.Operation.ADD_VALUE)));
        }

        @SubscribeEvent
        @SuppressWarnings("unused")
        public static void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
            if (event.getTarget() instanceof ArmorStand armorStand) {
                createAttributeModifier(armorStand);
                ItemStack stack = event.getItemStack();

                if (!stack.isEmpty()) {
                    equipItem(armorStand, stack, event);
                } else if (canUnequipCurio(event.getLocalPos(), armorStand)) {
                    unequipItem(armorStand, event);
                }
            }
        }

        public static void equipItem(ArmorStand armorStand, ItemStack stack, PlayerInteractEvent.EntityInteractSpecific event) {
            if (CuriosApi.getItemStackSlots(stack, armorStand.level()).isEmpty()) {
                return;
            }

            if (armorStand.level().isClientSide()) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }

            CuriosApi.getCuriosInventory(armorStand)
                    .flatMap(inv -> inv.getStacksHandler(SLOT))
                    .map(ICurioStacksHandler::getCosmeticStacks)
                    .ifPresent(cosmetics -> {
                        Optional<ICurio> curio = CuriosApi.getCurio(stack);

                        for (int slot = 0; slot < cosmetics.getSlots(); slot++) {
                            SlotContext slotContext = new SlotContext(SLOT, armorStand, slot, true, true);
                            if (cosmetics.getStackInSlot(slot).isEmpty() && (curio.isEmpty() || curio.get().canEquip(slotContext))) {
                                cosmetics.setStackInSlot(slot, stack.copy());

                                playEquipSound(curio, slotContext);
                                enableArmorStandArms(armorStand, stack);

                                if (!event.getEntity().isCreative()) {
                                    int count = stack.getCount();
                                    stack.shrink(count);
                                }

                                event.setCancellationResult(InteractionResult.SUCCESS);
                                event.setCanceled(true);
                                return;
                            }
                        }
                    });
        }

        private static void playEquipSound(Optional<ICurio> curio, SlotContext slotContext) {
            if (curio.isPresent()) {
                ICurio.SoundInfo soundInfo = curio.get().getEquipSound(slotContext);
                slotContext.entity().level().playSound(
                        null,
                        slotContext.entity().blockPosition(),
                        soundInfo.soundEvent(),
                        slotContext.entity().getSoundSource(),
                        soundInfo.volume(),
                        soundInfo.pitch()
                );
            } else {
                slotContext.entity().level().playSound(
                        null,
                        slotContext.entity().blockPosition(),
                        SoundEvents.ARMOR_EQUIP_GENERIC.value(),
                        slotContext.entity().getSoundSource(),
                        1,
                        1
                );
            }
        }

        public static void unequipItem(ArmorStand armorStand, PlayerInteractEvent.EntityInteractSpecific event) {
            CuriosApi.getCuriosInventory(armorStand)
                    .flatMap(handler -> handler.getStacksHandler(SLOT))
                    .ifPresent(stacksHandler -> {
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
                    });
        }

        private static void enableArmorStandArms(ArmorStand entity, ItemStack stack) {
            Set<String> slots = CuriosApi.getItemStackSlots(stack, entity.level()).keySet();
            if (slots.contains("hands") || slots.contains("ring") || slots.contains("bracelet")) {
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
