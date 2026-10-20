export class PublicError extends Error {
  constructor(status, code, message) {
    super(message);
    this.name = "PublicError";
    this.status = status;
    this.code = code;
  }
}

export class GitHubError extends Error {
  constructor(status, operation) {
    super(`GitHub API ${operation} failed with status ${status}`);
    this.name = "GitHubError";
    this.status = status;
    this.operation = operation;
  }
}
