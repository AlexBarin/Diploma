#!/usr/bin/env python3
import json, os

SERVICES = [
    {
        "name": "Application (metric-lab-app)",
        "job": "metric-lab-app",
        "metrics": [
            {"name": "lab_http_requests_total",                   "title": "HTTP Request Rate",              "unit": "reqps",  "rate": True},
            {"name": "lab_http_request_duration_seconds_sum",     "title": "HTTP Request Duration (sum)",    "unit": "s",      "rate": True},
            {"name": "lab_http_request_duration_seconds_count",   "title": "HTTP Request Count Rate",        "unit": "reqps",  "rate": True},
            {"name": "lab_active_sessions",                       "title": "Active Sessions",                "unit": "short",  "rate": False},
            {"name": "lab_db_operation_duration_seconds_sum",     "title": "DB Operation Duration (sum)",    "unit": "s",      "rate": True},
            {"name": "lab_db_operation_duration_seconds_count",   "title": "DB Operation Count Rate",        "unit": "ops",    "rate": True},
            {"name": "lab_cache_requests_total",                  "title": "Cache Requests Rate",            "unit": "reqps",  "rate": True},
            {"name": "lab_cart_items_total",                      "title": "Cart Items",                     "unit": "short",  "rate": False},
            {"name": "lab_orders_pending_total",                  "title": "Pending Orders",                 "unit": "short",  "rate": False},
            {"name": "lab_checkout_value_rubles_sum",             "title": "Checkout Value (sum)",            "unit": "currencyRUB", "rate": True},
            {"name": "lab_queue_depth",                           "title": "Queue Depth",                    "unit": "short",  "rate": False},
            {"name": "lab_error_injection_ratio",                 "title": "Error Injection Ratio",          "unit": "percentunit", "rate": False},
        ],
    },
    {
        "name": "Worker (metric-lab-worker)",
        "job": "metric-lab-worker",
        "metrics": [
            {"name": "lab_worker_jobs_total",                     "title": "Worker Job Rate",                "unit": "ops",    "rate": True},
            {"name": "lab_worker_job_duration_seconds_sum",       "title": "Worker Job Duration (sum)",      "unit": "s",      "rate": True},
            {"name": "lab_worker_job_duration_seconds_count",     "title": "Worker Job Count Rate",          "unit": "ops",    "rate": True},
            {"name": "lab_worker_backpressure_ratio",             "title": "Worker Backpressure",            "unit": "percentunit", "rate": False},
            {"name": "lab_queue_depth",                           "title": "Queue Depth",                    "unit": "short",  "rate": False},
            {"name": "lab_orders_pending_total",                  "title": "Pending Orders",                 "unit": "short",  "rate": False},
        ],
    },
    {
        "name": "Redis",
        "job": "redis",
        "metrics": [
            {"name": "redis_memory_used_bytes",                   "title": "Memory Used",                    "unit": "bytes",  "rate": False},
            {"name": "redis_connected_clients",                   "title": "Connected Clients",              "unit": "short",  "rate": False},
            {"name": "redis_blocked_clients",                     "title": "Blocked Clients",                "unit": "short",  "rate": False},
            {"name": "redis_commands_processed_total",            "title": "Commands Rate",                  "unit": "ops",    "rate": True},
            {"name": "redis_keyspace_hits_total",                 "title": "Keyspace Hit Rate",              "unit": "ops",    "rate": True},
            {"name": "redis_keyspace_misses_total",               "title": "Keyspace Miss Rate",             "unit": "ops",    "rate": True},
            {"name": "redis_evicted_keys_total",                  "title": "Evicted Keys Rate",              "unit": "ops",    "rate": True},
            {"name": "redis_db_keys",                             "title": "DB Keys Count",                  "unit": "short",  "rate": False},
            {"name": "redis_net_input_bytes_total",               "title": "Network Input Rate",             "unit": "Bps",    "rate": True},
            {"name": "redis_net_output_bytes_total",              "title": "Network Output Rate",            "unit": "Bps",    "rate": True},
            {"name": "redis_cpu_sys_seconds_total",               "title": "CPU System Rate",                "unit": "percentunit", "rate": True},
            {"name": "redis_cpu_user_seconds_total",              "title": "CPU User Rate",                  "unit": "percentunit", "rate": True},
        ],
    },
    {
        "name": "PostgreSQL",
        "job": "postgres",
        "metrics": [
            {"name": "pg_database_size_bytes",                    "title": "Database Size",                  "unit": "bytes",  "rate": False},
            {"name": "pg_stat_database_numbackends",              "title": "Active Backends",                "unit": "short",  "rate": False},
            {"name": "pg_stat_activity_count",                    "title": "Activity Count",                 "unit": "short",  "rate": False},
            {"name": "pg_stat_database_xact_commit",              "title": "Transactions Committed",         "unit": "short",  "rate": False},
            {"name": "pg_stat_database_xact_rollback",            "title": "Transactions Rolled Back",       "unit": "short",  "rate": False},
            {"name": "pg_stat_database_tup_fetched",              "title": "Tuples Fetched",                 "unit": "short",  "rate": False},
            {"name": "pg_stat_database_tup_inserted",             "title": "Tuples Inserted",                "unit": "short",  "rate": False},
            {"name": "pg_stat_database_tup_updated",              "title": "Tuples Updated",                 "unit": "short",  "rate": False},
            {"name": "pg_stat_database_tup_deleted",              "title": "Tuples Deleted",                 "unit": "short",  "rate": False},
            {"name": "pg_stat_database_deadlocks",                "title": "Deadlocks",                      "unit": "short",  "rate": False},
            {"name": "pg_locks_count",                            "title": "Locks Count",                    "unit": "short",  "rate": False},
            {"name": "pg_stat_database_blks_hit",                 "title": "Buffer Cache Hits",              "unit": "short",  "rate": False},
            {"name": "pg_stat_database_blks_read",                "title": "Disk Blocks Read",               "unit": "short",  "rate": False},
        ],
    },
    {
        "name": "Host / Infrastructure",
        "job": "metric-lab-host",
        "metrics": [
            {"name": "metric_lab_host_cpu_usage_percent",         "title": "Host CPU Usage",                 "unit": "percent", "rate": False},
            {"name": "metric_lab_host_memory_usage_ratio",        "title": "Host Memory Usage",              "unit": "percentunit", "rate": False},
            {"name": "metric_lab_host_memory_used_bytes",         "title": "Host Memory Used",               "unit": "bytes",  "rate": False},
            {"name": "metric_lab_host_disk_read_bytes_per_second","title": "Disk Read Throughput",            "unit": "Bps",    "rate": False},
            {"name": "metric_lab_host_disk_write_bytes_per_second","title": "Disk Write Throughput",          "unit": "Bps",    "rate": False},
            {"name": "metric_lab_container_cpu_usage_percent",    "title": "Container CPU Usage",            "unit": "percent", "rate": False},
            {"name": "metric_lab_container_memory_usage_bytes",   "title": "Container Memory Usage",         "unit": "bytes",  "rate": False},
            {"name": "metric_lab_container_memory_usage_ratio",   "title": "Container Memory Ratio",         "unit": "percentunit", "rate": False},
            {"name": "metric_lab_postgres_volume_usage_ratio",    "title": "Postgres Volume Usage",          "unit": "percentunit", "rate": False},
        ],
    },
    {
        "name": "Prometheus (self-monitoring)",
        "job": "prometheus",
        "metrics": [
            {"name": "prometheus_tsdb_head_series",               "title": "Head Series",                    "unit": "short",  "rate": False},
            {"name": "prometheus_engine_queries",                 "title": "Active Queries",                 "unit": "short",  "rate": False},
            {"name": "prometheus_tsdb_storage_blocks_bytes",      "title": "Storage Size",                   "unit": "bytes",  "rate": False},
        ],
    },
]


