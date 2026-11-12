from __future__ import annotations

import argparse
import csv
import json
import re
import shutil
import subprocess
import time
from pathlib import Path
from urllib.request import urlopen

import websocket


ROOT_DIR = Path(__file__).resolve().parents[1]
DEFAULT_OUT_DIR = ROOT_DIR / "data" / "public_site_crawls" / "rmfy_case_library"
DEFAULT_START_URL = "https://rmfyalk.court.gov.cn/"
DEFAULT_EDGE_PATHS = (
    Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"),
    Path(r"C:\Program Files\Microsoft\Edge\Application\msedge.exe"),
)

TEXT_CASE_LIBRARY = "\u4eba\u6c11\u6cd5\u9662\u6848\u4f8b\u5e93"
TEXT_RESULT_LIST = "\u6587\u4e66\u5217\u8868"
TEXT_NEXT_PAGE = "\u4e0b\u4e00\u9875"

VERIFY_SELECTORS = (".geetest_panel", ".nc_wrapper", "#nc_1_wrapper", ".captcha")
STOP_MARKERS = (
    "\u7248\u6743\u6240\u6709",
    "\u4e2d\u534e\u4eba\u6c11\u5171\u548c\u56fd\u6700\u9ad8\u4eba\u6c11\u6cd5\u9662",
    "\u4e2d\u56fd\u6cd5\u9662\u7f51",
    "\u4eacICP",
    "\u4eac\u516c\u7f51\u5b89\u5907",
)


class CDPConnection:
    def __init__(self, ws_url: str):
        self.ws = websocket.create_connection(ws_url, timeout=30, suppress_origin=True)
        self.next_id = 0

    def close(self) -> None:
        try:
            self.ws.close()
        except Exception:
            pass

    def call(self, method: str, params: dict | None = None) -> dict:
        self.next_id += 1
        self.ws.send(json.dumps({"id": self.next_id, "method": method, "params": params or {}}))
        while True:
            payload = json.loads(self.ws.recv())
            if payload.get("id") != self.next_id:
                continue
            if "error" in payload:
                raise RuntimeError(f"{method} failed: {payload['error']}")
            return payload.get("result", {})


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Scrape People's Court Case Library via Edge CDP.")
    parser.add_argument("--target", type=int, default=50, help="max case count to scrape")
    parser.add_argument("--start-url", default=DEFAULT_START_URL, help="page to open first if launching Edge")
    parser.add_argument("--save-dir", default=str(DEFAULT_OUT_DIR), help="output directory")
    parser.add_argument("--remote-debugging-port", type=int, default=9666, help="Edge CDP port")
    parser.add_argument("--edge-path", default="", help="custom Edge executable path")
    parser.add_argument("--user-data-dir", default="", help="custom Edge profile dir")
    parser.add_argument("--attach-only", action="store_true", help="attach to an existing Edge CDP port")
    parser.add_argument("--keep-profile", action="store_true", help="keep profile dir after exit")
    return parser.parse_args()


def discover_edge_path(custom_path: str) -> Path:
    if custom_path:
        path = Path(custom_path).expanduser().resolve()
        if path.exists():
            return path
        raise FileNotFoundError(f"Edge executable not found: {path}")
    for candidate in DEFAULT_EDGE_PATHS:
        if candidate.exists():
            return candidate
    raise FileNotFoundError("Microsoft Edge was not found. Use --edge-path to specify it.")


def choose_profile_dir(save_dir: Path, custom_profile: str) -> Path:
    if custom_profile:
        return Path(custom_profile).expanduser().resolve()
    return (save_dir / ".edge-profile").resolve()


def http_get_json(url: str) -> dict | list:
    with urlopen(url, timeout=10) as response:
        return json.loads(response.read().decode("utf-8"))


def wait_for_cdp(port: int, timeout: int = 30) -> dict:
    deadline = time.time() + timeout
    last_error = None
    while time.time() < deadline:
        try:
            return http_get_json(f"http://127.0.0.1:{port}/json/version")
        except Exception as exc:
            last_error = exc
            time.sleep(1)
    raise RuntimeError(f"CDP port {port} did not become ready: {last_error}")


