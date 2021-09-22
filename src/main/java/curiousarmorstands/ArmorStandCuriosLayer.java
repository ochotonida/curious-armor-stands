package curiousarmorstands;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import javax.annotation.Nonnull;

@OnlyIn(Dist.CLIENT)
public class ArmorStandCuriosLayer<ENTITY extends LivingEntity, MODEL extends EntityModel<ENTITY>>
        extends RenderLayer<ENTITY, MODEL> {

    public ArmorStandCuriosLayer(RenderLayerParent<ENTITY, MODEL> renderer) {
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
            CuriosApi.getCuriosHelper().getCuriosHandler(entity).ifPresent(
                    handler -> {
                        IDynamicStackHandler cosmetics = handler.getCurios().get(CuriousArmorStands.SLOT).getCosmeticStacks();
                        int itemCount = 0;
                        for (int slot = 0; slot < cosmetics.getSlots(); slot++) {
                            if (!cosmetics.getStackInSlot(slot).isEmpty()) {
                                itemCount++;
                            }
                        }

                        poseStack.pushPose();
                        poseStack.scale(0.25F, 0.25F, 0.25F);
                        poseStack.translate((itemCount - 1) / 2F, -4, 0);
                        poseStack.mulPose(Vector3f.XP.rotationDegrees(180));
                        poseStack.mulPose(Vector3f.YP.rotationDegrees(180));

                        for (int slot = cosmetics.getSlots() - 1; slot >= 0; slot--) {
                            ItemStack item = cosmetics.getStackInSlot(slot);
                            if (!item.isEmpty()) {
                                Minecraft.getInstance().getItemRenderer().renderStatic(
                                        item,
                                        ItemTransforms.TransformType.FIXED,
                                        light,
                                        OverlayTexture.NO_OVERLAY,
                                        poseStack,
                                        buffer,
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
