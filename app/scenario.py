from dataclasses import dataclass
import random
import time
import math


@dataclass(frozen=True)
class Phase:
    name: str
    duration_seconds: int
    target_users: int
    spawn_rate: int
    browse_delay: tuple[float, float]
    search_delay: tuple[float, float]
    product_delay: tuple[float, float]
    cart_delay: tuple[float, float]
    checkout_delay: tuple[float, float]
    checkout_error_rate: float
    worker_delay: tuple[float, float]
    worker_error_rate: float
    request_cpu_burn_ms: int
    worker_cpu_burn_ms: int
    memory_growth_bytes: int
    memory_decay_bytes: int
    cache_ttl_seconds: int

WEEKDAY_PHASES = [
    Phase(
        name="night_quiet",
        duration_seconds=375,
        target_users=6,
        spawn_rate=1,
        browse_delay=(0.008, 0.018),
        search_delay=(0.015, 0.035),
        product_delay=(0.010, 0.025),
        cart_delay=(0.015, 0.030),
        checkout_delay=(0.030, 0.065),
        checkout_error_rate=0.0008,
        worker_delay=(0.030, 0.060),
        worker_error_rate=0.0005,
        request_cpu_burn_ms=1,
        worker_cpu_burn_ms=1,
        memory_growth_bytes=0,
        memory_decay_bytes=131072,
        cache_ttl_seconds=300,
    ),
    Phase(
        name="morning_ramp",
        duration_seconds=187,
        target_users=20,
        spawn_rate=2,
        browse_delay=(0.018, 0.040),
        search_delay=(0.030, 0.065),
        product_delay=(0.020, 0.050),
        cart_delay=(0.030, 0.060),
        checkout_delay=(0.055, 0.110),
        checkout_error_rate=0.0015,
        worker_delay=(0.055, 0.100),
        worker_error_rate=0.0012,
        request_cpu_burn_ms=2,
        worker_cpu_burn_ms=2,
        memory_growth_bytes=65536,
        memory_decay_bytes=0,
        cache_ttl_seconds=180,
    ),
    Phase(
        name="day_peak",
        duration_seconds=500,
        target_users=36,
        spawn_rate=3,
        browse_delay=(0.030, 0.065),
        search_delay=(0.050, 0.100),
        product_delay=(0.035, 0.080),
        cart_delay=(0.050, 0.100),
        checkout_delay=(0.100, 0.185),
        checkout_error_rate=0.003,
        worker_delay=(0.100, 0.180),
        worker_error_rate=0.003,
        request_cpu_burn_ms=3,
        worker_cpu_burn_ms=3,
        memory_growth_bytes=131072,
        memory_decay_bytes=0,
        cache_ttl_seconds=120,
    ),
    Phase(
        name="evening_wind",
        duration_seconds=250,
        target_users=18,
        spawn_rate=2,
        browse_delay=(0.020, 0.045),
        search_delay=(0.035, 0.075),
        product_delay=(0.025, 0.055),
        cart_delay=(0.035, 0.070),
        checkout_delay=(0.065, 0.130),
        checkout_error_rate=0.002,
        worker_delay=(0.065, 0.120),
        worker_error_rate=0.0018,
        request_cpu_burn_ms=2,
        worker_cpu_burn_ms=2,
        memory_growth_bytes=0,
        memory_decay_bytes=65536,
        cache_ttl_seconds=180,
    ),
    Phase(
        name="late_quiet",
        duration_seconds=188,
        target_users=8,
        spawn_rate=1,
        browse_delay=(0.010, 0.022),
        search_delay=(0.018, 0.042),
        product_delay=(0.012, 0.030),
        cart_delay=(0.018, 0.038),
        checkout_delay=(0.035, 0.075),
        checkout_error_rate=0.001,
        worker_delay=(0.035, 0.070),
        worker_error_rate=0.0008,
        request_cpu_burn_ms=1,
        worker_cpu_burn_ms=1,
        memory_growth_bytes=0,
        memory_decay_bytes=196608,
        cache_ttl_seconds=300,
    ),
]