def make_threshold_panel(metric_name, title, job, unit, use_rate, panel_id, x, y):
    if use_rate:
        raw_expr = f'sum(rate({metric_name}{{job="{job}"}}[1m]))'
    else:
        raw_expr = f'sum({metric_name}{{job="{job}"}})'

    upper_expr = f'adaptive_threshold_upper{{metric="{metric_name}", job="{job}"}}'
    lower_expr = f'adaptive_threshold_lower{{metric="{metric_name}", job="{job}"}}'
    midline_expr = f'adaptive_threshold_midline{{metric="{metric_name}", job="{job}"}}'

    return {
        "datasource": {"type": "prometheus"},
        "fieldConfig": {
            "defaults": {
                "color": {"mode": "fixed", "fixedColor": "blue"},
                "custom": {
                    "drawStyle": "line",
                    "lineInterpolation": "smooth",
                    "lineWidth": 2,
                    "fillOpacity": 0,
                    "showPoints": "never",
                    "spanNulls": True,
                    "stacking": {"mode": "none"},
                    "thresholdsStyle": {"mode": "off"},
                    "axisBorderShow": False,
                },
                "unit": unit,
            },
            "overrides": [
                {
                    "matcher": {"id": "byName", "options": "Upper"},
                    "properties": [
                        {"id": "color", "value": {"fixedColor": "red", "mode": "fixed"}},
                        {"id": "custom.lineStyle", "value": {"fill": "dash", "dash": [10, 10]}},
                        {"id": "custom.lineWidth", "value": 1},
                        {"id": "custom.fillBelowTo", "value": "Lower"},
                        {"id": "custom.fillOpacity", "value": 8},
                    ],
                },
                {
                    "matcher": {"id": "byName", "options": "Lower"},
                    "properties": [
                        {"id": "color", "value": {"fixedColor": "red", "mode": "fixed"}},
                        {"id": "custom.lineStyle", "value": {"fill": "dash", "dash": [10, 10]}},
                        {"id": "custom.lineWidth", "value": 1},
                    ],
                },
                {
                    "matcher": {"id": "byName", "options": "Midline"},
                    "properties": [
                        {"id": "color", "value": {"fixedColor": "green", "mode": "fixed"}},
                        {"id": "custom.lineStyle", "value": {"fill": "dash", "dash": [4, 6]}},
                        {"id": "custom.lineWidth", "value": 1},
                    ],
                },
                {
                    "matcher": {"id": "byName", "options": "Current"},
                    "properties": [
                        {"id": "color", "value": {"fixedColor": "#3274D9", "mode": "fixed"}},
                        {"id": "custom.lineWidth", "value": 2},
                    ],
                },
            ],
        },
        "gridPos": {"h": 8, "w": 12, "x": x, "y": y},
        "id": panel_id,
        "options": {
            "legend": {
                "calcs": ["lastNotNull"],
                "displayMode": "list",
                "placement": "bottom",
                "showLegend": True,
            },
            "tooltip": {"mode": "multi", "sort": "none"},
        },
        "targets": [
            {"datasource": {"type": "prometheus"}, "expr": raw_expr,     "legendFormat": "Current", "refId": "A"},
            {"datasource": {"type": "prometheus"}, "expr": upper_expr,   "legendFormat": "Upper",   "refId": "B"},
            {"datasource": {"type": "prometheus"}, "expr": lower_expr,   "legendFormat": "Lower",   "refId": "C"},
            {"datasource": {"type": "prometheus"}, "expr": midline_expr, "legendFormat": "Midline",  "refId": "D"},
        ],
        "title": title,
        "type": "timeseries",
    }


