#!/usr/bin/env python3

import http.server
import json
import os
import re
import socket
import subprocess
import threading
import time

HOSTNAME = os.environ.get("HOST_HOSTNAME", socket.gethostname())
LISTEN_PORT = int(os.environ.get("EXPORTER_PORT", "9422"))
CACHE_SECONDS = int(os.environ.get("CACHE_SECONDS", "5"))
POSTGRES_DATA_PATH = os.environ.get(
    "POSTGRES_DATA_PATH", "/host-fs/opt/metric-lab/data/postgres"
)

SERVICE_MAP = {
    "metric-lab-metric-lab-app-1": "metric-lab-app",
    "metric-lab-metric-lab-worker-1": "metric-lab-worker",
    "metric-lab-predictive-module-1": "predictive-module",
    "metric-lab-redis-1": "redis",
    "metric-lab-postgres-1": "postgres",
    "metric-lab-host-exporter-linux-1": "host-exporter-linux",
    "metric-lab-locust-1": "locust",
}

_prev_cpu = {"idle": 0, "total": 0, "ts": 0}
_prev_disk = {}


def _read_proc_stat_cpu():
    with open("/host-fs/proc/stat") as f:
        for line in f:
            if line.startswith("cpu "):
                parts = line.split()
                vals = [int(v) for v in parts[1:]]
                idle = vals[3] + (vals[4] if len(vals) > 4 else 0)
                total = sum(vals)
                return idle, total
    return 0, 0


def get_host_cpu_percent():
    idle, total = _read_proc_stat_cpu()
    now = time.monotonic()
    prev = _prev_cpu
    d_total = total - prev["total"]
    d_idle = idle - prev["idle"]
    _prev_cpu.update(idle=idle, total=total, ts=now)
    if d_total <= 0 or prev["ts"] == 0:
        return 0.0
    return max(0.0, min(100.0, (1 - d_idle / d_total) * 100))


def get_host_memory():
    info = {}
    with open("/host-fs/proc/meminfo") as f:
        for line in f:
            m = re.match(r"^(\w+):\s+(\d+)", line)
            if m:
                info[m.group(1)] = int(m.group(2)) * 1024
    total = info.get("MemTotal", 0)
    available = info.get("MemAvailable", 0)
    used = max(0, total - available)
    ratio = used / total if total > 0 else 0
    return total, used, ratio


def get_disk_io_rates():
    now = time.monotonic()
    results = {}
    with open("/host-fs/proc/diskstats") as f:
        for line in f:
            parts = line.split()
            if len(parts) < 14:
                continue
            dev = parts[2]
            if re.match(r"^(vd[a-z]|sd[a-z]|nvme\d+n\d+)$", dev):
                read_bytes = int(parts[5]) * 512
                write_bytes = int(parts[9]) * 512
                prev = _prev_disk.get(dev)
                if prev and (now - prev["ts"]) > 0:
                    dt = now - prev["ts"]
                    r_rate = max(0, (read_bytes - prev["read_bytes"]) / dt)
                    w_rate = max(0, (write_bytes - prev["write_bytes"]) / dt)
                    results[dev] = {
                        "read_bytes_per_sec": r_rate,
                        "write_bytes_per_sec": w_rate,
                    }
                _prev_disk[dev] = {
                    "read_bytes": read_bytes,
                    "write_bytes": write_bytes,
                    "ts": now,
                }
    return results


def get_postgres_volume():
    try:
        st = os.statvfs(POSTGRES_DATA_PATH)
        total = st.f_blocks * st.f_frsize
        free = st.f_bavail * st.f_frsize
        used = total - free
        ratio = used / total if total > 0 else 0
        return total, free, used, ratio
    except Exception:
        return 0, 0, 0, 0


def _parse_pct(s):
    return float(s.strip().rstrip("%"))


def _parse_size(s):
    s = s.strip()
    m = re.match(r"^([\d.]+)\s*([KMGTPE]?i?B)$", s, re.IGNORECASE)
    if not m:
        return 0.0
    val = float(m.group(1))
    unit = m.group(2).upper()
    mult = {
        "B": 1, "KB": 1e3, "MB": 1e6, "GB": 1e9, "TB": 1e12,
        "KIB": 1024, "MIB": 1024**2, "GIB": 1024**3, "TIB": 1024**4,
    }
    return val * mult.get(unit, 1)


def get_container_metrics():
    try:
        out = subprocess.check_output(
            ["docker", "stats", "--no-stream", "--format", "{{json .}}"],
            timeout=15, stderr=subprocess.DEVNULL,
        ).decode()
    except Exception:
        return []
    results = []
    for line in out.strip().splitlines():
        if not line.strip():
            continue
        try:
            j = json.loads(line)
        except json.JSONDecodeError:
            continue
        cname = j.get("Name", "")
        service = SERVICE_MAP.get(cname)
        if not service:
            continue
        cpu_pct = _parse_pct(j.get("CPUPerc", "0%"))
        mem_parts = j.get("MemUsage", "0B / 0B").split("/")
        mem_used = _parse_size(mem_parts[0]) if len(mem_parts) >= 1 else 0
        mem_limit = _parse_size(mem_parts[1]) if len(mem_parts) >= 2 else 0
        mem_ratio = mem_used / mem_limit if mem_limit > 0 else 0
        results.append({
            "service": service, "container_name": cname,
            "cpu_pct": cpu_pct, "mem_bytes": mem_used,
            "mem_limit": mem_limit, "mem_ratio": mem_ratio,
        })
    return results


