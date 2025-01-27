package me.earth.earthhack.impl.modules.combat.autocrystal;

import me.earth.earthhack.impl.core.ducks.entity.IEntityRenderer;
import me.earth.earthhack.impl.event.events.render.Render3DEvent;
import me.earth.earthhack.impl.event.listeners.ModuleListener;
import me.earth.earthhack.impl.managers.Managers;
import me.earth.earthhack.impl.managers.render.TextRenderer;
import me.earth.earthhack.impl.modules.combat.autocrystal.modes.RenderDamage;
import me.earth.earthhack.impl.modules.combat.autocrystal.modes.RenderDamagePos;
import me.earth.earthhack.impl.util.math.rotation.RotationUtil;
import me.earth.earthhack.impl.util.minecraft.MotionTracker;
import me.earth.earthhack.impl.util.minecraft.entity.EntityUtil;
import me.earth.earthhack.impl.util.render.Interpolation;
import me.earth.earthhack.impl.util.render.Render2DUtil;
import me.earth.earthhack.impl.util.render.RenderUtil;
import me.earth.earthhack.impl.util.render.mutables.BBRender;
import me.earth.earthhack.impl.util.render.mutables.MutableBB;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

final class ListenerRender extends ModuleListener<AutoCrystal, Render3DEvent> {
    private final Map<BlockPos, Long> fadeList = new HashMap<>();
    private static final ResourceLocation CRYSTAL_LOCATION = new ResourceLocation("earthhack:textures/client/crystal.png");
    private final MutableBB bb = new MutableBB();

    public ListenerRender(AutoCrystal module) {
        super(module, Render3DEvent.class);
    }

    @Override
    public void invoke(Render3DEvent event) {
        RenderDamagePos mode = module.renderDamage.getValue();

        if (module.render.getValue()
                && module.box.getValue()
                && module.fade.getValue()
                && !module.isPingBypass()) {
            for (Map.Entry<BlockPos, Long> set : fadeList.entrySet()) {
                if (module.getRenderPos() == set.getKey()) {
                    continue;
                }

                final Color boxColor = module.boxColor.getValue();
                final Color outlineColor = module.outLine.getValue();
                final float maxBoxAlpha = boxColor.getAlpha();
                final float maxOutlineAlpha = outlineColor.getAlpha();
                final float alphaBoxAmount = maxBoxAlpha / module.fadeTime.getValue();
                final float alphaOutlineAmount = maxOutlineAlpha / module.fadeTime.getValue();
                final int fadeBoxAlpha = MathHelper.clamp((int) (alphaBoxAmount * (set.getValue() + module.fadeTime.getValue() - System.currentTimeMillis())), 0, (int) maxBoxAlpha);
                final int fadeOutlineAlpha = MathHelper.clamp((int) (alphaOutlineAmount * (set.getValue() + module.fadeTime.getValue() - System.currentTimeMillis())), 0, (int) maxOutlineAlpha);

                RenderUtil.renderBox(
                    Interpolation.interpolatePos(set.getKey(), 1.0f),
                    new Color(boxColor.getRed(), boxColor.getGreen(), boxColor.getBlue(), fadeBoxAlpha),
                    new Color(outlineColor.getRed(), outlineColor.getGreen(), outlineColor.getBlue(), fadeOutlineAlpha),
                    1.5f);
            }
        }

        BlockPos pos;
        if (module.render.getValue() && !module.isPingBypass() && (pos = module.getRenderPos()) != null) {
            if ((module.fadeComp.getValue() || !module.fade.getValue()) && module.box.getValue()) {
                BlockPos slide;
                if (module.slide.getValue() && (slide = module.slidePos) != null) {
                    double factor = module.slideTimer.getTime() / Math.max(1.0, module.slideTime.getValue());
                    if (factor >= 1.0) {
                        renderBoxMutable(pos);
                        if (mode != RenderDamagePos.None) {
                            renderDamage(pos);
                        }
                    } else {
                        double x = slide.getX() + (pos.getX() - slide.getX()) * factor;
                        double y = slide.getY() + (pos.getY() - slide.getY()) * factor;
                        double z = slide.getZ() + (pos.getZ() - slide.getZ()) * factor;
                        bb.setBB(
                            x,
                            y,
                            z,
                            x + 1,
                            y + 1,
                            z + 1
                        );
                        Interpolation.interpolateMutable(bb);
                        BBRender.renderBox(bb, module.boxColor.getValue(), module.outLine.getValue(), 1.5f);
                        if (mode != RenderDamagePos.None) {
                            renderDamage(x + 0.5, y, z + 0.5);
                        }
                    }
                } else {
                    if (module.zoom.getValue()) {
                        double grow = (module.zoomOffset.getValue() - Math.signum(module.zoomOffset.getValue()) * module.zoomTimer.getTime() / Math.max(1.0, module.zoomTime.getValue())) / 2.0;
                        if (module.zoomOffset.getValue() <= 0.0 && grow >= 0.0 || module.zoomOffset.getValue() > 0.0 && grow <= 0.0) {
                            renderBoxMutable(pos);
                        } else {
                            bb.setFromBlockPos(pos);
                            bb.growMutable(grow, grow, grow);
                            Interpolation.interpolateMutable(bb);
                            BBRender.renderBox(bb, module.boxColor.getValue(), module.outLine.getValue(), 1.5f);
                        }
                    } else {
                        renderBoxMutable(pos);
                    }

                    if (mode != RenderDamagePos.None) {
                        renderDamage(pos);
                    }
                }
            }

            if (module.fade.getValue()) {
                fadeList.put(pos, System.currentTimeMillis());
            }
        }

        fadeList.entrySet().removeIf(e ->
                e.getValue() + module.fadeTime.getValue()
                        < System.currentTimeMillis());

        if (module.renderExtrapolation.getValue())
        {
            for (EntityPlayer player : mc.world.playerEntities)
            {
                MotionTracker tracker;
                if (player == null
                    || EntityUtil.isDead(player)
                    || RenderUtil.getEntity().getDistanceSq(player) > 200
                    || !RenderUtil.isInFrustum(player.getEntityBoundingBox())
                    || player.equals(RotationUtil.getRotationPlayer())
                    || (tracker = module.extrapolationHelper
                                        .getTrackerFromEntity(player)) == null
                    || !tracker.active)
                {
                    continue;
                }

                Vec3d interpolation = Interpolation.interpolateEntity(player);
                double x = interpolation.x;
                double y = interpolation.y;
                double z = interpolation.z;

                double tX = tracker.posX - Interpolation.getRenderPosX();
                double tY = tracker.posY - Interpolation.getRenderPosY();
                double tZ = tracker.posZ - Interpolation.getRenderPosZ();

                RenderUtil.startRender();
                GlStateManager.enableAlpha();
                GlStateManager.enableBlend();
                GlStateManager.pushMatrix();
                GlStateManager.loadIdentity();

                if (Managers.FRIENDS.contains(player))
                {
                    GL11.glColor4f(0.33333334f, 0.78431374f, 0.78431374f, 0.55f);
                }
                else
                {
                    GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                }

                boolean viewBobbing = mc.gameSettings.viewBobbing;
                mc.gameSettings.viewBobbing = false;
                ((IEntityRenderer) mc.entityRenderer)
                    .invokeOrientCamera(event.getPartialTicks());
                mc.gameSettings.viewBobbing = viewBobbing;

                GL11.glLineWidth(1.5f);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex3d(tX, tY, tZ);
                GL11.glVertex3d(x, y, z);
                GL11.glEnd();

                GlStateManager.popMatrix();
                GlStateManager.disableAlpha();
                GlStateManager.disableBlend();
                RenderUtil.endRender();
            }
        }

        if (module.render.getValue() && module.targetRender.getValue()) {
            EntityPlayer target = module.getTarget();
            if (target != null) {
                jelloRender(target);
            }
        }
    }

