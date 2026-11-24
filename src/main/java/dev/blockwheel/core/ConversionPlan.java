package dev.blockwheel.core;

/** Exact integer batches preserve value in both directions, including odd slab counts. */
public record ConversionPlan(int consumed, int produced, int remainder) {
    public static ConversionPlan calculate(int count, int sourceUnits, int targetUnits) {
        if (count < 0 || sourceUnits < 1 || targetUnits < 1) throw new IllegalArgumentException("Invalid conversion quantities");
        int divisor = gcd(sourceUnits, targetUnits);
        int inputPerBatch = targetUnits / divisor;
        int outputPerBatch = sourceUnits / divisor;
        int batches = count / inputPerBatch;
        int consumed = Math.multiplyExact(batches, inputPerBatch);
        int produced = Math.multiplyExact(batches, outputPerBatch);
        return new ConversionPlan(consumed, produced, count - consumed);
    }

    public static int minimumInput(int sourceUnits, int targetUnits) {
        if (sourceUnits < 1 || targetUnits < 1) throw new IllegalArgumentException("Invalid material values");
        return targetUnits / gcd(sourceUnits, targetUnits);
    }

    private static int gcd(int a, int b) {
        while (b != 0) { int next = a % b; a = b; b = next; }
        return a;
    }
}
