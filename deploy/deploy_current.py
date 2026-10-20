import io
import os
import posixpath
import shlex
import subprocess
import sys
import tarfile
import time
from pathlib import Path

import paramiko

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except AttributeError:
    pass


PROJECT_ROOT = Path(__file__).resolve().parents[1]
REMOTE_TAR = "/tmp/enterprise-procurement-platform.tar"

EXCLUDE_DIR_NAMES = {
    ".git",
    ".vscode",
    ".workbuddy",
    "__pycache__",
    "node_modules",
    "dist",
    "target",
    "tools",
}

EXCLUDE_FILE_NAMES = {
    ".env",
    "apache-maven-3.9.9-bin.zip",
    "jdk17.zip",
}

EXCLUDE_SUFFIXES = {
    ".class",
    ".jar",
    ".map",
    ".pyc",
}


def require_env(name: str) -> str:
    value = os.getenv(name)
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


def redact_secrets(text: str) -> str:
    redacted = text
    for name in ("DEPLOY_PASSWORD", "DEPLOY_SUDO_PASSWORD"):
        value = os.getenv(name)
        if value:
            redacted = redacted.replace(value, "***")
    return redacted


def should_skip(path: Path, arcname: str) -> bool:
    parts = set(path.parts)
    if parts & EXCLUDE_DIR_NAMES:
        return True
    if path.name in EXCLUDE_FILE_NAMES:
        return True
    if path.suffix.lower() in EXCLUDE_SUFFIXES:
        return True
    if arcname.startswith("deploy/") and path.suffix.lower() == ".py" and path.name != "deploy_current.py":
        return True
    return False


def build_archive() -> io.BytesIO:
    tar_buffer = io.BytesIO()
    file_count = 0
    tracked_files = subprocess.check_output(
        ["git", "-C", str(PROJECT_ROOT), "ls-files"],
        text=True,
        encoding="utf-8",
    ).splitlines()
    with tarfile.open(fileobj=tar_buffer, mode="w", format=tarfile.PAX_FORMAT) as tar:
        for arcname in tracked_files:
            path = PROJECT_ROOT / arcname
            if not path.is_file():
                continue
            if should_skip(path.relative_to(PROJECT_ROOT), arcname):
                continue
            tar.add(path, arcname=arcname)
            file_count += 1
    tar_buffer.seek(0)
    print(f"Archive ready: {file_count} files, {len(tar_buffer.getvalue()) / 1024 / 1024:.1f} MB")
    return tar_buffer


def run(client: paramiko.SSHClient, command: str, timeout: int = 60, check: bool = True) -> str | tuple[int, str]:
    stdin, stdout, stderr = client.exec_command(command, timeout=timeout)
    exit_code = stdout.channel.recv_exit_status()
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    output = out + err
    if not check:
        return exit_code, output
    if exit_code != 0:
        safe_message = redact_secrets(f"Remote command failed ({exit_code}): {command}\n{out}\n{err}")
        raise RuntimeError(safe_message)
    return output


def sudo_prefix(password: str) -> str:
    return f"echo {shlex.quote(password)} | sudo -S"


def configure_host_keys(client: paramiko.SSHClient) -> None:
    client.load_system_host_keys()
    known_hosts = os.getenv("DEPLOY_KNOWN_HOSTS")
    if known_hosts:
        known_hosts_path = Path.home() / ".ssh" / "known_hosts"
        known_hosts_path.parent.mkdir(parents=True, exist_ok=True)
        with known_hosts_path.open("a", encoding="utf-8") as file:
            file.write(known_hosts.rstrip() + "\n")
        client.load_host_keys(str(known_hosts_path))
    client.set_missing_host_key_policy(paramiko.RejectPolicy())


def apply_incremental_sql(client: paramiko.SSHClient, remote_dir_q: str, sudo: str) -> None:
    command = (
        f"cd {remote_dir_q} && "
        "for f in backend-springboot/sql/zz_*.sql; do "
        "[ -f \"$f\" ] || continue; "
        "echo \"Applying $f\"; "
        f"{sudo} docker cp \"$f\" procurement-mysql:/tmp/procurement_patch.sql && "
        f"{sudo} docker exec procurement-mysql sh -c 'mysql --default-character-set=utf8mb4 -uroot -p\"$MYSQL_ROOT_PASSWORD\" \"$MYSQL_DATABASE\" < /tmp/procurement_patch.sql' "
        "|| exit 1; "
        "done"
    )
    print(run(client, command, timeout=300))


