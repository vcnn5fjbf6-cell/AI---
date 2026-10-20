import os
import shlex
from pathlib import Path

import paramiko


def require_env(name: str) -> str:
    value = os.getenv(name)
    if not value:
        raise SystemExit(f"{name} is required")
    return value


def run(client: paramiko.SSHClient, cmd: str) -> str:
    stdin, stdout, stderr = client.exec_command(cmd)
    stdout.channel.recv_exit_status()
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    return out + err


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


host = require_env("DEPLOY_HOST")
user = require_env("DEPLOY_USER")
password = require_env("DEPLOY_PASSWORD")
port = int(os.getenv("DEPLOY_PORT", "22"))
sudo_password = os.getenv("DEPLOY_SUDO_PASSWORD", password)
sudo = f"echo {shlex.quote(sudo_password)} | sudo -S"

client = paramiko.SSHClient()
configure_host_keys(client)
client.connect(host, port=port, username=user, password=password, timeout=30)

try:
    print("=== containers ===")
    print(run(client, f'{sudo} docker ps -a --format "table {{{{.Names}}}}\\t{{{{.Status}}}}\\t{{{{.Ports}}}}"'))

    print("\n=== backend logs ===")
    print(run(client, f"{sudo} docker logs procurement-backend --tail 50 2>&1"))

    print("\n=== health ===")
    print(run(client, 'curl -s -o /dev/null -w "frontend:%{http_code}\\n" http://localhost/'))
    print(run(client, 'curl -s -o /dev/null -w "login:%{http_code}\\n" http://localhost:8080/api/auth/login'))
finally:
    client.close()
