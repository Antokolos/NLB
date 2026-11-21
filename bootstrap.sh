#!/bin/sh
# Сборка NLB на новой машине без Java и Maven (Linux, macOS).
#
#   ./bootstrap.sh                 — скачать JDK 21 и Maven (если их ещё нет) и собрать дистрибутив (build.sh)
#   ./bootstrap.sh mvn test        — то же окружение для любой команды (здесь — тесты)
#
# JDK — Amazon Corretto 21 (бесплатная сборка OpenJDK, последняя версия 21 с обновлениями
# безопасности), Maven — 3.9.9. Оба ставятся в .tools/ внутри репозитория: без прав
# администратора, система не меняется; удалить — rm -rf .tools. Загрузки сверяются с контрольными
# суммами: JDK — SHA-256 с сайта Corretto, Maven — SHA-512, записанной здесь.
# Нужны curl (или wget), tar и sha256sum/shasum — они есть в любом Linux и macOS.
set -e

MAVEN_VERSION=3.9.9
MAVEN_SHA512=a555254d6b53d267965a3404ecb14e53c3827c09c3b94b5678835887ab404556bfaf78dcfe03ba76fa2508649dca8531c74bca4d5846513522404d48e8c4ac8b
MAVEN_URL=https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz

ROOT=$(cd "$(dirname "$0")" && pwd)
TOOLS=$ROOT/.tools

case "$(uname -s)" in
    Linux) OS=linux ;;
    Darwin) OS=macos ;;
    *) echo "bootstrap.sh: эта ОС не поддерживается ($(uname -s)); на Windows — bootstrap.bat" >&2; exit 1 ;;
esac
case "$(uname -m)" in
    x86_64|amd64) ARCH=x64 ;;
    aarch64|arm64) ARCH=aarch64 ;;
    *) echo "bootstrap.sh: этот процессор не поддерживается ($(uname -m))" >&2; exit 1 ;;
esac
JDK_FILE=amazon-corretto-21-$ARCH-$OS-jdk.tar.gz
JDK_URL=https://corretto.aws/downloads/latest/$JDK_FILE
JDK_SHA256_URL=https://corretto.aws/downloads/latest_sha256/$JDK_FILE

download() {  # адрес файл
    if command -v curl >/dev/null 2>&1; then
        curl -fL --retry 3 --progress-bar -o "$2" "$1"
    else
        wget -O "$2" "$1"
    fi
}

fetch() {  # адрес (вывод — в stdout)
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL --retry 3 "$1"
    else
        wget -qO- "$1"
    fi
}

check() {  # файл ожидаемая-сумма алгоритм(256|512)
    if command -v sha${3}sum >/dev/null 2>&1; then
        actual=$(sha${3}sum "$1" | cut -d' ' -f1)
    else
        actual=$(shasum -a "$3" "$1" | cut -d' ' -f1)
    fi
    if [ "$actual" != "$2" ]; then
        echo "bootstrap.sh: контрольная сумма $1 не совпала (SHA-$3: ожидалась $2, получена $actual)" >&2
        rm -f "$1"
        exit 1
    fi
}

# архив -> каталог: содержимое единственной папки архива (у JDK для macOS — её Contents/Home)
unpack() {  # архив каталог
    tmp="$2.part"
    rm -rf "$tmp" && mkdir -p "$tmp"
    tar -xzf "$1" -C "$tmp"
    top=$(find "$tmp" -mindepth 1 -maxdepth 1 -type d | head -n 1)
    if [ -d "$top/Contents/Home" ]; then
        top="$top/Contents/Home"
    fi
    rm -rf "$2"
    mv "$top" "$2"
    rm -rf "$tmp"
}

mkdir -p "$TOOLS"
if [ ! -x "$TOOLS/jdk/bin/java" ]; then
    echo "Скачиваю JDK 21 (Amazon Corretto, $OS $ARCH)..."
    sha=$(fetch "$JDK_SHA256_URL" | tr -d ' \r\n')
    download "$JDK_URL" "$TOOLS/jdk.tar.gz"
    check "$TOOLS/jdk.tar.gz" "$sha" 256
    unpack "$TOOLS/jdk.tar.gz" "$TOOLS/jdk"
    rm -f "$TOOLS/jdk.tar.gz"
fi
if [ ! -x "$TOOLS/maven/bin/mvn" ]; then
    echo "Скачиваю Maven $MAVEN_VERSION..."
    download "$MAVEN_URL" "$TOOLS/maven.tar.gz"
    check "$TOOLS/maven.tar.gz" "$MAVEN_SHA512" 512
    unpack "$TOOLS/maven.tar.gz" "$TOOLS/maven"
    rm -f "$TOOLS/maven.tar.gz"
fi

JAVA_HOME=$TOOLS/jdk
PATH=$JAVA_HOME/bin:$TOOLS/maven/bin:$PATH
export JAVA_HOME PATH
cd "$ROOT"
if [ $# -eq 0 ]; then
    set -- sh ./build.sh
fi
exec "$@"