def generate_thresholds_overview():
    panels = []
    panel_id = 1
    y = 0

    for svc in SERVICES:
        panels.append({
            "collapsed": False,
            "gridPos": {"h": 1, "w": 24, "x": 0, "y": y},
            "id": panel_id,
            "panels": [],
            "title": svc["name"],
            "type": "row",
        })
        panel_id += 1
        y += 1

        for i, m in enumerate(svc["metrics"]):
            x = (i % 2) * 12
            if i > 0 and i % 2 == 0:
                y += 8

            panel = make_threshold_panel(
                m["name"], m["title"], svc["job"], m["unit"], m["rate"],
                panel_id, x, y,
            )
            panels.append(panel)
            panel_id += 1

        y += 8

    return {
        "annotations": {"list": [
            {"builtIn": 1, "datasource": {"type": "grafana", "uid": "-- Grafana --"},
             "enable": True, "hide": True, "iconColor": "rgba(0, 211, 255, 1)",
             "name": "Annotations & Alerts", "type": "dashboard"},
        ]},
        "description": "Dynamic threshold corridors for all monitored metrics, grouped by service. Each panel shows the current value (blue), upper/lower bounds (red dashed + shaded), and midline (green dashed).",
        "editable": True,
        "graphTooltip": 1,
        "id": None,
        "links": [],
        "panels": panels,
        "refresh": "30s",
        "schemaVersion": 39,
        "tags": ["adaptive-thresholds"],
        "templating": {"list": []},
        "time": {"from": "now-1h", "to": "now"},
        "timepicker": {},
        "timezone": "browser",
        "title": "Dynamic Thresholds Overview",
        "uid": "adaptive-thresholds-overview",
        "version": 1,
    }


