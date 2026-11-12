from __future__ import annotations

import argparse
import csv
import html
import json
import re
import time
from pathlib import Path
from typing import Iterable

import requests
import urllib3


urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


ROOT_DIR = Path(__file__).resolve().parents[1]
DEFAULT_OUT_DIR = ROOT_DIR / "data" / "public_site_crawls" / "spc_judicial_interpretations"
LIST_URL_TEMPLATE = "https://www.court.gov.cn/fabu/gengduo/16{suffix}.html"
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0 Safari/537.36"

LI_RE = re.compile(r"(?is)<li[^>]*>(.*?)</li>")
ARTICLE_LINK_RE = re.compile(r'(?is)href="(?P<href>(?:https://www\.court\.gov\.cn)?/fabu/xiangqing/\d+\.html)"[^>]*>(?P<title>.*?)</a>')
DATE_RE = re.compile(r"(?P<date>\d{4}-\d{2}-\d{2})")
SCRIPT_RE = re.compile(r"(?is)<script.*?</script>|<style.*?</style>|<noscript.*?</noscript>")
COMMENT_RE = re.compile(r"(?is)<!--.*?-->")
TAG_RE = re.compile(r"(?is)<[^>]+>")
BLOCK_RE = re.compile(r"(?i)</(p|div|li|tr|td|h1|h2|h3|h4|section|article|ul|ol|table)>|<br\s*/?>")

