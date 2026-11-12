package com.example.examplemod.client.logistics;

/** Pure keyboard safety checks: no Minecraft bootstrap, renderer or native input required. */
public final class LogisticsActivationCheck {
    private static int checks;

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void run() {
        var physicalPress = new LogisticsActivationLatch();
        require(physicalPress.press(257), "fresh Enter activates a control");
        require(!physicalPress.press(257), "held Enter does not repeat");
        require(!physicalPress.press(257), "a rebuilt control shares the same physical press");
        require(physicalPress.press(32), "a distinct fresh Space press is accepted");
        require(!physicalPress.press(257), "Space cannot clear an Enter latch");
        require(physicalPress.release(32), "the actual Space release is consumed");
        require(!physicalPress.press(257), "releasing a different key cannot confirm the next step");
        require(physicalPress.release(257), "Enter releases without focus-dependent resets");
        require(physicalPress.press(257), "the next physical press can activate");
        physicalPress.reconcile(key -> key == 257);
        require(!physicalPress.press(257), "native polling preserves a still-held key across screens");
        physicalPress.reconcile(key -> false);
        require(physicalPress.press(257), "a release handled by another screen is reconciled");
        require(!physicalPress.release(65), "unrelated releases are ignored");
        require(!physicalPress.press(65), "unrelated keys are never activation keys");
        require(!physicalPress.press(257), "unrelated keys cannot unlock a held confirmation");
        require(physicalPress.release(257), "the retained Enter latch clears on release");
        require(physicalPress.press(335), "keypad Enter is accepted");
        require(!physicalPress.press(335), "keypad Enter repeats are suppressed");
        physicalPress.reconcile(key -> false);
        require(physicalPress.press(335), "keypad Enter release is reconciled too");
        System.out.println("PASS: " + checks + " logistics keyboard activation checks");
    }

    public static void main(String[] args) { run(); }
}
