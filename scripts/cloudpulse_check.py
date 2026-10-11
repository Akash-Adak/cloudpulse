#!/usr/bin/env python3
"""
CloudPulse automation check (standard library only; boto3 optional).

Signs in to the CloudPulse API, reads the live summary and service states, prints a report and
exits with a status that CI/cron/monitoring can act on:

    0  everything monitored is healthy
    1  at least one service is DOWN or an incident is active
    2  the CloudPulse API itself could not be reached / authenticated

Optionally publishes the numbers to Amazon CloudWatch as custom metrics (--cloudwatch), which
lets you build CloudWatch alarms on top of CloudPulse. Requires boto3 and AWS credentials/role.

Configuration (environment variables, never command-line secrets):
    CLOUDPULSE_URL               base URL, e.g. http://127.0.0.1:8081
    CLOUDPULSE_ADMIN_USERNAME
    CLOUDPULSE_ADMIN_PASSWORD

Examples:
    python scripts/cloudpulse_check.py
    python scripts/cloudpulse_check.py --json
    python scripts/cloudpulse_check.py --cloudwatch --region ap-south-1
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request

EXIT_OK, EXIT_UNHEALTHY, EXIT_API_ERROR = 0, 1, 2


def call(base, path, token=None, payload=None, timeout=15):
    data = json.dumps(payload).encode() if payload is not None else None
    request = urllib.request.Request(base + path, data=data, method="POST" if data else "GET")
    request.add_header("Accept", "application/json")
    if data:
        request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode())


def collect(base, username, password):
    token = call(base, "/api/auth/login", payload={"username": username, "password": password})["token"]
    return {
        "summary": call(base, "/api/summary", token),
        "services": call(base, "/api/services", token),
        "incidents": call(base, "/api/incidents?status=active&limit=50", token),
    }


def publish_cloudwatch(summary, region, namespace):
    try:
        import boto3  # optional dependency
    except ImportError:
        print("boto3 is not installed; run: pip install boto3", file=sys.stderr)
        raise SystemExit(EXIT_API_ERROR)
    metrics = [
        ("ServicesUp", summary["up"]),
        ("ServicesDown", summary["down"]),
        ("ActiveIncidents", summary["activeIncidents"]),
    ]
    if summary.get("avgResponseMs1h") is not None:  # never publish a made-up value
        metrics.append(("AvgResponseMs1h", summary["avgResponseMs1h"]))
    client = boto3.client("cloudwatch", region_name=region)
    client.put_metric_data(
        Namespace=namespace,
        MetricData=[{"MetricName": name, "Value": float(value), "Unit": "None"} for name, value in metrics],
    )
    print(f"Published {len(metrics)} metrics to CloudWatch namespace '{namespace}'.")


def render(data):
    s = data["summary"]
    lines = [
        f"CloudPulse report ({s['generatedAt']})",
        f"  services: {s['totalServices']}  up: {s['up']}  down: {s['down']}  "
        f"not checked yet: {s['unknown']}  paused: {s['disabledServices']}",
        f"  active incidents: {s['activeIncidents']}",
        "  average response (last hour): "
        + (f"{s['avgResponseMs1h']} ms" if s.get("avgResponseMs1h") is not None else "no data"),
        "",
    ]
    for svc in data["services"]:
        state = "PAUSED" if not svc["enabled"] else svc["status"]
        rt = f"{svc['lastResponseTimeMs']} ms" if svc.get("lastResponseTimeMs") is not None else "no data"
        lines.append(f"  [{state:<7}] {svc['name']:<30} {rt:>10}  {svc['url']}")
    for inc in data["incidents"]:
        lines.append(f"  ! incident #{inc['id']} {inc['serviceName']}: {inc.get('reason')} (since {inc['startedAt']})")
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description="Check CloudPulse and report service health.")
    parser.add_argument("--url", default=os.environ.get("CLOUDPULSE_URL", "http://127.0.0.1:8081"))
    parser.add_argument("--json", action="store_true", help="print raw JSON instead of a text report")
    parser.add_argument("--cloudwatch", action="store_true", help="publish summary metrics to CloudWatch")
    parser.add_argument("--region", default=os.environ.get("AWS_REGION", "ap-south-1"))
    parser.add_argument("--namespace", default="CloudPulse")
    args = parser.parse_args(argv)

    username = os.environ.get("CLOUDPULSE_ADMIN_USERNAME")
    password = os.environ.get("CLOUDPULSE_ADMIN_PASSWORD")
    if not username or not password:
        print("Set CLOUDPULSE_ADMIN_USERNAME and CLOUDPULSE_ADMIN_PASSWORD.", file=sys.stderr)
        return EXIT_API_ERROR

    try:
        data = collect(args.url.rstrip("/"), username, password)
    except urllib.error.HTTPError as e:
        print(f"CloudPulse API returned HTTP {e.code}.", file=sys.stderr)
        return EXIT_API_ERROR
    except (urllib.error.URLError, TimeoutError, KeyError, ValueError) as e:
        print(f"Could not query CloudPulse at {args.url}: {e}", file=sys.stderr)
        return EXIT_API_ERROR

    print(json.dumps(data, indent=2) if args.json else render(data))
    if args.cloudwatch:
        publish_cloudwatch(data["summary"], args.region, args.namespace)

    unhealthy = data["summary"]["down"] > 0 or data["summary"]["activeIncidents"] > 0
    return EXIT_UNHEALTHY if unhealthy else EXIT_OK


if __name__ == "__main__":
    sys.exit(main())
