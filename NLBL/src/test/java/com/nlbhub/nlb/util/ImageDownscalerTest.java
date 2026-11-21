/**
 * @(#)ImageDownscalerTest.java
 *
 * This file is part of the Non-Linear Book project.
 * Copyright (c) 2012-2026 Anton P. Kolosov
 * Authors: Anton P. Kolosov, et al.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * ANTON P. KOLOSOV. ANTON P. KOLOSOV DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the Non-Linear Book software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Anton P. Kolosov at this
 * address: antokolos@gmail.com
 *
 * Copyright (c) 2026 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.util;

import com.nlbhub.nlb.api.MediaFile;
import com.nlbhub.nlb.domain.MediaFileImpl;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.ObjImpl;
import com.nlbhub.nlb.domain.PageImpl;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Уменьшение картинок библиотеки: общий коэффициент группы, без увеличения, формат и
 * прозрачность сохраняются, кадры анимации и переадресации входят в группу
 */
public class ImageDownscalerTest {
    @Rule
    public TemporaryFolder m_tmp = new TemporaryFolder();

    private File png(String name, int w, int h) throws IOException {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        // левая половина прозрачна, правая — непрозрачный красный
        g.setColor(new Color(255, 0, 0, 255));
        g.fillRect(w / 2, 0, w - w / 2, h);
        g.dispose();
        File file = new File(m_tmp.getRoot(), name);
        ImageIO.write(image, "png", file);
        return file;
    }

    private File jpg(String name, int w, int h) throws IOException {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        File file = new File(m_tmp.getRoot(), name);
        ImageIO.write(image, "jpg", file);
        return file;
    }

    @Test
    public void spriteFitsScreenHeight() throws IOException {
        // спрайт CoolAir-Images: 1086×1448 в экран 1920×1080 -> 810×1080
        File sprite = png("drmunoz_neutral.png", 1086, 1448);
        ImageDownscaler.Plan plan = ImageDownscaler.plan(List.of(sprite), 1920, 1080);
        ImageDownscaler.Item item = plan.getItems().get(0);
        assertEquals(810, item.getNewWidth());
        assertEquals(1080, item.getNewHeight());
        assertTrue(plan.hasChanges());

        ImageDownscaler.apply(plan, 90);
        BufferedImage result = ImageIO.read(sprite);
        assertEquals(810, result.getWidth());
        assertEquals(1080, result.getHeight());
        assertFalse("временный файл убран", new File(sprite.getPath() + ".downscale-tmp").exists());
    }

    @Test
    public void groupSharesOneScale() throws IOException {
        // ракурсы одного персонажа разной высоты: коэффициент — по самому высокому
        File tall = png("a.png", 1000, 2000);
        File short_ = png("b.png", 1000, 1000);
        ImageDownscaler.Plan plan = ImageDownscaler.plan(List.of(tall, short_), 0, 1000);
        assertEquals(0.5, plan.getScale(), 1e-9);
        assertEquals(500, plan.getItems().get(0).getNewWidth());
        assertEquals(1000, plan.getItems().get(0).getNewHeight());
        assertEquals(500, plan.getItems().get(1).getNewWidth());
        assertEquals(500, plan.getItems().get(1).getNewHeight());
    }

    @Test
    public void widthAndHeightLimitsBothApply() throws IOException {
        // самая широкая и самая высокая — разные картинки: коэффициент — по худшему из ограничений
        File wide = png("wide.png", 3840, 1000);
        File tall = png("tall.png", 1000, 1620);
        ImageDownscaler.Plan plan = ImageDownscaler.plan(List.of(wide, tall), 1920, 1080);
        assertEquals(0.5, plan.getScale(), 1e-9);
        assertEquals(1920, plan.getItems().get(0).getNewWidth());
        assertEquals(810, plan.getItems().get(1).getNewHeight());
    }