WEEKEND_PHASES = [
    Phase(
        name="weekend_night",
        duration_seconds=375,
        target_users=4,
        spawn_rate=1,
        browse_delay=(0.006, 0.014),
        search_delay=(0.012, 0.028),
        product_delay=(0.008, 0.020),
        cart_delay=(0.012, 0.025),
        checkout_delay=(0.025, 0.050),
        checkout_error_rate=0.0005,
        worker_delay=(0.025, 0.050),
        worker_error_rate=0.0003,
        request_cpu_burn_ms=1,
        worker_cpu_burn_ms=1,
        memory_growth_bytes=0,
        memory_decay_bytes=196608,
        cache_ttl_seconds=300,
    ),
    Phase(
        name="weekend_morning",
        duration_seconds=187,
        target_users=10,
        spawn_rate=2,
        browse_delay=(0.014, 0.032),
        search_delay=(0.025, 0.050),
        product_delay=(0.016, 0.038),
        cart_delay=(0.025, 0.048),
        checkout_delay=(0.042, 0.085),
        checkout_error_rate=0.001,
        worker_delay=(0.042, 0.080),
        worker_error_rate=0.0008,
        request_cpu_burn_ms=1,
        worker_cpu_burn_ms=1,
        memory_growth_bytes=32768,
        memory_decay_bytes=0,
        cache_ttl_seconds=240,
    ),
    Phase(
        name="weekend_day",
        duration_seconds=500,
        target_users=20,
        spawn_rate=2,
        browse_delay=(0.022, 0.050),
        search_delay=(0.038, 0.078),
        product_delay=(0.026, 0.060),
        cart_delay=(0.038, 0.075),
        checkout_delay=(0.070, 0.135),
        checkout_error_rate=0.002,
        worker_delay=(0.070, 0.130),
        worker_error_rate=0.0018,
        request_cpu_burn_ms=2,
        worker_cpu_burn_ms=2,
        memory_growth_bytes=65536,
        memory_decay_bytes=0,
        cache_ttl_seconds=150,
    ),
    Phase(
        name="weekend_evening",
        duration_seconds=250,
        target_users=12,
        spawn_rate=2,
        browse_delay=(0.016, 0.036),
        search_delay=(0.028, 0.058),
        product_delay=(0.020, 0.042),
        cart_delay=(0.028, 0.055),
        checkout_delay=(0.050, 0.100),
        checkout_error_rate=0.0012,
        worker_delay=(0.050, 0.095),
        worker_error_rate=0.001,
        request_cpu_burn_ms=1,
        worker_cpu_burn_ms=1,
        memory_growth_bytes=0,
        memory_decay_bytes=98304,
        cache_ttl_seconds=240,
    ),
    Phase(
        name="weekend_late",
        duration_seconds=188,
        target_users=5,
        spawn_rate=1,
        browse_delay=(0.008, 0.018),
        search_delay=(0.015, 0.034),
        product_delay=(0.010, 0.024),
        cart_delay=(0.015, 0.030),
        checkout_delay=(0.028, 0.060),
        checkout_error_rate=0.0006,
        worker_delay=(0.028, 0.055),
        worker_error_rate=0.0005,
        request_cpu_burn_ms=1,
        worker_cpu_burn_ms=1,
        memory_growth_bytes=0,
        memory_decay_bytes=262144,
        cache_ttl_seconds=300,
    ),
]

DAY_SECONDS = 1500
WEEK_SECONDS = DAY_SECONDS * 7

PHASES = WEEKDAY_PHASES
CYCLE_SECONDS = DAY_SECONDS

_JITTER_RNG = random.Random(42)
_START_TIME: float | None = None


def _get_start_time() -> float:
    global _START_TIME
    if _START_TIME is None:
        _START_TIME = time.monotonic()
    return _START_TIME


class ScenarioClock:

    def __init__(self, started_at: float | None = None) -> None:
        self._started_at = started_at

    def _elapsed(self) -> float:
        if self._started_at is not None:
            return time.monotonic() - self._started_at
        return time.monotonic() - _get_start_time()

    def week_position(self) -> tuple[int, int]:
        elapsed = self._elapsed()
        week_pos = elapsed % WEEK_SECONDS
        day_of_week = int(week_pos // DAY_SECONDS)
        seconds_into_day = int(week_pos % DAY_SECONDS)
        return day_of_week, seconds_into_day

    def cycle_position(self) -> int:
        _, sec = self.week_position()
        return sec

    def is_weekend(self) -> bool:
        day, _ = self.week_position()
        return day >= 5

    def current_phase(self) -> Phase:
        day_of_week, seconds_into_day = self.week_position()
        phases = WEEKEND_PHASES if day_of_week >= 5 else WEEKDAY_PHASES
        cursor = 0
        for phase in phases:
            cursor += phase.duration_seconds
            if seconds_into_day < cursor:
                return _apply_jitter(phase)
        return _apply_jitter(phases[-1])

    def day_progress(self) -> float:
        _, sec = self.week_position()
        return sec / DAY_SECONDS

    def week_progress(self) -> float:
        elapsed = self._elapsed()
        return (elapsed % WEEK_SECONDS) / WEEK_SECONDS

    def elapsed_days(self) -> float:
        return self._elapsed() / DAY_SECONDS

    def elapsed_weeks(self) -> float:
        return self._elapsed() / WEEK_SECONDS


def _apply_jitter(phase: Phase) -> Phase:
    user_jitter = 1.0 + _JITTER_RNG.uniform(-0.15, 0.15)
    delay_jitter = 1.0 + _JITTER_RNG.uniform(-0.10, 0.10)
    error_jitter = 1.0 + _JITTER_RNG.uniform(-0.20, 0.20)

    return Phase(
        name=phase.name,
        duration_seconds=phase.duration_seconds,
        target_users=max(1, round(phase.target_users * user_jitter)),
        spawn_rate=phase.spawn_rate,
        browse_delay=_jitter_bounds(phase.browse_delay, delay_jitter),
        search_delay=_jitter_bounds(phase.search_delay, delay_jitter),
        product_delay=_jitter_bounds(phase.product_delay, delay_jitter),
        cart_delay=_jitter_bounds(phase.cart_delay, delay_jitter),
        checkout_delay=_jitter_bounds(phase.checkout_delay, delay_jitter),
        checkout_error_rate=max(0.0, phase.checkout_error_rate * error_jitter),
        worker_delay=_jitter_bounds(phase.worker_delay, delay_jitter),
        worker_error_rate=max(0.0, phase.worker_error_rate * error_jitter),
        request_cpu_burn_ms=phase.request_cpu_burn_ms,
        worker_cpu_burn_ms=phase.worker_cpu_burn_ms,
        memory_growth_bytes=phase.memory_growth_bytes,
        memory_decay_bytes=phase.memory_decay_bytes,
        cache_ttl_seconds=phase.cache_ttl_seconds,
    )


def _jitter_bounds(bounds: tuple[float, float], factor: float) -> tuple[float, float]:
    return (max(0.001, bounds[0] * factor), max(0.002, bounds[1] * factor))
