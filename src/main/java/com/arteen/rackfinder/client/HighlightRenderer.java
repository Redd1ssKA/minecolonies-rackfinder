package com.arteen.rackfinder.client;

import com.arteen.rackfinder.config.RackFinderConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Draws rack outlines straight into the GUI overlay, which makes them visible through walls and
 * avoids fighting with the depth buffer. Corners are projected with the matrices captured during
 * the world pass.
 *
 * <p>Search results are cyan, the nearest rack is golden.</p>
 */
public final class HighlightRenderer {
    private static final float[][] CORNERS = {
            {0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0},
            {0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}
    };
    private static final int[][] EDGES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private static final int LINE_ALPHA = 0xC8;

    private HighlightRenderer() {
    }

    /** Screen-space segments plus the bounding box used for the label. */
    private record Projected(List<float[]> segments, float minX, float minY, double distance,
                             int color, String label) {
    }

    public static void render(final GuiGraphics graphics) {
        final List<HighlightState.Box> boxes = HighlightState.target();
        if (boxes.isEmpty() || !ProjectionCache.valid()) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        final ResourceLocation dimension = minecraft.level.dimension().location();
        final Matrix4f view = ProjectionCache.view();
        final Matrix4f projection = ProjectionCache.projection();
        final int width = minecraft.getWindow().getGuiScaledWidth();
        final int height = minecraft.getWindow().getGuiScaledHeight();

        final List<Projected> projected = new ArrayList<>(boxes.size());
        for (final HighlightState.Box box : boxes) {
            final Projected result = project(view, projection, box, minecraft, dimension, width, height);
            if (result != null) {
                projected.add(result);
            }
        }
        if (projected.isEmpty()) {
            return;
        }
        projected.sort(Comparator.comparingDouble(Projected::distance));
        final Projected nearest = projected.get(0);
        projected.set(0, new Projected(nearest.segments(), nearest.minX(), nearest.minY(),
                nearest.distance(), HighlightState.NEAREST_TARGET_COLOR, nearest.label()));

        drawOutlines(graphics, projected);
        drawLabels(graphics, projected, minecraft, height);
        if (minecraft.screen == null) {
            drawHudList(graphics, projected, minecraft, width, height);
        }
    }

    private static final float NEAR_PLANE = 0.05F;

    private static Projected project(final Matrix4f view, final Matrix4f projection,
                                     final HighlightState.Box box, final Minecraft minecraft,
                                     final ResourceLocation dimension,
                                     final int width, final int height) {
        if (!box.dimension().equals(dimension)) {
            return null;
        }
        final float baseX = box.pos().getX();
        final float baseY = box.pos().getY();
        final float baseZ = box.pos().getZ();

        final float[] viewX = new float[8];
        final float[] viewY = new float[8];
        final float[] viewZ = new float[8];
        final boolean[] visible = new boolean[8];
        final Vector4f corner = new Vector4f();
        int visibleCorners = 0;
        for (int index = 0; index < 8; index++) {
            corner.set(baseX + CORNERS[index][0], baseY + CORNERS[index][1], baseZ + CORNERS[index][2], 1.0F);
            corner.mul(view);
            viewX[index] = corner.x();
            viewY[index] = corner.y();
            viewZ[index] = corner.z();
            visible[index] = viewZ[index] <= -NEAR_PLANE;
            if (visible[index]) {
                visibleCorners++;
            }
        }
        if (visibleCorners == 0) {
            return null;
        }

        final List<float[]> segments = new ArrayList<>(12);
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        for (final int[] edge : EDGES) {
            final int a = edge[0];
            final int b = edge[1];
            if (!visible[a] && !visible[b]) {
                continue;
            }
            float ax = viewX[a];
            float ay = viewY[a];
            float az = viewZ[a];
            float bx = viewX[b];
            float by = viewY[b];
            float bz = viewZ[b];
            if (!visible[a]) {
                final float t = (-NEAR_PLANE - az) / (bz - az);
                ax += (bx - ax) * t;
                ay += (by - ay) * t;
                az = -NEAR_PLANE;
            } else if (!visible[b]) {
                final float t = (-NEAR_PLANE - bz) / (az - bz);
                bx += (ax - bx) * t;
                by += (ay - by) * t;
                bz = -NEAR_PLANE;
            }
            final float[] first = screenPoint(projection, ax, ay, az, width, height);
            final float[] second = screenPoint(projection, bx, by, bz, width, height);
            if (first == null || second == null) {
                continue;
            }
            segments.add(new float[]{first[0], first[1], second[0], second[1]});
            minX = Math.min(minX, Math.min(first[0], second[0]));
            minY = Math.min(minY, Math.min(first[1], second[1]));
        }
        if (segments.isEmpty()) {
            return null;
        }
        final double distance = minecraft.player == null ? Double.MAX_VALUE
                : Math.sqrt(minecraft.player.blockPosition().distSqr(box.pos()));
        return new Projected(segments, minX, minY, distance, box.color(), box.label());
    }