def generate_anomaly_feed():
    return {
        "annotations": {"list": []},
        "description": "Real-time anomaly detection feed with severity breakdown.",
        "editable": True,
        "graphTooltip": 1,
        "id": None,
        "links": [],
        "panels": [
            {"type": "row", "title": "Summary", "collapsed": False,
             "gridPos": {"h": 1, "w": 24, "x": 0, "y": 0}, "id": 1, "panels": []},
            {
                "type": "stat", "title": "Tracked Metrics",
                "datasource": {"type": "prometheus"},
                "targets": [{"expr": "adaptive_engine_metrics_tracked_total", "legendFormat": "", "refId": "A"}],
                "fieldConfig": {"defaults": {"color": {"mode": "thresholds"}, "thresholds": {"mode": "absolute", "steps": [{"color": "blue", "value": None}]}}},
                "gridPos": {"h": 4, "w": 6, "x": 0, "y": 1}, "id": 2,
                "options": {"colorMode": "value", "graphMode": "none", "textMode": "value"},
            },
            {
                "type": "stat", "title": "Last Compute Duration",
                "datasource": {"type": "prometheus"},
                "targets": [{"expr": "adaptive_engine_compute_duration_seconds{quantile=\"0.5\"}", "legendFormat": "", "refId": "A"}],
                "fieldConfig": {"defaults": {"unit": "s", "color": {"mode": "thresholds"}, "thresholds": {"mode": "absolute", "steps": [{"color": "green", "value": None}, {"color": "yellow", "value": 1}, {"color": "red", "value": 5}]}}},
                "gridPos": {"h": 4, "w": 6, "x": 6, "y": 1}, "id": 3,
                "options": {"colorMode": "value", "graphMode": "area", "textMode": "value"},
            },
            {
                "type": "stat", "title": "Max Anomaly Score (now)",
                "datasource": {"type": "prometheus"},
                "targets": [{"expr": "max(adaptive_anomaly_score)", "legendFormat": "", "refId": "A"}],
                "fieldConfig": {"defaults": {"color": {"mode": "thresholds"}, "thresholds": {"mode": "absolute", "steps": [{"color": "green", "value": None}, {"color": "yellow", "value": 1}, {"color": "red", "value": 2}]}, "decimals": 2}},
                "gridPos": {"h": 4, "w": 6, "x": 12, "y": 1}, "id": 4,
                "options": {"colorMode": "value", "graphMode": "area", "textMode": "value"},
            },
            {
                "type": "stat", "title": "Anomalies Detected (total)",
                "datasource": {"type": "prometheus"},
                "targets": [{"expr": "sum(adaptive_anomaly_detected_total)", "legendFormat": "", "refId": "A"}],
                "fieldConfig": {"defaults": {"color": {"mode": "thresholds"}, "thresholds": {"mode": "absolute", "steps": [{"color": "green", "value": None}, {"color": "orange", "value": 10}, {"color": "red", "value": 50}]}}},
                "gridPos": {"h": 4, "w": 6, "x": 18, "y": 1}, "id": 5,
                "options": {"colorMode": "value", "graphMode": "area", "textMode": "value"},
            },
            {"type": "row", "title": "Anomaly Scores", "collapsed": False,
             "gridPos": {"h": 1, "w": 24, "x": 0, "y": 5}, "id": 6, "panels": []},
            {
                "type": "timeseries", "title": "Anomaly Scores Over Time",
                "datasource": {"type": "prometheus"},
                "targets": [{"expr": "adaptive_anomaly_score > 0", "legendFormat": "{{metric}}", "refId": "A"}],
                "fieldConfig": {
                    "defaults": {
                        "color": {"mode": "palette-classic"},
                        "custom": {"drawStyle": "points", "pointSize": 6, "showPoints": "always", "spanNulls": False, "lineWidth": 0},
                        "decimals": 2,
                    },
                },
                "gridPos": {"h": 8, "w": 24, "x": 0, "y": 6}, "id": 7,
                "options": {"legend": {"displayMode": "table", "placement": "right", "calcs": ["max", "lastNotNull"]},
                            "tooltip": {"mode": "multi"}},
            },
            {"type": "row", "title": "Anomaly Rates by Service", "collapsed": False,
             "gridPos": {"h": 1, "w": 24, "x": 0, "y": 14}, "id": 8, "panels": []},
            {
                "type": "timeseries", "title": "Anomaly Detection Rate by Job",
                "datasource": {"type": "prometheus"},
                "targets": [
                    {"expr": 'sum by (job) (rate(adaptive_anomaly_detected_total[5m]))', "legendFormat": "{{job}}", "refId": "A"},
                ],
                "fieldConfig": {"defaults": {"color": {"mode": "palette-classic"}, "custom": {"drawStyle": "bars", "fillOpacity": 50, "stacking": {"mode": "normal"}}, "unit": "ops"}},
                "gridPos": {"h": 8, "w": 12, "x": 0, "y": 15}, "id": 9,
                "options": {"legend": {"displayMode": "list", "placement": "bottom"}, "tooltip": {"mode": "multi"}},
            },
            {
                "type": "timeseries", "title": "Anomaly Detection Rate by Severity",
                "datasource": {"type": "prometheus"},
                "targets": [
                    {"expr": 'sum by (severity) (rate(adaptive_anomaly_detected_total[5m]))', "legendFormat": "{{severity}}", "refId": "A"},
                ],
                "fieldConfig": {
                    "defaults": {"color": {"mode": "palette-classic"}, "custom": {"drawStyle": "bars", "fillOpacity": 50, "stacking": {"mode": "normal"}}, "unit": "ops"},
                    "overrides": [
                        {"matcher": {"id": "byName", "options": "critical"}, "properties": [{"id": "color", "value": {"fixedColor": "red", "mode": "fixed"}}]},
                        {"matcher": {"id": "byName", "options": "warning"}, "properties": [{"id": "color", "value": {"fixedColor": "orange", "mode": "fixed"}}]},
                    ],
                },
                "gridPos": {"h": 8, "w": 12, "x": 12, "y": 15}, "id": 10,
                "options": {"legend": {"displayMode": "list", "placement": "bottom"}, "tooltip": {"mode": "multi"}},
            },
        ],
        "refresh": "30s",
        "schemaVersion": 39,
        "tags": ["adaptive-thresholds"],
        "templating": {"list": []},
        "time": {"from": "now-1h", "to": "now"},
        "timepicker": {},
        "timezone": "browser",
        "title": "Anomaly Feed",
        "uid": "adaptive-anomaly-feed",
        "version": 1,
    }


