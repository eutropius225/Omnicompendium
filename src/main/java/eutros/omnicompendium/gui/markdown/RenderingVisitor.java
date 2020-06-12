package eutros.omnicompendium.gui.markdown;

import com.google.common.collect.ImmutableMap;
import eutros.omnicompendium.gui.GuiCompendium;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.*;

import java.util.List;
import java.util.Map;

public class RenderingVisitor extends AbstractVisitor {

    public static final RenderingVisitor INSTANCE = new RenderingVisitor();

    public static final int CODE_COLOR = 0xFF000000;
    public static final int CODE_BLOCK_BG_COLOR = 0xFFDDDDDD;
    public static final TextFormatting DEFAULT_COLOUR = TextFormatting.BLACK;
    private final Minecraft mc = Minecraft.getMinecraft();
    private int baseX;
    private int width;
    private int x;
    public int y;

    private Style style;
    private String marker;

    private RenderingVisitor() {
        reset();
    }

    public void reset() {
        y = 0;
        x = 0;
        marker = null;
        baseX = 0;
        width = GuiCompendium.ENTRY_WIDTH;
        style = new Style();
        style.setColor(DEFAULT_COLOUR);
    }

    @Override
    public void visit(Document document) {
        reset();
        visitChildren(document);
    }

    private void drawText(String text) {
        String[] firstWord = text.trim().split(" ", 2);
        if(firstWord.length == 0) {
            return; // okay
        }

        if(x != 0 && width - x < mc.fontRenderer.getStringWidth(firstWord[0])) {
            x = 0;
            y += mc.fontRenderer.FONT_HEIGHT;
        }
        List<String> strings = mc.fontRenderer.listFormattedStringToWidth(style.getFormattingCode() + text, width - x);
        mc.fontRenderer.drawString(strings.get(0).trim(), baseX + x, y, 0xFF000000);
        if(strings.size() == 1) {
            x += mc.fontRenderer.getStringWidth(strings.get(0));
            return;
        }

        text = text.substring(strings.get(0).length() - style.getFormattingCode().length());
        text = style.getFormattingCode() + text.replaceFirst("^\\s+", "");

        y += mc.fontRenderer.FONT_HEIGHT;
        strings = mc.fontRenderer.listFormattedStringToWidth(text, width);

        for(String s : strings.subList(0, strings.size() - 1)) {
            mc.fontRenderer.drawString(s, baseX, y, 0xFF000000);
            y += mc.fontRenderer.FONT_HEIGHT;
        }
        String lastString = strings.get(strings.size() - 1);
        mc.fontRenderer.drawString(lastString, baseX, y, 0xFF000000);
        x = mc.fontRenderer.getStringWidth(lastString);
    }

    private void lineBreak(Node node) {
        finishLine();
        if(node != null && node.getNext() != null) {
            y += mc.fontRenderer.FONT_HEIGHT;
        }
    }

    private void finishLine() {
        if(x != 0) {
            y += mc.fontRenderer.FONT_HEIGHT;
            x = 0;
        }
    }

    @Override
    public void visit(BlockQuote blockQuote) {
        int oldY = this.y;
        baseX += 8;
        style.setColor(TextFormatting.GRAY);
        visitChildren(blockQuote);
        style.setColor(DEFAULT_COLOUR);
        baseX -= 8;
        finishLine();
        Gui.drawRect(
                0,
                oldY - 1,
                2,
                y - 1,
                0xFFAAAAAA
        );

        lineBreak(blockQuote);
    }

