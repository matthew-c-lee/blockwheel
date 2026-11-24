package dev.blockwheel.server;

import dev.blockwheel.core.ConversionPlan;
import dev.blockwheel.core.MaterialWithdrawal;
import dev.blockwheel.core.StorageCompaction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/** Shared server-side material planning. Automatic operations never drop output. */
public final class BuilderInventory {
    public record Plan(InventoryTransaction transaction, int count) {}
    private record Borrow(int count, int units, int[] slots) {}
    private record Routed(InventoryTransaction transaction, int units) {}
    private final BlockMaterials materials;

    public BuilderInventory(BlockMaterials materials) { this.materials = materials; }

    public Plan pickup(Inventory inventory, ItemStack incoming) {
        var source = materials.resolve(incoming);
        if (source == null) return null;
        var before = new InventoryTransaction(inventory);
        List<Integer> hotbar = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            var material = materials.resolve(before.get(slot));
            if (material != null && material.family() == source.family()) hotbar.add(slot);
        }
        hotbar.sort(Comparator.<Integer>comparingInt(slot -> slot == inventory.getSelectedSlot() ? 0
                : before.get(slot).is(incoming.getItem()) ? 1 : 2).thenComparingInt(Integer::intValue));
        long capacity = 0;
        for (int slot : hotbar) {
            var held = before.get(slot);
            capacity += (long) Math.max(0, before.limit(held) - held.getCount()) * materials.resolve(held).form().units();
        }
        long emptyCapacity = source.family().forms().stream().mapToLong(form ->
                (long) before.limit(new ItemStack(form.item())) * form.units()).max().orElse(0);
        long baseCapacity = (long) before.limit(new ItemStack(source.family().base().item())) * source.family().base().units();
        for (int slot = 9; slot < 36; slot++) {
            var held = before.get(slot);
            if (held.isEmpty()) { capacity += emptyCapacity; continue; }
            var member = materials.resolve(held);
            if (member == null || member.family() != source.family()) continue;
            // Include an upper bound for space recoverable by combining physical remainders.
            long merge = (long) Math.max(0, before.limit(held) - held.getCount()) * member.form().units();
            long compact = Math.max(0, baseCapacity - (long) held.getCount() * member.form().units());
            capacity += Math.max(merge, compact);
        }
        int maximum = (int) Math.min(incoming.getCount(), capacity / source.form().units());
        for (int accepted = maximum; accepted > 0; accepted--) {
          for (boolean combineTargets : new boolean[]{true, false}) {
            var trial = before.copy();
            int units = Math.multiplyExact(accepted, source.form().units());
            int external = units;
            for (int slot : hotbar) {
                var stack = trial.get(slot);
                var target = materials.resolve(stack);
                int count = Math.min(Math.max(0, trial.limit(stack) - stack.getCount()), units / target.form().units());
                stack.grow(count);
                units -= count * target.form().units();
                if (combineTargets && units > 0 && stack.getCount() < trial.limit(stack)) {
                    var combined = borrowFraction(trial, source.family(), units, target.form().units());
                    if (combined != null) {
                        trial = combined.transaction(); units = combined.units(); stack = trial.get(slot);
                        count = Math.min(trial.limit(stack) - stack.getCount(), units / target.form().units());
                        stack.grow(count); units -= count * target.form().units();
                    }
                }
            }
            var routed = source.family().logBased() ? storeWoodPickup(trial, source, units) : depositWithBorrow(trial, source.family(), units);
            if (routed != null) {
                checkBalance(routed, source.family(), external);
                return new Plan(routed, accepted);
            }
          }
        }
        return new Plan(before, 0);
    }

    /** Normalize only requested families; the caller supplies a completed transaction boundary. */
    public Plan normalize(Inventory inventory, Set<BlockMaterials.Family> affected) {
        return normalize(inventory, affected, Map.of());
    }

    public Plan normalize(Inventory inventory, Set<BlockMaterials.Family> affected,
                          Map<BlockMaterials.Family, List<Integer>> destinations) {
        var trial = new InventoryTransaction(inventory);
        for (var family : affected) {
            var preferred = destinations.getOrDefault(family, List.of());
            if (family.logBased()) {
                var compact = compactWood(trial, family, preferred, null, 0);
                if (compact != null) { trial = compact; checkBalance(trial, family, 0); }
                continue;
            }
            var all = trial.copy();
            int units = 0;
            for (int slot = 9; slot < 36; slot++) {
                var member = materials.resolve(all.get(slot));
                if (member == null || member.family() != family || member.form().base()) continue;
                units = Math.addExact(units, Math.multiplyExact(all.get(slot).getCount(), member.form().units()));
                all.set(slot, ItemStack.EMPTY);
            }
            if (units == 0) continue;
            // Preserve each destination's whole-block share, including multi-slot drag placement.
            for (int slot : preferred) {
                var original = trial.get(slot);
                var member = materials.resolve(original);
                if (member == null || member.family() != family || member.form().base() || !all.get(slot).isEmpty()) continue;
                int count = Math.min(all.limit(new ItemStack(family.base().item())),
                        Math.multiplyExact(original.getCount(), member.form().units()) / family.base().units());
                if (count > 0) {
                    all.set(slot, new ItemStack(family.base().item(), count)); units -= count * family.base().units();
                }
            }
            if (deposit(all, family, units, preferred)) {
                trial = all;
            } else {
                // With no spare space, apply only complete batches whose output fits.
                for (int slot = 9; slot < 36; slot++) {
                    var member = materials.resolve(trial.get(slot));
                    if (member == null || member.family() != family || member.form().base()) continue;
                    int batch = ConversionPlan.minimumInput(member.form().units(), family.base().units());
                    for (int count = trial.get(slot).getCount() / batch * batch; count > 0; count -= batch) {
                        var part = trial.copy();
                        part.remove(slot, count);
                        int output = Math.multiplyExact(count, member.form().units()) / family.base().units();
                        if (part.insertPreferred(new ItemStack(family.base().item(), output), preferred, 9, 36).isEmpty()) {
                            trial = part;
                            break;
                        }
                    }
                }
            }
            checkBalance(trial, family, 0);
        }
        return new Plan(trial, trial.changed() ? 1 : 0);
    }

    /** Pool eligible main storage, never other hotbar stacks. Every proposed result is capacity/slot checked. */
    private InventoryTransaction compactWood(InventoryTransaction before, BlockMaterials.Family family, List<Integer> preferred,
                                             BlockMaterials.Form extra, int extraCount) {
        var all = before.copy(); int units = 0;
        var inputs = new ArrayList<StorageCompaction.Stack>();
        var originals = new java.util.LinkedHashMap<BlockMaterials.Form, Integer>();
        var plankSlots = new ArrayList<Integer>(); var sourceSlots = new ArrayList<Integer>();
        for (int slot = 9; slot < 36; slot++) {
            var stack = all.get(slot); var material = materials.resolve(stack);
            if (material == null || material.family() != family || material.form().base()) continue;
            sourceSlots.add(slot);
            if (material.form().equals(family.plankBase())) plankSlots.add(slot);
            inputs.add(new StorageCompaction.Stack(stack.getCount(), material.form().units(), all.limit(stack)));
            originals.merge(material.form(), stack.getCount(), Math::addExact);
            units = Math.addExact(units, Math.multiplyExact(stack.getCount(), material.form().units())); all.set(slot, ItemStack.EMPTY);
        }
        if (extra != null && extraCount > 0) {
            units = Math.addExact(units, Math.multiplyExact(extraCount, extra.units()));
            originals.merge(extra, extraCount, Math::addExact);
            if (!extra.base()) inputs.add(new StorageCompaction.Stack(extraCount, extra.units(), all.limit(new ItemStack(extra.item()))));
        }
        if (inputs.isEmpty()) return null;
        boolean logsEligible = StorageCompaction.eligible(inputs, family.base().units());
        var plank = family.plankBase();
        if (!logsEligible && plank == null) return null;
        int budget = occupiedMain(before);
        if (extra != null) budget = Math.min(27, budget + (extraCount + all.limit(new ItemStack(extra.item())) - 1) / all.limit(new ItemStack(extra.item())));
        InventoryTransaction best = null; int bestSlots = Integer.MAX_VALUE;
        // Log eligibility does not disable ordinary plank normalization for smaller quantities.
        // Logs win ties when allowed; otherwise only the secondary plank form is proposed.
        var storageForms = logsEligible ? family.forms().stream().sorted(Comparator.comparingInt(form -> form.base() ? 0 : 1)).toList() : List.of(plank);
        var changeForms = logsEligible ? family.forms() : family.forms().stream().filter(form -> !form.base()).toList();
        var fallbackSlots = new ArrayList<Integer>();
        if (!logsEligible) { fallbackSlots.addAll(plankSlots); for (int slot : sourceSlots) if (!fallbackSlots.contains(slot)) fallbackSlots.add(slot); }
        for (var storageForm : storageForms) {
            var composition = storageForm.base() ? family.composition()
                    : new dev.blockwheel.core.MaterialComposition<>(storageForm, changeForms, BlockMaterials.Form::units);
            var parts = composition.decompose(units); if (parts.isEmpty()) continue;
            boolean sameForms = parts.get().size() == originals.size() && parts.get().stream()
                    .allMatch(part -> originals.getOrDefault(part.form(), 0) == part.count());
            // Preserve an explicit destination on a tie; merge elsewhere when it actually frees a slot.
            for (var destinations : preferred.isEmpty() ? List.of(fallbackSlots) : List.of(preferred, fallbackSlots)) {
                var candidate = all.copy(); boolean fits = true;
                for (var part : parts.get()) if (!candidate.insertPreferred(new ItemStack(part.form().item(), part.count()), destinations, 9, 36).isEmpty()) { fits = false; break; }
                if (!fits) continue;
                int occupied = occupiedMain(candidate);
                if (extraCount == 0 && sameForms && occupied == occupiedMain(before)) continue;
                if (occupied <= budget && occupied < bestSlots) { best = candidate; bestSlots = occupied; }
            }
        }
        // Dense wood may occupy fewer slots unchanged than it would as logs. Prefer that real reduction.
        var merged = all.copy(); boolean fits = true;
        for (var entry : originals.entrySet()) if (!merged.insert(new ItemStack(entry.getKey().item(), entry.getValue()), 9, 36, true).isEmpty()) { fits = false; break; }
        int mergedSlots = occupiedMain(merged);
        if (fits && mergedSlots <= budget && mergedSlots < bestSlots && (extraCount > 0 || mergedSlots < occupiedMain(before))) best = merged;
        return best;
    }
    private static int occupiedMain(InventoryTransaction transaction) {
        int count = 0; for (int slot = 9; slot < 36; slot++) if (!transaction.get(slot).isEmpty()) count++; return count;
    }
    private InventoryTransaction storeWoodPickup(InventoryTransaction trial, BlockMaterials.Material source, int units) {
        if (units == 0) return trial;
        if (units % source.form().units() == 0) {
            int count = units / source.form().units(); var originalShape = trial.copy();
            if (originalShape.insert(new ItemStack(source.form().item(), count), 9, 36, true).isEmpty()) {
                var compact = compactWood(originalShape, source.family(), List.of(), null, 0);
                return compact == null ? originalShape : compact;
            }
            // A complete log can fit an existing log stack even when the incoming source has no empty slot.
            return compactWood(trial, source.family(), List.of(), source.form(), count);
        }
        // Replenishing a held shape may already have split the incoming physical item; store exact change.
        return depositWithBorrow(trial, source.family(), units);
    }

    public Set<BlockMaterials.Family> storageFamilies(Inventory inventory) {
        Set<BlockMaterials.Family> result = new LinkedHashSet<>();
        for (int slot = 9; slot < 36; slot++) {
            var material = materials.resolve(inventory.getItem(slot));
            if (material != null) result.add(material.family());
        }
        return result;
    }

    public Plan restock(Inventory inventory, int slot, Item target) {
        return restock(inventory, null, MaterialSupply.Policy.mainOnly(), slot, target);
    }

    public Plan restock(Inventory inventory, Container ender, MaterialSupply.Policy policy, int slot, Item target) {
        return restock(inventory, ender, policy, slot, new ItemStack(target));
    }

    public Plan restock(Inventory inventory, Container ender, MaterialSupply.Policy policy, int slot, ItemStack reference) {
        return restock(inventory, ender, policy, slot, reference, dev.blockwheel.core.RestockSettings.MAXIMUM);
    }

    public Plan restock(Inventory inventory, Container ender, MaterialSupply.Policy policy, int slot, ItemStack reference, int amount) {
        if (reference.isEmpty() || slot < 0 || slot > 8 || inventory.getSelectedSlot() != slot) return null;
        var material = materials.resolve(reference);
        var trial = new InventoryTransaction(inventory, policy.shulkers() && policy.ender() ? ender : null);
        ItemStack held = trial.get(slot);
        if (!held.isEmpty() && !ItemStack.isSameItemSameComponents(held, reference)) return null;
        int wanted = Math.min(amount, trial.limit(reference) - held.getCount());
        if (wanted <= 0) return null;
        if (material == null) {
            return acquireExact(trial, reference, wanted, policy, (candidate, count) -> {
                candidate.set(slot, reference.copyWithCount(candidate.get(slot).getCount() + count)); return true;
            });
        }
        return acquire(trial, material, wanted, policy, (candidate, count) -> {
            candidate.set(slot, reference.copyWithCount(candidate.get(slot).getCount() + count)); return true;
        });
    }

    public Plan pick(Inventory inventory, int slot, Item target) {
        return pick(inventory, null, MaterialSupply.Policy.pickLoose(), slot, target);
    }

    public Plan pick(Inventory inventory, Container ender, MaterialSupply.Policy policy, int slot, Item target) {
        return pick(inventory, ender, policy, slot, new ItemStack(target));
    }

    public Plan pick(Inventory inventory, Container ender, MaterialSupply.Policy policy, int slot, ItemStack target) {
        if (target.isEmpty() || slot < 0 || slot > 8) return null;
        var material = materials.resolve(target);
        if (material == null && !policy.shulkers()) return null;
        var trial = new InventoryTransaction(inventory, policy.shulkers() && policy.ender() ? ender : null);
        var finish = handDestination(slot, target);
        return material == null ? acquireExact(trial, target, trial.limit(target), policy, finish)
                : acquire(trial, material, trial.limit(target), policy, finish);
    }

    /** A failed radial batch may fetch existing targets, without converting stored family material. */
    public Plan retrieveExact(Inventory inventory, Container ender, MaterialSupply.Policy policy, int slot,
                              ItemStack target, int maximum) {
        if (target.isEmpty() || slot < 0 || slot > 8 || maximum < 1) return null;
        // Radial retrieval never spends other loose hotbar stacks or loose ender items.
        var sources = new MaterialSupply.Policy(policy.shulkers(), policy.ender(), false, false);
        var trial = new InventoryTransaction(inventory, sources.shulkers() && sources.ender() ? ender : null);
        return acquireExact(trial, target, Math.min(maximum, trial.limit(target)), sources, handDestination(slot, target));
    }

    private static BiPredicate<InventoryTransaction, Integer> handDestination(int slot, ItemStack target) {
        return (candidate, count) -> {
            var displaced = candidate.get(slot).copy();
            candidate.set(slot, ItemStack.EMPTY);
            if (!displaced.isEmpty() && !candidate.insert(displaced, 9, 36, true).isEmpty()) return false;
            candidate.set(slot, target.copyWithCount(count)); return true;
        };
    }

    /** Exact refills and picks share withdrawal, reserves and nested transaction finalization. */
    private Plan acquireExact(InventoryTransaction trial, ItemStack target, int desired, MaterialSupply.Policy policy,
                              BiPredicate<InventoryTransaction, Integer> finish) {
        int remaining = desired;
        for (var source : MaterialSupply.collectExact(trial, target, policy)) {
            int take = Math.min(remaining, source.available(trial));
            source.remove(trial, take); remaining -= take;
            if (remaining == 0) break;
        }
        if (remaining == desired) return null;
        trial.flushContainers();
        return finish.test(trial, desired - remaining) ? new Plan(trial, desired - remaining) : null;
    }

    private Plan acquire(InventoryTransaction before, BlockMaterials.Material target, int desired,
                         MaterialSupply.Policy policy, BiPredicate<InventoryTransaction, Integer> finish) {
        var sources = MaterialSupply.collect(before, materials, target, policy);
        long available = sources.stream().mapToLong(source -> (long) source.available(before) * source.form().units()).sum();
        int maximum = (int) Math.min(desired, available / target.form().units());
        MaterialWithdrawal<MaterialSupply.Source> exact = null;
        for (int wanted = maximum; wanted > 0; wanted--) {
            var trial = before.copy();
            int needed = Math.multiplyExact(wanted, target.form().units()), pooled = 0;
            for (var source : sources) {
                int value = source.form().units();
                while (source.available(trial) > 0) {
                    int take = pooled < needed ? Math.min(source.available(trial), (needed - pooled + value - 1) / value) : 1;
                    source.remove(trial, take); pooled = Math.addExact(pooled, Math.multiplyExact(take, value));
                    if (pooled < needed) continue;
                    var result = finishAcquisition(trial, target, pooled - needed, wanted, finish);
                    if (result != null) return result;
                }
            }
            // A lower-priority exact combination can succeed when preferred sources would need unstorable change.
            if (exact == null) exact = new MaterialWithdrawal<>(sources.stream().map(source ->
                    new MaterialWithdrawal.Supply<>(source, source.available(before), source.form().units())).toList(),
                    Math.multiplyExact(maximum, target.form().units()));
            var withdrawal = exact.forUnits(needed);
            if (withdrawal.isPresent()) {
                var candidate = before.copy();
                for (var part : withdrawal.get()) part.source().remove(candidate, part.count());
                var result = finishAcquisition(candidate, target, 0, wanted, finish);
                if (result != null) return result;
            }
        }
        return null;
    }

    private Plan finishAcquisition(InventoryTransaction trial, BlockMaterials.Material target, int change, int wanted,
                                   BiPredicate<InventoryTransaction, Integer> finish) {
        var candidate = trial.copy();
        if (!deposit(candidate, target.family(), change)) return null;
        candidate.flushContainers();
        if (!finish.test(candidate, wanted)) return null;
        long before = MaterialSupply.totalUnits(candidate, materials, target.family(), true);
        long after = MaterialSupply.totalUnits(candidate, materials, target.family(), false);
        if (before != after) throw new IllegalStateException("Supply conservation failed for " + target.family().id());
        return new Plan(candidate, wanted);
    }

    /** Exact physical batches leave unaccepted source items in their original form and slot. */
    private Plan acquireBatches(InventoryTransaction before, BlockMaterials.Material target, int desired,
                                MaterialSupply.Policy policy, BiPredicate<InventoryTransaction, Integer> finish) {
        var sources = MaterialSupply.collect(before, materials, target, policy);
        long available = sources.stream().mapToLong(source -> (long) source.available(before) * source.form().units()).sum();
        int maximum = (int) Math.min(desired, available / target.form().units());
        if (maximum < 1) return null;
        var exact = new MaterialWithdrawal<>(sources.stream().map(source ->
                new MaterialWithdrawal.Supply<>(source, source.available(before), source.form().units())).toList(), maximum * target.form().units());
        for (int wanted = maximum; wanted > 0; wanted--) {
            var withdrawal = exact.forUnits(wanted * target.form().units()); if (withdrawal.isEmpty()) continue;
            var trial = before.copy();
            for (var part : withdrawal.get()) part.source().remove(trial, part.count());
            var result = finishAcquisition(trial, target, 0, wanted, finish);
            if (result != null) return result;
        }
        return null;
    }
    boolean logTemplate(InventoryTransaction snapshot, boolean remote, int outer, int inner) {
        var box = snapshot.box(remote, outer); if (box == null) return false;
        var material = materials.resolve(box.get(inner));
        return material != null && material.family().logBased() && material.form().base();
    }
    /** Convert ordinary loose inputs into one existing named template using the shared acquisition engine. */
    Plan fillTemplate(InventoryTransaction snapshot, int outer, int inner) { return fillTemplate(snapshot, true, outer, inner); }
    Plan fillTemplate(InventoryTransaction snapshot, boolean remote, int outer, int inner) {
        return fillTemplate(snapshot, remote, outer, inner, false);
    }
    Plan fillTemplate(InventoryTransaction snapshot, boolean remote, int outer, int inner, boolean includeHotbar) {
        var box = snapshot.box(remote, outer);
        if (box == null || box.reserve() != 1 || box.space(inner) == 0) return null;
        var target = materials.resolve(box.get(inner));
        if (target == null) return null;
        var sources = includeHotbar ? MaterialSupply.Policy.pickLoose() : MaterialSupply.Policy.mainOnly();
        if (target.family().logBased()) return acquireBatches(snapshot, target, box.space(inner), sources, (candidate, count) ->
                candidate.box(remote, outer).fillExisting(inner, new ItemStack(target.form().item(), count)) == count);
        return acquire(snapshot, target, box.space(inner), sources, (candidate, count) ->
                candidate.box(remote, outer).fillExisting(inner, new ItemStack(target.form().item(), count)) == count);
    }

    private boolean deposit(InventoryTransaction trial, BlockMaterials.Family family, int units) {
        return deposit(trial, family, units, List.of());
    }

    private boolean deposit(InventoryTransaction trial, BlockMaterials.Family family, int units, List<Integer> preferred) {
        var composition = family.composition().decompose(units);
        if (composition.isEmpty()) return false;
        for (var part : composition.get()) {
            if (!trial.insertPreferred(new ItemStack(part.form().item(), part.count()), preferred, 9, 36).isEmpty()) return false;
        }
        return true;
    }

    /** Combine a minimal compatible fractional remainder, without sweeping unrelated inventory. */
    private InventoryTransaction depositWithBorrow(InventoryTransaction trial, BlockMaterials.Family family, int units) {
        var combined = borrowFraction(trial, family, units, family.base().units());
        if (combined != null && deposit(combined.transaction(), family, combined.units())) return combined.transaction();
        var ordinary = trial.copy();
        return deposit(ordinary, family, units) ? ordinary : null;
    }

    private Routed borrowFraction(InventoryTransaction trial, BlockMaterials.Family family, int units, int modulus) {
        if (modulus > 1 && units % modulus != 0) {
            Borrow[] paths = new Borrow[modulus];
            paths[units % modulus] = new Borrow(0, 0, new int[27]);
            for (int slot = 9; slot < 36; slot++) {
                var material = materials.resolve(trial.get(slot));
                if (material == null || material.family() != family || material.form().units() % family.base().units() == 0) continue;
                Borrow[] next = paths.clone();
                int value = material.form().units();
                for (int remainder = 0; remainder < modulus; remainder++) {
                    var previous = paths[remainder];
                    if (previous == null) continue;
                    for (int take = 1; take <= Math.min(trial.get(slot).getCount(), modulus - 1); take++) {
                        int index = (remainder + take * value) % modulus;
                        int count = previous.count() + take, borrowed = previous.units() + take * value;
                        if (next[index] != null && (next[index].count() < count
                                || next[index].count() == count && next[index].units() <= borrowed)) continue;
                        int[] amounts = previous.slots().clone();
                        amounts[slot - 9] = take;
                        next[index] = new Borrow(count, borrowed, amounts);
                    }
                }
                paths = next;
            }
            if (paths[0] != null) {
                var combined = trial.copy();
                for (int i = 0; i < 27; i++) combined.remove(i + 9, paths[0].slots()[i]);
                return new Routed(combined, Math.addExact(units, paths[0].units()));
            }
        }
        return null;
    }

    private void checkBalance(InventoryTransaction trial, BlockMaterials.Family family, int incoming) {
        long before = 0, after = 0;
        for (int slot = 0; slot < 36; slot++) {
            var old = trial.before(slot);
            var oldMaterial = materials.resolve(old);
            var current = trial.get(slot);
            var newMaterial = materials.resolve(current);
            if (oldMaterial != null && oldMaterial.family() == family) before += (long) old.getCount() * oldMaterial.form().units();
            if (newMaterial != null && newMaterial.family() == family) after += (long) current.getCount() * newMaterial.form().units();
        }
        if (after != before + incoming) throw new IllegalStateException("Material conservation failed for " + family.id());
    }
}
