# DB Init

当前 `docker-compose.prod.yml` 已直接挂载：

`../backend-springboot/sql` -> `/docker-entrypoint-initdb.d`

即容器首次启动会自动执行 `backend-springboot/sql/schema.sql` 与 `seed.sql`。

本目录保留用于将来扩展（例如额外补丁脚本）。