def generate_engine_health():
    return {
        "annotations": {"list": []},
        "description": "Health and performance of the adaptive-threshold-engine service.",
        "editable": True,
        "graphTooltip": 1,
        "id": None,
        "links": [],
        "panels": [
            {"type": "row", "title": "Overview", "collapsed": False,
             "gridPos": {"h": 1, "w": 24, "x": 0, "y": 0}, "id": 1, "panels": []},
            {
                "type": "stat", "title": "Metrics Tracked",
                "datasource": {"type": "prometheus"},
                "targets": [{"expr": "adaptive_engine_metrics_tracked_total", "refId": "A"}],
                "fieldConfig": {"defaults": {"color": {"mode": "thresholds"}, "thresholds": {"mode": "absolute", "steps": [{"color": "blue", "value": None}]}}},
                "gridPos": {"h": 4, "w": 6, "x": 0, "y": 1}, "id": 2,
                "options": {"colorMode": "value", "graphMode": "none", "textMode": "value"},
            },
            {
                "type": "stat", "title": "Compute Duration (p50)",
                "datasource": {"type": "prometheus"},
                "targets": [{"expr": 'adaptive_engine_compute_duration_seconds{quantile="0.5"}', "refId": "A"}],
                "fieldConfig": {"defaults": {"unit": "s", "color": {"mode": "thresholds"}, "thresholds": {"mode": "absolute", "steps": [{"color": "green", "value": None}, {"color": "yellow", "value": 1}, {"color": "red", "value": 5}]}}},
                "gridPos": {"h": 4, "w": 6, "x": 6, "y": 1}, "id": 3,
                "options": {"colorMode": "value", "graphMode": "area", "textMode": "value"},
            },
            {
                "type": "stat", "title": "Compute Duration (p99)",
                "datasource": {"type": "prometheus"},
                "targets": [{"expr": 'adaptive_engine_compute_duration_seconds{quantile="0.99"}', "refId": "A"}],
                "fieldConfig": {"defaults": {"unit": "s", "color": {"mode": "thresholds"}, "thresholds": {"mode": "absolute", "steps": [{"color": "green", "value": None}, {"color": "yellow", "value": 2}, {"color": "red", "value": 10}]}}},
                "gridPos": {"h": 4, "w": 6, "x": 12, "y": 1}, "id": 4,
                "options": {"colorMode": "value", "graphMode": "area", "textMode": "value"},
            },
            {
                "type": "stat", "title": "Last Compute Age",
                "datasource": {"type": "prometheus"},
                "targets": [{"expr": "time() - adaptive_engine_last_compute_timestamp_seconds", "refId": "A"}],
                "fieldConfig": {"defaults": {"unit": "s", "color": {"mode": "thresholds"}, "thresholds": {"mode": "absolute", "steps": [{"color": "green", "value": None}, {"color": "yellow", "value": 60}, {"color": "red", "value": 120}]}}},
                "gridPos": {"h": 4, "w": 6, "x": 18, "y": 1}, "id": 5,
                "options": {"colorMode": "value", "graphMode": "none", "textMode": "value"},
            },
            {"type": "row", "title": "Performance", "collapsed": False,
             "gridPos": {"h": 1, "w": 24, "x": 0, "y": 5}, "id": 6, "panels": []},
            {
                "type": "timeseries", "title": "Compute Duration Over Time",
                "datasource": {"type": "prometheus"},
                "targets": [
                    {"expr": 'adaptive_engine_compute_duration_seconds{quantile="0.5"}', "legendFormat": "p50", "refId": "A"},
                    {"expr": 'adaptive_engine_compute_duration_seconds{quantile="0.95"}', "legendFormat": "p95", "refId": "B"},
                    {"expr": 'adaptive_engine_compute_duration_seconds{quantile="0.99"}', "legendFormat": "p99", "refId": "C"},
                ],
                "fieldConfig": {"defaults": {"unit": "s", "color": {"mode": "palette-classic"}, "custom": {"drawStyle": "line", "fillOpacity": 10, "lineWidth": 2, "showPoints": "never"}}},
                "gridPos": {"h": 8, "w": 12, "x": 0, "y": 6}, "id": 7,
                "options": {"legend": {"displayMode": "list", "placement": "bottom"}, "tooltip": {"mode": "multi"}},
            },
            {
                "type": "piechart", "title": "Algorithm Distribution",
                "datasource": {"type": "prometheus"},
                "targets": [{"expr": "count by (algorithm) (adaptive_threshold_upper)", "legendFormat": "{{algorithm}}", "refId": "A", "instant": True}],
                "fieldConfig": {"defaults": {"color": {"mode": "palette-classic"}}},
                "gridPos": {"h": 8, "w": 12, "x": 12, "y": 6}, "id": 8,
                "options": {"legend": {"displayMode": "table", "placement": "right", "values": ["value", "percent"]}, "pieType": "donut", "reduceOptions": {"calcs": ["lastNotNull"]}},
            },
        ],
        "refresh": "30s",
        "schemaVersion": 39,
        "tags": ["adaptive-thresholds"],
        "templating": {"list": []},
        "time": {"from": "now-1h", "to": "now"},
        "timepicker": {},
        "timezone": "browser",
        "title": "Engine Health",
        "uid": "adaptive-engine-health",
        "version": 1,
    }


if __name__ == "__main__":
    out_dir = os.path.join(os.path.dirname(__file__), "grafana", "dashboards")
    os.makedirs(out_dir, exist_ok=True)

    for filename, gen_fn in [
        ("dynamic-thresholds-overview.json", generate_thresholds_overview),
        ("anomaly-feed.json", generate_anomaly_feed),
        ("engine-health.json", generate_engine_health),
    ]:
        path = os.path.join(out_dir, filename)
        with open(path, "w") as f:
            json.dump(gen_fn(), f, indent=2)
        print(f"Generated {path}")
