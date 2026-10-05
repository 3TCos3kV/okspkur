"""Замер времени ответа операций контракта.

    python3 bench/measure.py --label small --json bench/small.json
    python3 bench/measure.py --label working --json bench/working.json
    python3 bench/measure.py --compare bench/small.json bench/working.json

Каждая операция выполняется --warmup раз без учёта и --repeats раз с учётом. Время берётся из заголовков
сервиса: X-Response-Time-Ms (обработка запроса), X-Db-Time-Ms и X-Db-Queries (SQL за один запрос).
Бронирование занимает свободные слоты услуги 1 после 01.11.2026, отмена отменяет эти же записи:
замер добавляет warmup + repeats отменённых записей.
"""
import argparse
import json
import math
import platform
import statistics
import time
import urllib.error
import urllib.request

READS = [
    ("POST /api/auth/login", "POST", "/api/auth/login"),
    ("GET /api/bookings", "GET", "/api/bookings?page=1&size=20"),
    ("GET /api/bookings (фильтр)", "GET",
     "/api/bookings?status=active&specialistId=1&from=2026-10-01&to=2026-10-31&page=1&size=20"),
    ("GET /api/bookings/{id}", "GET", "/api/bookings/100"),
    ("GET /api/services", "GET", "/api/services"),
    ("GET /api/services/{id}/free-slots", "GET", "/api/services/1/free-slots?from=2026-10-12&to=2026-10-18"),
    ("GET /api/summary", "GET", "/api/summary?from=2026-09-01&to=2026-09-30"),
]


def request(base, method, path, token=None, body=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(base + path, data=data, method=method, headers=headers)
    try:
        response = urllib.request.urlopen(req)
    except urllib.error.HTTPError as error:
        raise SystemExit(f"{method} {path}: {error.code} {error.read().decode()}")
    with response:
        payload = response.read()
        return {
            "status": response.status,
            "total": float(response.headers["X-Response-Time-Ms"]),
            "db": float(response.headers["X-Db-Time-Ms"]),
            "queries": int(response.headers["X-Db-Queries"]),
            "json": json.loads(payload) if payload else None,
        }


def percentile(values, share):
    ordered = sorted(values)
    return ordered[math.ceil(share * len(ordered)) - 1]


def stats(samples):
    totals = [s["total"] for s in samples]
    return {
        "p50": statistics.median(totals),
        "p95": percentile(totals, 0.95),
        "max": max(totals),
        "db_p50": statistics.median(s["db"] for s in samples),
        "queries": statistics.median(s["queries"] for s in samples),
    }


def series(call, warmup, repeats):
    for _ in range(warmup):
        call()
    return [call() for _ in range(repeats)]


def measure(args):
    login = {"login": args.user, "password": args.password}
    token = request(args.base, "POST", "/api/auth/login", body=login)["json"]["token"]
    results = {}
    for name, method, path in READS:
        body = login if method == "POST" else None
        results[name] = stats(series(lambda: request(args.base, method, path, token, body), args.warmup, args.repeats))

    needed = args.warmup + args.repeats
    free = request(args.base, "GET", f"/api/services/1/free-slots?from=2026-11-01&to=2027-02-28&size=100", token)
    slot_ids = [item["id"] for item in free["json"]["items"]]
    if len(slot_ids) < needed:
        raise SystemExit(f"Свободных слотов {len(slot_ids)}, а нужно {needed}: уменьшите --repeats")
    created = []

    def book():
        response = request(args.base, "POST", "/api/bookings", token, {"slotId": slot_ids[len(created)], "serviceId": 1})
        created.append(response["json"]["id"])
        return response

    results["POST /api/bookings"] = stats(series(book, args.warmup, args.repeats))
    cancelled = iter(created)
    results["POST /api/bookings/{id}/cancel"] = stats(
        series(lambda: request(args.base, "POST", f"/api/bookings/{next(cancelled)}/cancel", token), args.warmup, args.repeats))

    conditions = {
        "label": args.label,
        "base": args.base,
        "user": args.user,
        "warmup": args.warmup,
        "repeats": args.repeats,
        "machine": f"{platform.system()} {platform.release()} {platform.machine()}",
        "at": time.strftime("%Y-%m-%d %H:%M"),
        "bookings_added": len(created),
    }
    print_measure(conditions, results)
    if args.json:
        with open(args.json, "w", encoding="utf-8") as file:
            json.dump({"conditions": conditions, "results": results}, file, ensure_ascii=False, indent=2)


def print_measure(conditions, results):
    print("объём: {label}; сервис {base}; учётная запись {user}; прогрев {warmup}, повторов {repeats}; "
          "{machine}; {at}; замер добавил отменённых записей: {bookings_added}".format(**conditions))
    print(f"{'операция':<36}{'p50':>9}{'p95':>9}{'макс':>9}{'в БД p50':>10}{'запросов':>10}")
    for name, s in results.items():
        print(f"{name:<36}{s['p50']:>9.2f}{s['p95']:>9.2f}{s['max']:>9.2f}{s['db_p50']:>10.2f}{s['queries']:>10g}")
    print("время — мс, по заголовку X-Response-Time-Ms")


def compare(small_path, working_path):
    small = json.load(open(small_path, encoding="utf-8"))["results"]
    working = json.load(open(working_path, encoding="utf-8"))["results"]
    print(f"{'операция':<36}{'p50 мал':>9}{'p95 мал':>9}{'p50 раб':>9}{'p95 раб':>9}{'рост p50':>10}")
    for name, s in small.items():
        w = working[name]
        print(f"{name:<36}{s['p50']:>9.2f}{s['p95']:>9.2f}{w['p50']:>9.2f}{w['p95']:>9.2f}{w['p50'] / s['p50']:>9.1f}x")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base", default="http://localhost:8080")
    parser.add_argument("--user", default="admin")
    parser.add_argument("--password", default="demo")
    parser.add_argument("--warmup", type=int, default=3)
    parser.add_argument("--repeats", type=int, default=30)
    parser.add_argument("--label", default="")
    parser.add_argument("--json")
    parser.add_argument("--compare", nargs=2, metavar=("SMALL", "WORKING"))
    args = parser.parse_args()
    if args.compare:
        compare(*args.compare)
    else:
        measure(args)


if __name__ == "__main__":
    main()
