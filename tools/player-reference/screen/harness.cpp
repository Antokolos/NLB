// Эталон модели экрана для сверки Java-плеера: тот же консольный плеер NLBNavigator (NLBExplorer,
// как nlb_main explore), но после каждого шага (старт, каждая команда) в файл пишется снимок
// PlayerEngine::screen() — одна строка JSON на шаг. Имена полей — как в ScreenModel.h; revision
// не пишется (он считает внутренние перерисовки и для сверки не важен). Плюс portions — порции
// текста VN (GameScreen::portions).
//
//   harness <каталог книги> <vn|standard> <nlb.json> <файл снимков> < ввод
//
// Собирается make-screens.sh на исходниках NLBNavigator.
#include "shunting-yard.h"
#include "builtin-features.inc"

#include <fstream>
#include <functional>
#include <iostream>
#include <map>
#include <memory>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

#include "nlb/api/DummyProgressData.h"
#include "nlb/api/PropertyManager.h"
#include "nlb/domain/NonLinearBookImpl.h"
#include "nlb/domain/PlayerEngine.h"
#include "nlb/presentation/ScreenModel.h"
// Движок консольного плеера — его закрытое поле, порции текста VN — закрытый метод экрана;
// эталону они нужны, чтобы снять снимок
#define private public
#include "nlb/domain/NLBExplorer.h"
#include "nlb/presentation/GameScreen.h"
#undef private

using namespace nlb::presentation;

namespace {
std::string str(const std::string& s) {
    std::string r = "\"";
    for (unsigned char c : s) {
        switch (c) {
            case '"': r += "\\\""; break;
            case '\\': r += "\\\\"; break;
            case '\n': r += "\\n"; break;
            case '\r': r += "\\r"; break;
            case '\t': r += "\\t"; break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    std::snprintf(buf, sizeof(buf), "\\u%04x", c);
                    r += buf;
                } else {
                    r += static_cast<char>(c);
                }
        }
    }
    return r + "\"";
}
std::string b(bool v) { return v ? "true" : "false"; }
std::string n(long long v) { return std::to_string(v); }
std::string opt(const std::optional<std::string>& v) { return v ? str(*v) : "null"; }

template <typename T, typename F>
std::string list(const std::vector<T>& items, F f) {
    std::string r = "[";
    for (size_t i = 0; i < items.size(); ++i) r += (i ? "," : "") + f(items[i]);
    return r + "]";
}

const char* anchor(Anchor a) {
    static const char* N[] = {"LeftTop", "MiddleTop", "RightTop", "LeftMiddle", "MiddleMiddle",
                              "RightMiddle", "LeftBottom", "MiddleBottom", "RightBottom"};
    return N[static_cast<int>(a)];
}
const char* effect(AppearEffect e) {
    static const char* N[] = {"None", "MoveIn", "MoveOut", "FadeIn", "FadeOut", "ZoomIn", "ZoomOut", "Overlap"};
    return N[static_cast<int>(e)];
}
const char* direction(MoveDirection d) {
    static const char* N[] = {"None", "Left", "Right", "Top", "Bottom"};
    return N[static_cast<int>(d)];
}
const char* runKind(TextRun::Kind k) {
    static const char* N[] = {"Plain", "ObjectLink", "ChoiceLink"};
    return N[static_cast<int>(k)];
}

std::string image(const ImageRef& i) {
    return "{\"file\":" + str(i.file.path) + ",\"framePattern\":" + str(i.framePattern) + ",\"frameCount\":" +
           n(i.frameCount) + ",\"currentFrame\":" + n(i.currentFrame) + ",\"looped\":" + b(i.looped) + "}";
}
std::string paragraph(const Paragraph& p) {
    return "{\"runs\":" + list(p.runs, [](const TextRun& r) {
        // картинка — только у метки объекта с картинкой (как toJson в Java)
        return "{\"kind\":" + str(runKind(r.kind)) + ",\"text\":" + str(r.text) + ",\"target\":" + str(r.target) +
               (r.image.empty() ? "" : ",\"image\":" + image(r.image)) + "}";
    }) + ",\"joinPrevious\":" + b(p.joinPrevious) + "}";
}
std::string paragraphs(const std::vector<Paragraph>& ps) { return list(ps, paragraph); }

