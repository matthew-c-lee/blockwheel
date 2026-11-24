package dev.blockwheel.core;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.ToIntFunction;

/** Manual conversion policy layered over exact batching, with physical base-first change. */
public record ManualConversionPlan<T>(int consumed, int produced, int remainder,
                                      List<MaterialComposition.Part<T>> stored, int borrowed, int handLimit,
                                      List<MaterialComposition.Part<T>> withdrawals) {
    public ManualConversionPlan {
        stored = List.copyOf(stored);
        withdrawals = List.copyOf(withdrawals);
        if (borrowed < 0) throw new IllegalArgumentException("Invalid borrowed amount");
        if (handLimit < 1) throw new IllegalArgumentException("Invalid hand limit");
    }
    public ManualConversionPlan(int consumed, int produced, int remainder, List<MaterialComposition.Part<T>> stored, int borrowed, int handLimit) {
        this(consumed, produced, remainder, stored, borrowed, handLimit, List.of());
    }
    public ManualConversionPlan(int consumed, int produced, int remainder, List<MaterialComposition.Part<T>> stored, int borrowed) {
        this(consumed, produced, remainder, stored, borrowed, Integer.MAX_VALUE);
    }
    public ManualConversionPlan(int consumed, int produced, int remainder, List<MaterialComposition.Part<T>> stored) {
        this(consumed, produced, remainder, stored, 0);
    }

    /** Includes stored items accepted by consolidation, whether converted or moved as an exact match. */
    public int inputCount() {
        return Math.addExact(consumed, withdrawals.stream().mapToInt(MaterialComposition.Part::count).sum());
    }

    private record Donor<T>(T form, boolean held) {}

    /** Fill the single requested stack from compatible main-storage items using exact physical batches. */
    public static <T> ManualConversionPlan<T> calculate(int count, T source, T target, T base,
            List<T> forms, ToIntFunction<T> units, int outputLimit, List<MaterialWithdrawal.Supply<T>> available) {
        return calculate(count, source, target, base, forms, units, outputLimit, available, null);
    }
    public static <T> ManualConversionPlan<T> calculate(int count, T source, T target, T base,
            List<T> forms, ToIntFunction<T> units, int outputLimit, List<MaterialWithdrawal.Supply<T>> available, T secondary) {
        int copies = available.stream().filter(supply -> supply.source().equals(source)).mapToInt(MaterialWithdrawal.Supply::count).sum();
        var primary = calculate(count, source, target, base, forms, units, outputLimit, copies, secondary);
        if (source.equals(target) || primary.produced() == outputLimit) return primary;
        List<MaterialWithdrawal.Supply<Donor<T>>> supplies = new ArrayList<>();
        if (primary.remainder() > 0) supplies.add(new MaterialWithdrawal.Supply<>(new Donor<>(source, true), primary.remainder(), units.applyAsInt(source)));
        int borrowed = primary.borrowed();
        for (var supply : available) {
            if (!forms.contains(supply.source()) || supply.units() != units.applyAsInt(supply.source())) throw new IllegalArgumentException("Foreign family supply");
            int used = supply.source().equals(source) ? Math.min(borrowed, supply.count()) : 0;
            borrowed -= used;
            if (supply.count() > used) supplies.add(new MaterialWithdrawal.Supply<>(new Donor<>(supply.source(), false), supply.count() - used, supply.units()));
        }
        // Consolidate exact targets first, then finish the held remainder before opening other batches.
        supplies.sort(Comparator.<MaterialWithdrawal.Supply<Donor<T>>>comparingInt(supply ->
                supply.source().form().equals(target) ? 0 : supply.source().held() ? 1 : 2));
        long value = supplies.stream().mapToLong(supply -> (long) supply.count() * supply.units()).sum();
        int maximum = (int) Math.min(outputLimit - primary.produced(), value / units.applyAsInt(target));
        if (maximum == 0) return primary;
        var exact = new MaterialWithdrawal<>(supplies, Math.multiplyExact(maximum, units.applyAsInt(target)));
        for (int added = maximum; added > 0; added--) {
            var batch = exact.forUnits(added * units.applyAsInt(target));
            if (batch.isEmpty()) continue;
            List<MaterialComposition.Part<T>> withdrawals = new ArrayList<>();
            int held = 0;
            for (var part : batch.get()) {
                if (part.source().held()) held += part.count();
                else withdrawals.add(new MaterialComposition.Part<>(part.source().form(), part.count()));
            }
            return new ManualConversionPlan<>(primary.consumed() + held, primary.produced() + added,
                    primary.remainder() - held, primary.stored(), primary.borrowed(), outputLimit, withdrawals);
        }
        return primary;
    }

    public static <T> ManualConversionPlan<T> calculate(int count, T source, T target, T base,
            List<T> forms, ToIntFunction<T> units, int outputLimit) {
        return calculate(count, source, target, base, forms, units, outputLimit, 0);
    }

    /** Borrow only enough ordinary copies of the source from main storage to finish its last batch. */
    public static <T> ManualConversionPlan<T> calculate(int count, T source, T target, T base,
            List<T> forms, ToIntFunction<T> units, int outputLimit, int availableSource) {
        return calculate(count, source, target, base, forms, units, outputLimit, availableSource, null);
    }
    public static <T> ManualConversionPlan<T> calculate(int count, T source, T target, T base,
            List<T> forms, ToIntFunction<T> units, int outputLimit, int availableSource, T secondary) {
        if (outputLimit < 1) throw new IllegalArgumentException("Invalid output limit");
        if (availableSource < 0) throw new IllegalArgumentException("Invalid available source amount");
        var raw = ConversionPlan.calculate(count, units.applyAsInt(source), units.applyAsInt(target));
        if (source.equals(target)) return new ManualConversionPlan<>(raw.consumed(), raw.produced(), raw.remainder(), List.of(), 0, outputLimit);
        int batch = ConversionPlan.minimumInput(units.applyAsInt(source), units.applyAsInt(target));
        int needed = (batch - count % batch) % batch;
        int borrowed = raw.produced() < outputLimit && needed <= availableSource ? needed : 0;
        int combined = Math.addExact(count, borrowed);
        raw = ConversionPlan.calculate(combined, units.applyAsInt(source), units.applyAsInt(target));
        if (raw.produced() <= outputLimit) return new ManualConversionPlan<>(raw.consumed(), raw.produced(), raw.remainder(), List.of(), borrowed, outputLimit);
        // Do not turn fractional change into extra copies of the capped target.
        var changeForms = forms.stream().filter(form -> !form.equals(target)).toList();
        T preferred = base != null && !base.equals(target) ? base : source;
        T changeSecondary = secondary != null && !secondary.equals(target) ? secondary : null;
        var composition = new MaterialComposition<>(preferred, changeSecondary, changeForms, units);
        int total = Math.multiplyExact(combined, units.applyAsInt(source));
        for (int produced = outputLimit; produced > 0; produced--) {
            var change = composition.decompose(total - Math.multiplyExact(produced, units.applyAsInt(target)));
            if (change.isPresent()) return new ManualConversionPlan<>(combined, produced, 0, change.get(), borrowed, outputLimit);
        }
        return new ManualConversionPlan<>(0, 0, count, List.of(), 0, outputLimit);
    }

    /** Every radial target makes one stack; slabs also retain their existing 64-item ceiling. */
    public static int outputLimit(String shapeId, int itemLimit) {
        return shapeId.equals("blockwheel:slab") ? Math.min(64, itemLimit) : itemLimit;
    }
}
