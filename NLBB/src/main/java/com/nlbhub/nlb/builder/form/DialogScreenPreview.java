package com.nlbhub.nlb.builder.form;

import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.domain.ScreenLayout;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * «Screen preview»: экран VN 1920×1080 страницы так, как его покажет игра (раскладка —
 * {@link ScreenLayout}): фон и спрайты в своём размере, всё за краем экрана — затемнено (в игре
 * не видно), сам экран — рамкой; полупрозрачная полоса окна текста темы NLB — по флажку. Сверху —
 * сводка: что обрезается и на сколько, фон не того размера. Окно немодальное и растягивается.
 */
public final class DialogScreenPreview {
    /** Окно текста VN темы NLB (vn:auto_geom, theme.win.geom) и отступ подложки textpad */
    static final Rectangle TEXT_WINDOW = new Rectangle(248, 838, 1424, 204);
    private static final int TEXT_PAD = 8;
    /** За краем экрана показываем не больше экрана с каждой стороны — дальше картинка не нужна */
    private static final int MAX_MARGIN_X = ScreenLayout.SCREEN_WIDTH / 2;
    private static final int MAX_MARGIN_Y = ScreenLayout.SCREEN_HEIGHT / 2;
    private static final Color OUTSIDE_FILL = new Color(0, 0, 0, 150);
    private static final Color SCREEN_BG = new Color(1, 19, 34);  // фон темы VN NLB (#011322)
    private static final Color FRAME = new Color(255, 200, 0);
    private static final Color HIGHLIGHT = new Color(255, 120, 0);

    private final JDialog m_dialog;
    private final ScreenLayout.Screen m_screen;
    private final String m_highlightObjId;
    private final Map<File, BufferedImage> m_images = new HashMap<>();
    private final JCheckBox m_textWindow = new JCheckBox("Text window", true);

    /**
     * @param input          страница и несохранённые правки диалога свойств
     * @param highlightObjId объект, выделяемый рамкой (из свойств объекта); null — нет
     */
    public static void show(Component parent, NonLinearBookFacade facade, ScreenLayout.Input input, String highlightObjId, String title) {
        final NonLinearBookFacade main = facade.getMainFacade();
        final ScreenLayout.Screen screen = ScreenLayout.layout(
                input, main.getNlb().getImagesDir(), main.getNlb().getImageFiles()
        );
        new DialogScreenPreview(parent, screen, highlightObjId, title).m_dialog.setVisible(true);
    }

    private DialogScreenPreview(Component parent, ScreenLayout.Screen screen, String highlightObjId, String title) {
        m_screen = screen;
        m_highlightObjId = highlightObjId;
        final Window owner = parent == null ? null : (parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent));
        m_dialog = new JDialog(owner, "Screen preview" + (title == null || title.isEmpty() ? "" : " — " + title), Dialog.ModalityType.MODELESS);
        m_dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        final JLabel summary = new JLabel(summary(screen));
        summary.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        final ScreenCanvas canvas = new ScreenCanvas();
        canvas.setPreferredSize(new Dimension(960, 600));
        ToolTipManager.sharedInstance().registerComponent(canvas);
        m_textWindow.addActionListener(e -> canvas.repaint());
        m_textWindow.setToolTipText("Text window of the NLB VN theme (" + TEXT_WINDOW.width + "×" + TEXT_WINDOW.height + ")");

        final JLabel note = new JLabel("<html>Initial state of the page: objects that appear during the game are not shown;"
                + " animation — its first frame; of several images — the first one. Outside the screen — darkened.</html>");
        note.setForeground(Color.GRAY);
        final JPanel south = new JPanel(new BorderLayout(8, 0));
        south.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        south.add(m_textWindow, BorderLayout.WEST);
        south.add(note, BorderLayout.CENTER);

