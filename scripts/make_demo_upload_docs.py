from __future__ import annotations

import re
import shutil
from html import unescape
from html.parser import HTMLParser
from pathlib import Path

from docx import Document
from docx.shared import Pt
from docx.enum.text import WD_LINE_SPACING


ROOT = Path(r"D:\Users\sunqinxue\Desktop\涉毒法律演示上传包")
CASE_HTML_DIR = ROOT / "01_上传到法律案件管理_典型案例"
DOC_HTML_DIR = ROOT / "02_上传到法律文书管理_文书法规"
CLEAN_ROOT = ROOT / "03_推荐上传_DOCX和TXT_clean"
CASE_OUT = CLEAN_ROOT / "01_上传到法律案件管理"
DOC_OUT = CLEAN_ROOT / "02_上传到法律文书管理"


class TextExtractor(HTMLParser):
    block_tags = {
        "address",
        "article",
        "aside",
        "blockquote",
        "br",
        "dd",
        "div",
        "dl",
        "dt",
        "figcaption",
        "footer",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "header",
        "li",
        "main",
        "nav",
        "ol",
        "p",
        "pre",
        "section",
        "table",
        "td",
        "th",
        "tr",
        "ul",
    }

    skip_tags = {"script", "style", "noscript", "iframe", "svg"}

    def __init__(self) -> None:
        super().__init__(convert_charrefs=False)
        self.parts: list[str] = []
        self.skip_depth = 0
        self.title_parts: list[str] = []
        self.in_title = False

    def handle_starttag(self, tag: str, attrs) -> None:
        tag = tag.lower()
        if tag in self.skip_tags:
            self.skip_depth += 1
            return
        if tag == "title":
            self.in_title = True
        if tag in self.block_tags:
            self.parts.append("\n")

    def handle_endtag(self, tag: str) -> None:
        tag = tag.lower()
        if tag in self.skip_tags and self.skip_depth:
            self.skip_depth -= 1
            return
        if tag == "title":
            self.in_title = False
        if tag in self.block_tags:
            self.parts.append("\n")

    def handle_data(self, data: str) -> None:
        if self.skip_depth:
            return
        text = unescape(data)
        if self.in_title:
            self.title_parts.append(text)
        self.parts.append(text)


def sanitize_filename(value: str) -> str:
    value = re.sub(r"[\\/:*?\"<>|]", "_", value)
    value = re.sub(r"\s+", " ", value).strip()
    return value[:120] or "未命名文档"


def clean_text(raw: str) -> str:
    raw = raw.replace("\u3000", " ")
    raw = raw.replace("\xa0", " ")
    raw = re.sub(r"[ \t\r\f\v]+", " ", raw)
    raw = re.sub(r"\n[ \t]+", "\n", raw)
    raw = re.sub(r"[ \t]+\n", "\n", raw)
    raw = re.sub(r"\n{3,}", "\n\n", raw)

    lines = [line.strip() for line in raw.splitlines()]
    cleaned: list[str] = []
    junk_patterns = [
        r"^分享到",
        r"^字号",
        r"^打印",
        r"^关闭",
        r"^来源：?$",
        r"^发布时间：?$",
        r"^责任编辑",
        r"^Copyright",
        r"^版权所有",
        r"^网站地图",
        r"^首页\s*$",
        r"^当前位置",
    ]
    for line in lines:
        if not line:
            if cleaned and cleaned[-1] != "":
                cleaned.append("")
            continue
        if any(re.search(pattern, line, re.I) for pattern in junk_patterns):
            continue
        if len(line) <= 2 and re.fullmatch(r"[\W_]+", line):
            continue
        cleaned.append(line)

    # Collapse adjacent duplicate boilerplate lines while preserving legal text.
    deduped: list[str] = []
    previous = None
    for line in cleaned:
        key = line if line else ""
        if key and key == previous:
            continue
        deduped.append(line)
        previous = key

    text = "\n".join(deduped).strip()
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text


