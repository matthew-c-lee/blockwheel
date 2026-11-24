package dev.blockwheel.core;

/** One action per physical press: a short release or a completed hold, never both. */
public final class PressHoldAction {
    public static final long HOLD_MILLIS = 600;
    public enum Action { NONE, MAIN_INVENTORY, INCLUDING_HOTBAR }
    private long started;
    private boolean pending, fired;

    public void begin(long now) {
        if (pending) return;
        started = now; pending = true; fired = false;
    }
    public Action update(long now, boolean down) {
        if (!pending) return Action.NONE;
        Action result = Action.NONE;
        if (!fired && now - started >= HOLD_MILLIS) {
            fired = true; result = Action.INCLUDING_HOTBAR;
        } else if (!down && !fired) result = Action.MAIN_INVENTORY;
        if (!down) cancel();
        return result;
    }
    public boolean pending() { return pending; }
    public double progress(long now) {
        return pending ? Math.clamp((double) (now - started) / HOLD_MILLIS, 0, 1) : 0;
    }
    public void cancel() { pending = false; fired = false; }
}