def list_targets(port: int) -> list[dict]:
    data = http_get_json(f"http://127.0.0.1:{port}/json/list")
    if not isinstance(data, list):
        raise RuntimeError("Unexpected CDP target list response")
    return data


def score_target(target: dict, start_url: str) -> int:
    if target.get("type") != "page" or not target.get("webSocketDebuggerUrl"):
        return -10_000

    title = str(target.get("title", ""))
    url = str(target.get("url", ""))
    score = 0

    if "rmfyalk.court.gov.cn" in url:
        score += 200
    if "/view/list" in url:
        score += 320
    if "/view/" in url and "/view/content" not in url:
        score += 120
    if "/view/content" in url:
        score -= 180
    if url != DEFAULT_START_URL and url.rstrip("/") != DEFAULT_START_URL.rstrip("/"):
        score += 90
    if TEXT_RESULT_LIST in title:
        score += 260
    if TEXT_CASE_LIBRARY in title:
        score += 120
    if start_url and start_url != DEFAULT_START_URL and start_url in url:
        score += 150
    if url == DEFAULT_START_URL:
        score -= 80

    return score


def select_page_target(port: int, start_url: str) -> dict:
    best_target = None
    best_score = -10_000
    for target in list_targets(port):
        score = score_target(target, start_url)
        if score > best_score:
            best_score = score
            best_target = target
    if best_target is None:
        raise RuntimeError("No suitable page target found")
    return best_target


def launch_edge(edge_path: Path, start_url: str, port: int, profile_dir: Path) -> subprocess.Popen[str]:
    profile_dir.mkdir(parents=True, exist_ok=True)
    command = [
        str(edge_path),
        f"--remote-debugging-port={port}",
        "--remote-allow-origins=*",
        f"--user-data-dir={profile_dir}",
        "--new-window",
        start_url,
    ]
    creationflags = getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0)
    return subprocess.Popen(command, creationflags=creationflags)


def runtime_eval(page: CDPConnection, expression: str):
    result = page.call(
        "Runtime.evaluate",
        {"expression": expression, "returnByValue": True, "awaitPromise": True},
    )
    if "exceptionDetails" in result:
        raise RuntimeError(f"JavaScript execution failed: {result['exceptionDetails']}")
    return result.get("result", {}).get("value")


def wait_for_ready(page: CDPConnection, timeout: int = 60) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            if runtime_eval(page, "document.readyState") in ("interactive", "complete"):
                return
        except Exception:
            pass
        time.sleep(1)
    raise RuntimeError("Timed out waiting for page readyState")


def page_has_any_selector(page: CDPConnection) -> bool:
    expression = f"""
(() => {{
  const selectors = {json.dumps(list(VERIFY_SELECTORS), ensure_ascii=False)};
  return selectors.some((selector) => {{
    const el = document.querySelector(selector);
    if (!el) return false;
    const style = window.getComputedStyle(el);
    return style && style.display !== 'none' && style.visibility !== 'hidden';
  }});
}})()
"""
    return bool(runtime_eval(page, expression))


def attach_selected_page(port: int, start_url: str) -> tuple[dict, CDPConnection]:
    selected_target = select_page_target(port, start_url)
    page = CDPConnection(str(selected_target["webSocketDebuggerUrl"]))
    page.call("Page.enable")
    page.call("Runtime.enable")
    wait_for_ready(page)
    try:
        page.call("Page.bringToFront")
    except Exception:
        pass
    return selected_target, page


def wait_for_manual_ready() -> None:
    print("请在 Edge 中打开人民法院案例库的目标检索结果页，完成搜索/筛选后回终端按 Enter。")
    input()


def is_result_list_page(page: CDPConnection) -> bool:
    expression = """
(() => {
  const href = String(window.location.href || '');
  if (href.includes('/view/list')) return true;
  const list = document.querySelector('.al-list');
  if (!list) return false;
  if (list.querySelector('a[href*="content.html"]')) return true;
  return !!list.querySelector('li');
})()
"""
    return bool(runtime_eval(page, expression))