def extract_html(path: Path) -> tuple[str, str]:
    content = path.read_text(encoding="utf-8", errors="ignore")
    parser = TextExtractor()
    parser.feed(content)
    title = clean_text(" ".join(parser.title_parts)).splitlines()[0] if parser.title_parts else path.stem
    text = clean_text("".join(parser.parts))
    if title and title not in text[:300]:
        text = f"{title}\n\n{text}"
    return sanitize_filename(title or path.stem), text


def write_txt(path: Path, title: str, text: str, source_name: str) -> None:
    payload = f"{title}\n\n来源文件：{source_name}\n\n{text}\n"
    path.write_text(payload, encoding="utf-8")


def write_docx(path: Path, title: str, text: str, source_name: str) -> None:
    document = Document()
    section = document.sections[0]
    section.top_margin = Pt(72)
    section.bottom_margin = Pt(72)
    section.left_margin = Pt(72)
    section.right_margin = Pt(72)

    styles = document.styles
    normal = styles["Normal"]
    normal.font.name = "Microsoft YaHei"
    normal.font.size = Pt(11)
    normal.paragraph_format.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE
    normal.paragraph_format.space_after = Pt(6)

    title_para = document.add_heading(title, level=1)
    title_para.style.font.name = "Microsoft YaHei"
    title_para.style.font.size = Pt(18)

    source_para = document.add_paragraph()
    source_run = source_para.add_run(f"来源文件：{source_name}")
    source_run.italic = True
    source_run.font.size = Pt(9)

    for block in text.split("\n"):
        block = block.strip()
        if not block:
            continue
        if len(block) <= 36 and re.search(r"(案例|起诉书|解释|通知|意见|中华人民共和国|最高人民)", block):
            document.add_heading(block, level=2)
        else:
            document.add_paragraph(block)

    document.save(path)


def convert_html_folder(source_dir: Path, target_dir: Path) -> list[Path]:
    target_dir.mkdir(parents=True, exist_ok=True)
    outputs: list[Path] = []
    for html_path in sorted(source_dir.glob("*.html")):
        title, text = extract_html(html_path)
        base = sanitize_filename(html_path.stem)
        txt_path = target_dir / f"{base}.txt"
        docx_path = target_dir / f"{base}.docx"
        write_txt(txt_path, title, text, html_path.name)
        write_docx(docx_path, title, text, html_path.name)
        outputs.extend([txt_path, docx_path])
    return outputs


def main() -> None:
    if CLEAN_ROOT.exists():
        shutil.rmtree(CLEAN_ROOT)
    CASE_OUT.mkdir(parents=True, exist_ok=True)
    DOC_OUT.mkdir(parents=True, exist_ok=True)

    outputs = []
    outputs.extend(convert_html_folder(CASE_HTML_DIR, CASE_OUT))
    outputs.extend(convert_html_folder(DOC_HTML_DIR, DOC_OUT))

    for pdf_path in sorted(DOC_HTML_DIR.glob("*.pdf")):
        target = DOC_OUT / pdf_path.name
        shutil.copy2(pdf_path, target)
        outputs.append(target)

    guide = CLEAN_ROOT / "00_优先上传说明.txt"
    guide.write_text(
        "优先上传建议：\n"
        "1. 法律案件管理：进入 01_上传到法律案件管理，优先选择 .txt；如果想演示 Word 附件，也可选择 .docx。\n"
        "2. 法律文书管理：进入 02_上传到法律文书管理，优先选择 .txt 或 PDF；也可选择 .docx。\n"
        "3. 不建议再上传上一层的 .html 文件；本文件夹里的 txt/docx 已经去掉网页导航、脚本和样式噪声。\n",
        encoding="utf-8",
    )
    outputs.append(guide)

    print(f"created={len(outputs)}")
    for path in outputs:
        print(path)


if __name__ == "__main__":
    main()
