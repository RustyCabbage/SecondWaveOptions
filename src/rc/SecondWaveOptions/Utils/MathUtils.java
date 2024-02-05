package rc.SecondWaveOptions.Utils;

import java.util.Random;

public class MathUtils {
    public static float getNormalRandom(float mean, float sd) {
        return getNormalRandom(new Random(), mean, sd);
    }

    public static float getNormalRandom(Random random, float mean, float sd) {
        double r = random.nextGaussian();
        r *= sd;
        r += mean;
        return (float) r;
    }

    public static float clampValue(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    public static float roundtoNDecimals(float value, float n) {
        return (float) (Math.round(value * Math.pow(10, n)) / Math.pow(10, n));
    }

    public static float roundToTwoDecimals(float value) {
        return roundtoNDecimals(value, 2);
    }
}