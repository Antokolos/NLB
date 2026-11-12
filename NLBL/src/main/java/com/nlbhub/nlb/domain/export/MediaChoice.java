package com.nlbhub.nlb.domain.export;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.util.StringHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Картинка или звук страницы (объекта) для пошаговых экспортов (QSP, URQ, jsIQ2, AXMA,
 * ChoiceScript) — выбор как в Java-плеере (ModificationInterpreter.selectImage, playSound) и
 * nlb.lua (pic, snd):
 * <ul>
 *     <li>файлы — через «;», у каждого может быть условие (.constrid) — значение тега;</li>
 *     <li>картинка — первая, чьё условие совпадает с тегом страницы (объекта); без условия —
 *     подходит всегда, следующие уже не проверяются; анимированная — первый кадр;</li>
 *     <li>звук — без условия играет всегда, подряд идущие с условием — цепочка if/elseif по
 *     тегу; void — остановить музыку; SFX (флаг страницы, объекта или файла) — эффект или фраза
 *     озвучки, остальное — музыка (повторяется, тот же файл не перезапускается).</li>
 * </ul>
 * Путь — с переадресацией (.redirect) и каталогом внешнего модуля, как в экспорте INSTEAD.
 */
public class MediaChoice {
    /** Тег, при котором выбирается файл; пустой — без условия */
    private final String m_constraint;
    /** Путь к файлу (images/…, sound/…); у void — пустой */
    private final String m_path;
    private final boolean m_sfx;
    private final boolean m_stop;

    public MediaChoice(String constraint, String path, boolean sfx, boolean stop) {
        m_constraint = constraint == null ? Constants.EMPTY_STRING : constraint;
        m_path = path == null ? Constants.EMPTY_STRING : path;
        m_sfx = sfx;
        m_stop = stop;
    }

    public String getConstraint() {
        return m_constraint;
    }

    public boolean hasConstraint() {
        return StringHelper.notEmpty(m_constraint);
    }

    public String getPath() {
        return m_path;
    }

    /** Эффект (SFX), а не музыка */
    public boolean isSfx() {
        return m_sfx;
    }

    /** void — остановить музыку */
    public boolean isStop() {
        return m_stop;
    }

    /**
     * Картинки — до первой без условия включительно (дальше выбор не доходит). Анимированная —
     * первый кадр (у пути шаблон с %d)
     */
    public static List<MediaChoice> images(List<ImagePathData> paths) {
        List<MediaChoice> result = new ArrayList<>();
        for (ImagePathData data : paths) {
            String path = data.getImagePath();
            if (StringHelper.isEmpty(path)) {
                continue;
            }
            if (data.getMaxFrameNumber() > 0 && !data.isRemoveFrameNumber()) {
                path = path.replace("%d", "1");
            }
            MediaChoice choice = new MediaChoice(data.getConstraint(), path, false, false);
            result.add(choice);
            if (!choice.hasConstraint()) {
                break;
            }
        }
        return result;
    }

    /** Звуки по порядку поля; sfx — флаг SFX страницы (объекта) */
    public static List<MediaChoice> sounds(List<SoundPathData> paths, boolean sfx) {
        List<MediaChoice> result = new ArrayList<>();
        for (SoundPathData data : paths) {
            String path = data.getSoundPath();
            if (StringHelper.isEmpty(path)) {
                continue;
            }
            boolean stop = Constants.VOID.equals(data.getFileName());
            result.add(new MediaChoice(data.getConstraint(), stop ? Constants.EMPTY_STRING : path,
                    sfx || data.isSfx(), stop));
        }
        return result;
    }

    /**
     * Звуки по шагам, как playSound: шаг — либо один звук без условия, либо цепочка подряд идущих
     * звуков с условием (срабатывает первый, чьё условие совпало с тегом)
     */
    public static List<List<MediaChoice>> soundSteps(List<MediaChoice> sounds) {
        List<List<MediaChoice>> steps = new ArrayList<>();
        List<MediaChoice> chain = null;
        for (MediaChoice sound : sounds) {
            if (sound.hasConstraint()) {
                if (chain == null) {
                    chain = new ArrayList<>();
                    steps.add(chain);
                }
                chain.add(sound);
            } else {
                chain = null;
                steps.add(Collections.singletonList(sound));
            }
        }
        return steps;
    }
}
