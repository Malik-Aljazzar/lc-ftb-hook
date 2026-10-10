package dev.malik.lcftbhook.client.gui;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/** Small reusable single-line text prompt (e.g. naming a new region). */
public class TextPromptScreen extends LcScreen {
    private final String ghostText;
    private final Consumer<String> onConfirm;
    private EditBox textBox;

    public TextPromptScreen(@Nullable Screen parent, Component title, String ghostText, Consumer<String> onConfirm) {
        super(title, parent);
        this.ghostText = ghostText;
        this.onConfirm = onConfirm;
    }

    @Override
    protected int preferredWidth(int screenWidth) {
        return 220;
    }

    @Override
    protected int preferredHeight(int screenHeight) {
        return 76;
    }

    @Override
    protected void initContent() {
        textBox = addRenderableWidget(new EditBox(font, left + PAD, top + HEADER_HEIGHT + 8, boxWidth - PAD * 2, 18, title));
        textBox.setMaxLength(32);
        textBox.setHint(Component.literal(ghostText));
        setInitialFocus(textBox);
        button(Component.translatable("gui.lc_ftb_hook.confirm"), left + PAD, top + HEADER_HEIGHT + 32, boxWidth - PAD * 2, b -> confirm());
    }

    private void confirm() {
        String value = textBox.getValue().trim();
        if (!value.isEmpty()) {
            onConfirm.accept(value);
            onClose();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
