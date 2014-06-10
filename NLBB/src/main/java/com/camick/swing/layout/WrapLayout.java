package com.camick.swing.layout;

import javax.swing.*;
import java.awt.*;

/**
 * FlowLayout subclass that fully supports wrapping of components.
 * <p/>
 * Wrap Layout
 * Posted by Rob Camick on November 6, 2008
 * <p/>
 * A layout manager has two main functions:
 * <p/>
 * determine the preferred size of the container
 * layout the components in the container based on the layout rules
 * The FlowLayout is a strange animal. It does both of these functions.
 * The preferred size of the container assumes all components will be laid out in a single row.
 * The layout code will wrap components to the next row when the maximum width of the container is
 * encountered. However, the problem is that the functions don’t talk to one another.
 * When the components are wrapped to a new row, the preferred size doesn’t change so you never see
 * the components on the extra row.
 * <p/>
 * What we want is the preferred size to be dynamically calculated as the size of the container is
 * changed. In other words, as the width of the container changes the height will need to be
 * recalculated as well. The WrapLayout extends the FlowLayout to implement this functionality.
 * This will result in synchronizing the preferred size of the container with the layout
 * of the container.
 * <p/>
 * In the following example, the button panel was added to the north of a BorderLayout and the
 * blue panel added to the center. You use the WrapLayout the same as you would use the FlowLayout:
 * <p/>
 * buttons.setLayout(new WrapLayout());
 * <p/>
 * As the frame is resized smaller, the button panel will increase in height and the blue panel
 * will decrease:
 * <p/>
 * When the panel is added to a scroll pane, the size of the scroll pane won’t change,
 * but horizontal and vertical scrollbars will appear as required.
 * <p/>
 * The initial preferred size calculation of the layout manager still assumes all components
 * will be displayed on a single row. So if you pack() a frame the preferred width may be
 * excessive. You can limit the width of the container by using:
 * <p/>
 * buttons.setSize(new Dimension(300, 1));
 * <p/>
 * Note, you must use the setSize() method, this will still allow the preferred
 * size to be dynamically changed.
 */
public class WrapLayout extends FlowLayout {
    private Dimension preferredLayoutSize;

    /**
     * Constructs a new <code>WrapLayout</code> with a left
     * alignment and a default 5-unit horizontal and vertical gap.
     */
    public WrapLayout() {
        super();
    }

    /**
     * Constructs a new <code>FlowLayout</code> with the specified
     * alignment and a default 5-unit horizontal and vertical gap.
     * The value of the alignment argument must be one of
     * <code>WrapLayout</code>, <code>WrapLayout</code>,
     * or <code>WrapLayout</code>.
     *
     * @param align the alignment value
     */
    public WrapLayout(int align) {
        super(align);
    }

    /**
     * Creates a new flow layout manager with the indicated alignment
     * and the indicated horizontal and vertical gaps.
     * <p/>
     * The value of the alignment argument must be one of
     * <code>WrapLayout</code>, <code>WrapLayout</code>,
     * or <code>WrapLayout</code>.
     *
     * @param align the alignment value
     * @param hgap  the horizontal gap between components
     * @param vgap  the vertical gap between components
     */
    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    /**
     * Returns the preferred dimensions for this layout given the
     * <i>visible</i> components in the specified target container.
     *
     * @param target the component which needs to be laid out
     * @return the preferred dimensions to lay out the
     * subcomponents of the specified container
     */
    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    /**
     * Returns the minimum dimensions needed to layout the <i>visible</i>
     * components contained in the specified target container.
     *
     * @param target the component which needs to be laid out
     * @return the minimum dimensions to lay out the
     * subcomponents of the specified container
     */
    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    /**
     * Returns the minimum or preferred dimension needed to layout the target
     * container.
     *
     * @param target    target to get layout size for
     * @param preferred should preferred size be calculated
     * @return the dimension to layout the target container
     */
    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            //  Each row must fit with the width allocated to the containter.
            //  When the container width = 0, the preferred width of the container
            //  has not yet been calculated so lets ask for the maximum.

            int targetWidth = target.getSize().width;

            if (targetWidth == 0)
                targetWidth = Integer.MAX_VALUE;

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            //  Fit components into the allowed width

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            int nmembers = target.getComponentCount();

            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);

                if (m.isVisible()) {
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                    //  Can't add the component to current row. Start a new row.

                    if (rowWidth + d.width > maxWidth) {
                        addRow(dim, rowWidth, rowHeight);
                        rowWidth = 0;
                        rowHeight = 0;
                    }

                    //  Add a horizontal gap for all components after the first

                    if (rowWidth != 0) {
                        rowWidth += hgap;
                    }

                    rowWidth += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                }
            }

            addRow(dim, rowWidth, rowHeight);

            dim.width += horizontalInsetsAndGap;
            dim.height += insets.top + insets.bottom + vgap * 2;

            //    When using a scroll pane or the DecoratedLookAndFeel we need to
            //  make sure the preferred size is less than the size of the
            //  target containter so shrinking the container size works
            //  correctly. Removing the horizontal gap is an easy way to do this.

            Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);

            if (scrollPane != null && target.isValid()) {
                dim.width -= (hgap + 1);
            }

            return dim;
        }
    }

    /*
     *  A new row has been completed. Use the dimensions of this row
     *  to update the preferred size for the container.
     *
     *  @param dim update the width and height when appropriate
     *  @param rowWidth the width of the row to add
     *  @param rowHeight the height of the row to add
     */
    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);

        if (dim.height > 0) {
            dim.height += getVgap();
        }

        dim.height += rowHeight;
    }
}
