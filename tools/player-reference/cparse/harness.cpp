// Вычисляет выражения так же, как ModificationInterpreter::evaluate в NLBNavigator,
// и печатает тип и значение: для сверки Java-вычислителя с cparse
#include "shunting-yard.h"
#include <cctype>
#include <cmath>
#include <cstdio>
#include <iostream>
#include <string>
#include <vector>
using namespace cparse;
static bool isIdentStart(char c) { return std::isalpha((unsigned char)c) || c == '_'; }
static bool isIdentChar(char c) { return std::isalnum((unsigned char)c) || c == '_'; }
static std::string translateExpression(const std::string& expr, std::vector<std::string>& idents) {
    std::string out; size_t i = 0; const size_t n = expr.size();
    while (i < n) {
        const char c = expr[i];
        if (c == '"' || c == '\'') { const size_t start = i++; while (i < n && expr[i] != c) { if (expr[i] == '\\' && i + 1 < n) ++i; ++i; } if (i < n) ++i; out += expr.substr(start, i - start); }
        else if (c == '.' && i + 1 < n && expr[i + 1] == '.') { out += '+'; i += 2; }
        else if (c == '~' && i + 1 < n && expr[i + 1] == '=') { out += "!="; i += 2; }
        else if (isIdentStart(c)) {
            const size_t start = i; while (i < n && isIdentChar(expr[i])) ++i;
            const std::string word = expr.substr(start, i - start);
            size_t k = i; while (k < n && std::isspace((unsigned char)expr[k])) ++k;
            const bool isCall = k < n && expr[k] == '(';
            if (word == "and") out += "&&"; else if (word == "or") out += "||"; else if (word == "not") out += "!"; else if (word == "nil") out += "None";
            else { if (!isCall && word != "true" && word != "false" && word != "None") idents.push_back(word); out += word; }
        } else { out += c; ++i; }
    }
    return out;
}
static std::string display(const packToken& v) {
    switch (v->type) {
        case NONE: return "NONE:";
        case BOOL: return std::string("BOOL:") + (v.asBool() ? "true" : "false");
        case INT: return "INT:" + std::to_string(v.asInt());
        case REAL: { const double d = v.asDouble(); char buf[64];
            if (std::isfinite(d) && d == std::floor(d) && std::fabs(d) < 1e15) return "REAL:" + std::to_string((long long)d);
            std::snprintf(buf, sizeof(buf), "%.14g", d); return std::string("REAL:") + buf; }
        case STR: return "STR:" + v.asString();
        default: return "OTHER:" + v.str();
    }
}
int main() {
    TokenMap global(&TokenMap::default_global());
    global["true"] = true; global["false"] = false;
    global["n5"] = 5; global["r25"] = 2.5; global["s"] = std::string("abc"); global["e"] = std::string("");
    global["b"] = true; global["z"] = 0;
    std::string line;
    while (std::getline(std::cin, line)) {
        std::vector<std::string> idents;
        const std::string t = translateExpression(line, idents);
        bool blank = true; for (char c : t) if (!std::isspace((unsigned char)c)) { blank = false; break; }
        if (blank) { std::cout << "BOOL:false\n"; continue; }
        TokenMap scope = global.getChild();
        for (const auto& name : idents) if (!global.find(name)) scope[name] = false;
        try { std::cout << display(calculator::calculate(t.c_str(), scope)) << "\n"; }
        catch (const std::exception&) { std::cout << "ERROR\n"; }
    }
}
