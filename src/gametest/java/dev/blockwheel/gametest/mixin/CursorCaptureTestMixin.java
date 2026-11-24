package dev.blockwheel.gametest.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;

/** Fabric's test driver cancels this helper; restore vanilla capture to test the real cursor lifecycle. */
@Mixin(InputConstants.class)
public abstract class CursorCaptureTestMixin {
    @WrapMethod(method = "grabOrReleaseMouse")
    private static void captureForTest(Window window, int mode, double x, double y, Operation<Void> original) {
        GLFW.glfwSetCursorPos(window.handle(), x, y);
        GLFW.glfwSetInputMode(window.handle(), GLFW.GLFW_CURSOR, mode);
    }
}
