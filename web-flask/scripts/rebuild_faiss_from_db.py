from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from clients.springboot_client import SpringBootClient  # noqa: E402
from algo.vector.bge_client import BGEClient  # noqa: E402
from algo.vector.faiss_manager import FaissManager  # noqa: E402


def main():
    client = SpringBootClient()
    items = client.get_all_items()
    if not items:
      print("no items found from springboot")
      return

    index_dir = ROOT / "data" / "indexes"
    for suffix in (".index", ".meta"):
        target = index_dir / f"legal_cases{suffix}"
        if target.exists():
            target.unlink()

    bge = BGEClient()
    faiss_manager = FaissManager(str(index_dir))

    texts = []
    metadata_list = []
    for item in items:
        title = item.get("title", "")
        summary = item.get("summary", "")
        content = item.get("content", "")
        text = "\n".join([
            title,
            summary,
            content,
        ]).strip()
        if not text:
            continue
        texts.append(text)
        metadata_list.append({
            "item_id": item.get("id"),
            "title": title,
            "category": item.get("categoryName") or item.get("category") or "",
            "tags": item.get("tags", ""),
            "summary": summary,
            "content": content or summary or title,
        })

    vectors = bge.encode_batch(texts)
    faiss_manager.add_batch("legal_cases", vectors, metadata_list)
    faiss_manager.save("legal_cases")
    print(f"rebuilt {len(texts)} items into legal_cases")


if __name__ == "__main__":
    main()
