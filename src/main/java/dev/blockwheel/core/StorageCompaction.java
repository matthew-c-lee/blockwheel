package dev.blockwheel.core;

import java.math.BigInteger;
import java.util.List;

/** Log eligibility only; ordinary secondary-form normalization remains independent of this gate. */
public final class StorageCompaction {
    private StorageCompaction() {}
    public record Stack(int count, int units, int limit) {
        public Stack { if (count < 1 || units < 1 || limit < 1) throw new IllegalArgumentException("Invalid storage input"); }
    }
    public static boolean eligible(List<Stack> inputs, int canonicalUnits) {
        if (canonicalUnits < 1) throw new IllegalArgumentException("Invalid canonical value");
        if (inputs.isEmpty()) return false;
        long total = 0;
        BigInteger numerator = BigInteger.ZERO, denominator = BigInteger.ONE;
        for (var input : inputs) {
            total = Math.addExact(total, (long) input.count() * input.units());
            BigInteger limit = BigInteger.valueOf(input.limit());
            numerator = numerator.multiply(limit).add(BigInteger.valueOf(input.count()).multiply(denominator));
            denominator = denominator.multiply(limit);
            var gcd = numerator.gcd(denominator); numerator = numerator.divide(gcd); denominator = denominator.divide(gcd);
        }
        return total % canonicalUnits == 0 || numerator.compareTo(denominator) > 0;
    }
}