        final JPanel content = new JPanel(new BorderLayout());
        content.add(summary, BorderLayout.NORTH);
        content.add(canvas, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        m_dialog.setContentPane(content);
        m_dialog.getRootPane().registerKeyboardAction(
                e -> m_dialog.dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        m_dialog.pack();
        m_dialog.setLocationRelativeTo(owner);
    }

    /** Сводка для автора (по-английски, как интерфейс редактора) */
    static String summary(ScreenLayout.Screen screen) {
        final List<String> lines = new ArrayList<>();
        if (screen.isStandardTheme()) {
            lines.add("The page uses the STANDARD theme: its picture is fitted into the picture area of the theme,"
                    + " this preview shows the VN screen.");
        }
        final ScreenLayout.Element bg = screen.getBackground();
        if (bg != null && !bg.hasImage()) {
            lines.add("Background " + escape(bg.getLabel()) + " is not found or cannot be read.");
        } else if (screen.isBackgroundMismatched() && !screen.isStandardTheme()) {
            // у STANDARD картинка страницы вписывается в свою область — размер не беда
            lines.add("Background " + escape(bg.getLabel()) + " is " + bg.getWidth() + "×" + bg.getHeight() + ", not "
                    + ScreenLayout.SCREEN_WIDTH + "×" + ScreenLayout.SCREEN_HEIGHT
                    + ": INSTEAD shows it centered in its size (shown here), NLBNavigator and the web player stretch it.");
        }
        for (ScreenLayout.Element e : screen.getSprites()) {
            if (e.isCut()) {
                lines.add("<b>" + escape(e.getLabel()) + "</b> (" + e.getWidth() + "×" + e.getHeight() + "): " + overflow(e)
                        + " — cut off.");
            }
        }
        if (lines.isEmpty()) {
            lines.add(screen.getSprites().isEmpty() && bg == null ? "Nothing to show: no background and no graphical objects."
                    : "Everything fits the screen.");
        } else if (screen.getSprites().stream().anyMatch(ScreenLayout.Element::isCut)
                || (screen.isBackgroundMismatched() && !screen.isStandardTheme())) {
            lines.add("To fit: Set image... -> Downscale... in the media library.");
        }
        return "<html>" + String.join("<br>", lines) + "</html>";
    }

    private static String overflow(ScreenLayout.Element e) {
        final List<String> parts = new ArrayList<>();
        if (e.overflowTop() > 0) {
            parts.add(e.overflowTop() + " px above the screen");
        }
        if (e.overflowBottom() > 0) {
            parts.add(e.overflowBottom() + " px below the screen");
        }
        if (e.overflowLeft() > 0) {
            parts.add(e.overflowLeft() + " px left of the screen");
        }
        if (e.overflowRight() > 0) {
            parts.add(e.overflowRight() + " px right of the screen");
        }
        return String.join(", ", parts);
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private BufferedImage image(File file) {
        if (file == null) {
            return null;
        }
        return m_images.computeIfAbsent(file, f -> {
            try {
                return ImageIO.read(f);
            } catch (IOException e) {
                return null;
            }
        });
    }

    private List<ScreenLayout.Element> elements() {
        final List<ScreenLayout.Element> all = new ArrayList<>();
        if (m_screen.getBackground() != null) {
            all.add(m_screen.getBackground());
        }
        all.addAll(m_screen.getSprites());
        return all;
    }

    /** Холст: экран и всё, что выходит за него (не дальше полэкрана), вписаны в размер окна */
    private final class ScreenCanvas extends JComponent {
        /** Видимая область в координатах экрана игры */
        private Rectangle2D area() {
            double minX = 0;
            double minY = 0;
            double maxX = ScreenLayout.SCREEN_WIDTH;
            double maxY = ScreenLayout.SCREEN_HEIGHT;
            for (ScreenLayout.Element e : elements()) {
                if (!e.hasImage()) {
                    continue;
                }
                minX = Math.min(minX, Math.max(-MAX_MARGIN_X, e.getX()));
                minY = Math.min(minY, Math.max(-MAX_MARGIN_Y, e.getY()));
                maxX = Math.max(maxX, Math.min(ScreenLayout.SCREEN_WIDTH + MAX_MARGIN_X, e.getX() + e.getWidth()));
                maxY = Math.max(maxY, Math.min(ScreenLayout.SCREEN_HEIGHT + MAX_MARGIN_Y, e.getY() + e.getHeight()));
            }
            // поле вокруг экрана, чтобы рамка не прилипала к краю окна
            final double pad = 40;
            return new Rectangle2D.Double(minX - pad, minY - pad, maxX - minX + 2 * pad, maxY - minY + 2 * pad);
        }

        private double scale(Rectangle2D area) {
            return Math.min(getWidth() / area.getWidth(), getHeight() / area.getHeight());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            final Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(new Color(60, 60, 60));
                g.fillRect(0, 0, getWidth(), getHeight());
                final Rectangle2D area = area();
                final double k = scale(area);
                g.translate((getWidth() - area.getWidth() * k) / 2, (getHeight() - area.getHeight() * k) / 2);
                g.scale(k, k);
                g.translate(-area.getX(), -area.getY());
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                final int sw = ScreenLayout.SCREEN_WIDTH;
                final int sh = ScreenLayout.SCREEN_HEIGHT;
                g.setColor(SCREEN_BG);
                g.fillRect(0, 0, sw, sh);
                for (ScreenLayout.Element e : elements()) {
                    final BufferedImage img = e.hasImage() ? image(e.getFile()) : null;
                    if (img != null) {
                        g.drawImage(img, e.getX(), e.getY(), e.getWidth(), e.getHeight(), null);
                    }
                }
                if (m_textWindow.isSelected() && !m_screen.isStandardTheme()) {
                    g.setColor(new Color(0, 0, 0, 127));  // vn:textbg — bgalpha 127
                    g.fillRect(TEXT_WINDOW.x - TEXT_PAD, TEXT_WINDOW.y - TEXT_PAD,
                            TEXT_WINDOW.width + 2 * TEXT_PAD, TEXT_WINDOW.height + 2 * TEXT_PAD);
                    g.setColor(new Color(255, 255, 255, 140));
                    g.setFont(g.getFont().deriveFont(Font.PLAIN, 28f));
                    g.drawString("text window", TEXT_WINDOW.x + 12, TEXT_WINDOW.y + 36);
                }
                // за краем экрана — затемнение: в игре этого не видно
                g.setColor(OUTSIDE_FILL);
                final double ax = area.getX();
                final double ay = area.getY();
                final double aw = area.getWidth();
                final double ah = area.getHeight();
                g.fill(new Rectangle2D.Double(ax, ay, aw, -ay));                     // сверху
                g.fill(new Rectangle2D.Double(ax, sh, aw, ay + ah - sh));            // снизу
                g.fill(new Rectangle2D.Double(ax, 0, -ax, sh));                      // слева
                g.fill(new Rectangle2D.Double(sw, 0, ax + aw - sw, sh));             // справа
                final float px = (float) (1 / k);
                g.setStroke(new BasicStroke(2 * px));
                g.setColor(FRAME);
                g.drawRect(0, 0, sw, sh);
                for (ScreenLayout.Element e : m_screen.getSprites()) {
                    if (e.getObjId() != null && e.getObjId().equals(m_highlightObjId)) {
                        g.setStroke(new BasicStroke(3 * px, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{8 * px, 6 * px}, 0));
                        g.setColor(HIGHLIGHT);
                        g.drawRect(e.getX(), e.getY(), Math.max(e.getWidth(), 1), Math.max(e.getHeight(), 1));
                    }
                }
                if (m_screen.isStandardTheme()) {
                    g.setColor(new Color(255, 255, 255, 200));
                    g.setFont(g.getFont().deriveFont(Font.PLAIN, 40f));
                    g.drawString("STANDARD theme: VN screen shown for reference", 40, 80);
                }
            } finally {
                g.dispose();
            }
        }

        /** Подсказка: что под мышью (верхний спрайт), размер и что обрезается */
        @Override
        public String getToolTipText(MouseEvent event) {
            final Rectangle2D area = area();
            final double k = scale(area);
            final double x = (event.getX() - (getWidth() - area.getWidth() * k) / 2) / k + area.getX();
            final double y = (event.getY() - (getHeight() - area.getHeight() * k) / 2) / k + area.getY();
            final List<ScreenLayout.Element> all = elements();
            for (int i = all.size() - 1; i >= 0; i--) {
                final ScreenLayout.Element e = all.get(i);
                if (e.hasImage() && x >= e.getX() && x < e.getX() + e.getWidth() && y >= e.getY() && y < e.getY() + e.getHeight()) {
                    final String kind = e.getKind() == ScreenLayout.Kind.BACKGROUND ? "background" : "sprite";
                    return "<html><b>" + escape(e.getLabel()) + "</b> (" + kind + ")<br>" + e.getFile().getName() + ", "
                            + e.getWidth() + "×" + e.getHeight() + " at " + e.getX() + "," + e.getY()
                            + (e.isCut() ? "<br>" + overflow(e) + " — cut off" : "") + "</html>";
                }
            }
            return null;
        }
    }
}
