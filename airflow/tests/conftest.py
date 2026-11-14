"""Stubs mínimos do Airflow: permitem importar a DAG e testar as funções puras sem instalar o Airflow."""
import sys
import types
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "dags"))


def _install_airflow_stubs():
    decorators = types.ModuleType("airflow.decorators")
    postgres = types.ModuleType("airflow.providers.postgres.hooks.postgres")

    # @dag(...) devolve um construtor de DAG que não executa o corpo (só interessam os helpers do módulo)
    decorators.dag = lambda **_kw: (lambda fn: (lambda *a, **k: None))
    decorators.task = lambda *a, **k: (a[0] if a and callable(a[0]) else (lambda fn: fn))
    postgres.PostgresHook = type("PostgresHook", (), {})
    modulos = {
        "airflow": types.ModuleType("airflow"),
        "airflow.decorators": decorators,
        "airflow.providers": types.ModuleType("airflow.providers"),
        "airflow.providers.postgres": types.ModuleType("airflow.providers.postgres"),
        "airflow.providers.postgres.hooks": types.ModuleType("airflow.providers.postgres.hooks"),
        "airflow.providers.postgres.hooks.postgres": postgres,
    }
    for nome, mod in modulos.items():
        sys.modules.setdefault(nome, mod)


_install_airflow_stubs()
