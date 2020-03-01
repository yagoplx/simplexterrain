package supercoder79.simplexterrain.mixin;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biomes;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.CavesChunkGenerator;
import net.minecraft.world.gen.chunk.CavesChunkGeneratorConfig;
import net.minecraft.world.gen.chunk.ChunkGeneratorConfig;
import net.minecraft.world.gen.chunk.SurfaceChunkGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import supercoder79.simplexterrain.noise.gradient.SimplexStyleNoise;

import java.util.HashMap;
import java.util.Map;

@Mixin(CavesChunkGenerator.class)
public class MixinCavesChunkGenerator extends SurfaceChunkGenerator {

    @Shadow @Final private double[] noiseFalloff;

    private static Map<Biome, Double> biomeToExpansivenessMap = new HashMap<>();

    static {
        biomeToExpansivenessMap.put(Biomes.CRIMSON_FOREST, 1.2);
        biomeToExpansivenessMap.put(Biomes.WARPED_FOREST, 0.8);
        biomeToExpansivenessMap.put(Biomes.SOUL_SAND_VALLEY, 0.1);
    }

    private SimplexStyleNoise noise;
    private SimplexStyleNoise noise2;
    private SimplexStyleNoise lowerResolution;
    private SimplexStyleNoise higherResolution;
    private SimplexStyleNoise vertical;
    private SimplexStyleNoise threshold;

    public MixinCavesChunkGenerator(IWorld world, BiomeSource biomeSource, int verticalNoiseResolution, int horizontalNoiseResolution, int worldHeight, ChunkGeneratorConfig config, boolean useSimplexNoise) {
        super(world, biomeSource, verticalNoiseResolution, horizontalNoiseResolution, worldHeight, config, useSimplexNoise);
    }


    @Inject(method = "<init>", at = @At("RETURN"))
    private void constructor(World world, BiomeSource biomeSource, CavesChunkGeneratorConfig config, CallbackInfo ci) {
        noise = new SimplexStyleNoise(world.getSeed());
        noise2 = new SimplexStyleNoise(world.getSeed() + 20);
        lowerResolution = new SimplexStyleNoise(world.getSeed() + 21);
        higherResolution = new SimplexStyleNoise(world.getSeed() - 21);
        vertical = new SimplexStyleNoise(world.getSeed() + 22);
        threshold = new SimplexStyleNoise(world.getSeed() - 20);
    }

    @Override
    public void populateNoise(IWorld world, Chunk chunk) {
        BlockPos.Mutable posMutable = new BlockPos.Mutable();

        for (int x = 0; x < 16; x++) {
            posMutable.setX(x);

            for (int z = 0; z < 16; z++) {
                posMutable.setZ(z);

                double expansiveness = 0;

                for (int x1 = -1; x1 <= 1; x1++) {
                    for (int z1 = -1; z1 <= 1; z1++) {
                        expansiveness += biomeToExpansivenessMap.getOrDefault(
                            this.biomeSource.getBiomeForNoiseGen((chunk.getPos().x*16) + (x + x1), 32, (chunk.getPos().z*16) + (z + z1)), 1.0);
                    }
                }

                expansiveness /= 9;

                for (int y = 0; y < 127; y++) {
                    posMutable.setY(y);
                    if (getNoiseAt(expansiveness, (chunk.getPos().x*16) + x, y, (chunk.getPos().z*16) + z) > getThreshold((chunk.getPos().x*16) + x, y, (chunk.getPos().z*16) + z)) {
                        chunk.setBlockState(posMutable, Blocks.NETHERRACK.getDefaultState(), false);
                    } else if (y < this.getSeaLevel()) {
                        chunk.setBlockState(posMutable, Blocks.LAVA.getDefaultState(), false);
                    }
                }
            }
        }
    }

    private double getNoiseAt(double expansiveness, int x, int y, int z) {

        double baseline = noise.sample(x / 70f, y / 35f, z / 70f);
        double addition = noise2.sample(x / 70f, y / 35f, z / 70f);
        double addition2 = lowerResolution.sample(x / 35f, y / 20f, z / 35f);
        double addition3 = higherResolution.sample(x / 150f, y / 75f, z / 150f);
        double verticalNoise = vertical.sample(x / 70f, y / 7.5f, z / 70f);
        baseline += (15 / (float)y); //lower bound
        baseline += (-15 / ((float)(y - 130))); //upper bound
        return (baseline*0.6) + (addition*0.3*expansiveness) + (addition2*0.25*expansiveness) + (addition3*0.175*expansiveness) + (verticalNoise*0.1*expansiveness);
    }

    private double getThreshold(int x, int y, int z) {
        return 0.25 + (threshold.sample(x / 28f, y / 12f, z / 28f) * 0.125);
    }
    @Override
    public double[] computeNoiseRange(int x, int z) {
        return new double[]{0.0D, 0.0D};
    }

    @Override
    public double computeNoiseFalloff(double depth, double scale, int y) {
        return this.noiseFalloff[y];
    }

    @Override
    public void sampleNoiseColumn(double[] buffer, int x, int z) {

    }

    @Override
    public int getSpawnHeight() {
        return 0;
    }

    @Override
    public int getSeaLevel() {
        return 32;
    }
}