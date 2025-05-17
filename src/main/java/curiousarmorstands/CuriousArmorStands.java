package curiousarmorstands;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
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
import top.theillusivec4.curios.api.CuriosSlotTypes;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.client.CuriosLayer;

import java.util.Optional;
import java.util.Set;

// TODO switch to ModDevGradle
@Mod(CuriousArmorStands.MOD_ID)
public class CuriousArmorStands {

    public static final String MOD_ID = "curiousarmorstands";

    // use the default `curio` slot to avoid issues
    public static final String SLOT = "curio";
    public static final Set<String> HAND_SLOTS = Set.of("hands", "ring", "bracelet");

    public static final ResourceLocation ATTRIBUTE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "slots");

    @EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class ClientModEvents {

        @SubscribeEvent
        @SuppressWarnings("unused")
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            ResourceLocation strawStatueId = ResourceLocation.fromNamespaceAndPath("strawstatues", "straw_statue");
            BuiltInRegistries.ENTITY_TYPE.get(strawStatueId).ifPresent(entity -> addLayers(event, entity.value()));
            addLayers(event, EntityType.ARMOR_STAND);
        }

        private static void addLayers(EntityRenderersEvent.AddLayers event, EntityType<?> type) {
            EntityRenderer<?, ?> renderer = event.getRenderer(type);
            try {
                if (renderer != null) {
                    addLayers(cast(renderer), event.getContext());
                }
            } catch (ClassCastException ignored) {

            }
        }

        private static <T extends LivingEntity, S extends HumanoidRenderState, M extends HumanoidModel<S>> void addLayers(LivingEntityRenderer<T, S, M> renderer, EntityRendererProvider.Context context) {
            renderer.addLayer(new CuriosLayer<>(renderer, context));
            renderer.addLayer(new ArmorStandCuriosDisplayLayer<>(renderer));
        }

        @SuppressWarnings("unchecked")
        private static <T> T cast(Object object) {
            return (T) object;
        }
    }

    @EventBusSubscriber(modid = CuriousArmorStands.MOD_ID)
    public static class Events {

        // avoid changing the slot count of `curio`, since this would affect players as well
        private static void createAttributeModifier(ArmorStand armorStand) {
            CuriosApi.getCuriosInventory(armorStand)
                    .flatMap(inv -> inv.getStacksHandler(SLOT))
                    .filter(stacks -> !stacks.getModifiers().containsKey(ATTRIBUTE_MODIFIER_ID))
                    .ifPresent(stacks -> stacks.addPermanentModifier(new AttributeModifier(ATTRIBUTE_MODIFIER_ID, 8 - 1, AttributeModifier.Operation.ADD_VALUE)));
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
            if (CuriosSlotTypes.getItemSlotTypes(stack, armorStand.level().isClientSide()).isEmpty()) {
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
            Set<String> slots = CuriosSlotTypes.getItemSlotTypes(stack, entity.level().isClientSide()).keySet();
            if (slots.stream().anyMatch(HAND_SLOTS::contains)) {
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
