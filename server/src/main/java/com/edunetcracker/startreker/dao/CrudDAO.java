package com.edunetcracker.startreker.dao;

import com.edunetcracker.startreker.dao.annotations.Attribute;
import com.edunetcracker.startreker.dao.annotations.Table;
import com.edunetcracker.startreker.dao.annotations.PrimaryKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.support.rowset.SqlRowSet;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

public abstract class CrudDAO<T> {

    private JdbcTemplate jdbcTemplate;
    private Class<T> clazz;
    private String selectSql;
    private String createSql;
    private String updateSql;
    private String deleteSql;
    private String existsSql;
    private Map<Field, PrimaryKey> primaryMapper = new HashMap<>();
    private Map<Field, Attribute> mapper = new HashMap<>();
    private final Logger logger = LoggerFactory.getLogger(CrudDAO.class);

    public CrudDAO() {
        // Hack to get generic type class
        Type t = getClass().getGenericSuperclass();
        ParameterizedType pt = (ParameterizedType) t;
        this.clazz = (Class<T>) pt.getActualTypeArguments()[0];
        resolveFields();
        selectSql = assembleSelectSql();
        createSql = assembleCreateSql();
        updateSql = assembleUpdateSql();
        deleteSql = assembleDeleteSql();
        existsSql = assembleExistsSql();
    }

    public Optional<T> find(Number id) {
        SqlRowSet rowSet = jdbcTemplate.queryForRowSet(selectSql, id);
        if (!rowSet.next()) return Optional.empty();
        try {
            T entity = clazz.getConstructor().newInstance();
            for (Map.Entry<Field, PrimaryKey> entry : primaryMapper.entrySet()) {
                Object attr;
                attr = castTypes(
                        rowSet.getObject(entry.getValue().value()),
                        entry.getKey().getGenericType().getTypeName());
                entry.getKey().set(entity, attr);
            }
            for (Map.Entry<Field, Attribute> entry : mapper.entrySet()) {
                Object attr = castTypes(
                        rowSet.getObject(entry.getValue().value()),
                        entry.getKey().getGenericType().getTypeName());
                entry.getKey().set(entity, attr);
            }
            return Optional.of(entity);
        } catch (Exception e) {
            logger.warn(e.toString());
        }
        return Optional.empty();
    }

    public void save(T entity) {
        if (!isAlreadyExists(entity)) {
            PreparedStatementCreator psc = connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        createSql,
                        Statement.RETURN_GENERATED_KEYS);
                int i = 1;
                for(Object obj : resolveCreateParameters(entity)){
                    ps.setObject(i++, obj);
                }
                return ps;
            };
            KeyHolder holder = new GeneratedKeyHolder();
            jdbcTemplate.update(psc, holder);
            for(Field field : primaryMapper.keySet()){
                try {
                    field.set(entity, holder.getKeys().get(
                            field.getAnnotation(PrimaryKey.class)
                                    .value()));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        else update(entity);
    }

    public void delete(T entity) {
        jdbcTemplate.update(deleteSql, resolvePrimaryKeyParameters(entity));
    }

    protected void update(T entity) {
        jdbcTemplate.update(updateSql, resolveUpdateParameters(entity));
    }

    private boolean isAlreadyExists(T entity) {
        Long count = jdbcTemplate.queryForObject(
                existsSql,
                Long.class,
                resolvePrimaryKeyParameters(entity));
        return count > 0L;
    }

    private Object castTypes(Object attr, String fieldType) {
        if (attr instanceof BigDecimal) {
            BigDecimal bd = (BigDecimal) attr;
            if (fieldType.equals("java.lang.Integer")) {
                attr = bd.intValueExact();
            }
            if (fieldType.equals("java.lang.Long")) {
                attr = bd.longValueExact();
            }
        }
        return attr;
    }

    private Object[] resolveCreateParameters(T entity) {
        List<Object> objects = new ArrayList<>();
        addMapperParams(objects, entity, mapper);
        return objects.toArray();
    }

    private Object[] resolveUpdateParameters(T entity) {
        List<Object> objects = new ArrayList<>();
        addMapperParams(objects, entity, mapper);
        addMapperParams(objects, entity, primaryMapper);
        return objects.toArray();
    }

    private Object[] resolvePrimaryKeyParameters(T entity) {
        List<Object> objects = new ArrayList<>();
        addMapperParams(objects, entity, primaryMapper);
        return objects.toArray();
    }

    private void addMapperParams(List<Object> objects, T entity, Map<Field, ?> currMapper) {
        for (Field field : currMapper.keySet()) {
            try {
                objects.add(field.get(entity));
            } catch (IllegalAccessException e) {
                logger.warn(e.toString());
                throw new RuntimeException(e);
            }
        }
    }

    private String assembleCreateSql() {
        StringBuilder sb = new StringBuilder("INSERT INTO ");
        sb.append(clazz.getAnnotation(Table.class).value())
                .append(" (");
        for (Attribute attribute : mapper.values()) {
            sb.append(attribute.value()).append(", ");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append(") VALUES(");
        for (int i = 0; i < mapper.size(); i++) {
            sb.append("?, ");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append(")");
        return sb.toString();
    }

    private String assembleUpdateSql() {
        StringBuilder sb = new StringBuilder("UPDATE ");
        sb.append(clazz.getAnnotation(Table.class).value())
                .append(" SET ");
        for (Attribute attribute : mapper.values()) {
            sb.append(attribute.value()).append(" = ?, ");
        }
        sb.delete(sb.length() - 2, sb.length());
        addPrimaryKeysWhere(sb);
        return sb.toString();
    }

    private String assembleDeleteSql() {
        StringBuilder sb = new StringBuilder("DELETE FROM ");
        sb.append(clazz.getAnnotation(Table.class).value());
        addPrimaryKeysWhere(sb);
        return sb.toString();
    }

    private String assembleSelectSql() {
        StringBuilder sb = new StringBuilder("SELECT * FROM ");
        sb.append(clazz.getAnnotation(Table.class).value());
        addPrimaryKeysWhere(sb);
        return sb.toString();
    }

    private String assembleExistsSql() {
        StringBuilder sb = new StringBuilder("SELECT COUNT(*) FROM ");
        sb.append(clazz.getAnnotation(Table.class).value());
        addPrimaryKeysWhere(sb);
        return sb.toString();
    }

    private void addPrimaryKeysWhere(StringBuilder sb) {
        sb.append(" WHERE ");
        for (PrimaryKey primaryKey : primaryMapper.values()) {
            sb.append(primaryKey.value()).append(" = ? AND ");
        }
        sb.delete(sb.length() - 5, sb.length());
    }

    private void resolveFields() {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            Attribute attribute = field.getAnnotation(Attribute.class);
            if (attribute != null) mapper.put(field, attribute);
            PrimaryKey primaryKey = field.getAnnotation(PrimaryKey.class);
            if (primaryKey != null) primaryMapper.put(field, primaryKey);
        }
    }

    protected JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    @Autowired
    private void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
}