def should_retry_compose_recreate(output: str) -> bool:
    return "ContainerConfig" in output or (
        "container name" in output
        and "already in use" in output
    )


def main() -> None:
    host = require_env("DEPLOY_HOST")
    port = int(os.getenv("DEPLOY_PORT", "22"))
    user = require_env("DEPLOY_USER")
    password = require_env("DEPLOY_PASSWORD")
    sudo_password = os.getenv("DEPLOY_SUDO_PASSWORD", password)
    upload_env = os.getenv("DEPLOY_UPLOAD_ENV", "0") == "1"
    no_cache_build = os.getenv("DEPLOY_NO_CACHE", "0") != "0"
    remote_dir = os.getenv("DEPLOY_REMOTE_DIR", f"/home/{user}/procurement")

    tar_buffer = build_archive()

    client = paramiko.SSHClient()
    configure_host_keys(client)
    client.connect(host, port=port, username=user, password=password, timeout=30)
    sftp = client.open_sftp()

    try:
        remote_dir_q = shlex.quote(remote_dir)
        print("Uploading application archive...")
        run(client, f"mkdir -p {remote_dir_q}")
        remote_dir_abs = run(client, f"cd {remote_dir_q} && pwd").strip()
        sftp.putfo(tar_buffer, REMOTE_TAR)

        print("Extracting application archive...")
        run(client, f"cd {remote_dir_q} && tar -xf {shlex.quote(REMOTE_TAR)} && rm -f {shlex.quote(REMOTE_TAR)}", timeout=180)

        local_env = PROJECT_ROOT / "deploy" / ".env"
        remote_env = posixpath.join(remote_dir_abs, "deploy/.env")
        env_exists = run(client, f"test -f {shlex.quote(remote_env)} && echo yes || echo no").strip() == "yes"
        if upload_env and local_env.exists():
            print("Uploading deploy/.env because DEPLOY_UPLOAD_ENV=1...")
            sftp.put(str(local_env), remote_env)
        elif not env_exists:
            print("Remote deploy/.env is missing; copying .env.example as placeholder.")
            run(client, f"cp {remote_dir_q}/deploy/.env.example {shlex.quote(remote_env)}")

        sudo = sudo_prefix(sudo_password)
        compose_version = run(client, f"{sudo} docker compose version >/dev/null 2>&1 && echo compose || echo legacy").strip()
        compose = "docker compose" if compose_version == "compose" else "docker-compose"

        print("Rebuilding and restarting containers...")
        if no_cache_build:
            print("Building application images without Docker cache...")
            build_cmd = (
                f"cd {remote_dir_q}/deploy && "
                f"{sudo} {compose} -f docker-compose.prod.yml --env-file .env build --no-cache backend frontend"
            )
            code, build_out = run(client, build_cmd, timeout=1200, check=False)
            print(build_out[-4000:])
            if code != 0:
                raise RuntimeError("Docker Compose no-cache build failed")

        up_args = "-d --force-recreate" if no_cache_build else "-d --build"
        compose_cmd = (
            f"cd {remote_dir_q}/deploy && "
            f"{sudo} {compose} -f docker-compose.prod.yml --env-file .env up {up_args}"
        )
        code, compose_out = run(client, compose_cmd, timeout=1200, check=False)
        if code != 0 and should_retry_compose_recreate(compose_out):
            print("Docker Compose recreate hit a stale container conflict; retrying after compose down without deleting volumes.")
            print(run(client, f"cd {remote_dir_q}/deploy && {sudo} {compose} -f docker-compose.prod.yml --env-file .env down", timeout=300, check=False)[1][-2000:])
            code, compose_out = run(client, compose_cmd, timeout=1200, check=False)
        print(compose_out[-4000:])
        if code != 0:
            raise RuntimeError("Docker Compose rebuild/start failed")

        print("Waiting for containers...")
        time.sleep(12)

        print("Applying incremental database scripts...")
        apply_incremental_sql(client, remote_dir_q, sudo)

        print(run(client, f"{sudo} docker ps --format 'table {{{{.Names}}}}\\t{{{{.Status}}}}\\t{{{{.Ports}}}}'"))

        print("Checking frontend and backend...")
        print(run(client, "curl -s -o /dev/null -w 'frontend:%{http_code}\\n' http://localhost/"))
        print(run(client, "curl -s -o /dev/null -w 'auth:%{http_code}\\n' http://localhost/api/auth/me"))
    finally:
        sftp.close()
        client.close()


if __name__ == "__main__":
    main()
