package com.molox.createimp.client;

import com.molox.createimp.item.TemplateFluidTokenItemRenderer;
import com.molox.createimp.registry.ModItems;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * 纯客户端类：给流体模板令牌物品注册自定义渲染器（画真实流体材质图标，
 * 见 {@link TemplateFluidTokenItemRenderer}）。用的是 NeoForge 推荐的
 * {@code RegisterClientExtensionsEvent}（{@code Item.initializeClient} 那个
 * 老写法已经被标记为 deprecated、计划移除），跟项目里其他仅客户端专属注册
 * 保持同一种安全模式。
 * <p>
 * 【客户端安全】本类只应该在 {@code Dist.CLIENT} 上被引用/加载，调用方
 * （{@link com.molox.createimp.CreateImp}）负责用 {@code FMLLoader.getDist()}
 * 判断之后再决定要不要创建指向本类方法的方法引用，本类自身不做重复判断。
 */
public final class TemplateFluidTokenClientRenderer {

    private TemplateFluidTokenClientRenderer() {
    }

    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(
                SimpleCustomRenderer.create(ModItems.TEMPLATE_FLUID_TOKEN.get(), new TemplateFluidTokenItemRenderer()),
                ModItems.TEMPLATE_FLUID_TOKEN.get()
        );
    }
}