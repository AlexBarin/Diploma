import random
import uuid
import math
import time

from locust import HttpUser, LoadTestShape, between, task
from scenario import WEEKDAY_PHASES, WEEKEND_PHASES, DAY_SECONDS, WEEK_SECONDS


PRODUCT_IDS = [
    "sock-classic-blue",
    "sock-neon-run",
    "sock-merino-black",
    "sock-winter-thermal",
    "sock-office-gray",
    "sock-kids-orange",
    "sock-trail-green",
    "sock-compression-red",
    "sock-cotton-white",
    "sock-luxury-silk",
    "sock-gift-pack",
    "sock-ankle-light",
]

SEARCH_TERMS = [
    "sock",
    "sport",
    "premium",
    "winter",
    "business",
    "basic",
    "bundle",
]

def _build_phase_table(phases):
    table = []
    cursor = 0
    for p in phases:
        table.append((cursor, cursor + p.duration_seconds, p.target_users, p.spawn_rate))
        cursor += p.duration_seconds
    return table

_WEEKDAY_TABLE = _build_phase_table(WEEKDAY_PHASES)
_WEEKEND_TABLE = _build_phase_table(WEEKEND_PHASES)

_START_TIME = time.monotonic()


class MetricLabBaseUser(HttpUser):
    abstract = True
    wait_time = between(1, 3)

    def on_start(self):
        self.session_id = f"locust-{uuid.uuid4().hex[:12]}"
        self.headers = {"X-Session-Id": self.session_id}
        self.last_product_id = random.choice(PRODUCT_IDS)
        self.client.get("/api/home", headers=self.headers, name="/api/home")

    def browse_catalog(self):
        self.client.get("/api/catalog", headers=self.headers, name="/api/catalog")

    def search(self):
        term = random.choice(SEARCH_TERMS)
        self.client.get(f"/api/search?q={term}", headers=self.headers, name="/api/search")

    def view_product(self):
        self.last_product_id = random.choice(PRODUCT_IDS)
        self.client.get(
            f"/api/product/{self.last_product_id}",
            headers=self.headers,
            name="/api/product",
        )

    def add_to_cart(self):
        product_id = random.choice(PRODUCT_IDS)
        self.last_product_id = product_id
        self.client.post(
            "/api/cart/add",
            headers=self.headers,
            json={"product_id": product_id, "quantity": random.randint(1, 2)},
            name="/api/cart/add",
        )

    def view_cart(self):
        self.client.get("/api/cart", headers=self.headers, name="/api/cart")

    def checkout(self):
        self.add_to_cart()
        self.client.post("/api/checkout", headers=self.headers, name="/api/checkout")


class WindowShopper(MetricLabBaseUser):
    weight = 6
    wait_time = between(3, 5)

    @task(4)
    def home_and_catalog(self):
        self.client.get("/api/home", headers=self.headers, name="/api/home")
        self.browse_catalog()

    @task(3)
    def search_and_view(self):
        self.search()
        self.view_product()

    @task(1)
    def peek_into_cart(self):
        self.add_to_cart()
        self.view_cart()


class IntentBuyer(MetricLabBaseUser):
    weight = 3
    wait_time = between(2, 4)

    @task(3)
    def browse_then_buy(self):
        self.browse_catalog()
        self.view_product()
        self.add_to_cart()
        self.view_cart()

    @task(2)
    def search_then_checkout(self):
        self.search()
        self.view_product()
        self.checkout()


class FlashSaleHunter(MetricLabBaseUser):
    weight = 1
    wait_time = between(2, 3)

    @task(2)
    def aggressive_checkout(self):
        self.view_product()
        self.add_to_cart()
        self.checkout()

    @task(1)
    def fast_search(self):
        self.search()
        self.search()
        self.view_product()


def _elapsed_seconds() -> float:
    return time.monotonic() - _START_TIME


class BusinessCycleShape(LoadTestShape):

    def _week_position(self) -> tuple[int, int]:
        elapsed = _elapsed_seconds()
        week_pos = elapsed % WEEK_SECONDS
        day_of_week = int(week_pos // DAY_SECONDS)
        seconds_into_day = int(week_pos % DAY_SECONDS)
        return day_of_week, seconds_into_day

    def _phase_profile(self) -> tuple[int, int, int, int]:
        day_of_week, sec_in_day = self._week_position()
        phases = _WEEKEND_TABLE if day_of_week >= 5 else _WEEKDAY_TABLE
        for start, end, users, rate in phases:
            if start <= sec_in_day < end:
                phase_time = sec_in_day - start
                phase_duration = end - start
                return phase_time, phase_duration, users, rate
        return 0, 188, 8, 1

    def _user_multiplier(self, phase_time: int, phase_duration: int) -> float:
        primary = 0.10 * math.sin((2.0 * math.pi * phase_time) / max(phase_duration, 1))
        secondary = 0.04 * math.sin((2.0 * math.pi * phase_time) / 60.0)
        return 1.0 + primary + secondary

    def tick(self):
        phase_time, phase_duration, base_users, spawn_rate = self._phase_profile()
        target_users = max(1, round(base_users * self._user_multiplier(phase_time, phase_duration)))
        return target_users, spawn_rate
