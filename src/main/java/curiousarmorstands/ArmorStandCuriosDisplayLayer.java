package curiousarmorstands;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.EntityHitResult;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.client.CuriosClientMod;

import java.util.List;

public class ArmorStandCuriosDisplayLayer<S extends EntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {

    public ArmorStandCuriosDisplayLayer(RenderLayerParent<S, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            S renderState,
            float partialTicks,
            float ageInTicks
    ) {
        List<SlotResult> slots = renderState.getRenderData(CuriosClientMod.CUSTOM_RENDER);
        if (slots != null && !slots.isEmpty()
                && Minecraft.getInstance().hitResult instanceof EntityHitResult hitResult
                && slots.getFirst().slotContext().entity() == hitResult.getEntity()
        ) {
            poseStack.pushPose();

            poseStack.scale(0.25F, 0.25F, 0.25F);
            poseStack.translate((slots.size() - 1) / 2F, -4, 0);
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
            poseStack.mulPose(Axis.YP.rotationDegrees(180));

            for (SlotResult slot : slots) {
                Minecraft.getInstance().getItemRenderer().renderStatic(
                        slot.stack(), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                        poseStack, buffer, null, 0
                );
                poseStack.translate(1, 0, 0);
            }
            poseStack.popPose();
        }
    }
}