    @Override
    public void visit(Code code) {
        String literal = code.getLiteral();
        int i = MonoRenderer.sizeStringToWidth(literal, width - x);
        if(literal.length() <= i) {
            Gui.drawRect(baseX + x,
                    y,
                    baseX + x + MonoRenderer.getStringWidth(literal),
                    y + mc.fontRenderer.FONT_HEIGHT - 1,
                    CODE_BLOCK_BG_COLOR);
            MonoRenderer.drawString(literal, baseX + x, y, CODE_COLOR);
            x += MonoRenderer.getStringWidth(literal);
        } else {
            String first = literal.substring(0, i);
            Gui.drawRect(baseX + x,
                    y,
                    baseX + width,
                    y + mc.fontRenderer.FONT_HEIGHT - 1,
                    CODE_BLOCK_BG_COLOR);
            MonoRenderer.drawString(first, baseX + x, y, CODE_COLOR);

            char c0 = literal.charAt(i);
            boolean flag = c0 == ' ' || c0 == '\n';
            String s1 = literal.substring(i + (flag ? 1 : 0));
            List<String> strings = MonoRenderer.listFormattedStringToWidth(s1, GuiCompendium.ENTRY_WIDTH - baseX);

            if(strings.size() > 1) {
                for(String s : strings.subList(0, strings.size() - 1)) {
                    y += mc.fontRenderer.FONT_HEIGHT;
                    Gui.drawRect(baseX,
                            y,
                            baseX + width,
                            y + mc.fontRenderer.FONT_HEIGHT - 1,
                            CODE_BLOCK_BG_COLOR);
                    MonoRenderer.drawString(s, baseX, y, CODE_COLOR);
                }
            }

            y += mc.fontRenderer.FONT_HEIGHT;
            String lastString = strings.get(strings.size() - 1);
            Gui.drawRect(baseX,
                    y,
                    baseX + MonoRenderer.getStringWidth(lastString),
                    y + mc.fontRenderer.FONT_HEIGHT - 1,
                    CODE_BLOCK_BG_COLOR);
            MonoRenderer.drawString(lastString, baseX, y, CODE_COLOR);
            x += MonoRenderer.getStringWidth(lastString);
        }
    }

    @Override
    public void visit(Emphasis emphasis) {
        style.setItalic(true);
        visitChildren(emphasis);
        style.setItalic(false);
    }

    @Override
    public void visit(FencedCodeBlock fencedCodeBlock) {
        // TODO: show info
        drawCodeBlock(fencedCodeBlock.getLiteral());
        lineBreak(fencedCodeBlock);
    }

    @Override
    public void visit(IndentedCodeBlock indentedCodeBlock) {
        drawCodeBlock(indentedCodeBlock.getLiteral());
        lineBreak(indentedCodeBlock);
    }

    private void drawCodeBlock(String literal) {
        final int padding = 5;

        baseX += padding;
        int oldWidth = width;
        width -= padding * 2;
        y += padding;

        List<String> strings = MonoRenderer.listFormattedStringToWidth(literal, width);
        int height = strings.size() * mc.fontRenderer.FONT_HEIGHT;
        Gui.drawRect(baseX - padding, y - padding, oldWidth, y + height + padding, CODE_BLOCK_BG_COLOR);

        for(String string : strings) {
            MonoRenderer.drawString(string, baseX, y, 0xFF000000);
            y += mc.fontRenderer.FONT_HEIGHT;
        }

        width = oldWidth;
        y += padding;
        baseX -= padding;
    }

    @Override
    public void visit(Paragraph paragraph) {
        visitChildren(paragraph);
        lineBreak(paragraph);
    }

    @Override
    public void visit(HardLineBreak hardLineBreak) {
        lineBreak(hardLineBreak);
        visitChildren(hardLineBreak);
    }

    @Override
    public void visit(Heading heading) {
        int level = heading.getLevel();
        style.setBold(true);
        double scale = level < 5 ? 1 + (1.0 / level) : 1;

        int oldWidth = this.width;
        width = (int) (width / scale);
        int oldY = y;

        GlStateManager.pushMatrix();
        GlStateManager.translate(0, -y * (scale - 1), 0);
        GlStateManager.scale(scale, scale, 1);
        visitChildren(heading);
        GlStateManager.popMatrix();

        this.width = oldWidth;
        y = oldY + (int) ((y - oldY + mc.fontRenderer.FONT_HEIGHT) * scale);
        style.setBold(false);

        if(level < 3) {
            drawLine();
        }

        x = 0;
    }

    private void drawLine() {
        Gui.drawRect(5, y + 4, this.width - 5, y + 5, 0xFF000000);
        y += mc.fontRenderer.FONT_HEIGHT;
        x = 0;
    }

    @Override
    public void visit(ThematicBreak thematicBreak) {
        drawLine();
        visitChildren(thematicBreak);
    }

    @Override
    public void visit(Image image) {
        // FIXME: oh boy
        lineBreak(image);
        visitChildren(image);
        lineBreak(image);
    }

    @Override
    public void visit(Link link) {
        // FIXME: link clicky
        style.setColor(TextFormatting.BLUE);
        visitChildren(link);
        style.setColor(DEFAULT_COLOUR);
    }

    @Override
    public void visit(LinkReferenceDefinition linkReferenceDefinition) {
        // FIXME: what
        visitChildren(linkReferenceDefinition);
    }

    public static final int LIST_INDENT = 15;

    @Override
    public void visit(BulletList bulletList) {
        marker = "\u2022";
        startList();
        visitChildren(bulletList);
        endList(bulletList);
    }

