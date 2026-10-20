#!/usr/bin/env bash
# miyamizu-for-android: download native dependency sources into libs/.
#
# The old script skipped dependencies whenever libs/<name> existed. The repository
# contains placeholder directories, so it skipped soxr even though libs/soxr had
# no CMakeLists.txt. This version validates actual source files and fetches missing
# sources. It preserves the repository's small CMake wrapper files for FLAC, Ogg,
# and r8brain, while soxr uses its upstream top-level CMakeLists.txt.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
LIBS_DIR="${REPO_ROOT}/libs"
mkdir -p "${LIBS_DIR}"

fetch_repo() {
    local name="$1" url="$2" ref="$3" mode="$4"
    shift 4
    local dest="${LIBS_DIR}/${name}"
    local ok=1
    for marker in "$@"; do
        [[ -f "${dest}/${marker}" ]] || ok=0
    done
    if [[ ${ok} -eq 1 ]]; then
        echo "[skip] ${name}: source files already present"
        return
    fi

    local tmp
    tmp="$(mktemp -d)"
    trap 'rm -rf "${tmp}"' RETURN
    echo "[fetch] ${name} (${ref})"
    git clone --quiet --depth 1 --branch "${ref}" "${url}" "${tmp}/${name}"

    if [[ "${mode}" == "replace" ]]; then
        # soxr needs the upstream top-level CMakeLists.txt; the existing tracked
        # CMakeLists.txt.android is only a note/template and cannot be used here.
        rm -rf "${dest}"
        mv "${tmp}/${name}" "${dest}"
    else
        # Keep our Android-specific wrapper CMakeLists.txt, but replace stale or
        # placeholder source files with the upstream source tree.
        local wrapper=""
        if [[ -f "${dest}/CMakeLists.txt" ]]; then
            wrapper="${tmp}/CMakeLists.android.wrapper"
            cp "${dest}/CMakeLists.txt" "${wrapper}"
        fi
        rm -rf "${dest}"
        mkdir -p "${dest}"
        cp -a "${tmp}/${name}/." "${dest}/"
        rm -rf "${dest}/.git"
        if [[ -n "${wrapper}" ]]; then
            cp "${wrapper}" "${dest}/CMakeLists.txt"
        fi
    fi

    for marker in "$@"; do
        if [[ ! -f "${dest}/${marker}" ]]; then
            echo "ERROR: ${name} download is incomplete (missing ${marker})" >&2
            exit 1
        fi
    done
    echo "[ok] ${name}"
    rm -rf "${tmp}"
    trap - RETURN
}

# soxr has a full upstream CMake project; replace the incomplete placeholder folder.
fetch_repo soxr "https://github.com/chirlu/soxr.git" "0.1.3" replace \
    "CMakeLists.txt" "src/soxr.c" "src/vr32.c"

# These retain the Android wrapper CMakeLists.txt tracked in this repository.
fetch_repo flac "https://github.com/xiph/flac.git" "1.4.3" preserve \
    "src/libFLAC/stream_decoder.c" "include/FLAC/stream_decoder.h"
fetch_repo ogg "https://github.com/xiph/ogg.git" "v1.3.5" preserve \
    "src/bitwise.c" "src/framing.c"
fetch_repo r8brain-free-src "https://github.com/avaneev/r8brain-free-src.git" "master" preserve \
    "CDSPResampler.h" "r8bbase.h"

echo "=== native libraries ready ==="
