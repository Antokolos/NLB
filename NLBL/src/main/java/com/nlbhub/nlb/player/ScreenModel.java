/**
 * @(#)ScreenModel.java
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
package com.nlbhub.nlb.player;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Снимок экрана: что должно быть видно игроку прямо сейчас. Порт ScreenModel из NLBNavigator.
 * <p>
 * Модель декларативная: движок после каждой команды и каждого тика времени отдаёт новый снимок
 * ({@link PlayerEngine#screen()}), а интерфейс (веб-клиент, консоль) приводит экран в соответствие
 * с ним. Разовые вещи (звуковой эффект, достижение, переход во внешнюю игру) приходят отдельно —
 * событиями ({@link PlayerEngine.Event}). Источник истины для всех полей — поведение
 * INSTEAD-экспорта (STEAD/VNSTEAD). {@link #toJson()} — тот же снимок для веб-клиента; имена полей
 * JSON — как в ScreenModel.h NLBNavigator.
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class ScreenModel {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /**
     * Картинка, в том числе анимированная: число перед расширением — количество кадров,
     * intro-1478.png -> кадры intro-1.png .. intro-1478.png, которые сменяются по таймеру
     */
    public static final class ImageRef {
        /**
         * Статичная картинка (логический путь, уже с учётом тега и переадресаций); для анимации — пусто
         */
        String m_file = "";
        /**
         * Шаблон кадра с %d для анимации, иначе пусто
         */
        String m_framePattern = "";
        int m_frameCount = 0;
        /**
         * 1..frameCount: какой кадр показывать сейчас (по таймеру)
         */
        int m_currentFrame = 0;
        boolean m_looped = false;

        public String getFile() {
            return m_file;
        }

        public String getFramePattern() {
            return m_framePattern;
        }

        public int getFrameCount() {
            return m_frameCount;
        }

        public int getCurrentFrame() {
            return m_currentFrame;
        }

        public boolean isLooped() {
            return m_looped;
        }

        public boolean isEmpty() {
            return m_file.isEmpty() && m_framePattern.isEmpty();
        }

        ObjectNode toJson() {
            ObjectNode result = JSON.objectNode();
            result.put("file", m_file);
            result.put("framePattern", m_framePattern);
            result.put("frameCount", m_frameCount);
            result.put("currentFrame", m_currentFrame);
            result.put("looped", m_looped);
            return result;
        }
    }

    /**
     * Угол экрана, от которого отсчитывается положение графического объекта (coords-origin)
     */
    public enum Anchor {
        LeftTop, MiddleTop, RightTop, LeftMiddle, MiddleMiddle, RightMiddle,
        LeftBottom, MiddleBottom, RightBottom
    }

    /**
     * Положение графического объекта так, как его задаёт экспорт (decorateObjEffect, vn.lua).
     * Корневой объект: угол экрана + offset (если задан) или arm (координаты редактора,
     * пересчитанные на экран 1920×1080). Вложенный: положение внутри родителя — iarm
     */
    public static final class Placement {
        Anchor m_anchor = Anchor.LeftTop;
        boolean m_hasOffset = false;
        int m_offsetX = 0;
        int m_offsetY = 0;
        int m_armX = 0;
        int m_armY = 0;
        /**
         * Вложен в объект в самой книге: положение задано iarm
         */
        boolean m_hasInnerArm = false;
        int m_innerArmX = 0;
        int m_innerArmY = 0;

        public Anchor getAnchor() {
            return m_anchor;
        }

        public boolean hasOffset() {
            return m_hasOffset;
        }

        public int getOffsetX() {
            return m_offsetX;
        }

        public int getOffsetY() {
            return m_offsetY;
        }

        public int getArmX() {
            return m_armX;
        }

        public int getArmY() {
            return m_armY;
        }

        public boolean hasInnerArm() {
            return m_hasInnerArm;
        }

        public int getInnerArmX() {
            return m_innerArmX;
        }

        public int getInnerArmY() {
            return m_innerArmY;
        }

        ObjectNode toJson() {
            ObjectNode result = JSON.objectNode();
            result.put("anchor", m_anchor.name());
            result.put("hasOffset", m_hasOffset);
            result.put("offsetX", m_offsetX);
            result.put("offsetY", m_offsetY);
            result.put("armX", m_armX);
            result.put("armY", m_armY);
            result.put("hasInnerArm", m_hasInnerArm);
            result.put("innerArmX", m_innerArmX);
            result.put("innerArmY", m_innerArmY);
            return result;
        }
    }

    /**
     * Эффект появления графического объекта (файл effect)
     */
    public enum AppearEffect {None, MoveIn, MoveOut, FadeIn, FadeOut, ZoomIn, ZoomOut, Overlap}

    /**
     * Направление движения (файл mvdirection)
     */
    public enum MoveDirection {None, Left, Right, Top, Bottom}

    /**
     * Анимация появления и кадров графического объекта (поля gobj: maxStep, startFrame, curStep)
     */
    public static final class Appearance {
        AppearEffect m_effect = AppearEffect.None;
        MoveDirection m_direction = MoveDirection.None;
        int m_maxStep = 0;
        int m_startFrame = 0;
        int m_curStep = 0;
        int m_pauseFrames = 0;
        int m_preloadFrames = 0;

        public AppearEffect getEffect() {
            return m_effect;
        }

        public MoveDirection getDirection() {
            return m_direction;
        }

        public int getMaxStep() {
            return m_maxStep;
        }

        public int getStartFrame() {
            return m_startFrame;
        }

        public int getCurStep() {
            return m_curStep;
        }

        public int getPauseFrames() {
            return m_pauseFrames;
        }

        public int getPreloadFrames() {
            return m_preloadFrames;
        }

        ObjectNode toJson() {
            ObjectNode result = JSON.objectNode();
            result.put("effect", m_effect.name());
            result.put("direction", m_direction.name());
            result.put("maxStep", m_maxStep);
            result.put("startFrame", m_startFrame);
            result.put("curStep", m_curStep);
            result.put("pauseFrames", m_pauseFrames);
            result.put("preloadFrames", m_preloadFrames);
            return result;
        }
    }

    /**
     * Фрагмент текста: обычный текст или ссылка ({метка} из описания объекта)
     */
    public static final class TextRun {
        public enum Kind {
            /** обычный текст */
            Plain,
            /** {метка} из описания объекта: щелчок = действие с объектом target */
            ObjectLink,
            /** ссылка-переход внутри текста: target — номер варианта */
            ChoiceLink
        }

        final Kind m_kind;
        final String m_text;
        final String m_target;

        TextRun(Kind kind, String text, String target) {
            m_kind = kind;
            m_text = text;
            m_target = target;
        }

        public Kind getKind() {
            return m_kind;
        }

        public String getText() {
            return m_text;
        }

        public String getTarget() {
            return m_target;
        }

        TextRun withText(String text) {
            return new TextRun(m_kind, text, m_target);
        }

        ObjectNode toJson() {
            ObjectNode result = JSON.objectNode();
            result.put("kind", m_kind.name());
            result.put("text", m_text);
            result.put("target", m_target);
            return result;
        }
    }

    /**
     * Абзац ("^" и переводы строк из книги уже разбиты на абзацы)
     */
    public static final class Paragraph {
        final List<TextRun> m_runs = new ArrayList<>();
        /**
         * Продолжает предыдущий абзац через пробел (следующий ответ)
         */
        boolean m_joinPrevious = false;

        public List<TextRun> getRuns() {
            return Collections.unmodifiableList(m_runs);
        }

        public boolean isJoinPrevious() {
            return m_joinPrevious;
        }

        /**
         * Текст абзаца целиком
         */
        public String getText() {
            StringBuilder result = new StringBuilder();
            for (TextRun run : m_runs) {
                result.append(run.m_text);
            }
            return result.toString();
        }

        Paragraph copyWithoutRuns() {
            Paragraph result = new Paragraph();
            result.m_joinPrevious = m_joinPrevious;
            return result;
        }

        ObjectNode toJson() {
            ObjectNode result = JSON.objectNode();
            ArrayNode runs = result.putArray("runs");
            for (TextRun run : m_runs) {
                runs.add(run.toJson());
            }
            result.put("joinPrevious", m_joinPrevious);
            return result;
        }
    }

    /**
     * Вариант перехода (ссылка страницы, служебные ссылки модулей и autowired)
     */
    public static final class Choice {
        /**
         * Номер для PlayerEngine.choose(); -1 у недоступной ссылки
         */
        int m_index = 0;
        String m_text = "";
        /**
         * false — ссылка с ограничением: показывается её alt-текст
         */
        boolean m_enabled = true;
        boolean m_trivial = false;
        boolean m_inlineLink = false;
        String m_altText = "";

        public int getIndex() {
            return m_index;
        }

        public String getText() {
            return m_text;
        }

        public boolean isEnabled() {
            return m_enabled;
        }

        public boolean isTrivial() {
            return m_trivial;
        }

        public boolean isInlineLink() {
            return m_inlineLink;
        }

        public String getAltText() {
            return m_altText;
        }

        ObjectNode toJson() {
            ObjectNode result = JSON.objectNode();
            result.put("index", m_index);
            result.put("text", m_text);
            result.put("enabled", m_enabled);
            result.put("trivial", m_trivial);
            result.put("inlineLink", m_inlineLink);
            result.put("altText", m_altText);
            return result;
        }
    }

    /**
     * Графический или текстовый объект на странице (сцене)
     */
    public static final class SceneObject {
        String m_id = "";
        /**
         * Объект-контейнер; пусто — лежит прямо на странице
         */
        String m_parentId = "";
        String m_label = "";
        boolean m_graphical = false;
        ImageRef m_image = new ImageRef();
        /**
         * morphover: что показать под курсором; пусто — нет
         */
        ImageRef m_hoverImage = new ImageRef();
        Placement m_placement = new Placement();
        Appearance m_appearance = new Appearance();
        int m_zOrder = 0;
        boolean m_visible = true;
        boolean m_collapsed = false;
        boolean m_active = true;
        boolean m_takable = false;
        boolean m_preserved = false;
        boolean m_showOnCursor = false;
        boolean m_clearUnderTooltip = false;
        boolean m_actOnKey = false;
        boolean m_loadOnce = false;
        boolean m_cacheText = false;
        boolean m_noRedrawOnAct = false;

        public String getId() {
            return m_id;
        }

        public String getParentId() {
            return m_parentId;
        }

        public String getLabel() {
            return m_label;
        }

        public boolean isGraphical() {
            return m_graphical;
        }

        public ImageRef getImage() {
            return m_image;
        }

        public ImageRef getHoverImage() {
            return m_hoverImage;
        }

        public Placement getPlacement() {
            return m_placement;
        }

        public Appearance getAppearance() {
            return m_appearance;
        }

        public int getZOrder() {
            return m_zOrder;
        }

        public boolean isVisible() {
            return m_visible;
        }

        public boolean isCollapsed() {
            return m_collapsed;
        }

        public boolean isActive() {
            return m_active;
        }

        public boolean isTakable() {
            return m_takable;
        }

        public boolean isPreserved() {
            return m_preserved;
        }

        public boolean isShowOnCursor() {
            return m_showOnCursor;
        }

        public boolean isClearUnderTooltip() {
            return m_clearUnderTooltip;
        }

        public boolean isActOnKey() {
            return m_actOnKey;
        }

        public boolean isLoadOnce() {
            return m_loadOnce;
        }

        public boolean isCacheText() {
            return m_cacheText;
        }

        public boolean isNoRedrawOnAct() {
            return m_noRedrawOnAct;
        }

        ObjectNode toJson() {
            ObjectNode result = JSON.objectNode();
            result.put("id", m_id);
            result.put("parentId", m_parentId);
            result.put("label", m_label);
            result.put("graphical", m_graphical);
            result.set("image", m_image.toJson());
            result.set("hoverImage", m_hoverImage.toJson());
            result.set("placement", m_placement.toJson());
            result.set("appearance", m_appearance.toJson());
            result.put("zOrder", m_zOrder);
            result.put("visible", m_visible);
            result.put("collapsed", m_collapsed);
            result.put("active", m_active);
            result.put("takable", m_takable);
            result.put("preserved", m_preserved);
            result.put("showOnCursor", m_showOnCursor);
            result.put("clearUnderTooltip", m_clearUnderTooltip);
            result.put("actOnKey", m_actOnKey);
            result.put("loadOnce", m_loadOnce);
            result.put("cacheText", m_cacheText);
            result.put("noRedrawOnAct", m_noRedrawOnAct);
            return result;
        }
    }

    /**
     * Предмет инвентаря
     */
    public static final class InventoryItem {
        String m_id = "";
        /**
         * Вложенный предмет (кнопки боя Frontier внутри панели)
         */
        String m_parentId = "";
        String m_label = "";
        ImageRef m_icon = new ImageRef();
        /**
         * false — только для чтения (STAT, надписи)
         */
        boolean m_active = true;

        public String getId() {
            return m_id;
        }

        public String getParentId() {
            return m_parentId;
        }

        public String getLabel() {
            return m_label;
        }

        public ImageRef getIcon() {
            return m_icon;
        }

        public boolean isActive() {
            return m_active;
        }

        ObjectNode toJson() {
            ObjectNode result = JSON.objectNode();
            result.put("id", m_id);
            result.put("parentId", m_parentId);
            result.put("label", m_label);
            result.set("icon", m_icon.toJson());
            result.put("active", m_active);
            return result;
        }
    }

    /**
     * Оформление окна текста и инвентаря: модификации WINGEOM/INVGEOM/WINCOLOR/INVCOLOR.
     * null — по умолчанию интерфейса
     */
    public static final class WindowStyle {
        String m_textGeometry = null;
        String m_inventoryGeometry = null;
        String m_textColor = null;
        String m_inventoryColor = null;

        WindowStyle copy() {
            WindowStyle result = new WindowStyle();
            result.m_textGeometry = m_textGeometry;
            result.m_inventoryGeometry = m_inventoryGeometry;
            result.m_textColor = m_textColor;
            result.m_inventoryColor = m_inventoryColor;
            return result;
        }

        public String getTextGeometry() {
            return m_textGeometry;
        }

        public String getInventoryGeometry() {
            return m_inventoryGeometry;
        }

        public String getTextColor() {
            return m_textColor;
        }

        public String getInventoryColor() {
            return m_inventoryColor;
        }

        ObjectNode toJson() {
            ObjectNode result = JSON.objectNode();
            putOptional(result, "textGeometry", m_textGeometry);
            putOptional(result, "inventoryGeometry", m_inventoryGeometry);
            putOptional(result, "textColor", m_textColor);
            putOptional(result, "inventoryColor", m_inventoryColor);
            return result;
        }
    }

    public enum Layout {
        /** тема STANDARD: картинка, текст, ссылки, инвентарь */
        Standard,
        /** тема VN: фон на весь экран, текстовое окно, графические объекты */
        VisualNovel
    }

    long m_revision = 0;
    Layout m_layout = Layout.Standard;
    String m_pageId = "";
    String m_caption = "";
    /**
     * Название комнаты, как nam в экспорте STEAD: заголовок страницы (если показывается), иначе
     * название модуля (книги), иначе «...»; в VN — пусто
     */
    String m_roomTitle = "";
    ImageRef m_pageImage = new ImageRef();
    boolean m_imageIsBackground = false;
    final List<Paragraph> m_pageText = new ArrayList<>();
    final List<Paragraph> m_reactionText = new ArrayList<>();
    final List<Paragraph> m_roomText = new ArrayList<>();
    final List<Paragraph> m_objectsText = new ArrayList<>();
    final List<Paragraph> m_modText = new ArrayList<>();
    final List<Choice> m_choices = new ArrayList<>();
    final List<SceneObject> m_sceneObjects = new ArrayList<>();
    final List<InventoryItem> m_inventory = new ArrayList<>();
    WindowStyle m_style = new WindowStyle();
    boolean m_timerActive = false;
    int m_timerIntervalMs = 200;
    boolean m_finished = false;
    boolean m_canSave = true;
    /**
     * Идёт внешняя игра (мини-игра, 3D-сцена); null — нет
     */
    String m_externalGame = null;

    /**
     * Растёт при каждом изменении: интерфейс может пропускать одинаковые снимки
     */
    public long getRevision() {
        return m_revision;
    }

    public Layout getLayout() {
        return m_layout;
    }

    public String getPageId() {
        return m_pageId;
    }

    /**
     * Заголовок страницы (если показывается)
     */
    public String getCaption() {
        return m_caption;
    }

    public String getRoomTitle() {
        return m_roomTitle;
    }

    public ImageRef getPageImage() {
        return m_pageImage;
    }

    /**
     * imagebg: картинка — фон всего экрана
     */
    public boolean isImageBackground() {
        return m_imageIsBackground;
    }

    /**
     * Текст страницы, описания объектов и текст модификаций страницы
     */
    public List<Paragraph> getPageText() {
        return Collections.unmodifiableList(m_pageText);
    }

    /**
     * Ответ на последнее действие (lasttext)
     */
    public List<Paragraph> getReactionText() {
        return Collections.unmodifiableList(m_reactionText);
    }

    /**
     * Текст страницы (по строкам) — для раскладки как в INSTEAD
     */
    public List<Paragraph> getRoomText() {
        return Collections.unmodifiableList(m_roomText);
    }

    /**
     * Описания объектов — одним абзацем через пробел (STEAD)
     */
    public List<Paragraph> getObjectsText() {
        return Collections.unmodifiableList(m_objectsText);
    }

    /**
     * Текст модификаций страницы
     */
    public List<Paragraph> getModText() {
        return Collections.unmodifiableList(m_modText);
    }

    public List<Choice> getChoices() {
        return Collections.unmodifiableList(m_choices);
    }

    public List<SceneObject> getSceneObjects() {
        return Collections.unmodifiableList(m_sceneObjects);
    }

    public List<InventoryItem> getInventory() {
        return Collections.unmodifiableList(m_inventory);
    }

    public WindowStyle getStyle() {
        return m_style;
    }

    /**
     * У страницы есть таймер (или анимация): время должно идти
     */
    public boolean isTimerActive() {
        return m_timerActive;
    }

    /**
     * Как timer:set(200) в экспорте; у анимации 40 (nlb._fps = 25)
     */
    public int getTimerIntervalMs() {
        return m_timerIntervalMs;
    }

    public boolean isFinished() {
        return m_finished;
    }

    public boolean isCanSave() {
        return m_canSave;
    }

    public String getExternalGame() {
        return m_externalGame;
    }

    /**
     * Порции текста для VN-экрана с вариантами отдельным экраном: порции страницы, затем ответа
     * (см. {@link TextPortions})
     */
    public List<List<Paragraph>> portions() {
        List<List<Paragraph>> result = TextPortions.of(m_pageText);
        result.addAll(TextPortions.of(m_reactionText));
        return result;
    }

    private static void putOptional(ObjectNode node, String name, String value) {
        if (value == null) {
            node.putNull(name);
        } else {
            node.put(name, value);
        }
    }

    private static ArrayNode paragraphs(List<Paragraph> paragraphs) {
        ArrayNode result = JSON.arrayNode();
        for (Paragraph paragraph : paragraphs) {
            result.add(paragraph.toJson());
        }
        return result;
    }

    /**
     * Снимок в JSON: имена полей — как в ScreenModel.h NLBNavigator
     */
    public ObjectNode toJson() {
        ObjectNode result = JSON.objectNode();
        result.put("revision", m_revision);
        result.put("layout", m_layout.name());
        result.put("pageId", m_pageId);
        result.put("caption", m_caption);
        result.put("roomTitle", m_roomTitle);
        result.set("pageImage", m_pageImage.toJson());
        result.put("imageIsBackground", m_imageIsBackground);
        result.set("pageText", paragraphs(m_pageText));
        result.set("reactionText", paragraphs(m_reactionText));
        result.set("roomText", paragraphs(m_roomText));
        result.set("objectsText", paragraphs(m_objectsText));
        result.set("modText", paragraphs(m_modText));
        ArrayNode choices = result.putArray("choices");
        m_choices.forEach(c -> choices.add(c.toJson()));
        ArrayNode objects = result.putArray("sceneObjects");
        m_sceneObjects.forEach(o -> objects.add(o.toJson()));
        ArrayNode inventory = result.putArray("inventory");
        m_inventory.forEach(i -> inventory.add(i.toJson()));
        result.set("style", m_style.toJson());
        result.put("timerActive", m_timerActive);
        result.put("timerIntervalMs", m_timerIntervalMs);
        result.put("finished", m_finished);
        result.put("canSave", m_canSave);
        putOptional(result, "externalGame", m_externalGame);
        return result;
    }
}
