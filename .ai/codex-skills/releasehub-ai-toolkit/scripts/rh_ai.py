#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


MAX_READ_BYTES = 512 * 1024


def find_repo_root(start: Path) -> Path:
    current = start.resolve()
    for _ in range(12):
        if (current / ".ai" / "README.md").exists():
            return current
        if (current / ".git").exists():
            if (current / ".ai" / "README.md").exists():
                return current
        if current.parent == current:
            break
        current = current.parent
    raise FileNotFoundError("repo root not found (expected '.ai/README.md' in an ancestor directory)")


def normalize_token(token: str) -> str:
    return token.strip().lower()


def tokenize(text: str) -> list[str]:
    ascii_tokens = re.findall(r"[a-zA-Z0-9][a-zA-Z0-9_\-/.]*", text)
    cjk_tokens = re.findall(r"[\u4e00-\u9fff]{2,}", text)
    tokens = [normalize_token(t) for t in ascii_tokens + cjk_tokens if t.strip()]
    return list(dict.fromkeys(tokens))


def classify_task_type(task: str) -> str:
    t = task.lower()
    if any(k in task for k in ["修复", "异常", "回归", "不符合预期", "报错", "错误"]) or "bug" in t:
        return "bugfix"
    if any(k in task for k in ["重构", "refactor", "抽取", "整理", "改名", "命名", "结构优化"]):
        return "refactor"
    if any(k in task for k in ["性能", "优化", "延迟", "吞吐", "qps", "cost", "成本"]):
        return "optimization"
    if any(k in task for k in ["文档", "readme", "说明", "规范"]):
        return "docs"
    if any(k in task for k in ["新增", "添加", "实现", "支持", "引入"]):
        return "feature"
    return "chore"


def detect_domain(task: str) -> str:
    t = task.lower()
    if "releasewindow" in t or "release-window" in t or "release window" in t or "releasewindow" in task:
        return "release-window"
    if "version-policy" in t or "版本策略" in task or "version policy" in t:
        return "version-policy"
    return "unknown"


def requires_openspec(task: str, task_type: str) -> tuple[bool, list[str]]:
    reasons: list[str] = []
    if task_type in {"feature", "optimization"}:
        reasons.append(f"taskType={task_type}")
    if any(k in task for k in ["破坏性", "breaking", "契约", "接口", "API", "迁移", "数据库", "schema", "架构", "模型", "聚合根"]):
        reasons.append("mentions contract/data/architecture changes")
    required = bool(reasons) and task_type not in {"bugfix", "docs", "chore"} and "保持行为不变" not in task
    if required:
        return True, reasons
    return False, ["no strong openspec signal"]


@dataclass(frozen=True)
class TaskAnalysis:
    task: str
    taskType: str
    domain: str
    keywords: list[str]
    requiresOpenSpec: bool
    reasons: list[str]

    def to_json(self) -> dict:
        return {
            "task": self.task,
            "taskType": self.taskType,
            "domain": self.domain,
            "keywords": self.keywords,
            "requiresOpenSpec": self.requiresOpenSpec,
            "reasons": self.reasons,
        }


def analyze_task(task: str) -> TaskAnalysis:
    task_type = classify_task_type(task)
    domain = detect_domain(task)
    keywords = tokenize(task)
    if domain != "unknown" and domain not in keywords:
        keywords.insert(0, domain)
    required, reasons = requires_openspec(task, task_type)
    return TaskAnalysis(
        task=task,
        taskType=task_type,
        domain=domain,
        keywords=keywords[:40],
        requiresOpenSpec=required,
        reasons=reasons,
    )


@dataclass(frozen=True)
class ExperienceEntry:
    category: str
    question: str
    solution: str
    related_file: str
    tags: list[str]
    relevance_keywords: list[str]

    @property
    def title(self) -> str:
        return self.question.strip()


def _parse_backticked_list(value: str) -> list[str]:
    items = re.findall(r"`([^`]+)`", value)
    if items:
        return [i.strip() for i in items if i.strip()]
    return [v.strip() for v in re.split(r"[，,]\s*", value) if v.strip()]