    @Test
    public void noUpscale() throws IOException {
        File small = png("small.png", 640, 480);
        byte[] before = Files.readAllBytes(small.toPath());
        ImageDownscaler.Plan plan = ImageDownscaler.plan(List.of(small), 1920, 1080);
        assertEquals(1.0, plan.getScale(), 0.0);
        assertFalse(plan.hasChanges());
        ImageDownscaler.apply(plan, 90);
        assertArrayEquals("файл не перезаписан", before, Files.readAllBytes(small.toPath()));
    }

    @Test
    public void secondRunChangesNothing() throws IOException {
        File sprite = png("s.png", 1086, 1448);
        ImageDownscaler.apply(ImageDownscaler.plan(List.of(sprite), 1920, 1080), 90);
        assertFalse(ImageDownscaler.plan(List.of(sprite), 1920, 1080).hasChanges());
    }

    @Test
    public void pngKeepsTransparency() throws IOException {
        File sprite = png("t.png", 400, 400);
        ImageDownscaler.apply(ImageDownscaler.plan(List.of(sprite), 200, 200), 90);
        BufferedImage result = ImageIO.read(sprite);
        assertTrue(result.getColorModel().hasAlpha());
        assertEquals("слева прозрачно", 0, result.getRGB(10, 100) >>> 24);
        int right = result.getRGB(190, 100);
        assertEquals("справа непрозрачно", 255, right >>> 24);
        assertEquals("без тёмной каймы: красный остаётся красным", 255, (right >> 16) & 0xff);
    }

    @Test
    public void jpgStaysJpg() throws IOException {
        File bg = jpg("bg.jpg", 3840, 2160);
        ImageDownscaler.apply(ImageDownscaler.plan(List.of(bg), 1920, 1080), 90);
        byte[] bytes = Files.readAllBytes(bg.toPath());
        assertEquals("сигнатура JPEG", (byte) 0xFF, bytes[0]);
        assertEquals((byte) 0xD8, bytes[1]);
        BufferedImage result = ImageIO.read(bg);
        assertEquals(1920, result.getWidth());
        assertEquals(1080, result.getHeight());
        assertFalse(result.getColorModel().hasAlpha());
    }

    @Test
    public void unsupportedAndBrokenFilesAreReported() throws IOException {
        File gif = new File(m_tmp.getRoot(), "anim.gif");
        Files.write(gif.toPath(), new byte[]{1, 2, 3});
        File broken = new File(m_tmp.getRoot(), "broken.png");
        Files.write(broken.toPath(), "не картинка".getBytes(StandardCharsets.UTF_8));
        File big = png("big.png", 4000, 4000);
        ImageDownscaler.Plan plan = ImageDownscaler.plan(List.of(gif, broken, big), 1000, 1000);
        assertNotNull(plan.getItems().get(0).getProblem());
        assertNotNull(plan.getItems().get(1).getProblem());
        assertNull(plan.getItems().get(2).getProblem());
        assertFalse(plan.getItems().get(0).isChanged());
        assertEquals("коэффициент — по читаемым файлам", 0.25, plan.getScale(), 1e-9);
    }

    @Test
    public void animationFramesFromLastFrameName() {
        List<String> library = Arrays.asList("intro-1.jpg", "intro-2.jpg", "intro-3.jpg", "intro-10.jpg", "other.jpg");
        assertEquals(Arrays.asList("intro-1.jpg", "intro-2.jpg", "intro-3.jpg"),
                ImageDownscaler.animationFrames("intro-3.jpg", library));
        assertTrue("без номера кадра — не анимация", ImageDownscaler.animationFrames("other.jpg", library).isEmpty());
    }

    private static MediaFile media(String name, String redirect) {
        MediaFileImpl file = new MediaFileImpl(name);
        if (redirect != null) {
            file.setRedirect(redirect);
        }
        return file;
    }

