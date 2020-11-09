package curiousarmorstands;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.IEntityRenderer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class CuriosLayer<M extends EntityModel<ArmorStandEntity>> extends LayerRenderer<ArmorStandEntity, M> {

    public CuriosLayer(IEntityRenderer<ArmorStandEntity, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(@Nonnull MatrixStack matrixStack, @Nonnull IRenderTypeBuffer buffer, int light, @Nonnull ArmorStandEntity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        matrixStack.push();

        List<ItemStack> stacks = new ArrayList<>();

        CuriosApi.getCuriosHelper().getCuriosHandler(entity).ifPresent(handler -> handler.getCurios().forEach((id, stacksHandler) -> {
            IDynamicStackHandler stackHandler = stacksHandler.getStacks();
            IDynamicStackHandler cosmeticStacksHandler = stacksHandler.getCosmeticStacks();

            for (int i = 0; i < stackHandler.getSlots(); i++) {
                ItemStack stack = cosmeticStacksHandler.getStackInSlot(i);

                if (stack.isEmpty() && stacksHandler.getRenders().get(i)) {
                    stack = stackHandler.getStackInSlot(i);
                }

                if (!stack.isEmpty()) {
                    stacks.add(stack);
                    final int index = i;

                    CuriosApi.getCuriosHelper().getCurio(stack).ifPresent(curio -> {
                        if (curio.canRender(id, index, entity)) {
                            matrixStack.push();
                            curio.render(id, index, matrixStack, buffer, light, entity, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
                            matrixStack.pop();
                        }
                    });
                }
            }
        }));

        if (Minecraft.getInstance().objectMouseOver instanceof EntityRayTraceResult && ((EntityRayTraceResult) Minecraft.getInstance().objectMouseOver).getEntity() == entity) {
            matrixStack.scale(0.25F, 0.25F, 0.25F);
            matrixStack.translate((stacks.size() - 1) / 2F, -4, 0);
            matrixStack.rotate(Vector3f.XP.rotationDegrees(180));
            matrixStack.rotate(Vector3f.YP.rotationDegrees(180));

            for (int index = stacks.size() - 1; index >= 0; index--) {
                Minecraft.getInstance().getItemRenderer().renderItem(stacks.get(index), ItemCameraTransforms.TransformType.FIXED, light, OverlayTexture.NO_OVERLAY, matrixStack, buffer);
                matrixStack.translate(1, 0, 0);
            }
        }
        matrixStack.pop();
    }
}