def list_page_debug(page: CDPConnection) -> dict:
    expression = """
(() => {
  const list = document.querySelector('.al-list');
  const title = String(document.title || '').trim();
  const url = String(window.location.href || '').trim();
  const cardTexts = list
    ? Array.from(list.querySelectorAll('li')).slice(0, 3).map((node) =>
        String(node.innerText || node.textContent || '').replace(/\\s+/g, ' ').trim()
      ).filter(Boolean)
    : [];
  const linkCount = list ? list.querySelectorAll('a[href*="content.html"]').length : 0;
  return { title, url, linkCount, cardTexts };
})()
"""
    value = runtime_eval(page, expression) or {}
    return dict(value)


def collect_visible_case_links(page: CDPConnection) -> list[dict]:
    expression = """
(() => {
  const seen = new Set();
  const rows = [];

  function norm(value) {
    return String(value || '').replace(/\\u00A0/g, ' ').replace(/\\s+/g, ' ').trim();
  }

  function visible(el) {
    if (!el) return false;
    const rect = el.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return false;
    const style = window.getComputedStyle(el);
    if (!style) return false;
    if (style.display === 'none' || style.visibility === 'hidden') return false;
    return true;
  }

  const anchors = Array.from(document.querySelectorAll('.al-list a[href]'));
  for (const anchor of anchors) {
    if (!visible(anchor)) continue;
    const href = new URL(anchor.getAttribute('href') || '', window.location.href).href;
    const title = norm(anchor.innerText || anchor.textContent);
    if (!href.includes('rmfyalk.court.gov.cn')) continue;
    if (!(href.includes('/view/content') || href.includes('content.html?id='))) continue;
    if (!title || title.length < 6 || title.length > 160) continue;
    if (seen.has(href)) continue;
    seen.add(href);
    rows.push({ title, url: href });
  }
  return rows;
})()
"""
    return list(runtime_eval(page, expression) or [])


def click_next_page(page: CDPConnection) -> bool:
    expression = f"""
(async () => {{
  const target = {json.dumps(TEXT_NEXT_PAGE, ensure_ascii=False)};

  function norm(value) {{
    return String(value || '').replace(/\\u00A0/g, ' ').replace(/\\s+/g, ' ').trim();
  }}

  function visible(el) {{
    if (!el) return false;
    const rect = el.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return false;
    const style = window.getComputedStyle(el);
    if (!style) return false;
    return style.display !== 'none' && style.visibility !== 'hidden';
  }}

  window.scrollTo(0, document.body.scrollHeight);
  await new Promise((resolve) => setTimeout(resolve, 800));

  const nodes = Array.from(document.querySelectorAll('a, button, span, div, li'));
  for (const node of nodes) {{
    if (!visible(node)) continue;
    const text = norm(node.innerText || node.textContent);
    const title = norm(node.getAttribute && node.getAttribute('title'));
    const cls = String(node.className || '').toLowerCase();
    if (text.includes(target) || title.includes(target) || cls.includes('next')) {{
      node.scrollIntoView({{ block: 'center' }});
      node.click();
      return true;
    }}
  }}
  return false;
}})()
"""
    return bool(runtime_eval(page, expression))


def fetch_detail_record(page: CDPConnection, url: str, fallback_title: str) -> dict:
    page.call("Page.navigate", {"url": url})
    wait_for_ready(page)
    html_text = str(runtime_eval(page, "document.documentElement.outerHTML") or "")
    raw_lines = (
        re.sub(r"(?is)<script.*?</script>|<style.*?</style>|<noscript.*?</noscript>", " ", html_text)
        .replace("\r", "\n")
        .replace("&nbsp;", " ")
        .replace("\xa0", " ")
        .split("\n")
    )

    text_lines: list[str] = []
    for line in raw_lines:
        text = re.sub(r"(?is)<[^>]+>", " ", line)
        text = re.sub(r"\s+", " ", text).strip()
        if not text:
            continue
        if any(marker in text for marker in STOP_MARKERS):
            break
        text_lines.append(text)

    title = next((line for line in text_lines if fallback_title in line), fallback_title)
    summary = next((line for line in text_lines if len(line) >= 20), fallback_title)
    content = "\n".join(text_lines).strip()
    return {"title": title, "url": url, "summary": summary, "content": content, "html": html_text}


