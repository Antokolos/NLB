package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.MediaFileImpl;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.ObjImpl;
import com.nlbhub.nlb.domain.PageImpl;
import com.nlbhub.nlb.domain.VariableImpl;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Картинки и звук в экспортах QSP, URQ, jsIQ2, AXMA и ChoiceScript — как
 * ModificationInterpreter.selectImage и playSound (pic и snd в INSTEAD): картинка — первая, чьё
 * условие (.constrid — значение тега) совпало с тегом страницы (объекта), без условия — всегда;
 * звук страницы — при входе: музыка (повторяется, тот же файл не перезапускается) и эффект (флаг
 * SFX у файла); SND — звук объекта; картинка объекта — в сцене (imageInScene)
 */
public class MediaExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private PageImpl m_room;
    private ObjImpl m_lamp;

    private static MediaFileImpl media(String name, String constrId, boolean sfx) {
        MediaFileImpl file = new MediaFileImpl(name);
        file.setConstrId(constrId);
        file.setFlagged(sfx);
        return file;
    }

    /**
     * Комната: картинки a.jpg (тег night) и b.jpg, звуки m.mp3 (музыка) и s.ogg (SFX файла);
     * лампа: картинка l.jpg, звук click.ogg (SFX объекта). Модификация комнаты: SND lamp
     */
    private NonLinearBookImpl book() {
        TestBook b = new TestBook();
        m_room = b.page("Комната.");
        PageImpl out = b.page("Улица.");
        b.link(m_room, out, "Выйти");
        m_room.setImageFileName("a.jpg;b.jpg");
        m_room.setSoundFileName("m.mp3;s.ogg");
        m_lamp = b.obj(m_room, "lamp", "{Лампа}.", "Лампа", false);
        m_lamp.setImageFileName("l.jpg");
        m_lamp.setSoundFileName("click.ogg");
        m_lamp.setSoundSFX(true);
        VariableImpl night = b.variable(Variable.Type.TAG, Variable.DataType.STRING, null, "night");
        b.nlb.addImageFile(media("a.jpg", night.getId(), false));
        b.nlb.addImageFile(media("b.jpg", "", false));
        b.nlb.addImageFile(media("l.jpg", "", false));
        b.nlb.addSoundFile(media("m.mp3", "", false));
        b.nlb.addSoundFile(media("s.ogg", "", true));
        b.nlb.addSoundFile(media("click.ogg", "", false));
        b.mod(m_room, Modification.Type.SND, null, "lamp");
        return b.nlb;
    }

    @Test
    public void qsp() throws Exception {
        File file = new File(m_temp.getRoot(), "book.txt");
        book().exportToQSPTextFile(file);
        String qsp = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_16LE).replace("\r\n", "\n");
        String room = m_room.getId();
        String lamp = m_lamp.getId();
        assertTrue("выбор картинки по тегу: " + qsp, qsp.contains("#nlb_img_" + room + "\nIF $nlb_tag['" + room + "'] = 'night':\n"
                + "    $RESULT = 'images/a.jpg'\nELSE\n    $RESULT = 'images/b.jpg'\nEND\n"));
        assertTrue("звук — при входе: " + qsp, qsp.contains("GOSUB 'nlb_snd_" + room + "'\nGOSUB '" + room + "_show'\n"));
        assertTrue(qsp, qsp.contains("#nlb_snd_" + room + "\nnlb_voice = 0\nGOSUB 'nlb_music', 'sound/m.mp3'\n"
                + "IF nlb_voice = 0: PLAY 'sound/s.ogg' & nlb_voice = 1\n"));
        assertTrue("эффект объекта: " + qsp, qsp.contains("#nlb_snd_" + lamp + "\nPLAY 'sound/click.ogg'\n"));
        assertTrue("SND: " + qsp, qsp.contains("IF nlb_hassnd['" + lamp + "'] = 1: GOSUB 'nlb_snd_' + '" + lamp + "'\n"));
        assertTrue("картинка объекта — в ссылке: " + qsp, qsp.contains("''" + lamp + "''\"><<$FUNC(''nlb_imgtag'', $FUNC(''nlb_img_" + lamp + "''))>>Лампа</a>"));
        assertTrue("музыка: тот же файл не перезапускается: " + qsp, qsp.contains("#nlb_music\nIF $ARGS[0] <> $nlb_music:\n"));
    }

    @Test
    public void urq() throws Exception {
        File file = new File(m_temp.getRoot(), "book.qst");
        book().exportToURQTextFile(file);
        String urq = new String(Files.readAllBytes(file.toPath()), Charset.forName("windows-1251")).replace("\r\n", "\n");
        assertTrue("тег страницы — по ссылке pN (имя с дефисами UrqW не читает): " + urq, urq.contains(":nlb_img_p1\nnlb_img=\"\"\n"
                + "if nlb_img=\"\" and nlb_tag_p1=\"night\" then nlb_img=\"images/a.jpg\"\n"
                + "if nlb_img=\"\" then nlb_img=\"images/b.jpg\"\nend\n"));
        assertTrue(urq, urq.contains("proc nlb_img_p1\nif nlb_leave=0 and nlb_img<>\"\" then image #%nlb_img$\n"));
        assertTrue(urq, urq.contains(":nlb_snd_p1\nnlb_voice=0\nmusic = \"sound/m.mp3\"\nif nlb_voice=0 then nlb_voice=1 & play sound/s.ogg\nend\n"));
        assertTrue(urq, urq.contains(":nlb_snd_1\nplay sound/click.ogg\nend\n"));
        assertTrue("SND: " + urq, urq.contains("if nlb_hassnd_1=1 then proc nlb_snd_1\n"));
    }

    @Test
    public void jsiq() throws Exception {
        File file = new File(m_temp.getRoot(), "book.xml");
        book().exportToJSIQFile(file);
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String room = m_room.getId();
        String lamp = m_lamp.getId();
        assertTrue(xml, xml.contains("'" + room + "': [['night', 'images/a.jpg'], ['', 'images/b.jpg']]"));
        assertTrue(xml, xml.contains("'" + room + "': [['', 'sound/m.mp3', 'm'], ['', 'sound/s.ogg', 'v']]"));
        assertTrue(xml, xml.contains("'" + lamp + "': [['', 'sound/click.ogg', 's']]"));
        assertTrue("звук — при входе: " + xml, xml.contains("NLB.snd(vars, '" + room + "'); }"));
        assertTrue("место картинки страницы: " + xml, xml.contains("<div class=\"nlb_pic\"></div>"));
        assertTrue(xml, xml.contains("imgScene: true,"));
    }

    @Test
    public void asm() throws Exception {
        File file = new File(m_temp.getRoot(), "book.sm");
        book().exportToASMFile(file);
        String sm = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
        String room = m_room.getId();
        assertTrue("главная картинка: " + sm, sm.contains("<<if $nlb_tags[\"" + room + "\"] eq \"night\">><<pic 'images/a.jpg'>>"
                + "<<else>><<pic 'images/b.jpg'>><<endif>>\n"));
        assertTrue("музыка — mp3 через [[File: …]]: " + sm, sm.contains(":: nlb_snd_" + room + "[::]10-10-0\n"
                + "<<if $nlb_music neq \"sound/m.mp3\">><<set $nlb_music = \"sound/m.mp3\">>[[File: sound/m.mp3]]<<endif>>\n"));
        assertTrue("картинка объекта в тексте: " + sm, sm.contains("[[File: images/l.jpg]]\n"));
        assertFalse("эффекты AXMA играет только из вложенных файлов: " + sm, sm.contains("click.ogg"));
    }

    @Test
    public void choiceScript() throws Exception {
        File file = new File(m_temp.getRoot(), "startup.txt");
        book().exportToChoiceScript(file);
        String cs = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertTrue("тег — по умолчанию (пустой): картинка без условия: " + cs, cs.contains("*image scenes/images/b.jpg\n"));
        assertFalse(cs, cs.contains("images/a.jpg"));
        assertTrue(cs, cs.contains("*create nlb_music \"\"\n"));
        assertTrue(cs, cs.contains("*if nlb_music != \"sound/m.mp3\"\n    *set nlb_music \"sound/m.mp3\"\n    *sound scenes/sound/m.mp3\n"
                + "*sound scenes/sound/s.ogg\n"));
    }
}
