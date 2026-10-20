import os
import shlex
import sys
from pathlib import Path

import paramiko

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except AttributeError:
    pass


PROJECT_ROOT = Path(__file__).resolve().parents[2]
LOCAL_SQL = PROJECT_ROOT / "deploy" / "db" / "manual" / "reset_business_data.sql"
REMOTE_SQL = "/tmp/procurement_reset_business_data.sql"


def require_env(name: str) -> str:
    value = os.getenv(name)
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


def redact(text: str) -> str:
    redacted = text
    for name in ("DEPLOY_PASSWORD", "DEPLOY_SUDO_PASSWORD"):
        value = os.getenv(name)
        if value:
            redacted = redacted.replace(value, "***")
    return redacted


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


def run(client: paramiko.SSHClient, command: str, timeout: int = 120) -> str:
    stdin, stdout, stderr = client.exec_command(command, timeout=timeout)
    exit_code = stdout.channel.recv_exit_status()
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    if exit_code != 0:
        raise RuntimeError(redact(f"Remote command failed ({exit_code}): {command}\n{out}\n{err}"))
    return out + err


def run_sudo_bash(client: paramiko.SSHClient, password: str, command: str, timeout: int = 300) -> str:
    sudo_command = f"sudo -S bash -lc {shlex.quote(command)}"
    stdin, stdout, stderr = client.exec_command(sudo_command, timeout=timeout, get_pty=False)
    stdin.write(password + "\n")
    stdin.flush()
    exit_code = stdout.channel.recv_exit_status()
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    if exit_code != 0:
        raise RuntimeError(redact(f"Remote sudo command failed ({exit_code}): {command}\n{out}\n{err}"))
    return out + err


def main() -> None:
    if not LOCAL_SQL.exists():
        raise RuntimeError(f"Reset SQL not found: {LOCAL_SQL}")

    host = require_env("DEPLOY_HOST")
    port = int(os.getenv("DEPLOY_PORT", "22"))
    user = require_env("DEPLOY_USER")
    password = require_env("DEPLOY_PASSWORD")
    sudo_password = os.getenv("DEPLOY_SUDO_PASSWORD", password)
    mysql_container = os.getenv("MYSQL_CONTAINER", "procurement-mysql")
    backend_container = os.getenv("BACKEND_CONTAINER", "procurement-backend")

    client = paramiko.SSHClient()
    configure_host_keys(client)
    client.connect(host, port=port, username=user, password=password, timeout=30)
    sftp = client.open_sftp()

    try:
        print(f"Uploading manual reset SQL to {REMOTE_SQL}...")
        sftp.put(str(LOCAL_SQL), REMOTE_SQL)

        container_q = shlex.quote(mysql_container)
        remote_sql_q = shlex.quote(REMOTE_SQL)
        container_sql = "/tmp/procurement_reset_business_data.sql"
        container_sql_q = shlex.quote(container_sql)

        print("Checking MySQL container...")
        run_sudo_bash(
            client,
            sudo_password,
            f"docker ps --format '{{{{.Names}}}}' | grep -qx {container_q}",
            timeout=60,
        )

        print("Executing business-data reset...")
        reset_command = (
            f"docker cp {remote_sql_q} {container_q}:{container_sql_q} && "
            f"docker exec {container_q} sh -c "
            f"{shlex.quote(f'mysql --default-character-set=utf8mb4 -uroot -p\"$MYSQL_ROOT_PASSWORD\" \"$MYSQL_DATABASE\" < {container_sql}')}"
        )
        print(run_sudo_bash(client, sudo_password, reset_command, timeout=300))

        print("Clearing persisted order invoice files...")
        backend_container_q = shlex.quote(backend_container)
        invoice_dir = "/app/data/order-invoices"
        cleanup_invoice_files = (
            f"docker ps --format '{{{{.Names}}}}' | grep -qx {backend_container_q} && "
            f"docker exec {backend_container_q} sh -ec "
            + shlex.quote(
                f'invoice_dir={shlex.quote(invoice_dir)}; '
                'resolved="$(readlink -f "$invoice_dir")"; '
                '[ "$resolved" = "$invoice_dir" ]; '
                '[ -d "$invoice_dir" ]; '
                'find "$invoice_dir" -mindepth 1 -delete; '
                'test -z "$(find "$invoice_dir" -mindepth 1 -print -quit)"'
            )
        )
        print(run_sudo_bash(client, sudo_password, cleanup_invoice_files, timeout=300))

        print("Removing temporary SQL file...")
        run(client, f"rm -f {remote_sql_q}", timeout=60)
        print("Business data reset completed.")
    finally:
        sftp.close()
        client.close()


if __name__ == "__main__":
    main()
