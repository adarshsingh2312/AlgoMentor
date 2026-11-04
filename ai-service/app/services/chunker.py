import re

# Regex splitting by markdown headings (# H1, ## H2, etc.) at line starts
HEADING_PATTERN = re.compile(r"(?m)^(?=#{1,6}\s)")


def chunk_markdown(
    text: str,
    max_chars: int = 800,
    overlap: int = 100,
) -> list[str]:
    """
    Splits markdown text first by headings (# through ######),
    then subdivides large sections into windows of at most max_chars
    with overlap characters of overlap.

    Returns an empty list for empty/blank input.
    """
    if not text or not text.strip():
        return []

    # 1. Split text into sections preserving heading boundaries
    raw_sections = HEADING_PATTERN.split(text)
    sections = [s.strip() for s in raw_sections if s and s.strip()]

    if not sections:
        return []

    chunks: list[str] = []

    for section in sections:
        if len(section) <= max_chars:
            chunks.append(section)
            continue

        # 2. Section exceeds max_chars: slide window with overlap
        step = max_chars - overlap
        if step <= 0:
            step = max_chars

        start = 0
        section_len = len(section)

        while start < section_len:
            end = min(start + max_chars, section_len)
            chunk = section[start:end].strip()
            if chunk:
                chunks.append(chunk)

            if end >= section_len:
                break

            start += step

    return chunks