def load_experience_index(repo_root: Path) -> list[ExperienceEntry]:
    index_path = repo_root / ".ai" / "summaries" / "experience-index.md"
    if not index_path.exists():
        return []

    raw = index_path.read_text(encoding="utf-8")
    lines = raw.splitlines()

    entries: list[ExperienceEntry] = []
    category = "unknown"
    current: dict[str, str] = {}

    def flush() -> None:
        nonlocal current
        if not current.get("question"):
            current = {}
            return
        entries.append(
            ExperienceEntry(
                category=category,
                question=current.get("question", ""),
                solution=current.get("solution", ""),
                related_file=current.get("related_file", ""),
                tags=_parse_backticked_list(current.get("tags", "")),
                relevance_keywords=[
                    normalize_token(x)
                    for x in re.split(r"[，,]\s*", current.get("relevance_keywords", ""))
                    if x.strip()
                ],
            )
        )
        current = {}

    for line in lines:
        m = re.match(r"^###\s+问题类别：\s*(.+?)\s*$", line)
        if m:
            flush()
            category = m.group(1).strip()
            continue

        q = re.match(r"^\s*-\s+\*\*问题\*\*：\s*(.+?)\s*$", line)
        if q:
            flush()
            current["question"] = q.group(1).strip()
            continue

        s = re.match(r"^\s*-\s+\*\*解决方案\*\*：\s*(.+?)\s*$", line)
        if s:
            current["solution"] = s.group(1).strip()
            continue

        rf = re.match(r"^\s*-\s+\*\*相关文件\*\*：\s*`(.+?)`\s*$", line)
        if rf:
            current["related_file"] = rf.group(1).strip()
            continue

        tags = re.match(r"^\s*-\s+\*\*标签\*\*：\s*(.+?)\s*$", line)
        if tags:
            current["tags"] = tags.group(1).strip()
            continue

        rk = re.match(r"^\s*-\s+\*\*相关度关键词\*\*：\s*(.+?)\s*$", line)
        if rk:
            current["relevance_keywords"] = rk.group(1).strip()
            continue

    flush()
    return entries


def score_experience(entry: ExperienceEntry, keywords: Iterable[str]) -> float:
    kw = {normalize_token(k) for k in keywords if k.strip()}
    hay = set(entry.relevance_keywords)
    hay.update(tokenize(entry.question))
    hay.update(tokenize(entry.solution))
    if not kw:
        return 0.0
    hits = len(kw.intersection(hay))
    return hits / max(len(kw), 1)


def top_experiences(repo_root: Path, keywords: list[str], top: int) -> list[dict]:
    entries = load_experience_index(repo_root)
    scored = []
    for e in entries:
        s = score_experience(e, keywords)
        if s <= 0:
            continue
        scored.append((s, e))
    scored.sort(key=lambda x: x[0], reverse=True)
    result = []
    for s, e in scored[:top]:
        result.append(
            {
                "title": e.title,
                "category": e.category,
                "relevance": round(s, 3),
                "filePath": e.related_file,
                "summary": e.solution,
                "tags": e.tags,
            }
        )
    return result


def iter_md_files(base: Path) -> Iterable[Path]:
    for p in base.rglob("*.md"):
        if not p.is_file():
            continue
        try:
            if p.stat().st_size > MAX_READ_BYTES:
                continue
        except OSError:
            continue
        yield p


def score_file(path: Path, keywords: list[str]) -> int:
    try:
        content = path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return 0
    score = 0
    lowered = content.lower()
    for k in keywords:
        if not k.strip():
            continue
        if any("\u4e00" <= c <= "\u9fff" for c in k):
            score += content.count(k)
        else:
            score += lowered.count(k.lower())
    if score == 0:
        for k in keywords:
            if k and k.lower() in str(path).lower():
                score += 1
    return score


