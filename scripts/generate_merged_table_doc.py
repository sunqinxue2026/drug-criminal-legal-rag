from __future__ import annotations

import argparse
from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.table import _Cell, Table


DEFAULT_SOURCE = Path(
    r"D:\Users\sunqinxue\Desktop\毕设材料\表4-1至4-10_核心数据库表字段说明_学校规范精修版.docx"
)
DEFAULT_OUTPUT = Path(r"D:\CODE\RAG\表4-1至4-10_核心数据库表字段说明_四列合并_修正版.docx")


def set_cell_text_preserve_style(cell: _Cell, text: str, alignment=None):
    paragraph = cell.paragraphs[0]
    if alignment is not None:
        paragraph.alignment = alignment

    if not paragraph.runs:
        paragraph.add_run(text)
        return

    paragraph.runs[0].text = text
    for run in paragraph.runs[1:]:
        run._element.getparent().remove(run._element)


def set_cell_width(cell: _Cell, width: int):
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_w = tc_pr.first_child_found_in("w:tcW")
    if tc_w is None:
        tc_w = OxmlElement("w:tcW")
        tc_pr.append(tc_w)
    tc_w.set(qn("w:w"), str(width))
    tc_w.set(qn("w:type"), "dxa")


def set_border(parent, border_name: str, value: str, size: int | None = None):
    element = parent.find(qn(f"w:{border_name}"))
    if element is None:
        element = OxmlElement(f"w:{border_name}")
        parent.append(element)

    element.set(qn("w:val"), value)
    element.set(qn("w:color"), "000000")
    element.set(qn("w:space"), "0")
    if size is not None:
        element.set(qn("w:sz"), str(size))
    elif qn("w:sz") in element.attrib:
        del element.attrib[qn("w:sz")]


def set_table_border(table: Table, border_name: str, value: str, size: int | None = None):
    tbl_pr = table._tbl.tblPr
    tbl_borders = tbl_pr.first_child_found_in("w:tblBorders")
    if tbl_borders is None:
        tbl_borders = OxmlElement("w:tblBorders")
        tbl_pr.append(tbl_borders)
    set_border(tbl_borders, border_name, value, size)


def set_cell_border(cell: _Cell, border_name: str, value: str, size: int | None = None):
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_borders = tc_pr.first_child_found_in("w:tcBorders")
    if tc_borders is None:
        tc_borders = OxmlElement("w:tcBorders")
        tc_pr.append(tc_borders)
    set_border(tc_borders, border_name, value, size)


def combine_key_text(primary_key: str, logical_foreign_key: str) -> str:
    labels: list[str] = []

    if primary_key.strip() == "是":
        labels.append("主键")

    foreign_value = logical_foreign_key.strip()
    if foreign_value and foreign_value not in {"无", "否", "-", "—"}:
        labels.append(f"逻辑外键（{foreign_value}）")

    return "；".join(labels) if labels else "否"


def apply_strict_three_line_table(table: Table):
    # Word stores line width in eighths of a point:
    # 12 = 1.5 pt, 6 = 0.75 pt.
    for border_name in ("left", "right", "insideH", "insideV"):
        set_table_border(table, border_name, "nil")
    set_table_border(table, "top", "single", 12)
    set_table_border(table, "bottom", "single", 12)

    for row_index, row in enumerate(table.rows):
        for cell in row.cells:
            for border_name in ("left", "right", "top", "bottom", "insideH", "insideV"):
                set_cell_border(cell, border_name, "nil")

            if row_index == 0:
                set_cell_border(cell, "top", "single", 12)
                set_cell_border(cell, "bottom", "single", 6)
            elif row_index == len(table.rows) - 1:
                set_cell_border(cell, "bottom", "single", 12)


def merge_columns_in_table(table: Table):
    if len(table.columns) != 5:
        return

    header = [cell.text.strip() for cell in table.rows[0].cells]
    if header[:5] != ["字段", "数据类型", "主键", "逻辑外键", "作用说明"]:
        return

    width_3 = int(table.rows[0].cells[2]._tc.get_or_add_tcPr().first_child_found_in("w:tcW").w)
    width_4 = int(table.rows[0].cells[3]._tc.get_or_add_tcPr().first_child_found_in("w:tcW").w)
    merged_width = width_3 + width_4

    for row_index, row in enumerate(table.rows):
        cells = row.cells
        if len(cells) < 5:
            continue

        if row_index == 0:
            merged_text = "是否主/外键"
            alignment = WD_ALIGN_PARAGRAPH.CENTER
        else:
            merged_text = combine_key_text(cells[2].text, cells[3].text)
            alignment = cells[2].paragraphs[0].alignment

        set_cell_text_preserve_style(cells[2], merged_text, alignment=alignment)
        set_cell_width(cells[2], merged_width)
        row._tr.remove(cells[3]._tc)

    grid_cols = table._tbl.tblGrid.gridCol_lst
    if len(grid_cols) >= 4:
        grid_cols[2].w = int(grid_cols[2].w) + int(grid_cols[3].w)
        table._tbl.tblGrid.remove(grid_cols[3])

    apply_strict_three_line_table(table)


def build_output(source_path: Path, output_path: Path):
    doc = Document(str(source_path))
    for table in doc.tables:
        merge_columns_in_table(table)
    doc.save(str(output_path))


def main():
    parser = argparse.ArgumentParser(description="Merge primary-key and logical-foreign-key columns in the thesis tables.")
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    build_output(args.source, args.output)
    print(args.output)


if __name__ == "__main__":
    main()
