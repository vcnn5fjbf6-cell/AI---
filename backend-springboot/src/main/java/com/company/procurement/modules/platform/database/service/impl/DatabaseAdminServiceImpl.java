package com.company.procurement.modules.platform.database.service.impl;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.platform.database.dto.DatabaseRowDeleteDTO;
import com.company.procurement.modules.platform.database.dto.DatabaseRowSaveDTO;
import com.company.procurement.modules.platform.database.dto.DatabaseRowUpdateDTO;
import com.company.procurement.modules.platform.database.dto.DatabaseTablePageQueryDTO;
import com.company.procurement.modules.platform.database.service.DatabaseAdminService;
import com.company.procurement.modules.platform.database.vo.DatabaseColumnVO;
import com.company.procurement.modules.platform.database.vo.DatabaseTableDetailVO;
import com.company.procurement.modules.platform.database.vo.DatabaseTableVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DatabaseAdminServiceImpl implements DatabaseAdminService {

    private static final String PERMISSION_VIEW = "database_admin:view";
    private static final String PERMISSION_CREATE = "database_admin:create";
    private static final String PERMISSION_UPDATE = "database_admin:update";
    private static final String PERMISSION_DELETE = "database_admin:delete";

    private static final Set<String> SEARCHABLE_TYPES = Set.of(
        "char",
        "varchar",
        "tinytext",
        "text",
        "mediumtext",
        "longtext",
        "enum",
        "set"
    );

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<DatabaseTableVO> listTables(String keyword) {
        requireSuperAdminPermission(PERMISSION_VIEW);
        String schema = currentSchema();
        StringBuilder sql = new StringBuilder("""
            SELECT TABLE_NAME, TABLE_COMMENT, ENGINE, TABLE_COLLATION, TABLE_ROWS, DATA_LENGTH, INDEX_LENGTH, CREATE_TIME, UPDATE_TIME
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'
            """);
        List<Object> args = new ArrayList<>();
        args.add(schema);
        if (StringUtils.hasText(keyword)) {
            sql.append(" AND (TABLE_NAME LIKE ? OR TABLE_COMMENT LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY TABLE_NAME");
        return jdbcTemplate.query(sql.toString(), this::mapTable, args.toArray());
    }

    @Override
    public DatabaseTableDetailVO detail(String tableName) {
        requireSuperAdminPermission(PERMISSION_VIEW);
        TableSnapshot snapshot = loadTable(tableName);
        DatabaseTableDetailVO detail = new DatabaseTableDetailVO();
        detail.setTable(snapshot.table());
        detail.setColumns(snapshot.columns());
        detail.setPrimaryKeys(primaryKeys(snapshot.columns()));
        return detail;
    }

    @Override
    public PageResult<Map<String, Object>> pageRows(String tableName, DatabaseTablePageQueryDTO queryDTO) {
        requireSuperAdminPermission(PERMISSION_VIEW);
        TableSnapshot snapshot = loadTable(tableName);
        List<DatabaseColumnVO> columns = snapshot.columns();
        if (columns.isEmpty()) {
            return PageResult.of(queryDTO.getPageNo(), queryDTO.getPageSize(), 0, List.of());
        }

        List<Object> countArgs = new ArrayList<>();
        String whereSql = buildKeywordWhere(columns, queryDTO.getKeyword(), countArgs);
        String quotedTable = quoteIdentifier(snapshot.table().getTableName());
        Long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + quotedTable + whereSql,
            Long.class,
            countArgs.toArray()
        );

        long pageNo = queryDTO.getPageNo();
        long pageSize = queryDTO.getPageSize();
        long offset = (pageNo - 1) * pageSize;
        List<Object> rowArgs = new ArrayList<>(countArgs);
        rowArgs.add(pageSize);
        rowArgs.add(offset);
        String selectColumns = columns.stream()
            .map(DatabaseColumnVO::getColumnName)
            .map(this::quoteIdentifier)
            .collect(Collectors.joining(", "));
        String sql = "SELECT " + selectColumns
            + " FROM " + quotedTable
            + whereSql
            + orderBySql(columns)
            + " LIMIT ? OFFSET ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, rowArgs.toArray());
        return PageResult.of(pageNo, pageSize, total == null ? 0 : total, normalizeRows(rows, columns));
    }

    @Override
    public void createRow(String tableName, DatabaseRowSaveDTO dto) {
        requireSuperAdminPermission(PERMISSION_CREATE);
        TableSnapshot snapshot = loadTable(tableName);
        Map<String, Object> values = normalizeWriteValues(dto.getValues(), snapshot.columns(), true);
        if (values.isEmpty()) {
            throw new BizException("DATABASE_ROW_VALUES_EMPTY", "没有可写入的字段");
        }
        String columnsSql = values.keySet().stream()
            .map(this::quoteIdentifier)
            .collect(Collectors.joining(", "));
        String placeholders = values.keySet().stream()
            .map(key -> "?")
            .collect(Collectors.joining(", "));
        jdbcTemplate.update(
            "INSERT INTO " + quoteIdentifier(snapshot.table().getTableName()) + " (" + columnsSql + ") VALUES (" + placeholders + ")",
            values.values().toArray()
        );
    }

    @Override
    public void updateRow(String tableName, DatabaseRowUpdateDTO dto) {
        requireSuperAdminPermission(PERMISSION_UPDATE);
        TableSnapshot snapshot = loadTable(tableName);
        List<String> primaryKeys = primaryKeys(snapshot.columns());
        if (primaryKeys.isEmpty()) {
            throw new BizException("DATABASE_TABLE_NO_PRIMARY_KEY", "该表没有主键，不能通过管理页编辑");
        }
        Map<String, DatabaseColumnVO> columnMap = columnMap(snapshot.columns());
        Map<String, Object> values = normalizeWriteValues(dto.getValues(), snapshot.columns(), false);
        primaryKeys.forEach(values::remove);
        if (values.isEmpty()) {
            throw new BizException("DATABASE_ROW_VALUES_EMPTY", "没有可更新的字段");
        }

        List<Object> args = new ArrayList<>(values.values());
        String whereSql = primaryKeyWhereSql(primaryKeys, columnMap, dto.getKeyValues(), args);
        String setSql = values.keySet().stream()
            .map(columnName -> quoteIdentifier(columnName) + " = ?")
            .collect(Collectors.joining(", "));
        jdbcTemplate.update(
            "UPDATE " + quoteIdentifier(snapshot.table().getTableName()) + " SET " + setSql + whereSql,
            args.toArray()
        );
    }

    @Override
    public void deleteRow(String tableName, DatabaseRowDeleteDTO dto) {
        requireSuperAdminPermission(PERMISSION_DELETE);
        TableSnapshot snapshot = loadTable(tableName);
        List<String> primaryKeys = primaryKeys(snapshot.columns());
        if (primaryKeys.isEmpty()) {
            throw new BizException("DATABASE_TABLE_NO_PRIMARY_KEY", "该表没有主键，不能通过管理页删除");
        }
        List<Object> args = new ArrayList<>();
        String whereSql = primaryKeyWhereSql(primaryKeys, columnMap(snapshot.columns()), dto.getKeyValues(), args);
        jdbcTemplate.update("DELETE FROM " + quoteIdentifier(snapshot.table().getTableName()) + whereSql, args.toArray());
    }

    private void requireSuperAdminPermission(String permissionCode) {
        AuthUser authUser = AuthContextHolder.requireUser();
        if (!DataScopeHelper.isSuperAdmin(authUser)) {
            throw new BizException("FORBIDDEN", "仅超级管理员可访问数据库管理");
        }
        if (!hasPermission(authUser, permissionCode)) {
            throw new BizException("FORBIDDEN", "缺少数据库管理操作权限：" + permissionCode);
        }
    }

    private boolean hasPermission(AuthUser authUser, String permissionCode) {
        List<String> permissions = authUser.getPermissionCodes();
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        return permissions.contains("*")
            || permissions.contains("*:*:*")
            || permissions.contains(permissionCode);
    }

    private TableSnapshot loadTable(String tableName) {
        DatabaseTableVO table = findTable(tableName);
        return new TableSnapshot(table, listColumns(table.getTableName()));
    }

    private DatabaseTableVO findTable(String tableName) {
        if (!StringUtils.hasText(tableName)) {
            throw new BizException("DATABASE_TABLE_NOT_FOUND", "数据表不存在");
        }
        String sql = """
            SELECT TABLE_NAME, TABLE_COMMENT, ENGINE, TABLE_COLLATION, TABLE_ROWS, DATA_LENGTH, INDEX_LENGTH, CREATE_TIME, UPDATE_TIME
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' AND TABLE_NAME = ?
            """;
        List<DatabaseTableVO> tables = jdbcTemplate.query(sql, this::mapTable, currentSchema(), tableName.trim());
        if (tables.isEmpty()) {
            throw new BizException("DATABASE_TABLE_NOT_FOUND", "数据表不存在");
        }
        return tables.get(0);
    }

    private List<DatabaseColumnVO> listColumns(String tableName) {
        String sql = """
            SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT, ORDINAL_POSITION
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
            ORDER BY ORDINAL_POSITION
            """;
        return jdbcTemplate.query(sql, this::mapColumn, currentSchema(), tableName);
    }

    private String currentSchema() {
        String schema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!StringUtils.hasText(schema)) {
            throw new BizException("DATABASE_SCHEMA_EMPTY", "未选择数据库");
        }
        return schema;
    }

    private DatabaseTableVO mapTable(ResultSet rs, int rowNum) throws SQLException {
        DatabaseTableVO table = new DatabaseTableVO();
        table.setTableName(rs.getString("TABLE_NAME"));
        table.setTableComment(rs.getString("TABLE_COMMENT"));
        table.setEngine(rs.getString("ENGINE"));
        table.setTableCollation(rs.getString("TABLE_COLLATION"));
        table.setTableRows(nullableLong(rs, "TABLE_ROWS"));
        table.setDataLength(nullableLong(rs, "DATA_LENGTH"));
        table.setIndexLength(nullableLong(rs, "INDEX_LENGTH"));
        table.setCreateTime(nullableDateTime(rs, "CREATE_TIME"));
        table.setUpdateTime(nullableDateTime(rs, "UPDATE_TIME"));
        return table;
    }

    private DatabaseColumnVO mapColumn(ResultSet rs, int rowNum) throws SQLException {
        DatabaseColumnVO column = new DatabaseColumnVO();
        column.setColumnName(rs.getString("COLUMN_NAME"));
        column.setDataType(rs.getString("DATA_TYPE"));
        column.setColumnType(rs.getString("COLUMN_TYPE"));
        column.setNullable(rs.getString("IS_NULLABLE"));
        column.setColumnKey(rs.getString("COLUMN_KEY"));
        column.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
        column.setExtra(rs.getString("EXTRA"));
        column.setComment(rs.getString("COLUMN_COMMENT"));
        column.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
        return column;
    }

    private Long nullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime nullableDateTime(ResultSet rs, String columnName) throws SQLException {
        Timestamp value = rs.getTimestamp(columnName);
        return value == null ? null : value.toLocalDateTime();
    }

    private String buildKeywordWhere(List<DatabaseColumnVO> columns, String keyword, List<Object> args) {
        if (!StringUtils.hasText(keyword)) {
            return "";
        }
        List<DatabaseColumnVO> searchableColumns = columns.stream()
            .filter(this::isSearchableColumn)
            .toList();
        if (searchableColumns.isEmpty()) {
            return "";
        }
        String like = "%" + keyword.trim() + "%";
        searchableColumns.forEach(column -> args.add(like));
        String conditions = searchableColumns.stream()
            .map(column -> quoteIdentifier(column.getColumnName()) + " LIKE ?")
            .collect(Collectors.joining(" OR "));
        return " WHERE (" + conditions + ")";
    }

    private String orderBySql(List<DatabaseColumnVO> columns) {
        List<String> keys = primaryKeys(columns);
        if (keys.isEmpty()) {
            keys = columns.stream()
                .findFirst()
                .map(column -> List.of(column.getColumnName()))
                .orElse(List.of());
        }
        if (keys.isEmpty()) {
            return "";
        }
        return " ORDER BY " + keys.stream()
            .map(this::quoteIdentifier)
            .collect(Collectors.joining(", "));
    }

    private List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows, List<DatabaseColumnVO> columns) {
        List<String> columnNames = columns.stream().map(DatabaseColumnVO::getColumnName).toList();
        List<Map<String, Object>> normalizedRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> normalizedRow = new LinkedHashMap<>();
            for (String columnName : columnNames) {
                normalizedRow.put(columnName, normalizeReadValue(row.get(columnName)));
            }
            normalizedRows.add(normalizedRow);
        }
        return normalizedRows;
    }

    private Object normalizeReadValue(Object value) {
        if (value instanceof byte[] bytes) {
            return "BINARY(" + bytes.length + " bytes)";
        }
        return value;
    }

    private Map<String, Object> normalizeWriteValues(
        Map<String, Object> rawValues,
        List<DatabaseColumnVO> columns,
        boolean create
    ) {
        if (rawValues == null || rawValues.isEmpty()) {
            return Map.of();
        }
        Map<String, DatabaseColumnVO> columnMap = columnMap(columns);
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawValues.entrySet()) {
            DatabaseColumnVO column = columnMap.get(entry.getKey());
            if (column == null) {
                throw new BizException("DATABASE_COLUMN_INVALID", "字段不存在：" + entry.getKey());
            }
            if (isGeneratedColumn(column)) {
                continue;
            }
            if (create && isAutoIncrement(column) && isBlankValue(entry.getValue())) {
                continue;
            }
            values.put(column.getColumnName(), normalizeWriteValue(column, entry.getValue()));
        }
        return values;
    }

    private String primaryKeyWhereSql(
        List<String> primaryKeys,
        Map<String, DatabaseColumnVO> columnMap,
        Map<String, Object> keyValues,
        List<Object> args
    ) {
        if (keyValues == null || keyValues.isEmpty()) {
            throw new BizException("DATABASE_PRIMARY_KEY_EMPTY", "主键值不能为空");
        }
        List<String> conditions = new ArrayList<>();
        for (String key : primaryKeys) {
            DatabaseColumnVO column = columnMap.get(key);
            Object value = keyValues.get(key);
            if (isBlankValue(value)) {
                throw new BizException("DATABASE_PRIMARY_KEY_EMPTY", "主键值不能为空：" + key);
            }
            conditions.add(quoteIdentifier(key) + " = ?");
            args.add(normalizeWriteValue(column, value));
        }
        return " WHERE " + String.join(" AND ", conditions);
    }

    private Map<String, DatabaseColumnVO> columnMap(List<DatabaseColumnVO> columns) {
        return columns.stream().collect(Collectors.toMap(DatabaseColumnVO::getColumnName, column -> column, (left, right) -> left, LinkedHashMap::new));
    }

    private List<String> primaryKeys(List<DatabaseColumnVO> columns) {
        return columns.stream()
            .filter(column -> "PRI".equalsIgnoreCase(column.getColumnKey()))
            .map(DatabaseColumnVO::getColumnName)
            .toList();
    }

    private boolean isSearchableColumn(DatabaseColumnVO column) {
        return SEARCHABLE_TYPES.contains(normalizeDataType(column));
    }

    private boolean isTextColumn(DatabaseColumnVO column) {
        return SEARCHABLE_TYPES.contains(normalizeDataType(column));
    }

    private boolean isAutoIncrement(DatabaseColumnVO column) {
        return StringUtils.hasText(column.getExtra()) && column.getExtra().toLowerCase(Locale.ROOT).contains("auto_increment");
    }

    private boolean isGeneratedColumn(DatabaseColumnVO column) {
        return StringUtils.hasText(column.getExtra()) && column.getExtra().toLowerCase(Locale.ROOT).contains("generated");
    }

    private boolean isNullable(DatabaseColumnVO column) {
        return "YES".equalsIgnoreCase(column.getNullable());
    }

    private String normalizeDataType(DatabaseColumnVO column) {
        return column.getDataType() == null ? "" : column.getDataType().trim().toLowerCase(Locale.ROOT);
    }

    private Object normalizeWriteValue(DatabaseColumnVO column, Object value) {
        if (value instanceof String text && text.isEmpty() && isNullable(column) && !isTextColumn(column)) {
            return null;
        }
        return value;
    }

    private boolean isBlankValue(Object value) {
        return value == null || (value instanceof String text && !StringUtils.hasText(text));
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private record TableSnapshot(DatabaseTableVO table, List<DatabaseColumnVO> columns) {
    }
}
