package eutros.omnicompendium.gui.entry;

import eutros.omnicompendium.Omnicompendium;
import eutros.omnicompendium.gui.ClickableComponent;
import eutros.omnicompendium.gui.GuiCompendium;
import eutros.omnicompendium.gui.markdown.RenderingVisitor;
import eutros.omnicompendium.gui.markdown.TitleVisitor;
import eutros.omnicompendium.helper.FileHelper;
import eutros.omnicompendium.helper.MouseHelper;
import eutros.omnicompendium.helper.RenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.math.MathHelper;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.lwjgl.input.Mouse;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class CompendiumEntry {

    public static final int SCROLL_BAR_WIDTH = 10;
    public static final int PAD_BOTTOM = 10;
    public static final int SCROLL_BAR_OFFSET = 2;
    private final Node node;
    private final String title;
    private GuiCompendium compendium;
    private float scrollBarClicked = -1;

    @Nullable
    public List<ClickableComponent> clickableComponents = null;
    @Nullable
    public final File source;
    public int scroll = 0;

    public CompendiumEntry(String markdown, @Nullable File source) {
        List<Extension> extensions = Arrays.asList(
                TablesExtension.create(),
                StrikethroughExtension.create());
        Parser parser = Parser.builder()
                .extensions(extensions)
                .build();

        this.source = source;
        node = parser.parse(markdown);

        TitleVisitor visitor = new TitleVisitor();
        node.accept(visitor);
        if(visitor.title != null) {
            title = visitor.title;
        } else if(source != null) {
            title = FileHelper.fileNameToTitle(source);
        } else {
            title = CompendiumEntries.UNTITLED;
        }
    }

    public void draw() {
        RenderHelper.setupCamera(
                GuiCompendium.GUI_X + GuiCompendium.ENTRY_X,
                GuiCompendium.GUI_Y + GuiCompendium.ENTRY_Y,
                GuiCompendium.ENTRY_WIDTH,
                GuiCompendium.ENTRY_HEIGHT
        );

        if(clickableComponents == null) {
            clickableComponents = new ArrayList<>();
            RenderingVisitor.INSTANCE.entry = this;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, -scroll, 0);
        RenderingVisitor.INSTANCE.source = source;
        node.accept(RenderingVisitor.INSTANCE);
        GlStateManager.popMatrix();

        RenderHelper.resetCamera();

        RenderHelper.drawScrollBar(getScrollBar(), scrollBarClicked);
    }

    private int[] getScrollBar() {
        float maxScroll = getMaxScroll();
        float scrollPct = scroll / maxScroll;

        int barHeight = (int) (
                GuiCompendium.ENTRY_HEIGHT *
                        ((float) GuiCompendium.ENTRY_HEIGHT / (Math.max(RenderingVisitor.INSTANCE.y + PAD_BOTTOM, GuiCompendium.ENTRY_HEIGHT)))
        );
        int barY = (int) ((GuiCompendium.ENTRY_HEIGHT - barHeight) * scrollPct);

        return new int[] {
                GuiCompendium.ENTRY_WIDTH + SCROLL_BAR_OFFSET,
                barY,
                SCROLL_BAR_WIDTH,
                barHeight
        };
    }

    private void setScroll(int barY, int barHeight) {
        if(barHeight == GuiCompendium.ENTRY_HEIGHT) {
            scroll = 0;
            return;
        }
        float scrollPct = (float) barY / (GuiCompendium.ENTRY_HEIGHT - barHeight);
        int maxScroll = getMaxScroll();

        this.scroll = MathHelper.clamp(
                (int) (scrollPct * maxScroll),
                0,
                maxScroll
        );
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if(clickableComponents != null) {
            for(ClickableComponent component : clickableComponents) {
                if(component.onClick(mouseX, mouseY + scroll, mouseButton))
                    return true;
            }
        }

        if(mouseButton == 0) {
            int[] bar = getScrollBar();
            int barHeight = bar[3];
            int barY = bar[1];
            if(MouseHelper.isClicked(
                    bar[0],
                    barY,
                    bar[2],
                    barHeight,
                    mouseX,
                    mouseY
            )) {
                scrollBarClicked = (mouseY - barY) / (float) barHeight;
            } else if(MouseHelper.isClicked(GuiCompendium.ENTRY_WIDTH + SCROLL_BAR_OFFSET,
                    0,
                    SCROLL_BAR_WIDTH,
                    GuiCompendium.ENTRY_HEIGHT,
                    mouseX,
                    mouseY)) {
                setScroll(mouseY - barHeight / 2, barHeight);
                scrollBarClicked = 0.5F;
            } else {
                return false;
            }
            return true;
        }
        return false;
    }

    public CompendiumEntry setCompendium(GuiCompendium compendium) {
        this.compendium = compendium;
        clickableComponents = null;
        return this;
    }

    public void reset() {
        scroll = 0;
    }

    public static final double SCROLL_SENSITIVITY = 0.2;

    public void handleMouseInput(int mouseY) {
        int maxScroll = getMaxScroll();

        if(scrollBarClicked >= 0) {
            if(Mouse.isButtonDown(0)) {
                int[] bar = getScrollBar();
                int barHeight = bar[3];
                setScroll(mouseY - (int) (scrollBarClicked * barHeight), barHeight);
            } else {
                scrollBarClicked = -1;
            }
        }

        int wheel = Mouse.getDWheel();
        if(wheel != 0) {
            scroll = MathHelper.clamp(
                    (int) (scroll - wheel * SCROLL_SENSITIVITY),
                    0,
                    maxScroll
            );
        }
    }

    private int getMaxScroll() {
        return Math.max(0, RenderingVisitor.INSTANCE.y - GuiCompendium.ENTRY_HEIGHT + PAD_BOTTOM);
    }

    @Nullable
    public List<String> getTooltip(int mouseX, int mouseY) {
        if(clickableComponents != null) {
            mouseY += scroll;
            for(ClickableComponent component : clickableComponents) {
                if(component.isHovered(mouseX, mouseY)) {
                    List<String> tooltip = component.getTooltip();
                    if(tooltip != null) {
                        return tooltip;
                    }
                }
            }
        }

        return null;
    }

    public GuiCompendium getCompendium() {
        return compendium;
    }

    @Nonnull
    public String getTitle() {
        return title;
    }

    public LinkFunction linkFunction(String destination) {
        return new LinkFunction(destination);
    }

    public class LinkFunction implements ClickableComponent.ClickFunction {

        private final String link;

        public LinkFunction(String link) {
            this.link = link;
        }

        @Override
        public boolean click(int mouseX, int mouseY, int mouseButton) {
            if(mouseButton != 0) return false;

            try {
                if(Desktop.isDesktopSupported()) {
                    Desktop desktop = Desktop.getDesktop();

                    if(tryOpenAsEntry()) return true;
                    if(tryOpenAsFile(desktop)) return true;
                    if(tryOpenContaining(desktop)) return true;
                    if(tryOpenAsUrl(desktop)) return true;
                }
            } catch(Throwable e) {
                Omnicompendium.LOGGER.error("An unexpected error occurred.", e);
            }
            return false;
        }

        protected boolean tryOpenAsEntry() {
            Optional<CompendiumEntry> linkedEntry = CompendiumEntries.fromLink(link, source);
            GuiCompendium gui = getCompendium();
            if(linkedEntry.isPresent()) {
                gui.openEntry(linkedEntry.get());
                return true;
            }
            return false;
        }

        protected boolean tryOpenAsFile(Desktop desktop) throws IOException {
            if(!desktop.isSupported(Desktop.Action.OPEN)
                    || GameSettings.isKeyDown(Minecraft.getMinecraft().gameSettings.keyBindSneak)) return false;
            Optional<File> file = FileHelper.getRelative(source, link)
                    .map(Path::toFile)
                    .filter(File::exists);

            if(!file.isPresent()) return false;

            desktop.open(file.get().getCanonicalFile());
            return true;
        }

        protected boolean tryOpenContaining(Desktop desktop) throws IOException {
            if(!desktop.isSupported(Desktop.Action.OPEN)
                    || !GameSettings.isKeyDown(Minecraft.getMinecraft().gameSettings.keyBindSneak)) return false;

            Optional<File> parent = FileHelper.getRelative(source, link)
                    .map(Path::getParent)
                    .map(Path::toFile)
                    .filter(File::exists);

            if(!parent.isPresent()) return false;

            desktop.open(parent.get().getCanonicalFile());
            return true;
        }

        protected boolean tryOpenAsUrl(Desktop desktop) throws IOException {
            try {
                if(desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URL(link).toURI());
                    return true;
                }
            } catch(MalformedURLException | URISyntaxException ignored) {
            }
            return false;
        }

    }

}