    @Override
    public void visit(OrderedList orderedList) {
        int number = orderedList.getStartNumber();
        startList();
        Node node = orderedList.getFirstChild();
        while(node != null) {
            Node next = node.getNext();
            marker = String.valueOf(number++) + orderedList.getDelimiter();
            node.accept(this);
            node = next;
        }
        endList(orderedList);
    }

    private void endList(ListBlock list) {
        baseX -= LIST_INDENT;
        width += LIST_INDENT;
        lineBreak(list);
        marker = null;
    }

    private void startList() {
        baseX += LIST_INDENT;
        width -= LIST_INDENT;
        x = 0;
    }

    @Override
    public void visit(ListItem listItem) {
        mc.fontRenderer.drawString(marker, baseX - LIST_INDENT, y, 0xFF000000);
        visitChildren(listItem);
        if(listItem.getParent() instanceof ListBlock && ((ListBlock) listItem.getParent()).isTight()) {
            finishLine();
        } else {
            lineBreak(listItem);
        }
    }

    @Override
    public void visit(StrongEmphasis strongEmphasis) {
        style.setBold(true);
        visitChildren(strongEmphasis);
        style.setBold(false);
    }

    @Override
    public void visit(Text text) {
        drawText(text.getLiteral());
    }

    @Override
    public void visit(SoftLineBreak softLineBreak) {
        x += mc.fontRenderer.getStringWidth(" ");
        if(x > width) {
            finishLine();
        }
    }

    private Map<Class<? extends CustomNode>, INodeVisitor<?>> customNodeMap =
            ImmutableMap.<Class<? extends CustomNode>, INodeVisitor<?>>builder()
                    .put(Strikethrough.class, (Strikethrough strikethrough) -> {
                        style.setStrikethrough(true);
                        visitChildren(strikethrough);
                        style.setStrikethrough(false);
                    })
                    .put(TableRow.class, (TableRow tableRow) -> {
                        int color = 0xFF000000;

                        Gui.drawRect(
                                baseX,
                                y,
                                baseX + width,
                                y + 1,
                                color
                        );

                        Node child = tableRow.getFirstChild();
                        int count = 0;
                        while(child != null) {
                            count++;
                            child = child.getNext();
                        }

                        final int padding = 2;
                        int oldWidth = width;
                        int oldBaseX = baseX;
                        width = (width / count) - (count * padding * 2);
                        int startY = y;
                        int maxY = y;

                        baseX += padding;
                        x = 0;
                        y += padding;

                        child = tableRow.getFirstChild();
                        while(child != null) {
                            child.accept(this);
                            baseX += oldWidth / count;
                            finishLine();
                            child = child.getNext();
                            if(y > maxY) {
                                maxY = y;
                            }
                            y = startY + padding;
                        }

                        y = maxY;
                        baseX = oldBaseX;
                        width = oldWidth;
                        x = 0;

                        for(int i = 0; i < count; i++) {
                            int left = (width / count) * i;
                            Gui.drawRect(
                                    baseX + left,
                                    startY,
                                    baseX + left + 1,
                                    y,
                                    color
                            );
                        }
                        Gui.drawRect(baseX + width,
                                startY,
                                baseX + width - 1,
                                y,
                                color);


                        if(tableRow.getNext() == null) {
                            Gui.drawRect(
                                    baseX,
                                    y,
                                    baseX + width,
                                    y + 1,
                                    color
                            );
                        }
                    })
                    .build();

    @FunctionalInterface
    private interface INodeVisitor<T extends CustomNode> {

        void accept(T node);

        @SuppressWarnings("unchecked") // this is where the stack pollution happens
        default void visit(CustomNode node) {
            accept((T) node);
        }

    }

    @Override
    public void visit(CustomBlock customBlock) {
        visitChildren(customBlock);
        lineBreak(customBlock);
    }

    @Override
    public void visit(CustomNode customNode) {
        INodeVisitor<?> visitor = customNodeMap.get(customNode.getClass());
        if(visitor == null) {
            visitChildren(customNode);
        } else {
            visitor.visit(customNode);
        }
    }

    @Override
    public void visit(HtmlInline htmlInline) {
        // could you don't
        drawText(htmlInline.getLiteral());
        visitChildren(htmlInline);
    }

    @Override
    public void visit(HtmlBlock htmlBlock) {
        // could you don't
        drawText(htmlBlock.getLiteral());
        visitChildren(htmlBlock);
        lineBreak(htmlBlock);
    }

}
