package com.nlbhub.nlb.builder.form;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.ScreenSize;
import com.nlbhub.nlb.util.ImageDownscaler;
import com.nlbhub.nlb.util.StringHelper;

import javax.swing.*;
import java.io.File;
import java.util.List;

/**
 * Предупреждение в свойствах страницы и объекта: картинка больше экрана игры (1920×1080, экран VN
 * темы NLB). Спрайт (графический объект) и фон VN-страницы рисуются в своём размере — лишнее
 * обрезается (INSTEAD, NLBNavigator и веб-плеер одинаково). Предупреждение —
 * второй строкой в метке имени картинки и в её подсказке; лечится «Set image...» -> «Downscale...».
 */
final class ImageSizeWarning {
    private static final String NO_IMAGE = "<NO IMAGE>";

    private ImageSizeWarning() {
    }

    /**
     * @param screen         размер экрана книги
     * @param imageFileNames поле картинки (имена через «;»)
     * @param applies        проверять ли размер (графический объект; VN-страница или картинка-фон)
     * @param consequence    что будет в игре: «the sprite will be cut off»…
     */
    static void show(JLabel label, File imagesDir, ScreenSize screen, String imageFileNames, boolean applies, String consequence) {
        final int screenWidth = screen.getWidth();
        final int screenHeight = screen.getHeight();
        final String names = imageFileNames == null ? "" : imageFileNames;
        final List<ImageDownscaler.Item> oversized = (
                applies && imagesDir != null && StringHelper.notEmpty(names)
                        ? ImageDownscaler.oversized(imagesDir, names, screenWidth, screenHeight)
                        : List.of()
        );
        if (oversized.isEmpty()) {
            label.setText(names.isEmpty() ? NO_IMAGE : names);
            label.setToolTipText(null);
            return;
        }
        // имя файла — только если картинок в поле несколько (одно имя и так в первой строке)
        final boolean several = names.contains(Constants.MEDIA_FILE_NAME_SEP);
        final StringBuilder sizes = new StringBuilder();
        for (ImageDownscaler.Item item : oversized) {
            if (sizes.length() > 0) {
                sizes.append(", ");
            }
            if (several) {
                sizes.append(item.getFile().getName()).append(' ');
            }
            sizes.append(item.getWidth()).append('×').append(item.getHeight());
        }
        final String message = "Larger than the " + screenWidth + "×" + screenHeight + " screen (" + sizes + "): "
                + consequence + ". Set image... -> Downscale...";
        // ширина задана — длинное предупреждение переносится, а не растягивает диалог
        label.setText("<html><body style='width: 360px'>" + escape(names)
                + "<br><font color='#b45309'>&#9888; " + escape(message) + "</font></body></html>");
        label.setToolTipText(message);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