def suggest_context_files(repo_root: Path, analysis: TaskAnalysis, top: int) -> list[str]:
    suggestions: list[str] = []

    def add(path: str) -> None:
        if path not in suggestions:
            suggestions.append(path)

    add(".ai/summaries/project-context.md")
    add("AGENTS.md")

    if analysis.requiresOpenSpec:
        add("openspec/AGENTS.md")

    task_type = analysis.taskType
    if task_type in {"feature", "bugfix", "refactor", "optimization"}:
        add("context/tech/conventions/backend.md")
        add("context/tech/conventions/testing.md")
    if any(k in analysis.task for k in ["前端", "vue", "vite", "pinia", "页面", "ui", "组件"]):
        add("context/tech/conventions/frontend.md")
    if any(k in analysis.task for k in ["数据库", "flyway", "迁移", "sql", "schema"]):
        add("context/tech/conventions/database.md")

    md_bases = [
        repo_root / "context",
        repo_root / "openspec",
        repo_root / ".ai" / "summaries",
    ]
    scored: list[tuple[int, str]] = []
    for base in md_bases:
        if not base.exists():
            continue
        for p in iter_md_files(base):
            rel = str(p.relative_to(repo_root))
            if rel in suggestions:
                continue
            s = score_file(p, analysis.keywords)
            if s > 0:
                scored.append((s, rel))
    scored.sort(key=lambda x: x[0], reverse=True)
    for _, rel in scored[: max(0, top - len(suggestions))]:
        add(rel)
    return suggestions[:top]


def cmd_analyze(args: argparse.Namespace) -> int:
    analysis = analyze_task(args.task)
    print(json.dumps(analysis.to_json(), ensure_ascii=False, indent=2))
    return 0


def cmd_experience(args: argparse.Namespace) -> int:
    repo_root = find_repo_root(Path.cwd())
    analysis = analyze_task(args.task)
    matches = top_experiences(repo_root, analysis.keywords, args.top)
    print(json.dumps({"analysis": analysis.to_json(), "experiences": matches}, ensure_ascii=False, indent=2))
    return 0


def cmd_context(args: argparse.Namespace) -> int:
    repo_root = find_repo_root(Path.cwd())
    analysis = analyze_task(args.task)
    files = suggest_context_files(repo_root, analysis, args.top)
    print(json.dumps({"analysis": analysis.to_json(), "contexts": files}, ensure_ascii=False, indent=2))
    return 0


def cmd_bundle(args: argparse.Namespace) -> int:
    repo_root = find_repo_root(Path.cwd())
    analysis = analyze_task(args.task)
    experiences = top_experiences(repo_root, analysis.keywords, args.top_experience)
    contexts = suggest_context_files(repo_root, analysis, args.top_context)
    out = {"analysis": analysis.to_json(), "experiences": experiences, "contexts": contexts}
    if args.format == "json":
        print(json.dumps(out, ensure_ascii=False, indent=2))
        return 0

    print("# Task Analysis")
    print(json.dumps(analysis.to_json(), ensure_ascii=False, indent=2))
    print("\n# Suggested Context Files")
    for p in contexts:
        print(f"- {p}")
    print("\n# Relevant Experiences")
    if not experiences:
        print("- (none)")
    else:
        for e in experiences:
            fp = e.get("filePath") or "(no file)"
            print(f"- {e['title']} [{e['category']}] (relevance={e['relevance']}) → {fp}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="rh_ai.py")
    sub = p.add_subparsers(dest="cmd", required=True)

    pa = sub.add_parser("analyze", help="Analyze task type/domain/keywords/openspec")
    pa.add_argument("--task", required=True)
    pa.set_defaults(func=cmd_analyze)

    pe = sub.add_parser("experience", help="Search experience-index by task keywords")
    pe.add_argument("--task", required=True)
    pe.add_argument("--top", type=int, default=5)
    pe.set_defaults(func=cmd_experience)

    pc = sub.add_parser("context", help="Suggest which docs to load/read next")
    pc.add_argument("--task", required=True)
    pc.add_argument("--top", type=int, default=12)
    pc.set_defaults(func=cmd_context)

    pb = sub.add_parser("bundle", help="Analyze + experience + context")
    pb.add_argument("--task", required=True)
    pb.add_argument("--top-context", type=int, default=12)
    pb.add_argument("--top-experience", type=int, default=5)
    pb.add_argument("--format", choices=["text", "json"], default="text")
    pb.set_defaults(func=cmd_bundle)

    return p


def main(argv: list[str]) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