STOP_MARKERS = (
    "中国共产党新闻网",
    "中国政府网",
    "中国人大网",
    "中国政协网",
    "中国法院网",
    "最高人民检察院",
    "全国人民法庭信息网",
    "人民法院诉讼资产网",
    "中华人民共和国最高人民法院 版权所有",
    "京公网安备",
    "京ICP备",
    "地址：北京市东城区东交民巷27号",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Scrape SPC judicial-interpretation list and detail pages.")
    parser.add_argument("--page-from", type=int, default=1, help="start page number, default 1")
    parser.add_argument("--page-to", type=int, default=5, help="end page number, default 5")
    parser.add_argument("--max-items", type=int, default=0, help="optional hard cap, 0 means no cap")
    parser.add_argument("--out-dir", default=str(DEFAULT_OUT_DIR), help="output directory")
    return parser.parse_args()


def fetch_text(url: str) -> str:
    last_error: Exception | None = None
    for verify_ssl in (True, False):
        for _ in range(3):
            try:
                response = requests.get(
                    url,
                    headers={"User-Agent": USER_AGENT},
                    timeout=30,
                    verify=verify_ssl,
                )
                response.raise_for_status()
                response.encoding = response.apparent_encoding or response.encoding or "utf-8"
                return response.text
            except requests.RequestException as exc:
                last_error = exc
                time.sleep(1)
    if last_error is not None:
        raise last_error
    raise RuntimeError(f"failed to fetch {url}")


def page_url(page_no: int) -> str:
    suffix = "" if page_no == 1 else f"_{page_no}"
    return LIST_URL_TEMPLATE.format(suffix=suffix)


def normalize_text(value: str) -> str:
    text = html.unescape(value or "")
    text = TAG_RE.sub(" ", text)
    text = text.replace("\xa0", " ").replace("\u3000", " ")
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def html_to_lines(html_text: str) -> list[str]:
    text = COMMENT_RE.sub(" ", html_text)
    text = SCRIPT_RE.sub(" ", text)
    text = BLOCK_RE.sub("\n", text)
    text = TAG_RE.sub(" ", text)
    text = html.unescape(text)
    text = text.replace("\r", "\n").replace("\xa0", " ").replace("\u3000", " ")
    text = re.sub(r"[ \t\f\v]+", " ", text)
    text = re.sub(r"\n{2,}", "\n", text)
    return [re.sub(r"\s+", " ", line).strip() for line in text.split("\n") if line.strip()]


def parse_list_page(html_text: str) -> list[dict]:
    items: list[dict] = []
    seen = set()
    for li_match in LI_RE.finditer(html_text):
        li_html = li_match.group(1)
        link_match = ARTICLE_LINK_RE.search(li_html)
        if not link_match:
            continue
        href = link_match.group("href")
        title = normalize_text(link_match.group("title"))
        if not href or not title:
            continue
        if href.startswith("/"):
            href = "https://www.court.gov.cn" + href
        date_match = DATE_RE.search(normalize_text(li_html))
        publish_date = date_match.group("date") if date_match else ""
        if href in seen:
            continue
        seen.add(href)
        items.append({"title": title, "url": href, "publish_date": publish_date})
    return items


def parse_detail_page(url: str, html_text: str, fallback_title: str, fallback_date: str) -> dict:
    lines = html_to_lines(html_text)
    title = fallback_title
    title_idx = next((idx for idx, line in enumerate(lines) if fallback_title in line), None)
    if title_idx is not None:
        title = lines[title_idx]
    else:
        candidate = next((line for line in lines if len(line) >= 8 and "最高人民法院" in line), "")
        if candidate:
            title = candidate

    source = ""
    publish_time = fallback_date
    for idx, line in enumerate(lines[:30]):
        if line.startswith("来源："):
            source = line.replace("来源：", "", 1).strip()
        if line.startswith("发布时间："):
            publish_time = line.replace("发布时间：", "", 1).strip()
            break
        if "发布时间：" in line:
            publish_time = line.split("发布时间：", 1)[1].strip()
            if idx > 0 and lines[idx - 1].startswith("来源："):
                source = lines[idx - 1].replace("来源：", "", 1).strip()
            break

    start_idx = title_idx + 1 if title_idx is not None else 0
    body_lines: list[str] = []
    for line in lines[start_idx:]:
        if any(marker in line for marker in STOP_MARKERS):
            break
        if line in {title, "字号： 小 大", "打印本页"}:
            continue
        if line.startswith("来源：") or line.startswith("发布时间："):
            continue
        body_lines.append(line)

    body_text = "\n".join(body_lines).strip()
    summary = next((line for line in body_lines if len(line) >= 20), body_lines[0] if body_lines else "")
    return {
        "title": title,
        "url": url,
        "publish_time": publish_time,
        "source": source,
        "summary": summary,
        "content": body_text,
    }


def ensure_dirs(out_dir: Path) -> tuple[Path, Path]:
    raw_dir = out_dir / "raw_html"
    json_dir = out_dir / "json"
    raw_dir.mkdir(parents=True, exist_ok=True)
    json_dir.mkdir(parents=True, exist_ok=True)
    return raw_dir, json_dir


def write_index_csv(rows: Iterable[dict], path: Path) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as fp:
        writer = csv.writer(fp)
        writer.writerow(["title", "publish_time", "source", "url", "json_path"])
        for row in rows:
            writer.writerow([row["title"], row["publish_time"], row["source"], row["url"], row["json_path"]])


def main() -> int:
    args = parse_args()
    if args.page_from < 1 or args.page_to < args.page_from:
        raise ValueError("invalid page range")

    out_dir = Path(args.out_dir).expanduser().resolve()
    raw_dir, json_dir = ensure_dirs(out_dir)

    index_rows: list[dict] = []
    fetched = 0
    failed_pages: list[dict] = []

    for page_no in range(args.page_from, args.page_to + 1):
        try:
            html_text = fetch_text(page_url(page_no))
        except requests.RequestException as exc:
            failed_pages.append({"page": page_no, "url": page_url(page_no), "error": str(exc)})
            continue

        (raw_dir / f"list_{page_no}.html").write_text(html_text, encoding="utf-8")
        items = parse_list_page(html_text)
        if not items:
            continue

        for item in items:
            if args.max_items and fetched >= args.max_items:
                break
            try:
                detail_html = fetch_text(item["url"])
            except requests.RequestException as exc:
                failed_pages.append({"page": page_no, "url": item["url"], "error": str(exc)})
                continue
            article_id_match = re.search(r"/(\d+)\.html", item["url"])
            article_id = article_id_match.group(1) if article_id_match else f"page{page_no}_{fetched+1}"
            (raw_dir / f"{article_id}.html").write_text(detail_html, encoding="utf-8")
            record = parse_detail_page(item["url"], detail_html, item["title"], item["publish_date"])
            json_path = json_dir / f"{article_id}.json"
            json_path.write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
            index_rows.append({**record, "json_path": str(json_path)})
            fetched += 1
        if args.max_items and fetched >= args.max_items:
            break

    write_index_csv(index_rows, out_dir / "index.csv")
    summary = {
        "pages": [args.page_from, args.page_to],
        "fetched_count": fetched,
        "out_dir": str(out_dir),
        "failed_count": len(failed_pages),
    }
    (out_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    (out_dir / "failed_pages.json").write_text(json.dumps(failed_pages, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