    private void jelloRender(EntityLivingBase target) {
        int drawTime = (int) (System.currentTimeMillis() % 2000);
        boolean drawMode = drawTime > 1000;
        float drawPercent = drawTime / 1000f;

        if (!drawMode) {
            drawPercent = 1 - drawPercent;
        } else {
            drawPercent -= 1;
        }

        if (drawPercent < 0.5f) {
            drawPercent = 2.0f * drawPercent * drawPercent;
        } else {
            drawPercent = 1.0f - (-2.0f * drawPercent + 2.0f) / 2.0f;
        }

        mc.entityRenderer.disableLightmap();
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glEnable(GL11.GL_BLEND);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glShadeModel(7425);
        mc.entityRenderer.disableLightmap();

        double radius = target.width;
        double height = target.height + 0.1;

        double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * mc.getRenderPartialTicks() - mc.getRenderManager().viewerPosX;
        double y = (target.lastTickPosY + (target.posY - target.lastTickPosY) * mc.getRenderPartialTicks() - mc.getRenderManager().viewerPosY) + height * drawPercent;
        double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * mc.getRenderPartialTicks() - mc.getRenderManager().viewerPosZ;
        double eased = (height / 3) * ((drawPercent > 0.5) ? 1 - drawPercent : drawPercent) * ((drawMode) ? -1 : 1);

        for (int segments = 0; segments < 360; segments += 5) {
            Color color = module.jelloColor.getValue();

            double x1 = x - Math.sin(segments * Math.PI / 180F) * radius;
            double z1 = z + Math.cos(segments * Math.PI / 180F) * radius;
            double x2 = x - Math.sin((segments - 5) * Math.PI / 180F) * radius;
            double z2 = z + Math.cos((segments - 5) * Math.PI / 180F) * radius;

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4f(pulseColor(color, 200, 1).getRed() / 255.0f, pulseColor(color, 200, 1).getGreen() / 255.0f, pulseColor(color, 200, 1).getBlue() / 255.0f, 0.0f);
            GL11.glVertex3d(x1, y + eased, z1);
            GL11.glVertex3d(x2, y + eased, z2);
            GL11.glColor4f(pulseColor(color, 200, 1).getRed() / 255.0f, pulseColor(color, 200, 1).getGreen() / 255.0f, pulseColor(color, 200, 1).getBlue() / 255.0f, 200.0f);
            GL11.glVertex3d(x2, y, z2);
            GL11.glVertex3d(x1, y, z1);
            GL11.glEnd();
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex3d(x2, y, z2);
            GL11.glVertex3d(x1, y, z1);
            GL11.glEnd();
        }

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glShadeModel(7424);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }

    public Color pulseColor(Color color, int index, int count) {
        float[] hsb = new float[3];
        Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsb);
        float brightness = Math.abs((System.currentTimeMillis() % ((long) 1230675006 ^ 0x495A9BEEL) / Float.intBitsToFloat(Float.floatToIntBits(0.0013786979f) ^ 0x7ECEB56D) + index / (float) count * Float.intBitsToFloat(Float.floatToIntBits(0.09192204f) ^ 0x7DBC419F)) % Float.intBitsToFloat(Float.floatToIntBits(0.7858098f) ^ 0x7F492AD5) - Float.intBitsToFloat(Float.floatToIntBits(6.46708f) ^ 0x7F4EF252));
        brightness = Float.intBitsToFloat(Float.floatToIntBits(18.996923f) ^ 0x7E97F9B3) + Float.intBitsToFloat(Float.floatToIntBits(2.7958195f) ^ 0x7F32EEB5) * brightness;
        hsb[2] = brightness % Float.intBitsToFloat(Float.floatToIntBits(0.8992331f) ^ 0x7F663424);
        return new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
    }

    private void renderBoxMutable(BlockPos pos) {
        bb.setFromBlockPos(pos);
        Interpolation.interpolateMutable(bb);
        BBRender.renderBox(
            bb,
            module.boxColor.getValue(),
            module.outLine.getValue(),
            1.5f);
    }

    private void renderDamage(BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        renderDamage(x, y, z);
    }

    private void renderDamage(double x, double yIn, double z) {
        double y = yIn + (module.renderDamage.getValue() == RenderDamagePos.OnTop ? 1.35 : 0.5);
        String text = module.damage;
        GlStateManager.pushMatrix();
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(1.0f, -1500000.0f);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();

        float scale = 0.016666668f * (module.renderMode.getValue() == RenderDamage.Indicator ? 0.95f : 1.3f);
        GlStateManager.translate(x - Interpolation.getRenderPosX(),
                y - Interpolation.getRenderPosY(),
                z - Interpolation.getRenderPosZ());

        GlStateManager.glNormal3f(0.0f, 1.0f, 0.0f);
        GlStateManager.rotate(-mc.player.rotationYaw, 0.0f, 1.0f, 0.0f);

        GlStateManager.rotate(mc.player.rotationPitch,
                mc.gameSettings.thirdPersonView == 2
                        ? -1.0f
                        : 1.0f,
                0.0f,
                0.0f);

        GlStateManager.scale(-scale, -scale, scale);

        int distance = (int) mc.player.getDistance(x, y, z);
        float scaleD = (distance / 2.0f) / (2.0f + (2.0f - 1));
        if (scaleD < 1.0f) {
            scaleD = 1;
        }

        GlStateManager.scale(scaleD, scaleD, scaleD);
        TextRenderer m = Managers.TEXT;
        GlStateManager.translate(-(m.getStringWidth(text) / 2.0), 0, 0);
        if (module.renderMode.getValue() == RenderDamage.Indicator) {
            Color clr = module.indicatorColor.getValue();
            Render2DUtil.drawUnfilledCircle(m.getStringWidth(text) / 2.0f, 0, 22.f, new Color(5, 5, 5, clr.getAlpha()).getRGB(), 5.f);
            Render2DUtil.drawCircle(m.getStringWidth(text) / 2.0f, 0, 22.f, clr.getRGB());
            m.drawString(text, 0, 6.0f, new Color(255, 255, 255).getRGB());
            Minecraft.getMinecraft().getTextureManager().bindTexture(CRYSTAL_LOCATION);
            Gui.drawScaledCustomSizeModalRect((int) (m.getStringWidth(text) / 2.0f) - 10, -17, 0, 0, 12, 12, 22, 22, 12, 12);
        } else {
            m.drawStringWithShadow(text, 0, 0, new Color(255, 255, 255).getRGB());
        }
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.disablePolygonOffset();
        GlStateManager.doPolygonOffset(1.0f, 1500000.0f);
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

    }

}