def write_index_csv(rows: list[dict], path: Path) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as fp:
        writer = csv.writer(fp)
        writer.writerow(["title", "url", "summary", "json_path"])
        for row in rows:
            writer.writerow([row["title"], row["url"], row["summary"], row["json_path"]])


def main() -> int:
    args = parse_args()
    save_dir = Path(args.save_dir).expanduser().resolve()
    save_dir.mkdir(parents=True, exist_ok=True)
    raw_dir = save_dir / "raw_html"
    json_dir = save_dir / "json"
    raw_dir.mkdir(parents=True, exist_ok=True)
    json_dir.mkdir(parents=True, exist_ok=True)

    edge_proc = None
    profile_dir = choose_profile_dir(save_dir, args.user_data_dir)
    if not args.attach_only:
        edge_path = discover_edge_path(args.edge_path)
        edge_proc = launch_edge(edge_path, args.start_url, args.remote_debugging_port, profile_dir)

    version_info = wait_for_cdp(args.remote_debugging_port)
    browser = CDPConnection(str(version_info["webSocketDebuggerUrl"]))
    selected_target, page = attach_selected_page(args.remote_debugging_port, args.start_url)

    try:
        print(f"Attached page: {selected_target.get('title', '')} | {selected_target.get('url', '')}")
        wait_for_manual_ready()

        page.close()
        selected_target, page = attach_selected_page(args.remote_debugging_port, args.start_url)
        print(f"Selected page after manual step: {selected_target.get('title', '')} | {selected_target.get('url', '')}")

        while not is_result_list_page(page):
            print("当前标签还不是案例列表页，请在 Edge 中切到“文书列表”或 /view/list.html 页面后再按 Enter。")
            input()
            page.close()
            selected_target, page = attach_selected_page(args.remote_debugging_port, args.start_url)
            print(f"Re-selected page: {selected_target.get('title', '')} | {selected_target.get('url', '')}")

        if page_has_any_selector(page):
            print("检测到验证码，请先在 Edge 中完成验证，再按 Enter。")
            input()
            page.close()
            selected_target, page = attach_selected_page(args.remote_debugging_port, args.start_url)

        index_rows: list[dict] = []
        seen_urls: set[str] = set()
        fetched = 0
        page_no = 1

        while fetched < args.target:
            current_list_url = str(runtime_eval(page, "window.location.href") or "")
            links = collect_visible_case_links(page)
            new_links = [item for item in links if item["url"] not in seen_urls]
            if not new_links:
                debug = list_page_debug(page)
                samples = " | ".join(debug.get("cardTexts") or [])
                print(
                    f"Page {page_no}: no new case links found. "
                    f"title={debug.get('title', '')} url={debug.get('url', '')} "
                    f"link_count={debug.get('linkCount', 0)} samples={samples}"
                )
                break

            for item in new_links:
                if fetched >= args.target:
                    break
                seen_urls.add(item["url"])
                record = fetch_detail_record(page, item["url"], item["title"])
                file_id = f"{fetched + 1:04d}"
                raw_path = raw_dir / f"{file_id}.html"
                raw_path.write_text(record.pop("html"), encoding="utf-8")
                json_path = json_dir / f"{file_id}.json"
                json_path.write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
                index_rows.append({**record, "json_path": str(json_path)})
                fetched += 1
                print(f"[{fetched}/{args.target}] {record['title']}")

            if fetched >= args.target:
                break

            page.call("Page.navigate", {"url": current_list_url})
            wait_for_ready(page)
            if not click_next_page(page):
                break
            wait_for_ready(page)
            time.sleep(2)
            page_no += 1

        write_index_csv(index_rows, save_dir / "index.csv")
        summary = {"fetched_count": fetched, "save_dir": str(save_dir)}
        (save_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(summary, ensure_ascii=False, indent=2))
        return 0
    finally:
        page.close()
        browser.close()
        if edge_proc is not None and edge_proc.poll() is None:
            input("Press Enter to close the Edge window opened by the script...")
            try:
                edge_proc.terminate()
                edge_proc.wait(timeout=10)
            except Exception:
                edge_proc.kill()
        if not args.keep_profile and not args.attach_only and profile_dir.exists():
            shutil.rmtree(profile_dir, ignore_errors=True)


if __name__ == "__main__":
    raise SystemExit(main())
