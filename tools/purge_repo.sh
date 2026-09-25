#!/usr/bin/env bash
# ---------------------------------------------------------------
# purge_repo.sh —— 独立工具：只负责「列出 / 删除」GitHub 仓库里的文件
#
# 单独拆出来是为了排查：push_to_github.sh 的 PURGE 模式如果没生效，
# 用这个脚本单独跑一遍，能看到每一步的真实结果。
#
# 用法（Termux）：
#   export GITHUB_OWNER=你的用户名
#   export GITHUB_TOKEN=你的token
#
#   bash tools/purge_repo.sh            # 只列出仓库里有什么（不删）
#   DELETE=1 bash tools/purge_repo.sh   # 删除工程相关文件（src/ .github/ *.gradle）
#   DELETE=1 SCOPE=all bash tools/purge_repo.sh   # 删除所有文件
# ---------------------------------------------------------------
set -uo pipefail

TOKEN="${GITHUB_TOKEN:-}"
OWNER="${GITHUB_OWNER:-}"
REPO="${GITHUB_REPO:-clientmod-fabric}"
BRANCH="${GITHUB_BRANCH:-}"
DELETE="${DELETE:-0}"
SCOPE="${SCOPE:-project}"
API="https://api.github.com"

if [ -z "$TOKEN" ] || [ -z "$OWNER" ]; then
  echo "错误：请先 export GITHUB_TOKEN 和 GITHUB_OWNER"
  exit 1
fi
if ! command -v curl > /dev/null 2>&1; then
  echo "错误：找不到 curl，请先 pkg install curl"
  exit 1
fi

WORKDIR="$HOME/.ghpurge_tmp.$$"
mkdir -p "$WORKDIR" || { echo "错误：无法创建临时目录"; exit 1; }
trap 'rm -rf "$WORKDIR"' EXIT

AUTH=(-H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28")

echo "仓库：$OWNER/$REPO"
echo

# ---- 1. 确认能访问仓库，并拿到默认分支 ----
code="$(curl -s --connect-timeout 15 --max-time 60 -o "$WORKDIR/info.json" -w '%{http_code}' \
  "${AUTH[@]}" "$API/repos/$OWNER/$REPO")"
if [ "$code" != "200" ]; then
  echo "错误：访问不到仓库（HTTP $code）"
  echo "  检查：仓库名拼写？token 是否勾选 repo 权限？token 是否过期？"
  head -c 300 "$WORKDIR/info.json"; echo
  exit 1
fi
if [ -z "$BRANCH" ]; then
  BRANCH="$(tr -d ' \n' < "$WORKDIR/info.json" | grep -o '"default_branch":"[^"]*"' | head -1 | cut -d'"' -f4)"
  BRANCH="${BRANCH:-main}"
fi
echo "默认分支：$BRANCH"
echo

# ---- 2. 拉取文件树 ----
code="$(curl -s --connect-timeout 15 --max-time 120 -o "$WORKDIR/tree.json" -w '%{http_code}' \
  "${AUTH[@]}" "$API/repos/$OWNER/$REPO/git/trees/$BRANCH?recursive=1")"
if [ "$code" != "200" ]; then
  echo "错误：读取文件树失败（HTTP $code）"
  head -c 300 "$WORKDIR/tree.json"; echo
  exit 1
fi

if ! grep -q '"tree"' "$WORKDIR/tree.json"; then
  echo "提示：仓库可能是空的（没有文件树）"
  exit 0
fi

# 解析出所有 blob 路径
tr '{' '\n' < "$WORKDIR/tree.json" | grep '"type"[ ]*:[ ]*"blob"' > "$WORKDIR/blobs.txt"
total="$(wc -l < "$WORKDIR/blobs.txt" | tr -d ' ')"
echo "仓库里共 $total 个文件"
echo

if [ "$DELETE" != "1" ]; then
  echo "（只读模式，列出文件。要删除请加 DELETE=1）"
  echo
fi

listed=0
deleted=0
failed=0

while IFS= read -r line; do
  rpath="$(printf '%s' "$line" | grep -o '"path"[ ]*:[ ]*"[^"]*"' | head -1 | cut -d'"' -f4)"
  rsha="$(printf '%s' "$line" | grep -o '"sha"[ ]*:[ ]*"[^"]*"' | head -1 | cut -d'"' -f4)"
  [ -z "$rpath" ] && continue
  [ -z "$rsha" ] && continue

  listed=$((listed + 1))

  # 范围过滤
  if [ "$SCOPE" = "project" ]; then
    keep=0
    for prefix in src/ build.gradle settings.gradle gradle.properties gradle/ .github/; do
      case "$rpath" in
        "$prefix"*|"$prefix") keep=1; break ;;
      esac
    done
    if [ "$keep" = "0" ]; then
      [ "$DELETE" != "1" ] && echo "  [保留] $rpath"
      continue
    fi
  fi

  if [ "$DELETE" != "1" ]; then
    echo "  [可删] $rpath"
    continue
  fi

  printf '{"message":"Purge %s","sha":"%s","branch":"%s"}' "$rpath" "$rsha" "$BRANCH" \
    > "$WORKDIR/del.json"

  dcode=""
  for attempt in 1 2 3; do
    dcode="$(curl -s --connect-timeout 15 --max-time 60 -o "$WORKDIR/del_resp.json" -w '%{http_code}' \
      -X DELETE "$API/repos/$OWNER/$REPO/contents/$rpath" "${AUTH[@]}" -d @"$WORKDIR/del.json")"
    [ "$dcode" = "200" ] && break
    sleep $((attempt * 2))
  done

  if [ "$dcode" = "200" ]; then
    echo "  已删除：$rpath"
    deleted=$((deleted + 1))
  else
    echo "  ✗ 失败：$rpath  (HTTP $dcode)"
    head -c 200 "$WORKDIR/del_resp.json"; echo
    failed=$((failed + 1))
  fi
done < "$WORKDIR/blobs.txt"

echo
if [ "$DELETE" = "1" ]; then
  echo "扫描 $listed 个，删除 $deleted 个，失败 $failed 个"
  if [ "$failed" -gt 0 ]; then
    echo
    echo "删除失败的常见原因："
    echo "  - token 权限不足：classic token 需勾 repo；fine-grained 需 Contents=Read and write"
    echo "  - 仓库开了分支保护，禁止直接删文件"
    echo "  - 网络中断（已重试 3 次仍失败）"
  fi
else
  echo "共 $listed 个文件。确认无误后加 DELETE=1 执行删除。"
fi
