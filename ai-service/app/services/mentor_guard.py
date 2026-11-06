import re
from typing import Tuple


FORBIDDEN_CODE_PATTERNS = [
    r"```",                                  # Code blocks
    r"`[^`\n]{15,}`",                        # Inline long code snippet
    r"\bdef\s+[a-zA-Z_]\w*\s*\(",            # Python function definition
    r"\bfor\s*\(",                           # C/Java for loop
    r"\bwhile\s*\(",                         # C/Java while loop
    r"\bimport\s+[a-zA-Z_]",                 # Import statement
    r"\bclass\s+[a-zA-Z_]",                  # Class definition
    r"\bpublic\s+(static|class|void|int)\b", # Java declarations
    r"\bvector\s*<",                         # C++ vector
    r"\bint\[\]",                            # Array declaration
    r";\s*$",                                # Trailing semicolon
    r"[{}]\s*$",                             # Curly braces
    r"\bprint\s*\(",                         # Print call
    r"\bSystem\.out\.",                      # Java print
    r"=>|->",                                # Arrow syntax
]

FORBIDDEN_LEVEL_1_KEYWORDS = [
    "binary search",
    "two pointers",
    "two pointer",
    "dynamic programming",
    "segment tree",
    "fenwick",
    "dijkstra",
    "bfs",
    "dfs",
    "breadth first",
    "depth first",
    "monotonic stack",
    "monotonic queue",
    "sliding window",
    "hash map",
    "hashmap",
    "trie",
    "disjoint set",
    "union find",
]

MAX_CHARS_BY_LEVEL = {
    1: 450,
    2: 700,
    3: 1000,
}


def check_hint_safety(level: int, hint: str, follow_up: str = "") -> Tuple[bool, str | None]:
    """
    Deterministic safety validator for generated hints:
    1. Rejects code fences and code-like syntax across all levels.
    2. Enforces maximum character limits per level.
    3. Enforces sentence bounds for Level 3 (maximum 5 sentences).
    4. Forbids explicit algorithmic keywords for Level 1.
    """
    combined_text = f"{hint}\n{follow_up}".strip()

    if not hint or not hint.strip():
        return False, "Hint cannot be empty."

    # 1. Code syntax check
    for pattern in FORBIDDEN_CODE_PATTERNS:
        if re.search(pattern, combined_text, re.MULTILINE):
            return False, f"Detected code or programming syntax matching pattern: '{pattern}'."

    # 2. Level 1 buzzword check
    if level == 1:
        lower_text = combined_text.lower()
        for kw in FORBIDDEN_LEVEL_1_KEYWORDS:
            if re.search(r"\b" + re.escape(kw) + r"\b", lower_text):
                return False, f"Level 1 hint must not disclose technique names (found '{kw}')."

    # 3. Length checks
    max_chars = MAX_CHARS_BY_LEVEL.get(level, 800)
    if len(hint) > max_chars:
        return False, f"Level {level} hint exceeds max length ({len(hint)} > {max_chars} chars)."

    # 4. Sentence count for Level 3 (max 5 sentences)
    if level == 3:
        sentences = [s.strip() for s in re.split(r"[.!?]+", hint) if s.strip()]
        if len(sentences) > 5:
            return False, f"Level 3 hint exceeds maximum 5 sentences (found {len(sentences)})."

    return True, None


def get_safe_fallback(level: int, tags: list[str] | None = None) -> Tuple[str, str]:
    """
    Guaranteed safe, pedagogical fallback when the LLM repeatedly fails safety guard checks.
    """
    tags_str = ", ".join(tags[:2]) if tags else "this topic"
    if level == 1:
        return (
            "What invariants or subproblems emerge when you evaluate the smallest valid input on paper?",
            "Can you write down the state changes for the first two elements manually?",
        )
    elif level == 2:
        return (
            f"Consider the typical algorithmic pattern applied to {tags_str}. Focus on identifying what information from previous steps can be reused rather than recomputed.",
            "What property stays constant as you iterate through the problem?",
        )
    else:
        return (
            "Process the input sequentially while maintaining intermediate results in an appropriate structure. Break the problem into overlapping subproblems, verify boundary constraints, and ensure each element is visited a constant number of times.",
            "Can you trace this logic on paper with a sample input before writing any code?",
        )
