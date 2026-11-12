from routes.document import _decode_text_with_fallback, _extract_text_from_html


def test_decode_text_with_fallback_prefers_utf8_for_html_without_utf16_bom():
    payload = (
        "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>"
        "<body><div class=\"article-content\">关于依法保障在押犯罪嫌疑人、被告人选择辩护人权利有关问题的批复</div></body></html>"
    ).encode("utf-8")

    decoded = _decode_text_with_fallback(payload)

    assert decoded.startswith("<!DOCTYPE html>")
    assert "关于依法保障在押犯罪嫌疑人" in decoded


def test_extract_text_from_html_returns_visible_chinese_content():
    payload = (
        "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>测试页面</title></head>"
        "<body><div class=\"article-content\"><p>关于依法保障在押犯罪嫌疑人、被告人选择辩护人权利有关问题的批复</p>"
        "<p>法律援助机构指派律师后，受委托辩护人仍有权会见。</p></div></body></html>"
    ).encode("utf-8")

    extracted = _extract_text_from_html(payload)

    assert "关于依法保障在押犯罪嫌疑人、被告人选择辩护人权利有关问题的批复" in extracted
    assert "法律援助机构指派律师后，受委托辩护人仍有权会见。" in extracted
    assert "<html" not in extracted.lower()
