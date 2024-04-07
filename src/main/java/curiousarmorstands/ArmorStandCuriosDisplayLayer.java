package curiousarmorstands;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import javax.annotation.Nonnull;

public class ArmorStandCuriosDisplayLayer<ENTITY extends LivingEntity, MODEL extends EntityModel<ENTITY>>
        extends RenderLayer<ENTITY, MODEL> {

    public ArmorStandCuriosDisplayLayer(RenderLayerParent<ENTITY, MODEL> renderer) {
        super(renderer);
    }

    @Override
    public void render(
            @Nonnull PoseStack poseStack,
            @Nonnull MultiBufferSource buffer,
            int light,
            @Nonnull ENTITY entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float headYaw,
            float headPitch
    ) {
        if (Minecraft.getInstance().hitResult instanceof EntityHitResult hitResult && hitResult.getEntity() == entity) {
            CuriosApi.getCuriosInventory(entity)
                    .map(ICuriosItemHandler::getCurios)
                    .map(curios -> curios.get(CuriousArmorStands.SLOT))
                    .map(ICurioStacksHandler::getCosmeticStacks)
                    .ifPresent(cosmetics -> {
                        int itemCount = 0;
                        for (int slot = 0; slot < cosmetics.getSlots(); slot++) {
                            if (!cosmetics.getStackInSlot(slot).isEmpty()) {
                                itemCount++;
                            }
                        }

                        poseStack.pushPose();
                        poseStack.scale(0.25F, 0.25F, 0.25F);
                        poseStack.translate((itemCount - 1) / 2F, -4, 0);
                        poseStack.mulPose(Axis.XP.rotationDegrees(180));
                        poseStack.mulPose(Axis.YP.rotationDegrees(180));

                        for (int slot = cosmetics.getSlots() - 1; slot >= 0; slot--) {
                            ItemStack item = cosmetics.getStackInSlot(slot);
                            if (!item.isEmpty()) {
                                Minecraft.getInstance().getItemRenderer().renderStatic(
                                        item,
                                        ItemDisplayContext.FIXED,
                                        light,
                                        OverlayTexture.NO_OVERLAY,
                                        poseStack,
                                        buffer,
                                        null,
                                        0
                                );
                                poseStack.translate(1, 0, 0);
                            }
                        }
                        poseStack.popPose();
                    }
            );
        }
    }
}
