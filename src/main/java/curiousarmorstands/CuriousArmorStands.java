package curiousarmorstands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResultType;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod("curious_armor_stands")
@SuppressWarnings("unused")
public class CuriousArmorStands {

    public static final String MODID = "curious_armor_stands";

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            EntityRenderer<?> renderer = Minecraft.getInstance().getRenderManager().renderers.get(EntityType.ARMOR_STAND);
            if (renderer instanceof ArmorStandRenderer) {
                ((ArmorStandRenderer) renderer).addLayer(new CuriosLayer<>((ArmorStandRenderer) renderer));
            }
        }
    }

    @Mod.EventBusSubscriber(modid = CuriousArmorStands.MODID)
    public static class Events {

        @SubscribeEvent
        public static void onEntityTick(LivingEvent.LivingUpdateEvent event) {
            if (event.getEntityLiving() instanceof ArmorStandEntity) {
                CuriosApi.getCuriosHelper().getCuriosHandler((ArmorStandEntity) event.getEntity()).ifPresent(handler -> {
                    if (handler instanceof CurioInventoryCapability.CurioInventoryWrapper) {
                        ((CurioInventoryCapability.CurioInventoryWrapper) handler).dropInvalidStacks();
                    }
                });
            }
        }

        @SubscribeEvent
        public static void attachEntitiesCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof ArmorStandEntity) {
                event.addCapability(CuriosCapability.ID_INVENTORY, CurioInventoryCapability.createProvider((ArmorStandEntity) event.getObject()));
            }
        }

        @SubscribeEvent
        public static void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
            if (!(event.getTarget() instanceof ArmorStandEntity)) {
                return;
            }
            ArmorStandEntity entity = (ArmorStandEntity) event.getTarget();
            ItemStack stack = event.getItemStack();

            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                CuriosApi.getCuriosHelper().getCurio(stack).ifPresent(curio -> CuriosApi.getCuriosHelper().getCuriosHandler(entity).ifPresent(handler -> {
                    if (!entity.world.isRemote) {
                        Map<String, ICurioStacksHandler> curios = handler.getCurios();
                        for (Map.Entry<String, ICurioStacksHandler> entry : curios.entrySet()) {
                            IDynamicStackHandler stackHandler = entry.getValue().getCosmeticStacks();
                            for (int i = 0; i < stackHandler.getSlots(); i++) {
                                ItemStack present = stackHandler.getStackInSlot(i);
                                Set<String> tags = CuriosApi.getCuriosHelper().getCurioTags(stack.getItem());
                                String id = entry.getKey();
                                if (present.isEmpty() && (tags.contains(id) || tags.contains("curio")) && curio.canEquip(id, entity) && curio.canRender(id, i, entity)) {
                                    stackHandler.setStackInSlot(i, stack.copy());
                                    curio.playRightClickEquipSound(entity);
                                    enableArmorStandArms(entity, item);
                                    if (!event.getPlayer().isCreative()) {
                                        int count = stack.getCount();
                                        stack.shrink(count);
                                    }
                                    event.setCancellationResult(ActionResultType.SUCCESS);
                                    event.setCanceled(true);
                                    return;
                                }
                            }
                        }
                    } else {
                        event.setCancellationResult(ActionResultType.CONSUME);
                        event.setCanceled(true);
                    }
                }));
            } else {
                CuriosApi.getCuriosHelper().getCuriosHandler(entity).ifPresent(handler -> {
                    Map<String, ICurioStacksHandler> curios = handler.getCurios();
                    for (Map.Entry<String, ICurioStacksHandler> entry : curios.entrySet()) {
                        IDynamicStackHandler stackHandler = entry.getValue().getCosmeticStacks();
                        for (int i = 0; i < stackHandler.getSlots(); i++) {
                            ItemStack present = stackHandler.getStackInSlot(i);
                            Set<String> tags = CuriosApi.getCuriosHelper().getCurioTags(stack.getItem());
                            String id = entry.getKey();
                            if (!present.isEmpty()) {
                                if (!entity.world.isRemote()) {
                                    event.getPlayer().setHeldItem(event.getHand(), present);
                                    stackHandler.setStackInSlot(i, ItemStack.EMPTY);
                                }
                                event.setCancellationResult(ActionResultType.SUCCESS);
                                event.setCanceled(true);
                                return;
                            }
                        }
                    }
                });
            }
        }

        private static void enableArmorStandArms(ArmorStandEntity entity, Item curioItem) {
            if (CuriosApi.getCuriosHelper().getCurioTags(curioItem).contains("hands") || CuriosApi.getCuriosHelper().getCurioTags(curioItem).contains("ring") || CuriosApi.getCuriosHelper().getCurioTags(curioItem).contains("bracelet")) {
                CompoundNBT compoundNBT = entity.writeWithoutTypeId(new CompoundNBT());
                UUID uuid = entity.getUniqueID();
                compoundNBT.putBoolean("ShowArms", true);
                entity.setUniqueId(uuid);
                entity.read(compoundNBT);
            }
        }
    }
}