def build_metrics():
    lines = []
    host = HOSTNAME
    lines.append(f'metric_lab_host_exporter_up{{host="{host}"}} 1')

    collector_ok = {
        "containers": 1, "host": 1, "postgres_volume": 1, "host_disk": 1,
    }

    try:
        for c in get_container_metrics():
            svc, cn = c["service"], c["container_name"]
            lbl = f'host="{host}",service="{svc}",container_name="{cn}"'
            lines.append(f"metric_lab_container_info{{{lbl}}} 1")
            lines.append(
                f"metric_lab_container_cpu_usage_percent{{{lbl}}} {c['cpu_pct']:.6g}"
            )
            lines.append(
                f"metric_lab_container_memory_usage_bytes{{{lbl}}} {c['mem_bytes']:.6g}"
            )
            lines.append(
                f"metric_lab_container_memory_limit_bytes{{{lbl}}} {c['mem_limit']:.6g}"
            )
            lines.append(
                f"metric_lab_container_memory_usage_ratio{{{lbl}}} {c['mem_ratio']:.6g}"
            )
    except Exception:
        collector_ok["containers"] = 0

    try:
        cpu_pct = get_host_cpu_percent()
        lines.append(
            f'metric_lab_host_cpu_usage_percent{{host="{host}"}} {cpu_pct:.6g}'
        )
        mem_total, mem_used, mem_ratio = get_host_memory()
        lines.append(
            f'metric_lab_host_memory_total_bytes{{host="{host}"}} {mem_total:.6g}'
        )
        lines.append(
            f'metric_lab_host_memory_used_bytes{{host="{host}"}} {mem_used:.6g}'
        )
        lines.append(
            f'metric_lab_host_memory_usage_ratio{{host="{host}"}} {mem_ratio:.6g}'
        )
    except Exception:
        collector_ok["host"] = 0

    try:
        total, free, used, ratio = get_postgres_volume()
        lbl = f'host="{host}",service="postgres",drive="vda"'
        lines.append(f"metric_lab_postgres_volume_total_bytes{{{lbl}}} {total:.6g}")
        lines.append(f"metric_lab_postgres_volume_free_bytes{{{lbl}}} {free:.6g}")
        lines.append(f"metric_lab_postgres_volume_used_bytes{{{lbl}}} {used:.6g}")
        lines.append(f"metric_lab_postgres_volume_usage_ratio{{{lbl}}} {ratio:.6g}")
    except Exception:
        collector_ok["postgres_volume"] = 0

    try:
        for dev, rates in get_disk_io_rates().items():
            lbl = (
                f'host="{host}",drive="{dev}",disk_number="0",'
                f'friendly_name="{dev}",bus_type="virtio"'
            )
            lines.append(f"metric_lab_host_disk_info{{{lbl}}} 1")
            lines.append(
                f"metric_lab_host_disk_read_bytes_per_second{{{lbl}}} "
                f"{rates['read_bytes_per_sec']:.6g}"
            )
            lines.append(
                f"metric_lab_host_disk_write_bytes_per_second{{{lbl}}} "
                f"{rates['write_bytes_per_sec']:.6g}"
            )
    except Exception:
        collector_ok["host_disk"] = 0

    for name, ok in collector_ok.items():
        lines.append(
            f'metric_lab_host_exporter_collector_success'
            f'{{host="{host}",collector="{name}"}} {ok}'
        )

    return "\n".join(lines) + "\n"


_cache = {"payload": "", "expires": 0}
_cache_lock = threading.Lock()


def cached_metrics():
    now = time.monotonic()
    with _cache_lock:
        if now < _cache["expires"] and _cache["payload"]:
            return _cache["payload"]
    payload = build_metrics()
    with _cache_lock:
        _cache["payload"] = payload
        _cache["expires"] = now + CACHE_SECONDS
    return payload


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path in ("/", "/metrics"):
            body = cached_metrics().encode()
            self.send_response(200)
            self.send_header(
                "Content-Type", "text/plain; version=0.0.4; charset=utf-8"
            )
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif self.path.startswith("/health"):
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"ok\n")
        else:
            self.send_error(404)

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    _read_proc_stat_cpu()
    get_disk_io_rates()
    time.sleep(1)
    server = http.server.HTTPServer(("0.0.0.0", LISTEN_PORT), Handler)
    print(f"Linux host exporter listening on :{LISTEN_PORT}")
    server.serve_forever()
