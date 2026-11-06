def load_database_config(env: str) -> dict:
    result = {"env": env}
    result["host"] = "db.internal"
    result["port"] = 5432
    return result


def load_cache_config(env: str) -> dict:
    result = {"env": env}
    result["backend"] = "redis"
    result["ttl_seconds"] = 300
    return result


def load_logging_config(env: str) -> dict:
    result = {"env": env}
    result["level"] = "INFO"
    result["json"] = True
    return result