std::string screen(const ScreenModel& m) {
    std::string r = "{";
    r += "\"layout\":" + str(m.layout == Layout::VisualNovel ? "VisualNovel" : "Standard");
    r += ",\"pageId\":" + str(m.pageId) + ",\"caption\":" + str(m.caption) + ",\"roomTitle\":" + str(m.roomTitle);
    r += ",\"pageImage\":" + image(m.pageImage) + ",\"imageIsBackground\":" + b(m.imageIsBackground) + ",\"forceDsc\":" + b(m.forceDsc);
    r += ",\"pageText\":" + paragraphs(m.pageText) + ",\"reactionText\":" + paragraphs(m.reactionText);
    r += ",\"roomText\":" + paragraphs(m.roomText) + ",\"objectsText\":" + paragraphs(m.objectsText);
    r += ",\"modText\":" + paragraphs(m.modText);
    r += ",\"choices\":" + list(m.choices, [](const Choice& c) {
        return "{\"index\":" + n(c.index) + ",\"text\":" + str(c.text) + ",\"enabled\":" + b(c.enabled) +
               ",\"trivial\":" + b(c.trivial) + ",\"inlineLink\":" + b(c.inlineLink) + ",\"altText\":" + str(c.altText) + "}";
    });
    r += ",\"sceneObjects\":" + list(m.sceneObjects, [](const SceneObject& o) {
        const Placement& p = o.placement;
        const Appearance& a = o.appearance;
        return "{\"id\":" + str(o.id) + ",\"parentId\":" + str(o.parentId) + ",\"label\":" + str(o.label) +
               ",\"graphical\":" + b(o.graphical) + ",\"image\":" + image(o.image) + ",\"hoverImage\":" + image(o.hoverImage) +
               ",\"placement\":{\"anchor\":" + str(anchor(p.anchor)) + ",\"hasOffset\":" + b(p.hasOffset) +
               ",\"offsetX\":" + n(p.offsetX) + ",\"offsetY\":" + n(p.offsetY) + ",\"armX\":" + n(p.armX) +
               ",\"armY\":" + n(p.armY) + ",\"hasInnerArm\":" + b(p.hasInnerArm) + ",\"innerArmX\":" + n(p.innerArmX) +
               ",\"innerArmY\":" + n(p.innerArmY) + "}" +
               ",\"appearance\":{\"effect\":" + str(effect(a.effect)) + ",\"direction\":" + str(direction(a.direction)) +
               ",\"maxStep\":" + n(a.maxStep) + ",\"startFrame\":" + n(a.startFrame) + ",\"curStep\":" + n(a.curStep) +
               ",\"pauseFrames\":" + n(a.pauseFrames) + ",\"preloadFrames\":" + n(a.preloadFrames) + "}" +
               ",\"zOrder\":" + n(o.zOrder) + ",\"visible\":" + b(o.visible) + ",\"collapsed\":" + b(o.collapsed) +
               ",\"active\":" + b(o.active) + ",\"takable\":" + b(o.takable) + ",\"preserved\":" + b(o.preserved) +
               ",\"showOnCursor\":" + b(o.showOnCursor) + ",\"clearUnderTooltip\":" + b(o.clearUnderTooltip) +
               ",\"actOnKey\":" + b(o.actOnKey) + ",\"loadOnce\":" + b(o.loadOnce) + ",\"cacheText\":" + b(o.cacheText) +
               ",\"noRedrawOnAct\":" + b(o.noRedrawOnAct) + "}";
    });
    r += ",\"inventory\":" + list(m.inventory, [](const InventoryItem& i) {
        return "{\"id\":" + str(i.id) + ",\"parentId\":" + str(i.parentId) + ",\"label\":" + str(i.label) +
               ",\"icon\":" + image(i.icon) + ",\"active\":" + b(i.active) + "}";
    });
    r += ",\"style\":{\"textGeometry\":" + opt(m.style.textGeometry) + ",\"inventoryGeometry\":" + opt(m.style.inventoryGeometry) +
         ",\"textColor\":" + opt(m.style.textColor) + ",\"inventoryColor\":" + opt(m.style.inventoryColor) + "}";
    r += ",\"timerActive\":" + b(m.timerActive) + ",\"timerIntervalMs\":" + n(m.timerIntervalMs);
    r += ",\"finished\":" + b(m.finished) + ",\"canSave\":" + b(m.canSave) + ",\"externalGame\":" + opt(m.externalGame);
    // Порции текста VN, как GameScreen::portions(): порции страницы, затем ответа
    auto portions = GameScreen::portionsOf(m.pageText);
    for (auto& part : GameScreen::portionsOf(m.reactionText)) portions.push_back(std::move(part));
    r += ",\"portions\":" + list(portions, [](const std::vector<Paragraph>& portion) { return paragraphs(portion); });
    return r + "}";
}
}

int main(int argc, char* argv[]) {
    if (argc < 5) {
        std::cerr << "Usage: harness <book> <vn|standard> <nlb.json> <screens.jsonl> < input" << std::endl;
        return 1;
    }
    cparse_startup();
    std::string error;
    if (!PropertyManager::loadConfig(argv[3], error)) {
        std::cerr << "Cannot read the settings: " << error << std::endl;
        return 1;
    }
    NonLinearBookImpl book;
    DummyProgressData progress;
    if (!book.load(argv[1], progress)) {
        std::cerr << "Cannot load book: " << argv[1] << std::endl;
        return 1;
    }
    PlayerEngine::Settings settings;
    settings.exportMode = std::string(argv[2]) == "standard" ? PlayerEngine::ExportMode::Standard : PlayerEngine::ExportMode::VN;
    settings.deterministic = true;
    std::ofstream screens(argv[4], std::ios::binary | std::ios::trunc);
    std::ostringstream console;  // вывод консоли эталону не нужен
    NLBExplorer explorer(&book, book.getStartPoint(), settings, std::cin, console);
    // Слушатель событий зовётся раз на шаг: после старта, каждой команды и внешней игры
    explorer.setEventListener([&](const std::vector<PlayerEngine::Event>&) {
        screens << screen(explorer.m_engine->screen()) << "\n";
    });
    explorer.explore();
    return 0;
}
