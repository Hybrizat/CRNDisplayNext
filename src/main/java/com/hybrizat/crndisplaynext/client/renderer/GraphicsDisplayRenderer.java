package com.hybrizat.crndisplaynext.client.renderer;

import com.hybrizat.crndisplaynext.block.GraphicsDisplayBlock;
import com.hybrizat.crndisplaynext.block.entity.GraphicsDisplayBlockEntity;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Renders colored quads using faceVerts() direct vertex calculation.
 * Uses RenderType.debugQuads (POSITION_COLOR format, NO Normal required).
 */
public class GraphicsDisplayRenderer implements BlockEntityRenderer<GraphicsDisplayBlockEntity> {

    private static final float Z = 1f/128f; // z-offset to avoid z-fighting

    public GraphicsDisplayRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(GraphicsDisplayBlockEntity be, float p, PoseStack ps,
                       MultiBufferSource buf, int light, int overlay) {
        BlockState st = be.getBlockState();
        if (!(st.getBlock() instanceof GraphicsDisplayBlock)) return;

        int bg = be.getBgColor();
        float r = ((bg>>16)&0xFF)/255f, g = ((bg>>8)&0xFF)/255f, b = (bg&0xFF)/255f;

        Direction facing = st.getValue(GraphicsDisplayBlock.FACING);
        float[][] v = faceVerts(facing);

        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.debugQuads());

        // Background (darker)
        rect(vc, m, v, r*0.55f, g*0.55f, b*0.55f, 1f);

        // Header stripe (full color, top 22%)
        float[][] vStrip = faceVerts(facing);
        float h = 0.22f;
        // Adjust top vertices of the strip
        vStrip[2][1] = v[2][1] + (v[0][1]-v[2][1])*(1-h); // bottom of strip
        vStrip[3][1] = v[3][1] + (v[1][1]-v[3][1])*(1-h);
        rect(vc, m, vStrip, r, g, b, 1f);
    }

    private static void rect(VertexConsumer vc, Matrix4f m, float[][] verts,
                              float R, float G, float B, float A) {
        // verts: [TL, TR, BR, BL]
        for (int i = 0; i < 4; i++)
            vc.addVertex(m, verts[i][0], verts[i][1], verts[i][2]).setColor(R,G,B,A);
    }

    private static float[][] faceVerts(Direction f) {
        float e = Z;
        return switch (f) {
            // NORTH: face at z=e, right=+x
            case NORTH -> new float[][]{{0,1,e},{1,1,e},{1,0,e},{0,0,e}};
            // SOUTH: face at z=1-e, right=-x
            case SOUTH -> new float[][]{{1,1,1-e},{0,1,1-e},{0,0,1-e},{1,0,1-e}};
            // EAST: face at x=1-e, right=-z
            case EAST  -> new float[][]{{1-e,1,1},{1-e,1,0},{1-e,0,0},{1-e,0,1}};
            // WEST: face at x=e, right=+z
            case WEST  -> new float[][]{{e,1,0},{e,1,1},{e,0,1},{e,0,0}};
            default    -> new float[][]{{0,1,e},{1,1,e},{1,0,e},{0,0,e}};
        };
    }
}
