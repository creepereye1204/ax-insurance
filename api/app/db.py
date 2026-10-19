from pgvector.psycopg import register_vector
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

from .config import settings


def _configure(conn):
    register_vector(conn)


pool = ConnectionPool(
    settings.database_url,
    min_size=1,
    max_size=5,
    kwargs={"row_factory": dict_row},
    configure=_configure,
    open=False,
)
