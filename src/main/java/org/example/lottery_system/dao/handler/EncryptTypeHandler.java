package org.example.lottery_system.dao.handler;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.example.lottery_system.dao.dataobject.Encrypt;
import org.springframework.util.StringUtils;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(Encrypt.class)//被处理类型
@MappedJdbcTypes(JdbcType.VARCHAR)//转换后jdbc类型
public class EncryptTypeHandler extends BaseTypeHandler<Encrypt> {
    //密钥
    private final byte[] key = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    /*
     * 设置加密值
     * @param ps 预编译对象
     * @param i 参数索引
     * @param parameter 参数值
     * @param jdbcType jdbc类型
     * @throws SQLException
     * */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Encrypt parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null || parameter.getValue() == null) {
            ps.setString(i, null);
            return;
        }
        //加密前值
        System.out.println(parameter.getValue());
        //加密
        AES aes = SecureUtil.aes(key);
        String str = aes.encryptHex(parameter.getValue());
        ps.setString(i, str);
    }

    /*
     * 获取值
     * @param rs 结果集
     * @param columnName 列名
     * @return 加密值
     * @throws SQLException
     * */
    @Override
    public Encrypt getNullableResult(ResultSet rs, String columnName) throws SQLException {
        //获取值得到的加密值
        System.out.println(rs.getString(columnName));
        return decrypt(rs.getString(columnName));
    }

    /*
     * 获取值
     * @param rs 结果集
     * @param columnIndex 列索引
     * @return 加密值
     * @throws SQLException
     * */
    @Override
    public Encrypt getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        //获取值得到的加密值
        System.out.println(rs.getString(columnIndex));
        return decrypt(rs.getString(columnIndex));
    }

    /*
     * 获取值
     * @param cs 可调用语句
     * @param columnIndex 列索引
     * @return 加密值
     * @throws SQLException
     * */
    @Override
    public Encrypt getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        //获取值得到的加密值
        System.out.println(cs.getString(columnIndex));
        return decrypt((cs.getString(columnIndex)));
    }
    /*
     * 解密
     * @param str 加密值
     * @return 解密值
     * */
    private Encrypt decrypt(String str) {
        if (!StringUtils.hasText(str)) {
            return null;
        }
        return new Encrypt(SecureUtil.aes(key).decryptStr(str));
    }
}
