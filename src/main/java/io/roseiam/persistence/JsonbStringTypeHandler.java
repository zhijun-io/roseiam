package io.roseiam.persistence;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

public final class JsonbStringTypeHandler extends BaseTypeHandler<String> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String value, JdbcType type) throws SQLException {
        PGobject json = new PGobject();
        json.setType("jsonb");
        json.setValue(value);
        ps.setObject(i, json);
    }

    @Override
    public String getNullableResult(ResultSet rs, String name) throws SQLException {
        return rs.getString(name);
    }

    @Override
    public String getNullableResult(ResultSet rs, int index) throws SQLException {
        return rs.getString(index);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int index) throws SQLException {
        return cs.getString(index);
    }
}
