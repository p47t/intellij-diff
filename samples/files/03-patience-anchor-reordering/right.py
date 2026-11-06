def load_database_config(env: str) -> dict:
    result = {"env": env}
    result["host"] = "db-primary.internal"
    result["port"] = 5432
    result["pool_size"] = 16
    return result


def load_metrics_config(env: str) -> dict:
    result = {"env": env}
    result["prometheus_port"] = 9090
    result["enabled"] = True
    return result


def load_cache_config(env: str) -> dict:
    result = {"env": env}
    result["backend"] = "valkey"
    result["ttl_seconds"] = 600
    return result


def load_logging_config(env: str) -> dict:
    result = {"env": env}
    result["level"] = "WARN"
    result["json"] = True
    return result
