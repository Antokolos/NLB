package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.api.Link;
import com.nlbhub.nlb.api.Obj;
import com.nlbhub.nlb.api.Page;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.util.MultiLangString;
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.DummyVCSAdapter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Тексты по умолчанию (Page.DEFAULT_TEXT и др.) — общие константы. Поле нового элемента ссылалось
 * прямо на константу, а одноязычные сеттеры (setText, setCaption…) меняли объект на месте: текст
 * одной страницы становился «текстом по умолчанию» для всех. Сохранение считает текст, совпавший с
 * текстом по умолчанию, умолчанием и удаляет его файлы — текст другой страницы пропадал (#7:
 * «Text and caption of page become cleared sometimes after save»). И сценарий из #7 — смена
 * языка книги и правка на другом языке — тексты и заголовки на обоих языках сохраняются
 */
public class DefaultTextsNotSharedTest {
    private Path m_dir;

    @Before
    public void setUp() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
        m_dir = Files.createTempDirectory("default-texts");
    }

    @After
    public void tearDown() throws IOException {
        try (Stream<Path> walk = Files.walk(m_dir)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    private static MultiLangString texts(String... langAndText) {
        final MultiLangString result = MultiLangString.createEmptyText();
        for (int i = 0; i < langAndText.length; i += 2) {
            result.put(langAndText[i], langAndText[i + 1]);
        }
        return result;
    }

    private static String textFile(File book, Page page, String language) throws IOException {
        final File file = new File(book, "pages/" + page.getId() + "/text/" + language);
        return file.isFile() ? Files.readString(file.toPath()) : "(нет файла)";
    }

    @Test
    public void singleLanguageSettersDoNotChangeDefaults() {
        final NonLinearBookImpl nlb = new NonLinearBookImpl();
        final PageImpl page = new PageImpl(nlb);
        page.setText("Текст первой страницы");
        page.setCaption("Заголовок");
        page.setReturnText("Назад");
        page.setAutowireInText("Войти");
        page.setAutowireOutText("Выйти");
        final ObjImpl obj = new ObjImpl(nlb);
        obj.setText("Описание");
        obj.setActText("Действие");
        obj.setNouseText("Нельзя");
        obj.setDisp("Предмет");
        final LinkImpl link = new LinkImpl(page, page.getId());
        link.setText("Ссылка");
        link.setAltText("Иначе");

        assertTrue("Page.DEFAULT_TEXT не изменился", Page.DEFAULT_TEXT.get(nlb.getLanguage()).isEmpty());
        assertTrue(Page.DEFAULT_CAPTION.get(nlb.getLanguage()).isEmpty());
        assertTrue(Page.DEFAULT_RETURN_TEXT.get(nlb.getLanguage()).isEmpty());
        assertEquals(MultiLangString.createDefaultLinkText().get(nlb.getLanguage()),
                Page.DEFAULT_AUTOWIRE_IN_TEXT.get(nlb.getLanguage()));
        assertTrue(Obj.DEFAULT_TEXT.get(nlb.getLanguage()).isEmpty());
        assertTrue(Obj.DEFAULT_DISP.get(nlb.getLanguage()).isEmpty());
        assertEquals(MultiLangString.createDefaultLinkText().get(nlb.getLanguage()),
                Link.DEFAULT_TEXT.get(nlb.getLanguage()));
        assertTrue("новая страница — с пустым текстом", new PageImpl(nlb).getText().isEmpty());
        assertTrue(new ObjImpl(nlb).getText().isEmpty());
    }

    @Test
    public void textsGivenToSetterAreNotShared() {
        final NonLinearBookImpl nlb = new NonLinearBookImpl();
        final MultiLangString given = texts(nlb.getLanguage(), "Своё");
        final PageImpl page = new PageImpl(nlb);
        page.setTexts(given);
        given.put(nlb.getLanguage(), "Чужая правка");
        assertEquals("Своё", page.getText());
    }

    @Test
    public void textEqualToAnotherPagesTextSurvivesSave() throws Exception {
        // Страница B с явным текстом; на новой странице A — тот же текст через setText (раньше он
        // становился текстом по умолчанию, и сохранение удаляло файлы текста B)
        final NonLinearBookFacade facade = new NonLinearBookFacade(new Author("t", "t@e"), new DummyVCSAdapter());
        facade.createNewBook();
        final NonLinearBookImpl nlb = (NonLinearBookImpl) facade.getNlb();
        final PageImpl b = new PageImpl(nlb);
        b.setTexts(texts(nlb.getLanguage(), "Совпадающий текст"));
        nlb.addPage(b);
        final PageImpl a = new PageImpl(nlb);
        a.setText("Совпадающий текст");
        nlb.addPage(a);
        final File book = m_dir.resolve("book").toFile();
        facade.saveAs(book, new DummyProgressData());

        // файлы на диске: загрузка в этом же процессе подставила бы испорченный текст по умолчанию
        // вместо удалённого файла, и потеря не была бы видна
        assertEquals("текст B сохранён", "Совпадающий текст", textFile(book, b, nlb.getLanguage()));
        assertEquals("текст A сохранён", "Совпадающий текст", textFile(book, a, nlb.getLanguage()));
    }

    @Test
    public void textsAndCaptionsInBothLanguagesSurviveLanguageSwitchAndSaves() throws Exception {
        // Сценарий из #7: книга на en, страница с текстом и заголовком на en и ru; язык книги
        // переключают на ru, правят страницу (как диалог: все языки, правка — в ru), сохраняют,
        // открывают, сохраняют ещё раз
        final File book = m_dir.resolve("bilingual").toFile();
        final NonLinearBookFacade facade = new NonLinearBookFacade(new Author("t", "t@e"), new DummyVCSAdapter());
        facade.createNewBook();
        final NonLinearBookImpl nlb = (NonLinearBookImpl) facade.getNlb();
        final PageImpl page = new PageImpl(nlb);
        page.setTexts(texts("en", "English text", "ru", "Русский текст"));
        page.setCaptions(texts("en", "Caption", "ru", "Заголовок"));
        nlb.addPage(page);
        facade.saveAs(book, new DummyProgressData());

        final NonLinearBookFacade second = new NonLinearBookFacade(new Author("t", "t@e"), new DummyVCSAdapter());
        second.createNewBook();
        second.load(book.getPath(), new DummyProgressData());
        final NonLinearBookImpl opened = (NonLinearBookImpl) second.getNlb();
        final PageImpl openedPage = opened.getPageImplById(page.getId());
        for (String language : new String[]{"ru", "en"}) {
            // как диалог страницы: все языки из страницы, правка — на одном
            final MultiLangString edited = openedPage.getTexts();
            edited.put(language, edited.get(language) + "!");
            openedPage.setTexts(edited);
            second.save(false, new DummyProgressData());
        }

        final NonLinearBookImpl reloaded = new NonLinearBookImpl();
        reloaded.load(book.getPath(), new DummyProgressData());
        final Page result = reloaded.getPageById(page.getId());
        assertEquals("Русский текст!", result.getTexts().get("ru"));
        assertEquals("English text!", result.getTexts().get("en"));
        assertEquals("Заголовок", result.getCaptions().get("ru"));
        assertEquals("Caption", result.getCaptions().get("en"));
    }
}