    private static float[] screenPoint(final Matrix4f projection, final float x, final float y,
                                       final float z, final int width, final int height) {
        final Vector4f clip = new Vector4f(x, y, z, 1.0F).mul(projection);
        if (clip.w() <= 1.0E-4F) {
            return null;
        }
        final float screenX = (clip.x() / clip.w() * 0.5F + 0.5F) * width;
        final float screenY = (0.5F - clip.y() / clip.w() * 0.5F) * height;
        return new float[]{screenX, screenY};
    }

    private static void drawOutlines(final GuiGraphics graphics, final List<Projected> boxes) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        final Tesselator tesselator = Tesselator.getInstance();
        final BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        final Matrix4f pose = graphics.pose().last().pose();

        for (final Projected box : boxes) {
            final float red = ((box.color() >> 16) & 0xFF) / 255.0F;
            final float green = ((box.color() >> 8) & 0xFF) / 255.0F;
            final float blue = (box.color() & 0xFF) / 255.0F;
            final float alpha = LINE_ALPHA / 255.0F;
            for (final float[] segment : box.segments()) {
                vertex(pose, buffer, segment[0], segment[1], red, green, blue, alpha);
                vertex(pose, buffer, segment[2], segment[3], red, green, blue, alpha);
            }
        }
        tesselator.end();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void vertex(final Matrix4f pose, final BufferBuilder buffer,
                               final float x, final float y, final float red, final float green,
                               final float blue, final float alpha) {
        buffer.vertex(pose, x, y, 0.0F).color(red, green, blue, alpha).endVertex();
    }

    private static void drawLabels(final GuiGraphics graphics, final List<Projected> boxes,
                                   final Minecraft minecraft, final int height) {
        final int maxLabels = RackFinderConfig.MAX_LABELS.get();
        if (maxLabels <= 0) {
            return;
        }
        final int count = Math.min(maxLabels, boxes.size());
        for (int index = 0; index < count; index++) {
            final Projected box = boxes.get(index);
            final float top = box.minY();
            final float left = box.minX();
            final String text = box.label() + "  ·  " + distanceText(box.distance());
            final int textWidth = minecraft.font.width(text);
            int x = Math.round(left);
            int y = Math.round(top) - 11;
            x = Math.max(2, Math.min(x, minecraft.getWindow().getGuiScaledWidth() - textWidth - 4));
            y = Math.max(2, Math.min(y, height - 12));
            graphics.fill(x - 2, y - 1, x + textWidth + 2, y + 9, 0x90000000);
            graphics.drawString(minecraft.font, text, x, y, box.color(), false);
        }
    }

    private static void drawHudList(final GuiGraphics graphics, final List<Projected> boxes,
                                    final Minecraft minecraft, final int width, final int height) {
        if (!RackFinderConfig.SHOW_HUD_LIST.get()) {
            return;
        }
        final int max = Math.min(5, boxes.size());
        if (max <= 0) {
            return;
        }
        final int lineHeight = 10;
        int widest = 70;
        for (int index = 0; index < max; index++) {
            widest = Math.max(widest, minecraft.font.width(hudLine(boxes.get(index))));
        }
        final int panelWidth = widest + 10;
        final int panelHeight = max * lineHeight + 16;
        final int x = width - panelWidth - 6;
        final int y = height - panelHeight - 6;

        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0x8C000000);
        graphics.drawString(minecraft.font, "Rack Finder", x + 5, y + 4, 0xFF66D9FF, false);
        for (int index = 0; index < max; index++) {
            final Projected box = boxes.get(index);
            graphics.drawString(minecraft.font, hudLine(box), x + 5, y + 14 + index * lineHeight, box.color(), false);
        }
    }

    private static String hudLine(final Projected box) {
        return distanceText(box.distance()) + "  " + box.label();
    }

    private static String distanceText(final double distance) {
        if (!Double.isFinite(distance) || distance >= Double.MAX_VALUE / 2.0) {
            return "?";
        }
        if (distance >= 1000.0) {
            return String.format(Locale.ROOT, "%.1f км", distance / 1000.0);
        }
        return String.format(Locale.ROOT, "%.0f м", distance);
    }
}
