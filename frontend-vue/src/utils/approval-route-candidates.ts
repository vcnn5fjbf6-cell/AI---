export interface ApprovalCandidateSelection {
  candidateUserIds?: unknown;
  candidateUserIdList?: unknown;
}

export function parseIdList(value: unknown): number[] {
  return String(value || "")
    .split(/[,\uFF0C\u3001/\s]+/)
    .map((item) => Number(item.trim()))
    .filter((id) => Number.isFinite(id) && id > 0);
}

export function resolveCandidateUserIdList(
  level: ApprovalCandidateSelection,
  fallbackCandidateUserIds: unknown = ""
): number[] {
  if (Array.isArray(level.candidateUserIdList)) {
    return level.candidateUserIdList
      .map((id) => Number(id))
      .filter((id) => Number.isFinite(id) && id > 0);
  }
  return parseIdList(level.candidateUserIds || fallbackCandidateUserIds);
}
