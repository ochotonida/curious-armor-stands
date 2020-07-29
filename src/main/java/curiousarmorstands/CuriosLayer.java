package curiousarmorstands;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.IEntityRenderer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import javax.annotation.Nonnull;

public class CuriosLayer<T extends LivingEntity, M extends EntityModel<T>> extends LayerRenderer<T, M> {

    public CuriosLayer(IEntityRenderer<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(@Nonnull MatrixStack matrixStack, @Nonnull IRenderTypeBuffer renderTypeBuffer, int light, @Nonnull T livingEntity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        matrixStack.push();
        CuriosApi.getCuriosHelper().getCuriosHandler(livingEntity).ifPresent(handler -> handler.getCurios().forEach((id, stacksHandler) -> {
            IDynamicStackHandler stackHandler = stacksHandler.getStacks();
            IDynamicStackHandler cosmeticStacksHandler = stacksHandler.getCosmeticStacks();

            for (int i = 0; i < stackHandler.getSlots(); i++) {
                ItemStack stack = cosmeticStacksHandler.getStackInSlot(i);

                if (stack.isEmpty() && stacksHandler.getRenders().get(i)) {
                    stack = stackHandler.getStackInSlot(i);
                }

                if (!stack.isEmpty()) {
                    int index = i;

                    CuriosApi.getCuriosHelper().getCurio(stack).ifPresent(curio -> {
                        if (curio.canRender(id, index, livingEntity)) {
                            matrixStack.push();
                            curio.render(id, index, matrixStack, renderTypeBuffer, light, livingEntity, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
                            matrixStack.pop();
                        }
                    });
                }
            }
        }));
        matrixStack.pop();
    }
}