    @Test
    public void groupAddsWholeAnimationAndResolvesRedirects() {
        List<MediaFile> library = new ArrayList<>(List.of(
                media("walk1.png", null), media("walk2.png", null), media("walk3.png", null),
                media("hero.png", "hero_big.png"), media("hero_big.png", null),
                media("single.png", null)
        ));
        Set<String> animated = new HashSet<>(List.of("walk3.png"));
        // выделен один кадр — в группе вся серия; переадресованный файл — заменён целью
        assertEquals(Arrays.asList("walk2.png", "walk1.png", "walk3.png", "hero_big.png"),
                ImageDownscaler.group(List.of("walk2.png", "hero.png"), library, animated));
        // файл с номером, но не анимированный, серию не тянет
        assertEquals(Collections.singletonList("single.png"),
                ImageDownscaler.group(List.of("single.png"), library, animated));
        assertEquals(Collections.singletonList("walk1.png"),
                ImageDownscaler.group(List.of("walk1.png"), library, Collections.emptySet()));
    }

    @Test
    public void animatedImageNamesFromPagesAndObjs() {
        NonLinearBookImpl book = new NonLinearBookImpl();
        PageImpl page = new PageImpl(book);
        page.setImageFileName("intro-1478.jpg");
        page.setImageAnimated(true);
        book.addPage(page);
        PageImpl still = new PageImpl(book);
        still.setImageFileName("still5.png");
        book.addPage(still);
        ObjImpl obj = new ObjImpl(book);
        obj.setImageFileName("fire8.png;smoke4.png");
        obj.setAnimatedImage(true);
        book.addObj(obj);
        assertEquals(new HashSet<>(List.of("intro-1478.jpg", "fire8.png", "smoke4.png")),
                ImageDownscaler.animatedImageNames(book));
    }
    @Test
    public void offsetWarningsForOffsetsAndNestedObjects() {
        NonLinearBookImpl book = new NonLinearBookImpl();
        ObjImpl scroll = new ObjImpl(book);
        scroll.setName("scroll");
        scroll.setImageFileName("scroll.png");
        book.addObj(scroll);
        ObjImpl icon = new ObjImpl(book);
        icon.setName("icon");
        icon.setImageFileName("icon.png");
        icon.setContainerId(scroll.getId());
        book.addObj(icon);
        ObjImpl hero = new ObjImpl(book);
        hero.setName("hero");
        hero.setImageFileName("hero.png");
        hero.setOffset("120,40");
        book.addObj(hero);
        ObjImpl doctor = new ObjImpl(book);
        doctor.setName("doctor");
        doctor.setImageFileName("drmunoz_neutral.png");
        doctor.setOffset("0,0");
        book.addObj(doctor);
        List<MediaFile> library = List.of(media("hero.png", "hero_big.png"), media("hero_big.png", null),
                media("scroll.png", null), media("icon.png", null), media("drmunoz_neutral.png", null));

        List<String> warnings = ImageDownscaler.offsetWarnings(book, List.of("scroll.png", "hero_big.png", "drmunoz_neutral.png"), library);
        assertEquals(warnings.toString(), 2, warnings.size());
        assertTrue(warnings.toString(), warnings.stream().anyMatch(w -> w.contains("'scroll'") && w.contains("1 nested")));
        assertTrue("переадресованный на файл группы — тоже", warnings.stream().anyMatch(w -> w.contains("'hero'") && w.contains("120,40")));
        assertTrue("смещение 0,0 — без предупреждения", ImageDownscaler.offsetWarnings(book, List.of("drmunoz_neutral.png"), library).isEmpty());
    }
    @Test
    public void replanWithoutReadingFiles() throws IOException {
        File sprite = png("r.png", 1086, 1448);
        ImageDownscaler.Plan plan = ImageDownscaler.plan(List.of(sprite), 1920, 1080);
        assertTrue(sprite.delete());
        ImageDownscaler.Plan again = ImageDownscaler.replan(plan, 0, 1000);
        assertNull("файл уже не читается, но размер известен", again.getItems().get(0).getProblem());
        assertEquals(750, again.getItems().get(0).getNewWidth());
        assertEquals(1000, again.getItems().get(0).getNewHeight());
        assertFalse(ImageDownscaler.replan(plan, 0, 0).hasChanges());
    }
}
